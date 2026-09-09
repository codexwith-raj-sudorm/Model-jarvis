package com.jarvis.assistant.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin wrapper over libjarvis_llama.so (see app/src/main/cpp/llama_jni.cpp).
 *
 * The native layer is mutex-serialized and abort-safe:
 *  - [stopGenerate] sets an atomic flag without taking the native lock, so it
 *    is instant and callable from the UI thread;
 *  - load/unload set the abort flag *before* locking, so a mid-generation
 *    switch never waits more than ~one decode step;
 *  - [isLoaded] reads a lock-free atomic.
 *
 * Unloading is launched on a background dispatcher on purpose: freeing a
 * multi-GB mmap can take a moment and must never jank the caller.
 */
class LlamaCppEngine : LlmEngine {

    companion object {
        init {
            System.loadLibrary("jarvis_llama")
        }
    }

    // --- native surface (names/signatures must match llama_jni.cpp exactly) ---
    private external fun nativeInit(modelPath: String, ctxLen: Int, threads: Int): Boolean
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeUnload()
    private external fun nativeStopGenerate()
    private external fun nativeGenerate(
        prompt: String,
        nPredict: Int,
        temp: Float,
        topP: Float,
        topK: Int,
        callback: Any?,
    ): String

    /**
     * The native code resolves `onToken(String)` via GetMethodID on whatever
     * object it is handed — this tiny bridge keeps the JNI contract in one
     * place and forwards pieces to a Kotlin lambda.
     */
    private class TokenForwarder(val sink: (String) -> Unit) {
        fun onToken(piece: String) = sink(piece)
    }

    override suspend fun load(modelPath: String, contextLen: Int, threads: Int): Boolean =
        withContext(Dispatchers.IO) {
            nativeInit(modelPath, contextLen, threads)
        }

    override fun isLoaded(): Boolean = nativeIsLoaded()

    override suspend fun generate(
        prompt: String,
        config: GenerationConfig,
        onToken: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        nativeGenerate(
            prompt,
            config.nPredict,
            config.temp,
            config.topP,
            config.topK,
            TokenForwarder(onToken),
        )
    }

    override fun stopGenerate() {
        nativeStopGenerate() // lock-free on the native side by design
    }

    override suspend fun unload() {
        // free() of a multi-GB mmap is not instant — keep it off the caller.
        withContext(Dispatchers.IO) { nativeUnload() }
    }
}
