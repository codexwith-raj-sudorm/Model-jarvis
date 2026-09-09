package com.jarvis.assistant.llm

import com.jarvis.assistant.chat.ChatMessage
import com.jarvis.assistant.chat.Role

/**
 * Hand-rolled chat templates for the big four GGUF families, detected from the
 * model filename (Qwen → ChatML, Gemma, Llama-3, Phi-3). Exotic GGUFs may need
 * an extra entry here — the parser is deliberately table-driven to make that a
 * three-line change.
 *
 * The native layer receives one fully-rendered prompt string; stop sequences
 * are *not* enforced natively (generation ends on EOG), so [stripStops] must
 * be applied to anything we show or feed back into history.
 */
enum class Family { CHATML, GEMMA, LLAMA3, PHI3 }

class ChatTemplate private constructor(val family: Family) {

    companion object {
        fun forModelFile(fileName: String): ChatTemplate {
            val n = fileName.lowercase()
            return when {
                "gemma" in n                    -> ChatTemplate(Family.GEMMA)
                "phi" in n                      -> ChatTemplate(Family.PHI3)
                "llama" in n                    -> ChatTemplate(Family.LLAMA3)
                // Qwen (and most ChatML friends) — also the sane default
                else                            -> ChatTemplate(Family.CHATML)
            }
        }
    }

    /** Per-family strings that signal "the assistant is done". */
    fun stops(): List<String> = when (family) {
        Family.CHATML -> listOf("<|im_start|>", "<|im_end|>")
        Family.GEMMA  -> listOf("<end_of_turn>", "<start_of_turn>")
        Family.LLAMA3 -> listOf("<|eot_id|>", "<|start_header_id|>")
        Family.PHI3   -> listOf("<|end|>", "<|assistant|>", "<|system|>", "<|user|>")
    }

    /** Truncates the text at the first stop string and trims. */
    fun stripStops(text: String): String {
        var out = text
        for (s in stops()) {
            val i = out.indexOf(s)
            if (i >= 0) out = out.substring(0, i)
        }
        return out.trim()
    }

    /**
     * Renders system + conversation into the model's prompt format.
     * Tool results are mapped to the closest role each family supports.
     * Note: BOS tokens are added by the tokenizer (add_special=true on the
     * native side), so they must NOT appear here.
     */
    fun render(system: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        when (family) {
            Family.CHATML -> {
                sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
                for (m in messages) {
                    val role = when (m.role) {
                        Role.TOOL -> "tool"
                        Role.USER -> "user"
                        Role.ASSISTANT -> "assistant"
                        Role.SYSTEM -> "system"
                    }
                    sb.append("<|im_start|>").append(role).append('\n')
                        .append(m.content).append("<|im_end|>\n")
                }
                sb.append("<|im_start|>assistant\n")
            }

            Family.GEMMA -> {
                // Gemma has no system/tool roles: fold the system text into the
                // first user turn and deliver tool results as user messages.
                var systemPending = system
                for (m in messages) {
                    when (m.role) {
                        Role.USER, Role.TOOL -> {
                            val body = if (systemPending.isNotEmpty())
                                "$systemPending\n\n${m.content}" else m.content
                            systemPending = ""
                            sb.append("<start_of_turn>user\n").append(body).append("<end_of_turn>\n")
                        }
                        Role.ASSISTANT ->
                            sb.append("<start_of_turn>model\n").append(m.content).append("<end_of_turn>\n")
                        Role.SYSTEM -> { /* folded above */ }
                    }
                }
                if (messages.none { it.role == Role.USER } && systemPending.isNotEmpty()) {
                    sb.append("<start_of_turn>user\n").append(systemPending).append("<end_of_turn>\n")
                }
                sb.append("<start_of_turn>model\n")
            }

            Family.LLAMA3 -> {
                sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                    .append(system).append("<|eot_id|>")
                for (m in messages) {
                    val role = when (m.role) {
                        Role.USER, Role.TOOL -> "user"   // 3.1 has ipython/tool, 3.0 does not
                        Role.ASSISTANT -> "assistant"
                        Role.SYSTEM -> "system"
                    }
                    sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                        .append(m.content).append("<|eot_id|>")
                }
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            }

            Family.PHI3 -> {
                sb.append("<|system|>\n").append(system).append("<|end|>\n")
                for (m in messages) {
                    when (m.role) {
                        Role.USER, Role.TOOL ->
                            sb.append("<|user|>\n").append(m.content).append("<|end|>\n")
                        Role.ASSISTANT ->
                            sb.append("<|assistant|>\n").append(m.content).append("<|end|>\n")
                        Role.SYSTEM -> { /* covered above */ }
                    }
                }
                sb.append("<|assistant|>\n")
            }
        }
        return sb.toString()
    }
}
