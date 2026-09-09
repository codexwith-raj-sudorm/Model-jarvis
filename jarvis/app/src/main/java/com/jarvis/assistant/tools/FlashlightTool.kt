package com.jarvis.assistant.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext

/**
 * Torch toggle via CameraManager.setTorchMode — no camera permission needed,
 * no CameraDevice to open. Tracks the last commanded state so "toggle" works.
 */
class FlashlightTool : Tool {

    override val name = "flashlight"
    override val description = "Turn the phone's flashlight (torch) on or off."
    override val parameters = """{"action": "string, one of 'on', 'off', 'toggle' — defaults to 'toggle'"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val action = (args["action"] ?: args["mode"] ?: "toggle").lowercase()

        val manager = context.appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return "[error] camera service unavailable"

        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "[error] this phone has no flash unit"

        val target = when (action) {
            "on" -> true
            "off" -> false
            else -> !lastState // toggle
        }

        return try {
            manager.setTorchMode(cameraId, target)
            lastState = target
            "Flashlight ${if (target) "on" else "off"}."
        } catch (e: Exception) {
            "[error] couldn't switch the torch: ${e.message}"
        }
    }

    companion object {
        /** Last state we commanded — the camera id -> torch state map the
         *  system keeps isn't readable without a camera callback. */
        @Volatile
        private var lastState = false
    }
}
