package com.jarvis.assistant.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService

/**
 * The system Assistant role: when the user picks JARVIS as their "Digital
 * assistant app" (Settings → Apps → Default apps → Digital assistant app),
 * the home-swipe-corner / power-hold gesture lands here.
 *
 * Pattern verified against the AOSP VoiceInteractionService docs (Session 5):
 * the manifest binds this service with BIND_VOICE_INTERACTION and points its
 * android.voice_interaction meta-data at JarvisSessionService, which the
 * platform requires to exist — but the visible UI is the translucent
 * AssistantActivity, so onShowSession simply launches it instead of showing
 * a session window.
 */
class JarvisVoiceService : VoiceInteractionService() {

    override fun onReady() {
        active = this
    }

    override fun onDestroy() {
        active = null
        super.onDestroy()
    }

    override fun onShowSession(args: Bundle?, flags: Int) {
        // default impl would show a VoiceInteractionSession window; we summon
        // the overlay activity instead
        startActivity(
            Intent(this, AssistantActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        /** Non-null while the service is bound; used by the QS tile. */
        @Volatile
        var active: JarvisVoiceService? = null
    }
}
