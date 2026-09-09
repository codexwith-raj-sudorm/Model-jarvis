package com.jarvis.assistant.agent

import com.jarvis.assistant.chat.ChatLog
import com.jarvis.assistant.chat.ChatMessage
import com.jarvis.assistant.chat.Role
import com.jarvis.assistant.llm.ChatTemplate
import com.jarvis.assistant.llm.GenerationConfig
import com.jarvis.assistant.llm.LlmEngine
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The agent loop: prompt assembly → generation → TOOL_CALL parsing → tool
 * execution → repeat, up to [MAX_TOOL_ROUNDS] rounds.
 *
 * Context-overflow hardening (Session 12) lives here, at the choke points:
 *  - every tool result is clamped to [MAX_TOOL_RESULT_CHARS] *before* it can
 *    enter history — one guard covers all current and future tools;
 *  - the whole conversation fed to the model is trimmed oldest-first to a
 *    [HISTORY_CHAR_BUDGET] character budget (≈8000 chars ≈ safe for a 2048
 *    token ctx even with Hindi/Hinglish's ~2 chars/token ratio), always
 *    preserving the last [MIN_PRESERVED_TURNS] turns.
 */
class Orchestrator(
    private val engine: LlmEngine,
    /** Re-derived when the active model changes (per-family prompt format). */
    @Volatile var template: ChatTemplate,
    private val registry: ToolRegistry,
    private val memory: com.jarvis.assistant.memory.MemoryStore,
) {

    var generationConfig = GenerationConfig(
        nPredict = 384,
        temp = 0.6f,
        topP = 0.9f,
        topK = 40,
    )

    /** Voice mode: shorter answers, no markdown, source attribution. */
    @Volatile
    var voiceMode: Boolean = false

    /**
     * Runs one full user turn. Streams tokens via [onToken], tool activity
     * via [onStatus]. Appends to [chatLog]. Returns the final assistant
     * message.
     */
    suspend fun handleUserInput(
        text: String,
        chatLog: ChatLog,
        toolContext: ToolContext,
        onToken: (String) -> Unit = {},
        onStatus: (String) -> Unit = {},
    ): ChatMessage {
        chatLog.add(Role.USER, text)

        var rounds = 0
        while (true) {
            val messages = budgetedHistory(chatLog)
            val system = buildSystemPrompt(text, toolContext)
            val prompt = template.render(system, messages)

            val raw = try {
                engine.generate(prompt, generationConfig, onToken)
            } catch (e: CancellationException) {
                throw e // rethrow so coroutine cancellation propagates
            }

            val cleaned = template.stripStops(raw)
            val parsed = ToolCallParser.parse(cleaned)

            if (parsed.calls.isEmpty() || rounds >= MAX_TOOL_ROUNDS) {
                // final answer (or the parser saw nothing callable)
                val answer = if (parsed.calls.isEmpty()) {
                    parsed.speech.ifBlank { cleaned.trim() }
                } else {
                    // hit the round cap with the model STILL calling tools:
                    // answer from what we have rather than looping forever
                    parsed.speech.ifBlank {
                        "I gathered the information but couldn't finish composing an answer — " +
                            "please ask again."
                    }
                }
                return chatLog.add(Role.ASSISTANT, answer)
            }

            // execute every call, feed results back as TOOL messages
            for (call in parsed.calls) {
                onStatus("⚙ ${call.name}…")
                val result = runCatching {
                    registry.execute(call.name, call.args, toolContext)
                }.getOrElse { "[error] ${call.name}: ${it.message}" }

                val clamped = clamp(result, MAX_TOOL_RESULT_CHARS)
                chatLog.add(Role.TOOL, "TOOL_RESULT ${call.name}: $clamped")
            }
            rounds++
        }
    }

    // ---- system prompt --------------------------------------------------------

    private fun buildSystemPrompt(userQuery: String, toolContext: ToolContext): String {
        val sb = StringBuilder()
        sb.append(
            "You are JARVIS — modeled on Tony Stark's AI butler. You run entirely on " +
                "the user's phone; no cloud is involved. Personality: an unflappable " +
                "British butler — calm, dry wit, quietly competent, never sycophantic. " +
                "Address the user as \"sir\" (an occasional \"boss\" for variety). Be " +
                "concise and precise; a touch of understated humour when it fits, never " +
                "at the cost of clarity. You may close with a short butler flourish " +
                "(\"Anything else, sir?\") — but not on every turn. The user may speak " +
                "English or Hindi/Hinglish; always reply in the language of the " +
                "question.\n\n"
        )

        sb.append("TOOLS\n").append(registry.manifest()).append('\n')
        sb.append(
            "To use a tool, output exactly one line and nothing else:\n" +
                "TOOL_CALL {\"name\": \"tool_name\", \"args\": {\"key\": \"value\"}}\n" +
                "After tool results arrive (as TOOL_RESULT messages), use them to answer. " +
                "If a tool returns [error] or nothing useful, say so honestly and answer " +
                "from your own knowledge, noting it may be out of date.\n\n"
        )

        if (toolContext.webEnabled) {
            WebIntents.suggestedTool(WebIntents.classify(userQuery))?.let { tool ->
                sb.append("NOTE: the user's question needs fresh data — strongly consider " +
                    "the \"$tool\" tool first.\n\n")
            }
        } else if (WebIntents.needsWeb(userQuery)) {
            sb.append(
                "NOTE: web access is turned OFF right now. If you cannot answer offline, " +
                    "say you'd need the internet for that instead of guessing stale facts.\n\n"
            )
        }

        if (voiceMode) {
            sb.append(
                "VOICE MODE: your reply will be spoken aloud. Answer in at most 3 short " +
                    "sentences of plain text — no markdown, no lists, no URLs. Mention the " +
                    "source naturally (e.g. 'according to Wikipedia, sir').\n\n"
            )
        }

        val facts = memory.searchFacts(userQuery, limit = 5)
        if (facts.isNotEmpty()) {
            sb.append("FACTS ABOUT THE USER (from long-term memory):\n")
            facts.forEach { sb.append("- ").append(it).append('\n') }
            sb.append('\n')
        }

        sb.append("Current date/time: ").append(nowFormatted())
        return sb.toString()
    }

    private fun nowFormatted(): String =
        SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", Locale.ENGLISH).format(Date())

    // ---- history budget ---------------------------------------------------------

    /**
     * Keeps total content within [HISTORY_CHAR_BUDGET] by dropping the oldest
     * turns first, but never fewer than the last [MIN_PRESERVED_TURNS] turns
     * (a fresh question + its tool trail must survive).
     */
    private fun budgetedHistory(chatLog: ChatLog): List<ChatMessage> {
        val history = chatLog.historyForPrompt()
        var total = history.sumOf { it.content.length }
        if (total <= HISTORY_CHAR_BUDGET) return history

        val kept = history.toMutableList()
        while (kept.size > MIN_PRESERVED_TURNS) {
            total -= kept.first().content.length
            kept.removeAt(0)
            if (total <= HISTORY_CHAR_BUDGET) break
        }
        return kept
    }

    private fun clamp(text: String, max: Int): String =
        if (text.length <= max) text else text.substring(0, max - 1) + "…"

    companion object {
        const val MAX_TOOL_ROUNDS = 3
        const val MAX_TOOL_RESULT_CHARS = 1500
        const val HISTORY_CHAR_BUDGET = 8000

        /** The freshest turns that always survive budget trimming. */
        const val MIN_PRESERVED_TURNS = 2
    }
}
