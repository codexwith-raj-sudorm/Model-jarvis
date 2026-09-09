package com.jarvis.assistant.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import com.jarvis.assistant.core.ServiceLocator
import com.jarvis.assistant.ui.ChatScreen
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.wake.JarvisWakeService

/**
 * Translucent, voice-first overlay summoned by the assistant gesture, the QS
 * tile or the wake word.
 *
 * Behavior:
 *  - opens with the mic hot (auto-starts listening after a beat);
 *  - hands-free loop lives in the shared ChatViewModel: reply spoken →
 *    mic re-opens automatically (when 🎧 is on);
 *  - tap anywhere = instant barge-in (kills TTS);
 *  - pauses the wake service's mic while in front (no double-listening),
 *    resumes it when dismissed.
 */
class AssistantActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)

        setContent {
            JarvisTheme {
                ChatScreen(overlay = true)

                // pause the wake listener while this overlay owns the mic
                LaunchedEffect(Unit) {
                    JarvisWakeService.pause(this@AssistantActivity)
                }
            }
        }
    }

    override fun onDestroy() {
        val resumed = ServiceLocator.isWakeArmed(this)
        if (resumed) JarvisWakeService.resume(this)
        ServiceLocator.viewModel?.onOverlayClosed()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            finish()
        } else {
            super.onBackPressed()
        }
    }
}
