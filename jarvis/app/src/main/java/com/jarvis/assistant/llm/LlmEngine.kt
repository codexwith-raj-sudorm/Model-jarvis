package com.jarvis.assistant.llm

/**
 * Engine-agnostic LLM contract. Today there is one implementation
 * ([LlamaCppEngine] over libjarvis_llama.so); the interface exists so a
 * future engine (ExecuTorch/QNN) can drop in without touching the agent.
 */
interface LlmEngine {

    /** Loads a GGUF model. Returns false (instead of throwing) on failure. */
    suspend fun load(modelPath: String, contextLen: Int = 2048, threads: Int = 4): Boolean

    /** Cheap, non-blocking status check. */
    fun isLoaded(): Boolean

    /**
     * Generates a completion for a fully-rendered prompt.
     * [onToken] is invoked with each flushed piece (already UTF-8-sanitized
     * on the native side) from the generation thread — keep it cheap.
     * [grammar] (optional) is a GBNF grammar constraining the whole output —
     * used by the tool-call repair path to force valid TOOL_CALL JSON.
     * Returns the complete text (also sanitized).
     */
    suspend fun generate(
        prompt: String,
        config: GenerationConfig,
        onToken: (String) -> Unit = {},
        grammar: String? = null,
    ): String

    /**
     * Cooperative stop: the running generation exits at its next decode step.
     * Safe to call from the UI thread; never blocks.
     */
    fun stopGenerate()

    /** Unloads the model. Must free all native memory. */
    suspend fun unload()
}

/** Sampling + length knobs, mirroring what the JNI bridge accepts. */
data class GenerationConfig(
    val nPredict: Int = 512,
    val temp: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
)
