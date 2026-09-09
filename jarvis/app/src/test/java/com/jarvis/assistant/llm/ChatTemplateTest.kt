package com.jarvis.assistant.llm

import com.jarvis.assistant.chat.ChatMessage
import com.jarvis.assistant.chat.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Chat templates for the big four GGUF families. */
class ChatTemplateTest {

    private val convo = listOf(
        ChatMessage(Role.USER, "hello"),
        ChatMessage(Role.ASSISTANT, "hi, sir"),
        ChatMessage(Role.TOOL, "TOOL_RESULT datetime: It's Friday."),
    )

    @Test
    fun `qwen renders chatml with tool role`() {
        val t = ChatTemplate.forModelFile("Qwen3-1.7B-Q4_K_M.gguf")
        val prompt = t.render("SYS", convo)
        assertTrue(prompt.startsWith("<|im_start|>system\nSYS<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>tool\n"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `gemma folds system into first user turn and uses model role`() {
        val t = ChatTemplate.forModelFile("gemma-3-1b-it-Q4_K_M.gguf")
        val prompt = t.render("SYS", convo)
        assertTrue(prompt.startsWith("<start_of_turn>user\nSYS\n\nhello<end_of_turn>"))
        assertTrue(prompt.contains("<start_of_turn>model\nhi, sir<end_of_turn>"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
        // tool results are folded into user turns (Gemma has no tool role)
        assertTrue(prompt.contains("<start_of_turn>user\nTOOL_RESULT datetime"))
    }

    @Test
    fun `llama3 uses header blocks`() {
        val t = ChatTemplate.forModelFile("Meta-Llama-3-8B-Instruct.Q4_K_M.gguf")
        val prompt = t.render("SYS", convo)
        assertTrue(prompt.contains("<|start_header_id|>system<|end_header_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>assistant<|end_header_id|>\n\n"))
        assertTrue(prompt.endsWith("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun `phi3 uses pipe tags`() {
        val t = ChatTemplate.forModelFile("Phi-3.5-mini-instruct.Q4_K_M.gguf")
        val prompt = t.render("SYS", convo)
        assertTrue(prompt.startsWith("<|system|>\nSYS<|end|>\n"))
        assertTrue(prompt.endsWith("<|assistant|>\n"))
    }

    @Test
    fun `stripStops cuts at family stop strings`() {
        val t = ChatTemplate.forModelFile("qwen.gguf")
        assertEquals("done", t.stripStops("done<|im_end|> junk after"))
        assertEquals("done", t.stripStops("done<|im_start|>user\nmore"))
    }

    @Test
    fun `unknown families default to chatml`() {
        assertEquals(
            ChatTemplate.forModelFile("Qwen3-1.7B.gguf").family,
            ChatTemplate.forModelFile("mystery-model.gguf").family,
        )
    }
}
