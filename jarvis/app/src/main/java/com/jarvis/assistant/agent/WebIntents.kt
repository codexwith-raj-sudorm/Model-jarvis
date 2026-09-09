package com.jarvis.assistant.agent

/**
 * Fresh-data router: decides from the *user's words* whether a question
 * needs the internet, and which tool would serve it best. This runs before
 * the LLM ever sees the prompt — it is the "when to go online" gate from the
 * design doc (§ privacy), and it understands English + Hindi/Hinglish cues.
 *
 * Offline mode (web disabled) the orchestrator uses [classify] to answer
 * honestly instead of hallucinating stale facts.
 */
object WebIntents {

    enum class Need { NONE, WEATHER, NEWS, LATEST, SCORES, TIME_SENSITIVE }

    fun classify(query: String): Need {
        val q = query.lowercase()
        val has = { vararg words: String -> words.any { it in q } }

        return when {
            has(
                "weather", "temperature", "forecast", "rain today", "how hot",
                "how cold", "humidity", "mausam", "मौसम", "garmi", "thand", "बारिश",
            ) -> Need.WEATHER

            has(
                "news", "headlines", "khabar", "khobar", "samachar", "समाचार", "खबर",
                "what's happening",
            ) -> Need.NEWS

            has(
                "score", "match", "स्कोर", "मैच",
            ) -> Need.SCORES

            has(
                "latest", "right now", "current", "today", "this week", "recent",
                "breaking", "live", "price of", "stock", "who won", "aaj", "abhi",
                "kal", "आज", "अभी", "कल", "इस सप्ताह",
            ) -> Need.LATEST

            else -> Need.NONE
        }
    }

    /** The tool that best serves a classified need, or null for offline Q&A. */
    fun suggestedTool(need: Need): String? = when (need) {
        Need.WEATHER -> "weather"
        Need.NEWS -> "news"
        Need.SCORES, Need.LATEST -> "web_search"
        Need.TIME_SENSITIVE, Need.NONE -> null
    }

    fun needsWeb(query: String): Boolean = classify(query) != Need.NONE
}
