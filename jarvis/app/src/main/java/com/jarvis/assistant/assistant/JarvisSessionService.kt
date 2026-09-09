package com.jarvis.assistant.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * The session host the platform requires for a VoiceInteractionService
 * (declared via the android.voice_interaction meta-data). The real UI is the
 * AssistantActivity overlay, so the session we hand back is a no-op shell —
 * it never draws a window.
 */
class JarvisSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        object : VoiceInteractionSession(this) {
            // nothing to show — AssistantActivity is the UI
        }
}
