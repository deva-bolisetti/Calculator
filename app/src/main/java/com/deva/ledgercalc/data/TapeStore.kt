package com.deva.ledgercalc.data

import android.content.Context
import com.deva.ledgercalc.domain.HistoryEntry
import com.deva.ledgercalc.domain.Operator
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeRow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Keeps the last tape and the finished-tape history in SharedPreferences.
 *
 * A tape is a handful of rows of text, so a database would buy nothing here: one JSON blob is
 * written atomically by the framework and restored synchronously, which means the first frame
 * already shows the previous tape instead of flashing an empty one.
 */
class TapeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ledgercalc", Context.MODE_PRIVATE)

    fun load(): Tape {
        val raw = prefs.getString(KEY_TAPE, null) ?: return Tape()
        return try {
            decode(raw)
        } catch (_: Exception) {
            Tape()
        }
    }

    fun save(tape: Tape) {
        prefs.edit().putString(KEY_TAPE, encode(tape)).apply()
    }

    fun loadHistory(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            decodeHistory(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveHistory(entries: List<HistoryEntry>) {
        prefs.edit().putString(KEY_HISTORY, encodeHistory(entries)).apply()
    }

    private fun encode(tape: Tape): String =
        JSONObject().apply {
            put("rows", encodeRows(tape.rows))
            put("nextId", tape.nextId)
            put("archived", tape.archived?.let { encodeRows(it) } ?: JSONObject.NULL)
            put("sessionId", tape.sessionId)
            put("nextSessionId", tape.nextSessionId)
            put("sealed", tape.sealed)
            put("archivedSessionId", tape.archivedSessionId ?: JSONObject.NULL)
        }.toString()

    private fun decode(raw: String): Tape {
        val root = JSONObject(raw)
        val rows = root.optJSONArray("rows").toRows()
        val nextId = root.optLong("nextId", 1L)
        val archived = if (root.isNull("archived")) null else root.optJSONArray("archived").toRows()
        val sessionId = root.optLong("sessionId", 1L)
        val nextSessionId = root.optLong("nextSessionId", sessionId + 1L)
        return Tape(
            rows = rows,
            nextId = if (nextId > 0) nextId else (rows.maxOfOrNull { it.id } ?: 0L) + 1L,
            archived = archived?.takeIf { it.isNotEmpty() },
            sessionId = if (sessionId > 0) sessionId else 1L,
            nextSessionId = if (nextSessionId > sessionId) nextSessionId else sessionId + 1L,
            // A sealed tape must stay sealed across a restart, otherwise the next digit would
            // silently append to a finished calculation instead of starting a new one.
            sealed = root.optBoolean("sealed", false),
            archivedSessionId = if (root.isNull("archivedSessionId")) {
                null
            } else {
                root.optLong("archivedSessionId", 0L).takeIf { it > 0 }
            }
        )
    }

    private fun encodeHistory(entries: List<HistoryEntry>): String =
        JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("savedAt", entry.savedAt)
                        put("rows", encodeRows(entry.rows))
                    }
                )
            }
        }.toString()

    private fun decodeHistory(raw: String): List<HistoryEntry> {
        val array = JSONArray(raw)
        val result = mutableListOf<HistoryEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val rows = item.optJSONArray("rows").toRows()
            if (rows.isEmpty()) continue
            result += HistoryEntry(
                id = item.optLong("id", index + 1L),
                rows = rows,
                savedAt = item.optLong("savedAt", 0L)
            )
        }
        return result
    }

    private fun encodeRows(rows: List<TapeRow>): JSONArray =
        JSONArray().apply {
            rows.forEach { row ->
                put(
                    JSONObject().apply {
                        put("id", row.id)
                        put("op", row.operator?.name ?: JSONObject.NULL)
                        put("amount", row.amount.toPlainString())
                        put("expression", row.expression ?: JSONObject.NULL)
                    }
                )
            }
        }

    private fun JSONArray?.toRows(): List<TapeRow> {
        if (this == null) return emptyList()
        val result = mutableListOf<TapeRow>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val amount = item.optString("amount").takeIf { it.isNotEmpty() } ?: continue
            result += TapeRow(
                id = item.optLong("id", index + 1L),
                operator = if (item.isNull("op")) {
                    null
                } else {
                    item.optString("op").takeIf { it.isNotEmpty() }?.let { name ->
                        Operator.entries.find { it.name == name }
                    }
                },
                amount = BigDecimal(amount),
                expression = if (item.isNull("expression")) null else item.optString("expression").takeIf { it.isNotEmpty() }
            )
        }
        return result
    }

    private companion object {
        const val KEY_TAPE = "tape"
        const val KEY_HISTORY = "history"
    }
}
