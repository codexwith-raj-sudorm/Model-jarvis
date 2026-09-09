package com.jarvis.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.jarvis.assistant.core.ServiceLocator
import com.jarvis.assistant.ui.ChatScreen
import com.jarvis.assistant.ui.JarvisTheme

/**
 * Entry activity: permissions + the full chat UI. Everything heavy (engine,
 * speech, agent) lives in ServiceLocator, shared with the overlay and wake
 * service — this activity is just the window.
 */
class MainActivity : ComponentActivity() {

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the mic button simply does nothing until granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)

        if (!hasMicPermission()) micPermission.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            JarvisTheme {
                ChatScreen(overlay = false)
            }
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
