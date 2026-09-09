package com.jarvis.assistant.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jarvis.assistant.chat.ChatMessage
import com.jarvis.assistant.chat.Role
import com.jarvis.assistant.web.ParagraphRanker

/**
 * Long-term memory + chat history, all on-device in jarvis.db.
 *
 * Facts: "my name is Raj", "I live in Bhātpāra" — injected into the system
 * prompt when relevant. Chat log: every turn, for continuity.
 *
 * Retrieval is semantic: each fact carries a [HashEmbedder] vector and
 * queries score by cosine similarity, blended with a small lexical-overlap
 * bonus (exact keyword hits still matter for names). Rows written before
 * the embedding column are backfilled lazily on first search.
 */
class MemoryStore(
    context: Context,
    private val embedder: Embedder = HashEmbedder(),
) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE facts(
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 text TEXT NOT NULL,
                 created_at INTEGER NOT NULL,
                 embedding BLOB)"""
        )
        db.execSQL("CREATE INDEX idx_facts_created ON facts(created_at DESC)")
        db.execSQL(
            """CREATE TABLE chat_log(
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 role TEXT NOT NULL,
                 content TEXT NOT NULL,
                 created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_chat_created ON chat_log(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 → v2: add the embedding column; existing facts are kept and
            // backfilled lazily on the first search
            db.execSQL("ALTER TABLE facts ADD COLUMN embedding BLOB")
        }
    }

    // ---- facts ----------------------------------------------------------------

    fun saveFact(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        writableDatabase.execSQL(
            "INSERT INTO facts(text, created_at, embedding) VALUES(?, ?, ?)",
            arrayOf(trimmed, System.currentTimeMillis(), VecBytes.encode(embedder.embed(trimmed))),
        )
    }

    fun allFacts(): List<String> =
        readableDatabase.rawQuery("SELECT text FROM facts ORDER BY created_at DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    /**
     * Semantic top-k: cosine(query, fact) plus a small exact-token bonus,
     * newest first on ties. Cosine runs on stored embeddings; pre-v2 rows
     * are embedded and persisted on first touch. Returns at most [limit]
     * facts whose relevance clears [minScore].
     */
    fun searchFacts(query: String, limit: Int = 5, minScore: Float = 0.20f): List<String> {
        val rows = readableDatabase.rawQuery(
            "SELECT id, text, embedding FROM facts ORDER BY created_at DESC", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getBlob(2)))
            }
        }
        if (rows.isEmpty()) return emptyList()

        val qTerms = ParagraphRanker.tokenize(query).toSet()
        if (qTerms.isEmpty()) return rows.take(limit).map { it.second } // "what do you know": freshest

        val qVec = embedder.embed(query)
        val scored = rows.mapIndexedNotNull { i, (id, text, blob) ->
            var vec = blob?.let { VecBytes.decode(it) }
            if (vec == null || vec.size != embedder.dims) {
                // pre-v2 row (or embedder changed): embed now, persist back
                vec = embedder.embed(text)
                writableDatabase.execSQL("UPDATE facts SET embedding = ? WHERE id = ?",
                    arrayOf(VecBytes.encode(vec), id))
            }
            val overlap = ParagraphRanker.tokenize(text).count { it in qTerms }
            // blend: semantic first, exact keyword hits as a bonus
            val score = cosine(qVec, vec) + 0.15f * overlap / qTerms.size.coerceAtLeast(1)
            if (score >= minScore) i to score else null
        }
        return scored
            .sortedWith(compareByDescending<Pair<Int, Float>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { rows[it.first].second }
    }

    // ---- chat log ---------------------------------------------------------------

    fun logMessage(role: Role, content: String) {
        val cv = ContentValues().apply {
            put("role", role.name)
            put("content", content)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("chat_log", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Loads persisted history back into a fresh ChatLog (oldest first). */
    fun loadHistory(limit: Int = 100): List<ChatMessage> =
        readableDatabase.rawQuery(
            "SELECT role, content, created_at FROM chat_log ORDER BY created_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val role = try { Role.valueOf(c.getString(0)) } catch (_: Exception) { Role.USER }
                    add(ChatMessage(role, c.getString(1), timestamp = c.getLong(2)))
                }
            }.asReversed()
        }

    companion object {
        private const val DB_NAME = "jarvis.db"
        private const val DB_VERSION = 1
    }
}
