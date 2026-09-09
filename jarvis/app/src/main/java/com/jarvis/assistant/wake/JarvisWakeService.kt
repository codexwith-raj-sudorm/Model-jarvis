package com.jarvis.assistant.wake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.assistant.AssistantActivity

/**
 * Always-on "Hey JARVIS" wake word listener — a microphone-type foreground
 * service with a persistent (low-priority) notification.
 *
 * Lifecycle semantics (Session 9, deliberately chosen):
 *  - manual opt-in via the 👂 toggle; wake is OFF by default (battery);
 *  - survives app-close and screen-off (the service keeps running);
 *  - does NOT auto-start on boot — a boot receiver is parked until there's a
 *    user setting for it (P2 roadmap).
 *
 * Mic handoff: on detection the driver stops FIRST, then the overlay is
 * launched (the overlay re-opens the mic after a short grace window; see
 * SherpaSttEngine.openMicWithGrace).
 */
class JarvisWakeService : Service() {

    private val driverLock = Any()

    @Volatile
    private var driver: WakeDriver? = null

    /** Gate so a stop that races driver initialization is honored. */
    @Volatile
    private var wantListening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> stopDriver()
            ACTION_RESUME -> startDriver()
            else -> {
                startInForeground()
                startDriver()
            }
        }
        return START_STICKY
    }

    /**
     * Driver creation (ONNX init — the ASR fallback can take a second or
     * two) must never run on the main thread (Session 11 rules); heavy work
     * happens on a worker thread while holding [driverLock] so a concurrent
     * stopDriver() serializes correctly instead of racing.
     */
    private fun startDriver() {
        if (driver != null) return
        wantListening = true
        Thread({
            synchronized(driverLock) {
                if (!wantListening || driver != null) return@synchronized
                val d = bestWakeDriver(this)
                if (d == null) {
                    stopSelf()
                    return@synchronized
                }
                driver = d
                isRunning = true
                d.start(
                    onDetected = {
                        // hand the mic to the overlay: stop listening, then summon
                        stopDriver()
                        startActivity(
                            Intent(this, AssistantActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onError = { stopSelf() },
                )
            }
        }, "wake-driver-start").apply { isDaemon = true }.start()
    }

    /** Fast from any thread: flags + a release thread; no joins, no waits. */
    private fun stopDriver() {
        wantListening = false
        Thread({
            synchronized(driverLock) {
                driver?.stop() // non-blocking: flags + its own release threads
                driver = null
                isRunning = false
            }
        }, "wake-driver-stop").apply { isDaemon = true }.start()
    }

    override fun onDestroy() {
        wantListening = false
        stopDriver()
        super.onDestroy()
    }

    // ---- foreground plumbing ---------------------------------------------------

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Wake word listener",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows while \"Hey JARVIS\" listening is armed"
                    setShowBadge(false)
                }
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS is listening")
            .setContentText("Say \"Hey JARVIS\" to summon")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= 30) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "jarvis_wake"
        const val NOTIFICATION_ID = 42

        /** Pause the mic (overlay is taking over) without killing the service. */
        const val ACTION_PAUSE = "com.jarvis.assistant.wake.PAUSE"

        /** Resume listening after the overlay closed. */
        const val ACTION_RESUME = "com.jarvis.assistant.wake.RESUME"

        /** True while this service is actively listening (for the UI status line). */
        @Volatile
        var isRunning: Boolean = false

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, JarvisWakeService::class.java)
            )
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) {
            context.stopService(Intent(context, JarvisWakeService::class.java))
        }

        private fun send(context: Context, action: String) {
            context.startService(
                Intent(context, JarvisWakeService::class.java).setAction(action)
            )
        }
    }
}
