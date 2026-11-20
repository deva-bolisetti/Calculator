package com.deva.ledgercalc.data

import com.deva.ledgercalc.domain.Operator
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TapeTextTest {

    private fun tape(vararg rows: TapeRow) = Tape(rows = rows.toList())

    @Test
    fun `renders two decimals and keeps columns aligned`() {
        val text = TapeText.render(
            tape(
                TapeRow(1, null, BigDecimal("200")),
                TapeRow(2, Operator.PLUS, BigDecimal("300")),
                TapeRow(3, Operator.MINUS, BigDecimal("50"))
            )
        )

        val lines = text.trimEnd('\n').lines()
        assertEquals(5, lines.size)
        assertEquals("  200.00", lines[0])
        assertEquals("+ 300.00", lines[1])
        assertEquals("−  50.00", lines[2])
        assertTrue(lines[3].all { it == '-' })
        assertEquals("= 450.00", lines[4])
    }

    @Test
    fun `negative total keeps its sign on the total line`() {
        val text = TapeText.render(
            tape(
                TapeRow(1, null, BigDecimal("50")),
                TapeRow(2, Operator.MINUS, BigDecimal("80"))
            )
        )
        assertTrue(text.contains("= -30.00"))
    }

    @Test
    fun `bracket working is included beside the amount`() {
        val text = TapeText.render(
            tape(
                TapeRow(1, null, BigDecimal("200")),
                TapeRow(2, Operator.PLUS, BigDecimal("60"), expression = "(30×2)")
            )
        )
        assertTrue(text.contains("(30×2)"))
        assertTrue(text.contains("260.00"))
    }

    @Test
    fun `empty tape renders nothing`() {
        assertEquals("", TapeText.render(Tape()))
    }
}
