package com.jarvis.assistant.memory

/**
 * Text embedding for long-term memory recall — pure Kotlin, fully offline,
 * deterministic. No model download, no native code.
 *
 * The reference implementation is [HashEmbedder]: signed feature hashing
 * ("the hashing trick") over word unigrams, word bigrams and character
 * 3-grams, L2-normalized. Character n-grams are what make it robust to the
 * things personal memory is full of — names, places, transliterations
 * ("Bhātpāra" ≈ "bhatpara"), and inflection ("sister's" ≈ "sisters").
 *
 * A future upgrade may swap in a transformer embedder via llama.cpp; the
 * interface is the seam, and stored vectors carry their dimensionality so
 * a different [dims] is detected rather than silently mis-scored.
 */
interface Embedder {
    val dims: Int

    /** Returns an L2-normalized vector of length [dims] (never null). */
    fun embed(text: String): FloatArray
}

/**
 * Feature-hashing embedder. Each feature hashes to an index in [0, dims)
 * and contributes its signed weight (+w or −w by a second hash bit);
 * the final vector is L2-normalized.
 *
 * Pipeline (validated by simulation before porting): fold → tokenize →
 * drop stopwords (English + Hinglish + Devanagari — function words
 * otherwise dominate short texts) → light plural stemming → features.
 * Features: word unigrams ×1.0, word bigrams ×0.5, char 3-grams over the
 * concatenated content words ×0.3. 512 dims keeps collision noise ~±0.03
 * for personal-store-sized inputs (hundreds of facts); related-paraphrase
 * pairs score ≈0.3, unrelated pairs ≈0.
 *
 * Known limit: true paraphrase ("what's my job" vs "works as a
 * schoolteacher") is out of reach for lexical hashing — the [Embedder]
 * interface is the seam for a future transformer embedder via llama.cpp.
 */
class HashEmbedder(override val dims: Int = 512) : Embedder {

    override fun embed(text: String): FloatArray {
        val v = FloatArray(dims)
        val words = fold(text).split(WS).filter { it.isNotEmpty() && it !in STOP }.map(::stem)
        for (w in words) addFeature(v, w, 1.0f)
        for (i in 0 until words.size - 1) addFeature(v, words[i] + "_" + words[i + 1], 0.5f)
        // char 3-grams over content words only — concentrated signal,
        // robust to names/places and small spelling variance
        val chars = words.joinToString("")
        if (chars.length >= 3) {
            for (i in 0..chars.length - 3) addFeature(v, "#" + chars.substring(i, i + 3), 0.3f)
        }
        // L2 normalize (empty text stays an all-zero vector)
        var sq = 0.0
        for (x in v) sq += (x * x).toDouble()
        if (sq > 0) {
            val inv = (1.0 / kotlin.math.sqrt(sq)).toFloat()
            for (i in v.indices) v[i] *= inv
        }
        return v
    }

    private fun addFeature(v: FloatArray, feature: String, weight: Float) {
        val h = fnv1a(feature)
        // unsigned 63-bit value mod dims — matches the validated simulation
        val idx = ((h ushr 1) % dims.toLong()).toInt()
        val sign = if (h and 1L == 0L) 1f else -1f
        v[idx] += sign * weight
    }

    companion object {
        private val WS = Regex("[\\s\\p{Punct}]+")

        /** 64-bit FNV-1a — fast, stable across processes and platforms. */
        private fun fnv1a(s: String): Long {
            var h = 0xcbf29ce484222325UL
            for (c in s) {
                h = h xor c.code.toULong()
                h *= 0x100000001b3UL
            }
            return h.toLong()
        }

        /** lowercase + strip diacritics: "Bhātpāra" → "bhatpara", "café" → "cafe". */
        private fun fold(s: String): String {
            val lower = s.lowercase()
            val nfd = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
            return nfd.filter { Character.getType(it.code) != Character.NON_SPACING_MARK.toInt() }
        }

        /** Light plural stemming: "lives" → "live", "sisters" → "sister". */
        private fun stem(w: String): String =
            if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss")) w.dropLast(1) else w

        /**
         * Function words that dominate short texts if left in — English,
         * romanized Hinglish, and Devanagari. Content words (incl. "name")
         * deliberately stay.
         */
        private val STOP = setOf(
            // English
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "of", "in", "on", "at", "to", "for", "from", "with", "by", "and", "or",
            "but", "if", "then", "when", "what", "where", "who", "whom", "whose",
            "why", "how", "which", "my", "your", "his", "her", "its", "our",
            "their", "me", "you", "him", "them", "i", "we", "they", "he", "she",
            "it", "this", "that", "these", "those", "there", "here", "do", "does",
            "did", "done", "have", "has", "had", "having", "will", "would", "can",
            "could", "should", "shall", "may", "might", "must", "not", "no",
            "nor", "so", "as", "than", "too", "very", "just", "about", "into",
            "over", "under", "again", "once", "all", "any", "both", "each",
            "few", "more", "most", "other", "some", "such", "only", "own",
            "same", "s", "t", "amp",
            // romanized Hinglish
            "ka", "ki", "ke", "ko", "se", "kya", "hai", "hain", "aur", "mera",
            "meri", "apna", "apni", "aap", "aapka", "aapki", "apka", "apki",
            "karta", "karti", "karna", "kar", "ho", "hona", "nahi", "nahin",
            "bata", "batao", "sunao", "chahiye", "please", "pls",
            // Devanagari
            "का", "की", "के", "को", "से", "में", "क्या", "है", "हैं", "और",
            "मेरा", "मेरी", "अपना", "अपनी", "आप", "आपका", "आपकी", "नहीं",
            "कर", "करना", "हो", "होना", "बताओ", "सुनाओ", "चाहिए",
        )
    }
}

/** Cosine similarity for L2-normalized vectors — a plain dot product. */
fun cosine(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return 0f
    var dot = 0f
    for (i in a.indices) dot += a[i] * b[i]
    return dot
}

/** FloatArray ↔ little-endian ByteArray for SQLite BLOB storage. */
object VecBytes {
    fun encode(v: FloatArray): ByteArray {
        val out = ByteArray(v.size * 4)
        for (i in v.indices) {
            val bits = java.lang.Float.floatToIntBits(v[i])
            out[i * 4] = (bits and 0xff).toByte()
            out[i * 4 + 1] = ((bits ushr 8) and 0xff).toByte()
            out[i * 4 + 2] = ((bits ushr 16) and 0xff).toByte()
            out[i * 4 + 3] = ((bits ushr 24) and 0xff).toByte()
        }
        return out
    }

    fun decode(b: ByteArray): FloatArray? {
        if (b.size % 4 != 0) return null
        val out = FloatArray(b.size / 4)
        for (i in out.indices) {
            val bits = (b[i * 4].toInt() and 0xff) or
                ((b[i * 4 + 1].toInt() and 0xff) shl 8) or
                ((b[i * 4 + 2].toInt() and 0xff) shl 16) or
                ((b[i * 4 + 3].toInt() and 0xff) shl 24)
            out[i] = java.lang.Float.intBitsToFloat(bits)
        }
        return out
    }
}
