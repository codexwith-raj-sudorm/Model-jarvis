package com.jarvis.assistant.core

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.assistant.agent.Orchestrator
import com.jarvis.assistant.agent.ToolRegistry
import com.jarvis.assistant.chat.ChatLog
import com.jarvis.assistant.llm.ChatTemplate
import com.jarvis.assistant.llm.LlamaCppEngine
import com.jarvis.assistant.llm.ModelManager
import com.jarvis.assistant.memory.MemoryStore
import com.jarvis.assistant.speech.AndroidTtsEngine
import com.jarvis.assistant.speech.SherpaSttEngine
import com.jarvis.assistant.speech.SherpaTtsEngine
import com.jarvis.assistant.speech.SpeechOutput
import com.jarvis.assistant.speech.SttEngine
import com.jarvis.assistant.speech.VoiceInput
import com.jarvis.assistant.tools.AlarmTool
import com.jarvis.assistant.tools.CalendarTool
import com.jarvis.assistant.tools.CallTool
import com.jarvis.assistant.tools.DateTimeTool
import com.jarvis.assistant.tools.FlashlightTool
import com.jarvis.assistant.tools.MemoryTool
import com.jarvis.assistant.tools.SmsTool
import com.jarvis.assistant.tools.NewsTool
import com.jarvis.assistant.tools.TimerTool
import com.jarvis.assistant.tools.WeatherTool
import com.jarvis.assistant.tools.WebSearchTool
import com.jarvis.assistant.tools.WikipediaTool
import com.jarvis.assistant.web.WebFetcher

/**
 * The dependency graph — built once per process. MainActivity, the voice
 * overlay and the wake service all share these instances, which is what
 * makes cross-screen conversation continuity and warm-engine reuse work.
 *
 * Engine selection (graceful degrade):
 *   STT: sherpa zipformer when files/voice/asr is installed → system STT
 *   TTS: sherpa piper/vits when files/voice/tts is installed → system TTS
 *   LLM: whatever GGUF is in files/models (ChatViewModel reports if absent)
 */
object ServiceLocator {

    @Volatile
    private var built = false

    lateinit var memory: MemoryStore
        private set
    lateinit var web: WebFetcher
        private set
    lateinit var modelManager: ModelManager
        private set
    lateinit var modelDownloader: com.jarvis.assistant.llm.ModelDownloader
        private set
    lateinit var voicePacks: com.jarvis.assistant.llm.VoicePackManager
        private set
    lateinit var engine: LlamaCppEngine
        private set
    lateinit var registry: ToolRegistry
        private set
    lateinit var orchestrator: Orchestrator
        private set
    lateinit var chatLog: ChatLog
        private set
    lateinit var stt: VoiceInput
        private set
    lateinit var tts: SpeechOutput
        private set

    @Volatile
    var viewModel: com.jarvis.assistant.chat.ChatViewModel? = null
        private set

    fun init(context: Context) {
        if (built) return
        synchronized(this) {
            if (built) return
            val app = context.applicationContext

            memory = MemoryStore(app)
            web = WebFetcher(app)
            modelManager = ModelManager(app)
            modelDownloader = com.jarvis.assistant.llm.ModelDownloader(app, web, modelManager)
            voicePacks = com.jarvis.assistant.llm.VoicePackManager(app, web, onActivated = {
                // swap the live engine: if the new voice's files check out,
                // sherpa takes over from (or stays ahead of) system TTS
                val sherpa = com.jarvis.assistant.speech.SherpaTtsEngine(app)
                if (sherpa.isAvailable) {
                    (tts as? com.jarvis.assistant.speech.SherpaTtsEngine)?.invalidate()
                    tts = sherpa
                }
            })
            engine = LlamaCppEngine()

            registry = ToolRegistry().apply {
                register(WeatherTool())
                register(WikipediaTool())
                register(WebSearchTool())
                register(NewsTool())
                register(AlarmTool())
                register(TimerTool())
                register(FlashlightTool())
                register(DateTimeTool())
                register(MemoryTool())
                register(CallTool())
                register(SmsTool())
                register(CalendarTool())
            }

            orchestrator = Orchestrator(
                engine = engine,
                template = ChatTemplate.forModelFile(
                    modelManager.activeModel()?.name ?: "qwen3.gguf"
                ),
                registry = registry,
                memory = memory,
                replyLanguage = { voicePacks.activeReplyLang() },
            )

            chatLog = ChatLog(memory)
            chatLog.restore(memory.loadHistory())

            stt = SherpaSttEngine(app).takeIf { it.isAvailable } ?: SttEngine(app)
            tts = SherpaTtsEngine(app).takeIf { it.isAvailable } ?: AndroidTtsEngine(app)

            viewModel = com.jarvis.assistant.chat.ChatViewModel(app)

            built = true
        }
    }

    // ---- persisted toggles ---------------------------------------------------

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    /** Web layer state — ON by default (Session 6 decision), user-revocable. */
    fun isWebEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WEB, true)

    fun setWebEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WEB, enabled).apply()
    }

    /** Whether the user armed the wake listener (survives restarts of the app). */
    fun isWakeArmed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WAKE, false)

    fun setWakeArmed(context: Context, armed: Boolean) {
        prefs(context).edit().putBoolean(KEY_WAKE, armed).apply()
    }

    fun isHandsFree(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HANDS_FREE, false)

    fun setHandsFree(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_HANDS_FREE, on).apply()
    }

    private const val KEY_WEB = "web_enabled"
    private const val KEY_WAKE = "wake_armed"
    private const val KEY_HANDS_FREE = "hands_free"
}
