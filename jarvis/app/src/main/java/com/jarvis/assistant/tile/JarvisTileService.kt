package com.jarvis.assistant.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jarvis.assistant.assistant.AssistantActivity
import com.jarvis.assistant.assistant.JarvisVoiceService

/**
 * Quick Settings tile: summons the JARVIS overlay with one tap.
 *
 * Route order: if our VoiceInteractionService is live, showSession (native
 * assistant path); otherwise launch the overlay activity directly via
 * startActivityAndCollapse.
 */
class JarvisTileService : TileService() {

    override fun onStartListening() {
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            // Tile#subtitle needs API 29
            if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = "summon"
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val voiceService = JarvisVoiceService.active
        if (voiceService != null) {
            voiceService.showSession(null, 0)
            return
        }

        val intent = android.content.Intent(this, AssistantActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
