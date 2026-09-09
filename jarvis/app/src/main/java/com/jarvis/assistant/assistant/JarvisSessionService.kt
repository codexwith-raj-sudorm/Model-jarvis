package com.jarvis.assistant.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * The session host the platform requires for a VoiceInteractionService
 * (declared via the android.voice_interaction meta-data). The real UI is the
 * translucent AssistantActivity, so per the documented "delegate to an
 * activity" pattern, onShow hands off with startAssistantActivity — the
 * session itself draws no window.
 */
class JarvisSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        object : VoiceInteractionSession(this) {

            override fun onShow(args: Bundle?, showFlags: Int) {
                // Summon the voice-first overlay; startAssistantActivity
                // applies the proper assistant window/launch flags.
                startAssistantActivity(
                    Intent(context, AssistantActivity::class.java)
                )
            }
        }
}
