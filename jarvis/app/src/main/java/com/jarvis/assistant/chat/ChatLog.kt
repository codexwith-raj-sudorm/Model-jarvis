package com.jarvis.assistant.chat

import com.jarvis.assistant.memory.MemoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide shared conversation log. MainActivity and the voice overlay both
 * render this single source of truth, which is what makes conversation
 * continuity across screens work: talk in the overlay, read it back in the
 * chat screen, no hand-off protocol needed.
 */
class ChatLog(private val store: MemoryStore? = null) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    /** Appends + emits + (best effort) persists. Returns the message added. */
    fun add(role: Role, content: String, source: String? = null): ChatMessage {
        val msg = ChatMessage(role, content, source = source)
        _messages.value = _messages.value + msg
        store?.logMessage(role, content)
        return msg
    }

    /** Replaces the last message (used when finalizing a streamed reply). */
    fun replaceLast(role: Role, content: String, source: String? = null): ChatMessage {
        val msg = ChatMessage(role, content, source = source)
        val list = _messages.value.toMutableList()
        if (list.isNotEmpty() && list.last().role == role) list[list.lastIndex] = msg else list.add(msg)
        _messages.value = list
        return msg
    }

    /** Only the turns that matter for prompting (drops system chatter). */
    fun historyForPrompt(): List<ChatMessage> =
        _messages.value.filter { it.role != Role.SYSTEM }

    /**
     * Replaces the in-memory log WITHOUT re-persisting (used by ServiceLocator
     * to backfill from SQLite at startup — [add] would double-write).
     */
    fun restore(messages: List<ChatMessage>) {
        _messages.value = messages
    }

    fun clear() {
        _messages.value = emptyList()
    }

    companion object {
        /** How many recent turns the overlay shows when it opens. */
        const val OVERLAY_PREVIEW_TURNS = 4
    }
}
