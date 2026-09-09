package com.jarvis.assistant.speech

/**
 * Cleans model output for spoken delivery: markdown that sounds awful read
 * aloud is flattened, citation markers like [1] are dropped, URLs are read
 * as their label, and whitespace collapses. Voice mode pairs this with the
 * "3 short sentences" prompt rule.
 */
object SpeechFormatter {

    fun forSpeech(text: String): String {
        var t = text

        // fenced code blocks → "(code)" — nobody wants raw code read aloud
        t = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL).replace(t) { m ->
            if (m.value.replace("```", "").isBlank()) "" else " (code) "
        }
        t = Regex("`([^`]*)`").replace(t, "$1")

        // links: [label](url) → label ; bare URLs → dropped
        t = Regex("\\[([^\\]]*)\\]\\((?:https?://)?[^)]+\\)").replace(t, "$1")
        t = Regex("https?://\\S+").replace(t, "")

        // emphasis / headers / citation markers
        t = Regex("\\*\\*([^*]+)\\*\\*").replace(t, "$1")
        t = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)").replace(t, "$1")
        t = Regex("(?m)^#{1,6}\\s*").replace(t, "")
        t = Regex("\\[\\d+]").replace(t, "")
        t = Regex("```").replace(t, "")

        // list bullets and stray table pipes
        t = Regex("(?m)^\\s*[-*+]\\s+").replace(t, "")
        t = Regex("\\s*\\|\\s*").replace(t, ", ")

        // collapse whitespace
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    /** Hard cap for voice replies: at most [maxChars] characters, cut on a word. */
    fun capForSpeech(text: String, maxChars: Int = 600): String {
        if (text.length <= maxChars) return text
        val cut = text.substring(0, maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.substring(0, lastSpace) else cut).trim() + "…"
    }
}
