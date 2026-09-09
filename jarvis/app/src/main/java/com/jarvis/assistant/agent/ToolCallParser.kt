package com.jarvis.assistant.agent

import org.json.JSONObject

/**
 * Lenient parser for the model's tool-call protocol:
 *
 *   TOOL_CALL {"name": "weather", "args": {"city": "Kolkata"}}
 *
 * 1–4B models get this wrong in every way imaginable: fenced in ```json,
 * wrapped in <think> musing, quotes missing, trailing commas, arguments
 * inlined at top level. This parser absorbs all of it. Failing a hard parse,
 * it degrades to regex scraping of "name"/"key":"value" pairs.
 */
object ToolCallParser {

    data class ToolCall(val name: String, val args: Map<String, String>)

    data class Parsed(
        /** <think>…</think> musing, stripped from everything else. */
        val thinking: String,
        /** Spoken/visible text with think-blocks and TOOL_CALL segments removed. */
        val speech: String,
        /** All tool calls found, in order. */
        val calls: List<ToolCall>,
    )

    private val THINK_BLOCK = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
    private const val MARKER = "TOOL_CALL"

    fun stripThinking(text: String): String = THINK_BLOCK.replace(text, "").trim()

    fun parse(raw: String): Parsed {
        val thinkMatches = THINK_BLOCK.findAll(raw).toList()
        val thinking = thinkMatches.joinToString(" ") { it.value.removePrefix("<think>").removeSuffix("</think>").trim() }.trim()

        val calls = ArrayList<ToolCall>()
        val spans = ArrayList<IntRange>()

        var searchFrom = 0
        while (true) {
            val markerAt = raw.indexOf(MARKER, searchFrom)
            if (markerAt < 0) break
            val braceAt = raw.indexOf('{', markerAt + MARKER.length)
            // the '{' must be near the marker (model may add a colon/spaces)
            if (braceAt < 0 || braceAt - markerAt > 24) {
                searchFrom = markerAt + MARKER.length
                continue
            }
            val end = balancedBraceEnd(raw, braceAt)
            if (end < 0) {
                searchFrom = braceAt + 1
                continue
            }
            val body = raw.substring(braceAt, end + 1)
            parseCall(body)?.let {
                calls.add(it)
                spans.add(markerAt..end)
            }
            searchFrom = end + 1
        }

        // speech = everything except think-blocks and consumed call segments
        val sb = StringBuilder()
        var cursor = 0
        val removed = spans + thinkMatches.map { it.range }
        for (range in removed.sortedBy { it.first }) {
            if (range.first > cursor) sb.append(raw, cursor, range.first)
            cursor = (range.last + 1).coerceAtLeast(cursor)
        }
        if (cursor < raw.length) sb.append(raw, cursor, raw.length)

        return Parsed(
            thinking = thinking,
            speech = sb.toString().trim(),
            calls = calls,
        )
    }

    /** Finds the index of the '}' that closes the '{' at [start], or -1. */
    private fun balancedBraceEnd(s: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun parseCall(body: String): ToolCall? {
        // 1) strict-ish JSON
        try {
            val json = JSONObject(body)
            val name = json.optString("name", json.optString("tool", ""))
            if (name.isBlank()) return null
            val args = HashMap<String, String>()
            val argsObj = json.optJSONObject("args")
                ?: json.optJSONObject("arguments")
                ?: json.optJSONObject("parameters")
            if (argsObj != null) {
                for (key in argsObj.keys()) {
                    args[key] = argsObj.opt(key)?.toString() ?: ""
                }
            } else {
                // model inlined arguments at top level: {"name":"x","city":"Kolkata"}
                for (key in json.keys()) {
                    if (key !in setOf("name", "tool", "args", "arguments", "parameters")) {
                        args[key] = json.opt(key)?.toString() ?: ""
                    }
                }
            }
            return ToolCall(name.trim(), args)
        } catch (_: Exception) {
            // fall through to regex scraping
        }

        // 2) lenient fallback: pull "name" and any "key":"value" pairs
        val name = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: return null
        val args = HashMap<String, String>()
        for (m in Regex("\"([\\w ]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(body)) {
            val key = m.groupValues[1]
            if (key !in setOf("name", "tool", "args", "arguments", "parameters")) {
                args[key] = m.groupValues[2]
            }
        }
        return ToolCall(name.trim(), args)
    }
}
