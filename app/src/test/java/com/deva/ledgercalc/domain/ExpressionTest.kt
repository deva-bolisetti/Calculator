package com.deva.ledgercalc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The only hand-written parser in the app, and until now the only non-trivial class in `domain`
 * with no tests at all. It is fed whatever the user typed inside a bracket group, so the interesting
 * cases are the malformed ones.
 */
class ExpressionTest {

    private fun value(text: String): String? = Expression.evaluate(text)?.let { Amounts.format(it) }

    @Test
    fun `multiplication binds tighter than addition`() {
        assertEquals("14.00", value("2+3×4"))
        assertEquals("14.00", value("2+3*4"))
        assertEquals("20.00", value("2×3+14"))
        assertEquals("2.50", value("10÷4"))
        assertEquals("3.00", value("6/2"))
    }

    @Test
    fun `brackets nest and override precedence`() {
        assertEquals("9.00", value("((1+2)×3)"))
        assertEquals("20.00", value("(2+3)×(2+2)"))
        assertEquals("1.00", value("(1)"))
    }

    @Test
    fun `unicode and ascii operators are both accepted`() {
        assertEquals("2.00", value("5−3"))
        assertEquals("2.00", value("5-3"))
        assertEquals("8.00", value("5--3"))
    }

    @Test
    fun `unary minus is allowed`() {
        assertEquals("-5.00", value("-5"))
        assertEquals("-9.00", value("-(4+5)"))
        assertEquals("5.00", value("+5"))
    }

    @Test
    fun `division by zero is refused rather than thrown`() {
        assertNull(value("1÷0"))
        assertNull(value("1/(2-2)"))
    }

    @Test
    fun `incomplete or malformed text is refused`() {
        val malformed = listOf(
            "", "   ", "1+", "(1", "1)", ".", "1..2", "abc", "1 2", "×3", "1+×2",
            // The shape the keypad used to be able to produce inside a bracket before consecutive
            // operators were made to replace each other.
            "(5−×3)"
        )
        malformed.forEach { text -> assertNull("expected null for \"$text\"", value(text)) }
    }

    @Test
    fun `overlong input is refused before it reaches the parser`() {
        assertNull(value("1".repeat(121)))
        assertEquals("111.00", value("1".repeat(3)))
    }

    @Test
    fun `spaces are ignored between tokens`() {
        assertEquals("5.00", value(" 2 + 3 "))
        assertEquals("7.00", value("1 + 2 × 3"))
    }
}
