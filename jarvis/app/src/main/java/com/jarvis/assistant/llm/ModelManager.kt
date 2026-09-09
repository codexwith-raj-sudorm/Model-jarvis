package com.jarvis.assistant.llm

import android.content.Context
import java.io.File

/**
 * Discovers GGUF models under files/models/ (pushed there by
 * scripts/get_models.sh) and remembers which one is active.
 *
 * Layout on device:
 *   /sdcard/Android/data/com.jarvis.assistant/files/models/*.gguf
 */
class ModelManager(private val context: Context) {

    val modelsDir: File
        get() = File(context.filesDir, "models")

    /** All GGUFs found, alphabetically. */
    fun list(): List<File> =
        modelsDir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** The remembered active model, else the largest GGUF as a sensible default. */
    fun activeModel(): File? {
        val models = list()
        if (models.isEmpty()) return null
        val preferred = prefs().getString(KEY_ACTIVE, null)
        return models.firstOrNull { it.name == preferred }
            ?: models.maxByOrNull { it.length() }
    }

    fun setActive(model: File) {
        prefs().edit().putString(KEY_ACTIVE, model.name).apply()
    }

    private fun prefs() = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACTIVE = "active_model"

        /** Where the model scripts install: external app-specific dir first. */
        fun baseDir(context: Context): File =
            context.getExternalFilesDir(null) ?: context.filesDir
    }
}
