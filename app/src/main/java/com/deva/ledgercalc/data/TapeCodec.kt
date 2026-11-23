package com.deva.ledgercalc.data

import com.deva.ledgercalc.domain.HistoryBook
import com.deva.ledgercalc.domain.HistoryEntry
import com.deva.ledgercalc.domain.Operator
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeRow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * The on-disk shape of the tape and its history.
 *
 * Deliberately free of Android types, so the round trip can be tested on the JVM, and deliberately
 * forgiving on read: one damaged row must not cost the user the rest of the tape, and an amount the
 * engine cannot divide by must never reach it.
 *
 * Every blob carries [VERSION] so a future reader can tell "written by an older build" from
 * "corrupt". Nothing branches on it yet; that is the point of writing it before it is needed.
 */
internal object TapeCodec {

    const val VERSION = 1

    /** Amounts beyond this are not figures a person typed, so a stored one is treated as junk. */
    private const val MAX_PRECISION = 30

    /** Every committed row is stored at scale 2; anything wilder came from damaged data. */
    private const val MAX_SCALE = 20

    fun encodeTape(tape: Tape): String =
        JSONObject().apply {
            put("v", VERSION)
            put("rows", encodeRows(tape.rows))
            put("nextId", tape.nextId)
            put("archived", tape.archived?.let { encodeRows(it) } ?: JSONObject.NULL)
            put("archivedSessionId", tape.archivedSessionId ?: JSONObject.NULL)
            put("sessionId", tape.sessionId)
            put("nextSessionId", tape.nextSessionId)
            put("sealed", tape.sealed)
        }.toString()

    /**
     * @throws org.json.JSONException when [raw] is not a JSON object at all. Callers treat that as
     * unreadable storage rather than as an empty tape, so the blob can be kept for diagnosis.
     */
    fun decodeTape(raw: String): Tape {
        val root = JSONObject(raw)
        val rows = decodeRows(root.optJSONArray("rows"))
        val highestId = rows.maxOfOrNull { it.id } ?: 0L
        val archived = if (root.isNull("archived")) null else decodeRows(root.optJSONArray("archived"))
        val sessionId = root.optLong("sessionId", 1L).takeIf { it > 0 } ?: 1L
        val nextSessionId = root.optLong("nextSessionId", sessionId + 1L)

        return Tape(
            rows = rows,
            // Ids must stay unique even if the stored counter was lost or rolled back.
            nextId = maxOf(root.optLong("nextId", 1L), highestId + 1L),
            archived = archived?.takeIf { it.isNotEmpty() },
            sessionId = sessionId,
            // Only clamped to a sane positive value: TapeEngine.freshSession already guarantees the
            // next tape cannot reuse the current session's id.
            nextSessionId = maxOf(nextSessionId, 1L),
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

    fun encodeHistory(entries: List<HistoryEntry>): String =
        JSONObject().apply {
            put("v", VERSION)
            put(
                "entries",
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
                }
            )
        }.toString()

    /**
     * One unreadable entry is skipped; the rest of the history survives. Old builds kept a bare
     * array here, so that shape is still accepted.
     */
    fun decodeHistory(raw: String): List<HistoryEntry> {
        val array = if (raw.trimStart().startsWith("[")) {
            JSONArray(raw)
        } else {
            JSONObject(raw).optJSONArray("entries") ?: JSONArray()
        }

        val entries = mutableListOf<HistoryEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val rows = decodeRows(item.optJSONArray("rows"))
            if (rows.isEmpty()) continue
            entries += HistoryEntry(
                id = item.optLong("id", index + 1L),
                rows = rows,
                savedAt = item.optLong("savedAt", 0L)
            )
        }

        return entries
            .sortedByDescending { it.savedAt }
            .distinctBy { it.id }
            .take(HistoryBook.MAX_ENTRIES)
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

    private fun decodeRows(array: JSONArray?): List<TapeRow> {
        if (array == null) return emptyList()
        val rows = mutableListOf<TapeRow>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val row = decodeRow(item, index) ?: continue
            rows += row
        }
        return rows
    }

    private fun decodeRow(item: JSONObject, index: Int): TapeRow? {
        val text = item.optString("amount").takeIf { it.isNotEmpty() } ?: return null
        val amount = try {
            BigDecimal(text)
        } catch (_: NumberFormatException) {
            return null
        }
        if (amount.precision() > MAX_PRECISION || amount.scale() > MAX_SCALE || amount.scale() < -MAX_SCALE) return null

        val operator = when {
            item.isNull("op") -> null
            else -> {
                val name = item.optString("op")
                // An operator this build does not know is read as "add": additive keeps the row
                // readable and keeps the total finite, which dropping the row would not.
                Operator.entries.find { it.name == name } ?: Operator.PLUS
            }
        }

        // A stored divide row with a zero divisor would throw from inside composition, which
        // crashes the app on every launch. Such a row is not recoverable, so it is dropped.
        if (operator == Operator.DIVIDE && amount.signum() == 0) return null

        return TapeRow(
            id = item.optLong("id", index + 1L),
            operator = operator,
            amount = amount,
            expression = if (item.isNull("expression")) {
                null
            } else {
                item.optString("expression").takeIf { it.isNotEmpty() }
            }
        )
    }
}
