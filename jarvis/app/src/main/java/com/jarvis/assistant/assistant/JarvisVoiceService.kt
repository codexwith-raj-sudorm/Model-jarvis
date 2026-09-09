package com.jarvis.assistant.assistant

import android.service.voice.VoiceInteractionService

/**
 * The system Assistant role: when the user picks JARVIS as their "Digital
 * assistant app" (Settings → Apps → Default apps → Digital assistant app),
 * the home-swipe-corner / power-hold gesture lands here.
 *
 * Pattern (verified against the AOSP/SDK docs, Session 5 + 16): the system
 * fires `showSession(args, flags)` on this service, the platform then asks
 * JarvisSessionService for a session and calls its `onShow` — THAT is where
 * the translucent AssistantActivity gets summoned (see JarvisSessionService).
 * This class itself is just the bound handle the platform talks to.
 */
class JarvisVoiceService : VoiceInteractionService() {

    override fun onReady() {
        active = this
    }

    override fun onDestroy() {
        active = null
        super.onDestroy()
    }

    // NOTE: there is deliberately no onShowSession override — the SDK has no
    // such overridable member (first CI run failed on exactly that).

    companion object {
        /** Non-null while the service is bound; used by the QS tile. */
        @Volatile
        var active: JarvisVoiceService? = null
    }
}
