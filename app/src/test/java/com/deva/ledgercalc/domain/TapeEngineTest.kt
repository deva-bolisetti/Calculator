package com.deva.ledgercalc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TapeEngineTest {

    private fun tapeOf(vararg keys: String): Tape {
        var tape = Tape()
        keys.forEach { key ->
            tape = when (key) {
                "+" -> TapeEngine.operator(tape, Operator.PLUS)
                "-" -> TapeEngine.operator(tape, Operator.MINUS)
                "*" -> TapeEngine.operator(tape, Operator.TIMES)
                "/" -> TapeEngine.operator(tape, Operator.DIVIDE)
                "=" -> TapeEngine.equals(tape)
                "%" -> TapeEngine.percent(tape)
                "." -> TapeEngine.decimal(tape)
                "(" -> TapeEngine.openBracket(tape)
                ")" -> TapeEngine.closeBracket(tape)
                else -> key.fold(tape) { acc, char -> TapeEngine.digit(acc, char) }
            }
        }
        return tape
    }

    @Test
    fun `matches the reference tape`() {
        val tape = tapeOf("200", "+", "300", "+", "44", "-", "50", "=")
        assertEquals(4, tape.rows.size)
        assertEquals("200.00", Amounts.format(tape.rows[0].amount))
        assertNull(tape.rows[0].operator)
        assertEquals(Operator.PLUS, tape.rows[1].operator)
        assertEquals("300.00", Amounts.format(tape.rows[1].amount))
        assertEquals("44.00", Amounts.format(tape.rows[2].amount))
        assertEquals(Operator.MINUS, tape.rows[3].operator)
        assertEquals("494.00", Amounts.format(tape.total))
    }

    @Test
    fun `evaluates strictly down the tape without precedence`() {
        val tape = tapeOf("200", "+", "300", "*", "2", "=")
        assertEquals("1,000.00", Amounts.format(tape.total))
    }

    @Test
    fun `bracket group keeps its working and joins as one value`() {
        val tape = tapeOf("200", "+", "(", "30", "*", "2", ")", "-", "50", "=")
        val bracketRow = tape.rows[1]
        assertEquals("(30×2)", bracketRow.expression)
        assertEquals("60.00", Amounts.format(bracketRow.amount))
        assertEquals("210.00", Amounts.format(tape.total))
    }

    @Test
    fun `percent applies to the running total for plus`() {
        val tape = tapeOf("200", "+", "10", "%", "=")
        assertEquals("220.00", Amounts.format(tape.total))
    }

    @Test
    fun `percent is a plain fraction for multiply`() {
        val tape = tapeOf("200", "*", "10", "%", "=")
        assertEquals("20.00", Amounts.format(tape.total))
    }

    @Test
    fun `typed decimals are kept to two places`() {
        val tape = tapeOf("0", ".", "5", "+", "0", ".", "2", "5", "=")
        assertEquals("0.50", Amounts.format(tape.rows[0].amount))
        assertEquals("0.25", Amounts.format(tape.rows[1].amount))
        assertEquals("0.75", Amounts.format(tape.total))
    }

    @Test
    fun `rows add up to the shown total after rounding`() {
        // 1.005 and 2.005 each round to 1.01 and 2.01 when committed. If the rows were shown
        // rounded but the total were computed from the raw values, the tape would read
        // 1.01 + 2.01 = 3.01 and fail the whole point of the app.
        val tape = tapeOf("1", ".", "0", "0", "5", "+", "2", ".", "0", "0", "5", "=")

        assertEquals("1.01", Amounts.format(tape.rows[0].amount))
        assertEquals("2.01", Amounts.format(tape.rows[1].amount))
        assertEquals("3.02", Amounts.format(tape.total))

        val summed = tape.rows.drop(1).fold(tape.rows[0].amount) { acc, row ->
            Tape.applyOperator(acc, row.operator, row.amount)
        }
        assertEquals(Amounts.format(tape.total), Amounts.format(summed))
    }

    @Test
    fun `divide by zero is rejected and leaves the tape untouched`() {
        val tape = tapeOf("200", "/", "0", "=")
        assertEquals(1, tape.rows.size)
        assertEquals("Cannot divide by zero", tape.error)
        assertEquals("200.00", Amounts.format(tape.total))
    }

    @Test
    fun `undo removes the last row`() {
        var tape = tapeOf("200", "+", "300", "=")
        assertEquals(2, tape.rows.size)
        tape = TapeEngine.undo(tape)
        assertEquals(1, tape.rows.size)
        assertEquals("200.00", Amounts.format(tape.total))
    }

    @Test
    fun `clear can be undone`() {
        var tape = tapeOf("200", "+", "300", "=")
        tape = TapeEngine.clear(tape)
        assertTrue(tape.rows.isEmpty())
        tape = TapeEngine.undo(tape)
        assertEquals(2, tape.rows.size)
        assertEquals("500.00", Amounts.format(tape.total))
    }

    @Test
    fun `editing a row recomputes the tail`() {
        var tape = tapeOf("200", "+", "300", "+", "44", "=")
        assertEquals("544.00", Amounts.format(tape.total))

        val secondRowId = tape.rows[1].id
        tape = TapeEngine.editRow(tape, secondRowId)
        assertEquals(secondRowId, tape.draft.editingId)

        tape = TapeEngine.digit(tape, '0')
        tape = TapeEngine.equals(tape)

        assertEquals(3, tape.rows.size)
        assertEquals("3,000.00", Amounts.format(tape.rows[1].amount))
        assertEquals("3,244.00", Amounts.format(tape.total))
    }

    @Test
    fun `cancelling an edit leaves the tape alone`() {
        var tape = tapeOf("200", "+", "300", "+", "44", "=")
        tape = TapeEngine.editRow(tape, tape.rows[1].id)
        "9".forEach { char -> tape = TapeEngine.digit(tape, char) }
        tape = TapeEngine.cancelEdit(tape)
        assertEquals("544.00", Amounts.format(tape.total))
        assertEquals(3, tape.rows.size)
    }

    @Test
    fun `backspacing an edited row to empty stays in edit mode`() {
        var tape = tapeOf("200", "+", "300", "=")
        val rowId = tape.rows[1].id
        tape = TapeEngine.editRow(tape, rowId)
        repeat(3) { tape = TapeEngine.backspace(tape) }
        assertEquals(rowId, tape.draft.editingId)
        assertEquals(2, tape.rows.size)

        "400".forEach { char -> tape = TapeEngine.digit(tape, char) }
        tape = TapeEngine.equals(tape)
        assertEquals(2, tape.rows.size)
        assertEquals("400.00", Amounts.format(tape.rows[1].amount))
        assertEquals("600.00", Amounts.format(tape.total))
    }

    @Test
    fun `next number after equals starts a fresh tape and keeps history`() {
        var tape = tapeOf("200", "+", "300", "=")
        assertEquals("500.00", Amounts.format(tape.total))
        tape = tapeOf("200", "+", "300", "=", "10", "=")
        assertEquals(1, tape.rows.size)
        assertEquals("10.00", Amounts.format(tape.total))
        assertTrue(tape.archived != null)
    }

    @Test
    fun `reopening a history tape keeps its session so it updates one entry`() {
        val finished = tapeOf("200", "+", "300", "=")
        val session = finished.sessionId

        var tape = TapeEngine.open(Tape(), finished.rows, session)
        assertEquals(session, tape.sessionId)
        assertTrue(!tape.sealed)

        // Continuing the reopened tape must land back on the same history identity.
        "50".forEach { char -> tape = TapeEngine.digit(tape, char) }
        tape = TapeEngine.equals(tape)
        assertEquals(session, tape.sessionId)
        assertEquals("550.00", Amounts.format(tape.total))
    }

    @Test
    fun `reopening keeps the displaced tape reachable by undo`() {
        val current = tapeOf("10", "+", "5", "=")
        var restored = TapeEngine.open(current, listOf(TapeRow(1, null, BigDecimal("99"))), sessionId = 42L)

        assertEquals("99.00", Amounts.format(restored.total))
        assertEquals(42L, restored.sessionId)
        assertEquals(current.rows, restored.archived)
        assertEquals(2L, restored.nextId)

        // Undoing the restored rows and then once more falls back to the tape that was replaced.
        restored = TapeEngine.undo(restored)
        assertTrue(restored.rows.isEmpty())
        restored = TapeEngine.undo(restored)
        assertEquals(current.rows, restored.rows)
        assertEquals("15.00", Amounts.format(restored.total))
    }

    @Test
    fun `reopening the tape already on screen does not archive a copy of itself`() {
        val finished = tapeOf("200", "+", "300", "=")
        val reopened = TapeEngine.open(finished, finished.rows, finished.sessionId)

        assertNull(reopened.archived)
        assertTrue(!reopened.sealed)
        assertEquals(finished.rows, reopened.rows)
    }

    @Test
    fun `operator after equals continues the tape instead of starting over`() {
        val finished = tapeOf("10", "+", "20", "=")
        assertEquals("30.00", Amounts.format(finished.total))
        assertEquals(2, finished.rows.size)

        var tape = TapeEngine.operator(finished, Operator.PLUS)
        assertTrue(!tape.sealed)
        assertEquals(2, tape.rows.size)

        "5".forEach { char -> tape = TapeEngine.digit(tape, char) }
        tape = TapeEngine.equals(tape)

        // A desk calculator reads 10 + 20 = 30, then + 5 = 35.
        assertEquals(3, tape.rows.size)
        assertEquals("35.00", Amounts.format(tape.total))
        assertEquals(finished.sessionId, tape.sessionId)
    }

    @Test
    fun `all operators continue a finished tape`() {
        val finished = tapeOf("10", "+", "20", "=")

        val minus = tapeOf("10", "+", "20", "=", "-", "5", "=")
        assertEquals("25.00", Amounts.format(minus.total))

        val times = tapeOf("10", "+", "20", "=", "*", "2", "=")
        assertEquals("60.00", Amounts.format(times.total))

        val divide = tapeOf("10", "+", "20", "=", "/", "2", "=")
        assertEquals("15.00", Amounts.format(divide.total))

        assertEquals(finished.sessionId, minus.sessionId)
    }

    @Test
    fun `digit after equals still starts a fresh tape`() {
        val tape = tapeOf("10", "+", "20", "=", "7")
        assertTrue(tape.rows.isEmpty())
        assertEquals("7", tape.draft.digits)
        assertEquals("30.00", Amounts.format(Tape.evaluate(tape.archived.orEmpty())))
    }

    @Test
    fun `operator after equals does not archive the finished tape`() {
        val finished = tapeOf("10", "+", "20", "=")
        val continued = TapeEngine.operator(finished, Operator.PLUS)
        assertNull(continued.archived)
        assertEquals(finished.rows, continued.rows)
    }

    @Test
    fun `reopening a session id that is newer advances the counter`() {
        val tape = TapeEngine.open(Tape(), listOf(TapeRow(1, null, BigDecimal("5"))), sessionId = 9L)
        assertEquals(10L, tape.nextSessionId)

        // A fresh tape after that must not reuse the reopened session id.
        val cleared = TapeEngine.clear(tape)
        assertEquals(10L, cleared.sessionId)
        assertEquals(11L, cleared.nextSessionId)
    }

    @Test
    fun `undoing a clear restores the session that owned those rows`() {
        val sealed = tapeOf("200", "+", "300", "=")
        val session = sealed.sessionId

        val cleared = TapeEngine.clear(sealed)
        assertEquals(session + 1L, cleared.sessionId)

        val restored = TapeEngine.undo(cleared)
        assertEquals(session, restored.sessionId)
        assertEquals(sealed.rows, restored.rows)
        assertNull(restored.archivedSessionId)
    }

    @Test
    fun `undoing back past equals keeps the tape on its own session`() {
        val sealed = tapeOf("200", "+", "300", "=")
        val session = sealed.sessionId

        val unsealed = TapeEngine.undo(sealed)
        assertEquals(session, unsealed.sessionId)
        assertTrue(!unsealed.sealed)
        assertEquals(1, unsealed.rows.size)
    }
}
