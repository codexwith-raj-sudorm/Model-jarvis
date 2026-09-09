package com.jarvis.assistant.chat

/** Who said a thing. TOOL = a tool result fed back to the model. */
enum class Role { USER, ASSISTANT, SYSTEM, TOOL }

/**
 * One message in the conversation. [source] names where an assistant answer's
 * facts came from ("wikipedia", "web: duckduckgo", …) for citation display.
 */
data class ChatMessage(
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String? = null,
)
