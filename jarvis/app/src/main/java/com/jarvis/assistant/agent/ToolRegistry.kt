package com.jarvis.assistant.agent

/**
 * Tool registry: the manifest fed into the system prompt and the guarded
 * executor. Every tool failure is caught and stringified — the agent loop
 * must never die because a tool threw.
 */
class ToolRegistry {

    private val tools = LinkedHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun all(): List<Tool> = tools.values.toList()

    fun names(): List<String> = tools.keys.toList()

    /** Renders the tool manifest for the system prompt. */
    fun manifest(): String = tools.values.joinToString("\n") { t ->
        "- ${t.name}: ${t.description} | args: ${t.parameters}"
    }

    /**
     * Executes a tool by name. Unknown tools and exceptions both become
     * "[error] …" strings the model can read and recover from.
     */
    suspend fun execute(name: String, args: Map<String, String>, context: ToolContext): String {
        val tool = tools[name]
            ?: return "[error] unknown tool \"$name\". Available: ${names().joinToString(", ")}"
        return try {
            tool.execute(args, context)
        } catch (e: Exception) {
            "[error] ${tool.name} failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
