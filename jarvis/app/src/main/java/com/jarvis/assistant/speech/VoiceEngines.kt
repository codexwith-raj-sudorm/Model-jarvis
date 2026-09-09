package com.jarvis.assistant.speech

/**
 * Voice input (STT) contract.
 *
 * Threading contract (Session 11): [stopListening] and [shutdown] must be
 * non-blocking and safe from the UI thread — implementations clean up on
 * their own worker threads, never by joining them from the caller.
 */
interface VoiceInput {

    /** Human-readable engine name for the status line ("sherpa zipformer"). */
    val displayName: String

    /** Whether this engine can run right now (model present, etc.). */
    val isAvailable: Boolean

    /**
     * Starts one listening session. [onPartial] fires with live transcript
     * updates; [onFinal] fires once with the endpoint-detected final text and
     * ends the session; [onError] ends the session with a user-readable
     * message. Callbacks arrive on a worker thread — hop to the main thread
     * before touching UI state.
     */
    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    )

    /** Stops the current session; non-blocking. */
    fun stopListening()

    /** Releases engine resources; non-blocking. */
    fun shutdown()
}

/**
 * Voice output (TTS) contract. [stop] is the barge-in path: it must silence
 * playback *instantly* (before generation has even finished, ideally).
 */
interface SpeechOutput {

    val displayName: String
    val isAvailable: Boolean

    /**
     * Speaks [text]. If a previous utterance is playing it is replaced.
     * [onDone] is invoked exactly once when the utterance finishes, is
     * stopped, or fails — safe to use as the "re-open the mic" hook of the
     * hands-free loop.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null)

    /** Instant barge-in: silences audio and aborts generation; non-blocking. */
    fun stop()

    /** Releases engine resources; non-blocking. */
    fun shutdown()
}
