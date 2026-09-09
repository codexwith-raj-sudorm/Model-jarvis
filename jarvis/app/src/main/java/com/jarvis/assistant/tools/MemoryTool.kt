package com.jarvis.assistant.tools

import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext

/**
 * save_memory: stores a durable fact about the user ("my name is Raj",
 * "I take the 8:40 train"). Facts live in jarvis.db and are lexically
 * retrieved into every future system prompt.
 */
class MemoryTool : Tool {

    override val name = "save_memory"
    override val description =
        "Save a lasting fact about the user (name, home city, preferences, routines) to long-term memory."
    override val parameters = """{"text": "string, the fact to remember"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val fact = args["text"]?.takeIf { it.isNotBlank() }
            ?: args["fact"]?.takeIf { it.isNotBlank() }
            ?: args["value"]?.takeIf { it.isNotBlank() }
            ?: return "[error] save_memory needs the fact to store"

        val memory = context.memory ?: return "[error] memory store unavailable"
        memory.saveFact(fact)

        // a home-city fact also becomes the weather default
        val lower = fact.lowercase()
        if ("live in" in lower || "home city" in lower || "city is" in lower ||
            Regex("\\bmaine?\\s+\\w+\\s+(shehar|city)\\b").containsMatchIn(lower)
        ) {
            Regex("\\b(?:live in|city is)\\s+([\\p{L} .-]+)").find(lower)?.let { m ->
                val city = m.groupValues[1].trim().split(" ").firstOrNull { it.length > 2 }
                if (city != null) {
                    context.appContext
                        .getSharedPreferences("jarvis", android.content.Context.MODE_PRIVATE)
                        .edit().putString("home_city", city.replaceFirstChar { it.uppercase() })
                        .apply()
                }
            }
        }
        return "Saved to memory: \"$fact\""
    }
}
