package com.deva.ledgercalc.data

import android.content.Context
import com.deva.ledgercalc.domain.Operator
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeRow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Keeps the last tape across launches in SharedPreferences.
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

    private fun encode(tape: Tape): String {
        val rows = JSONArray()
        tape.rows.forEach { row ->
            rows.put(
                JSONObject().apply {
                    put("id", row.id)
                    put("op", row.operator?.name ?: JSONObject.NULL)
                    put("amount", row.amount.toPlainString())
                    put("expression", row.expression ?: JSONObject.NULL)
                }
            )
        }
        val archived = tape.archived?.let { stored ->
            JSONArray().apply {
                stored.forEach { row ->
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
        }
        return JSONObject().apply {
            put("rows", rows)
            put("nextId", tape.nextId)
            put("archived", archived ?: JSONObject.NULL)
        }.toString()
    }

    private fun decode(raw: String): Tape {
        val root = JSONObject(raw)
        val rows = root.optJSONArray("rows").toRows()
        val nextId = root.optLong("nextId", 1L)
        val archived = if (root.isNull("archived")) null else root.optJSONArray("archived").toRows()
        return Tape(
            rows = rows,
            nextId = if (nextId > 0) nextId else (rows.maxOfOrNull { it.id } ?: 0L) + 1L,
            archived = archived?.takeIf { it.isNotEmpty() }
        )
    }

    private fun JSONArray?.toRows(): List<TapeRow> {
        if (this == null) return emptyList()
        val result = mutableListOf<TapeRow>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val amount = item.optString("amount").takeIf { it.isNotEmpty() } ?: continue
            result += TapeRow(
                id = item.optLong("id", index + 1L),
                operator = item.optString("op").takeIf { it.isNotEmpty() }?.let { name ->
                    Operator.entries.find { it.name == name }
                },
                amount = BigDecimal(amount),
                expression = item.optString("expression").takeIf { it.isNotEmpty() }
            )
        }
        return result
    }

    private companion object {
        const val KEY_TAPE = "tape"
    }
}
