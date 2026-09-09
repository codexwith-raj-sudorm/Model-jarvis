package com.jarvis.assistant.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Boot auto-start for the wake word — but ONLY if the user armed it
 * (👂 toggle / isWakeArmed). Session 9 parked this until a user setting
 * existed; the armed flag is exactly that setting, so "I said JARVIS is
 * allowed to listen" now survives a reboot too.
 */
class WakeBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // ServiceLocator isn't built in a receiver process-start path yet —
        // read the preference directly (cheap, no engine spin-up)
        val armed = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .getBoolean("wake_armed", false)
        if (!armed) return

        JarvisWakeService.start(context)
    }
}
