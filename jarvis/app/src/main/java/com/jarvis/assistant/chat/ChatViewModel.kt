package com.jarvis.assistant.chat

import android.content.Context
import com.jarvis.assistant.agent.ToolContext
import com.jarvis.assistant.core.ServiceLocator
import com.jarvis.assistant.llm.ChatTemplate
import com.jarvis.assistant.speech.SpeechFormatter
import com.jarvis.assistant.wake.JarvisWakeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-scoped UI state + orchestration entry point. Deliberately NOT an
 * Android ViewModel: one instance lives in ServiceLocator so MainActivity
 * and the AssistantActivity overlay share state, engine warmth and the
 * hands-free loop.
 */
class ChatViewModel(private val appContext: Context) {

    data class UiState(
        val engineStatus: String = "no model loaded",
        val modelName: String? = null,
        val generating: Boolean = false,
        val partialReply: String = "",
        val toolStatus: String? = null,
        val listening: Boolean = false,
        val partialTranscript: String = "",
        val speaking: Boolean = false,
        val handsFree: Boolean = false,
        val webEnabled: Boolean = true,
        val wakeArmed: Boolean = false,
        val sttName: String = "",
        val ttsName: String = "",
        val overlayVisible: Boolean = false,
    )

    private val _state = MutableStateFlow(
        UiState(
            webEnabled = ServiceLocator.isWebEnabled(appContext),
            handsFree = ServiceLocator.isHandsFree(appContext),
            wakeArmed = ServiceLocator.isWakeArmed(appContext),
            sttName = ServiceLocator.stt.displayName,
            ttsName = ServiceLocator.tts.displayName,
        )
    )
    val state: StateFlow<UiState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var generationJob: Job? = null

    /** CAS guard: a second send() while one turn is running is dropped. */
    private val sendGuard = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        loadModelIfIdle()
    }

    // ---- model lifecycle -----------------------------------------------------

    /** Loads the active (or only) GGUF once, off the UI thread. */
    fun loadModelIfIdle() {
        val engine = ServiceLocator.engine
        if (engine.isLoaded()) return
        val model = ServiceLocator.modelManager.activeModel()
        if (model == null) {
            _state.update { it.copy(engineStatus = "no model — run scripts/get_models.sh") }
            return
        }
        _state.update { it.copy(engineStatus = "loading ${model.name}…", modelName = model.name) }
        scope.launch(Dispatchers.IO) {
            val ok = engine.load(model.absolutePath, CTX_LEN, THREADS)
            if (ok) {
                ServiceLocator.orchestrator.template = ChatTemplate.forModelFile(model.name)
                _state.update { it.copy(engineStatus = "ready · ${model.name}") }
            } else {
                _state.update { it.copy(engineStatus = "failed to load ${model.name}") }
            }
        }
    }

    /** Model picker: warm-swap the GGUF (unload → load → re-template). */
    fun switchModel(file: java.io.File) {
        if (_state.value.modelName == file.name && ServiceLocator.engine.isLoaded()) return
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(engineStatus = "switching to ${file.name}…") }
            ServiceLocator.engine.stopGenerate()
            ServiceLocator.engine.unload()
            val ok = ServiceLocator.engine.load(file.absolutePath, CTX_LEN, THREADS)
            if (ok) {
                ServiceLocator.modelManager.setActive(file)
                ServiceLocator.orchestrator.template = ChatTemplate.forModelFile(file.name)
                _state.update { it.copy(engineStatus = "ready · ${file.name}", modelName = file.name) }
            } else {
                _state.update { it.copy(engineStatus = "failed to load ${file.name}") }
            }
        }
    }

    // ---- conversation ----------------------------------------------------------

    /**
     * Sends a user turn through the agent. [viaVoice] marks mic-sourced input
     * (reply is guaranteed speech-optimized and spoken). Runs on
     * Dispatchers.Default: prompt building hits SQLite and the token loop is
     * pure CPU — none of that belongs on the main thread.
     */
    fun send(text: String, viaVoice: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // "Good morning, JARVIS" → the briefing, no LLM round needed
        if (Briefing.matchesIntent(trimmed)) {
            deliverBriefing(force = true)
            return
        }

        if (_state.value.generating) return
        if (!sendGuard.compareAndSet(false, true)) return
        stopListening()

        ServiceLocator.orchestrator.voiceMode = viaVoice || _state.value.handsFree

        generationJob = scope.launch(Dispatchers.Default) {
            try {
                _state.update { it.copy(generating = true, partialReply = "", toolStatus = null) }
                val toolContext = ToolContext(
                    appContext = appContext,
                    web = ServiceLocator.web,
                    memory = ServiceLocator.memory,
                    webEnabled = ServiceLocator.isWebEnabled(appContext),
                )
                val reply = ServiceLocator.orchestrator.handleUserInput(
                    text = trimmed,
                    chatLog = ServiceLocator.chatLog,
                    toolContext = toolContext,
                    onToken = { piece -> _state.update { it.copy(partialReply = it.partialReply + piece) } },
                    onStatus = { s -> _state.update { it.copy(toolStatus = s) } },
                )
                _state.update { it.copy(generating = false, partialReply = "", toolStatus = null) }
                speakReply(reply.content)
            } catch (e: CancellationException) {
                _state.update { it.copy(generating = false, partialReply = "", toolStatus = null) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, partialReply = "", toolStatus = null) }
                ServiceLocator.chatLog.add(Role.ASSISTANT, "[error] ${e.message ?: "generation failed"}")
            } finally {
                sendGuard.set(false)
            }
        }
    }

    /** ■ button: halts the native token loop AND silences speech. */
    fun stopGeneration() {
        ServiceLocator.engine.stopGenerate() // instant, lock-free on the native side
        generationJob?.cancel()
        generationJob = null
        ServiceLocator.tts.stop()
        stopListening()
        _state.update { it.copy(generating = false, partialReply = "", toolStatus = null, speaking = false) }
    }

    private fun speakReply(text: String) {
        val spoken = SpeechFormatter.capForSpeech(SpeechFormatter.forSpeech(text))
        if (spoken.isBlank() || !ServiceLocator.tts.isAvailable) {
            reopenMicIfHandsFree()
            return
        }
        _state.update { it.copy(speaking = true) }
        ServiceLocator.tts.speak(spoken) {
            _state.update { it.copy(speaking = false) }
            reopenMicIfHandsFree()
        }
    }

    // ---- briefing ------------------------------------------------------------------

    /**
     * Delivers the morning briefing by voice. Returns false when suppressed
     * (already delivered today and not forced, or a turn is generating).
     * [force] backs the explicit "good morning" intent.
     */
    private fun deliverBriefing(force: Boolean): Boolean {
        if (!force && !Briefing.shouldDeliver(appContext)) return false
        if (_state.value.generating) return false
        if (!sendGuard.compareAndSet(false, true)) return false
        Briefing.markDelivered(appContext)
        stopListening()

        generationJob = scope.launch(Dispatchers.Default) {
            try {
                _state.update { it.copy(generating = true, toolStatus = "⚙ briefing…") }
                val toolContext = ToolContext(
                    appContext = appContext,
                    web = ServiceLocator.web,
                    memory = ServiceLocator.memory,
                    webEnabled = ServiceLocator.isWebEnabled(appContext),
                )
                val briefing = runCatching { Briefing.build(toolContext) }.getOrElse {
                    "Good morning, sir. I'm afraid the briefing service is " +
                        "unavailable at present."
                }
                _state.update { it.copy(generating = false, toolStatus = null) }
                ServiceLocator.chatLog.add(Role.ASSISTANT, briefing, source = "briefing")
                speakReply(briefing)
            } finally {
                sendGuard.set(false)
            }
        }
        return true
    }

    // ---- voice loop -------------------------------------------------------------

    fun startListening() {
        val s = _state.value
        if (s.listening || s.speaking || s.generating) return
        _state.update { it.copy(listening = true, partialTranscript = "") }
        ServiceLocator.stt.startListening(
            onPartial = { p -> _state.update { it.copy(partialTranscript = p) } },
            onFinal = { t ->
                _state.update { it.copy(listening = false, partialTranscript = "") }
                send(t, viaVoice = true)
            },
            onError = { _state.update { it.copy(listening = false, partialTranscript = "") } },
        )
    }

    fun stopListening() {
        ServiceLocator.stt.stopListening()
        _state.update { it.copy(listening = false, partialTranscript = "") }
    }

    /** Tap = instant barge-in (speak-over-it is parked — needs AEC, see README). */
    fun bargeIn() {
        if (_state.value.speaking) {
            ServiceLocator.tts.stop()
            _state.update { it.copy(speaking = false) }
            reopenMicIfHandsFree()
        }
    }

    /** The hands-free loop hook: reply finished → mic re-opens automatically. */
    private fun reopenMicIfHandsFree() {
        val s = _state.value
        if (s.handsFree && !s.generating && !s.speaking && !s.listening) {
            startListening()
        }
    }

    // ---- toggles ------------------------------------------------------------------

    fun toggleHandsFree() {
        val on = !_state.value.handsFree
        ServiceLocator.setHandsFree(appContext, on)
        _state.update { it.copy(handsFree = on) }
        if (!on) stopListening() else reopenMicIfHandsFree()
    }

    fun toggleWeb() {
        val on = !ServiceLocator.isWebEnabled(appContext)
        ServiceLocator.setWebEnabled(appContext, on)
        _state.update { it.copy(webEnabled = on) }
    }

    /**
     * Arms/disarms the wake service. The UI must have already obtained
     * POST_NOTIFICATIONS on 13+ before arming (see MainActivity).
     */
    fun setWake(enabled: Boolean) {
        ServiceLocator.setWakeArmed(appContext, enabled)
        if (enabled) JarvisWakeService.start(appContext)
        else JarvisWakeService.stop(appContext)
        _state.update { it.copy(wakeArmed = enabled) }
    }

    // ---- overlay lifecycle -----------------------------------------------------------

    fun onOverlayOpened() {
        _state.update { it.copy(overlayVisible = true) }
        // first summon of the morning gets the briefing; otherwise mic-hot
        if (!deliverBriefing(force = false)) startListening()
    }

    fun onOverlayClosed() {
        _state.update { it.copy(overlayVisible = false) }
        if (!_state.value.handsFree) stopListening()
    }

    fun shutdown() {
        scope.cancel()
        ServiceLocator.stt.shutdown()
        ServiceLocator.tts.shutdown()
    }

    companion object {
        /** llama.cpp decode threads — 4 is the sweet spot on big.LITTLE. */
        const val THREADS = 4
        const val CTX_LEN = 2048
    }
}
