package com.deva.ledgercalc.data

import com.deva.ledgercalc.domain.Amounts
import com.deva.ledgercalc.domain.HistoryEntry
import com.deva.ledgercalc.domain.Operator
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeRow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The persistence layer is the one place the app cannot afford to be wrong: there is no server, no
 * database and no migration step, so a read that fails is a user's calculation gone.
 *
 * These tests run on the JVM because [TapeCodec] has no Android types. `org.json` comes from the
 * real implementation on the test classpath rather than the throwing `android.jar` stub.
 */
class TapeCodecTest {

    private fun row(id: Long, operator: String?, amount: String, expression: String? = null): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("op", operator ?: JSONObject.NULL)
            put("amount", amount)
            put("expression", expression ?: JSONObject.NULL)
        }

    private fun tapeJson(vararg rows: JSONObject): String =
        JSONObject().apply {
            put("v", TapeCodec.VERSION)
            put("rows", JSONArray().apply { rows.forEach { put(it) } })
        }.toString()

    @Test
    fun `a tape survives the round trip`() {
        val tape = Tape(
            rows = listOf(
                TapeRow(1, null, BigDecimal("200.00")),
                TapeRow(2, Operator.MINUS, BigDecimal("50.50"), "(25×2)"),
                TapeRow(3, Operator.DIVIDE, BigDecimal("2.00"))
            ),
            nextId = 4,
            sealed = true,
            archived = listOf(TapeRow(1, null, BigDecimal("9.00"))),
            archivedSessionId = 4,
            sessionId = 5,
            nextSessionId = 6
        )

        assertEquals(tape, TapeCodec.decodeTape(TapeCodec.encodeTape(tape)))
    }

    @Test
    fun `an unreadable row is dropped and the rest of the tape survives`() {
        val raw = tapeJson(
            row(1, null, "10.00"),
            row(2, "PLUS", "not a number"),
            row(3, "PLUS", "5.00")
        )

        val tape = TapeCodec.decodeTape(raw)

        assertEquals(2, tape.rows.size)
        assertEquals("15.00", Amounts.format(tape.total))
    }

    @Test
    fun `a stored divide by zero row cannot crash the tape`() {
        val raw = tapeJson(row(1, null, "10.00"), row(2, "DIVIDE", "0.00"))

        val tape = TapeCodec.decodeTape(raw)

        assertEquals(1, tape.rows.size)
        assertEquals("10.00", Amounts.format(tape.total))
    }

    @Test
    fun `an operator this build does not know is read as add`() {
        val raw = tapeJson(row(1, null, "10.00"), row(2, "MODULO", "5.00"))

        val tape = TapeCodec.decodeTape(raw)

        assertEquals(Operator.PLUS, tape.rows[1].operator)
        assertEquals("15.00", Amounts.format(tape.total))
    }

    @Test
    fun `negatives and a blank expression are readable`() {
        val raw = tapeJson(row(1, null, "50.00"), row(2, "MINUS", "80.00", ""))

        val tape = TapeCodec.decodeTape(raw)

        assertEquals(Operator.MINUS, tape.rows[1].operator)
        assertNull(tape.rows[1].expression)
        assertEquals("-30.00", Amounts.format(tape.total))
    }

    @Test
    fun `the next row id is pushed past whatever was stored`() {
        val raw = tapeJson(row(1, null, "10.00"), row(7, "PLUS", "5.00"))

        assertEquals(8L, TapeCodec.decodeTape(raw).nextId)
    }

    @Test
    fun `session ids stay usable when the stored ones are missing or zero`() {
        val raw = JSONObject().apply { put("rows", JSONArray()) }.toString()
        val empty = TapeCodec.decodeTape(raw)

        assertEquals(1L, empty.sessionId)
        assertEquals(2L, empty.nextSessionId)
        assertEquals(1L, empty.nextId)
    }

    @Test
    fun `history round trips and comes back newest first`() {
        val older = HistoryEntry(id = 1L, rows = listOf(TapeRow(1, null, BigDecimal("10.00"))), savedAt = 100L)
        val newer = HistoryEntry(id = 2L, rows = listOf(TapeRow(1, null, BigDecimal("20.00"))), savedAt = 300L)

        val decoded = TapeCodec.decodeHistory(TapeCodec.encodeHistory(listOf(older, newer)))

        assertEquals(listOf(newer, older), decoded)
    }

    @Test
    fun `the bare-array history written by older builds is still read`() {
        val legacy = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", 9L)
                    put("savedAt", 42L)
                    put("rows", JSONArray().apply { put(row(1, null, "12.50")) })
                }
            )
        }.toString()

        val decoded = TapeCodec.decodeHistory(legacy)

        assertEquals(1, decoded.size)
        assertEquals(9L, decoded.single().id)
        assertEquals(42L, decoded.single().savedAt)
        assertEquals("12.50", Amounts.format(decoded.single().total))
    }

    @Test
    fun `one damaged history entry does not take the others with it`() {
        val broken = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", 1L)
                    put("savedAt", 10L)
                    put("rows", JSONArray().apply { put(row(1, null, "5.00")) })
                    put("nonsense", JSONObject().apply { put("not", "a row list") })
                }
            )
            put(
                JSONObject().apply {
                    put("id", 2L)
                    put("savedAt", 20L)
                    // Every row here is unreadable, so the entry itself is dropped.
                    put("rows", JSONArray().apply { put(row(1, null, "abc")) })
                }
            )
        }.toString()

        val decoded = TapeCodec.decodeHistory(broken)

        assertEquals(1, decoded.size)
        assertEquals(1L, decoded.single().id)
    }

    @Test
    fun `duplicate session ids collapse to the newest`() {
        val duplicate = HistoryEntry(id = 1L, rows = listOf(TapeRow(1, null, BigDecimal("5.00"))), savedAt = 10L)
        val restamped = HistoryEntry(id = 1L, rows = listOf(TapeRow(1, null, BigDecimal("5.00"))), savedAt = 90L)

        val decoded = TapeCodec.decodeHistory(TapeCodec.encodeHistory(listOf(duplicate, restamped)))

        assertEquals(1, decoded.size)
        assertEquals(90L, decoded.single().savedAt)
    }

    @Test
    fun `an empty tape encodes to something readable`() {
        val decoded = TapeCodec.decodeTape(TapeCodec.encodeTape(Tape()))
        assertEquals(Tape(), decoded)
        assertTrue(decoded.rows.isEmpty())
    }
}
