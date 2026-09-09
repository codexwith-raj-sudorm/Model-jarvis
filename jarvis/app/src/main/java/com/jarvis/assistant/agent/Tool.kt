package com.jarvis.assistant.agent

import android.content.Context
import com.jarvis.assistant.memory.MemoryStore
import com.jarvis.assistant.web.WebFetcher

/**
 * The agent-layer tool contract. Implementations must be stateless enough to
 * run on any thread; args arrive as strings (small models are unreliable with
 * typed JSON — leniency is deliberate). Return human/model-readable text;
 * throw on failure, the registry converts that into "[error] …".
 */
interface Tool {
    /** Short machine name used in TOOL_CALL, e.g. "weather". */
    val name: String

    /** One-line description for the system prompt. */
    val description: String

    /**
     * JSON-schema-ish parameter hint for the prompt, e.g.
     * `{"city": "string, optional"}`. Kept as a plain string on purpose:
     * the model only ever sees it as text.
     */
    val parameters: String

    suspend fun execute(args: Map<String, String>, context: ToolContext): String
}

/**
 * Everything a tool may need. [web] is the app's only networking class;
 * web tools must check [webEnabled] and degrade to a clear offline message.
 */
data class ToolContext(
    val appContext: Context,
    val web: WebFetcher?,
    val memory: MemoryStore?,
    val webEnabled: Boolean,
)
