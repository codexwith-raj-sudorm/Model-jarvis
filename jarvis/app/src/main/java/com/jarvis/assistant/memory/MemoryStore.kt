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
 * prompt when lexically relevant. Chat log: every turn, for continuity.
 *
 * Retrieval is lexical for now (token overlap scoring); the roadmap replaces
 * this with embeddings once a small embedder ships on-device.
 */
class MemoryStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE facts(
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 text TEXT NOT NULL,
                 created_at INTEGER NOT NULL)"""
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
        // pre-1.0 scaffold: wipe-and-rebuild is fine, nothing precious yet
        db.execSQL("DROP TABLE IF EXISTS facts")
        db.execSQL("DROP TABLE IF EXISTS chat_log")
        onCreate(db)
    }

    // ---- facts ----------------------------------------------------------------

    fun saveFact(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        writableDatabase.execSQL("INSERT INTO facts(text, created_at) VALUES(?, ?)",
            arrayOf(trimmed, System.currentTimeMillis()))
    }

    fun allFacts(): List<String> =
        readableDatabase.rawQuery("SELECT text FROM facts ORDER BY created_at DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    /**
     * Lexical top-k: facts sharing the most tokens with the query win,
     * newest first on ties. Returns at most [limit] facts.
     */
    fun searchFacts(query: String, limit: Int = 5): List<String> {
        val facts = allFacts()
        if (facts.isEmpty()) return emptyList()
        val qTerms = ParagraphRanker.tokenize(query).toSet()
        if (qTerms.isEmpty()) return facts.take(limit)
        return facts
            .mapIndexed { i, f -> i to ParagraphRanker.tokenize(f).count { it in qTerms } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { facts[it.first] }
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
