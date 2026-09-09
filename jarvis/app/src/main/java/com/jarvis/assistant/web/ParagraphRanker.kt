package com.jarvis.assistant.web

/**
 * BM25-lite passage ranking — the "rank" half of mini-RAG. Given a query and
 * the paragraphs Readability extracted, returns the k most relevant chunks so
 * a 1–4B model only ever sees a few hundred words of any web page.
 *
 * Unicode-aware tokenization (\p{L}\p{N}) keeps Hindi/Hinglish working.
 */
object ParagraphRanker {

    private const val K1 = 1.5f
    private const val B = 0.75f

    fun tokenize(text: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }.toList()

    /**
     * Returns the top [k] paragraphs most relevant to [query], longest-first
     * as a stable tiebreak. Empty if there is nothing to rank.
     */
    fun topK(query: String, paragraphs: List<String>, k: Int = 4): List<String> {
        if (paragraphs.isEmpty()) return emptyList()
        val qTerms = tokenize(query).distinct()
        if (qTerms.isEmpty()) return paragraphs.sortedByDescending { it.length }.take(k)

        val docs = paragraphs.map { tokenize(it) }
        val avgLen = docs.sumOf { it.size }.toFloat() / docs.size
        if (avgLen <= 0f) return paragraphs.take(k)

        // document frequency per query term
        val df = HashMap<String, Int>()
        for (t in qTerms) {
            df[t] = docs.count { d -> t in d }
        }

        val scored = paragraphs.indices.map { i ->
            val doc = docs[i]
            var score = 0f
            for (t in qTerms) {
                val termDf = df[t] ?: 0
                if (termDf == 0) continue
                val tf = doc.count { it == t }.toFloat()
                if (tf == 0f) continue
                val idf = kotlin.math.ln((paragraphs.size - termDf + 0.5f) / (termDf + 0.5f) + 1f)
                val norm = tf * (K1 + 1f) /
                        (tf + K1 * (1f - B + B * doc.size / avgLen))
                score += idf * norm
            }
            Triple(score, paragraphs[i].length, i)
        }

        return scored
            .sortedWith(compareByDescending<Triple<Float, Int, Int>> { it.first }.thenByDescending { it.second })
            .take(k)
            .map { paragraphs[it.third] }
    }
}
