package com.deva.ledgercalc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The history rules, driven directly instead of through the ViewModel.
 *
 * This is the subtlest behaviour in the app: an entry must appear when a tape is sealed, be
 * rewritten in place when the tape is corrected, and disappear when its tape is undone away —
 * without ever producing two lines for one calculation.
 */
class HistorySyncTest {

    private fun rows(vararg amounts: String): List<TapeRow> = amounts.mapIndexed { index, value ->
        TapeRow(
            id = index + 1L,
            operator = if (index == 0) null else Operator.PLUS,
            amount = BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP)
        )
    }

    private fun open(vararg amounts: String, sessionId: Long = 1L) =
        Tape(rows = rows(*amounts), sessionId = sessionId, nextSessionId = sessionId + 1)

    @Test
    fun `sealing a tape records one entry`() {
        val typing = open("200", "300")
        val history = HistoryBook.reconcile(emptyList(), typing, typing.copy(sealed = true), now = 1000L)

        assertEquals(1, history.size)
        assertEquals(1L, history.single().id)
        assertEquals("500.00", Amounts.format(history.single().total))
        assertEquals(1000L, history.single().savedAt)
    }

    @Test
    fun `pressing equals twice does not restamp the entry`() {
        val typing = open("10")
        val sealed = typing.copy(sealed = true)
        val first = HistoryBook.reconcile(emptyList(), typing, sealed, now = 1000L)
        val second = HistoryBook.reconcile(first, sealed, sealed, now = 2000L)

        assertEquals(first, second)
    }

    @Test
    fun `correcting a sealed tape rewrites its entry in place`() {
        val typing = open("10")
        val sealed = typing.copy(sealed = true)
        val first = HistoryBook.reconcile(emptyList(), typing, sealed, now = 1000L)

        val corrected = sealed.copy(sealed = false, rows = rows("10", "5"))
        val second = HistoryBook.reconcile(first, sealed, corrected, now = 2000L)

        assertEquals(1, second.size)
        assertEquals("15.00", Amounts.format(second.single().total))
        // The entry keeps the time it was first recorded: it is still the same calculation.
        assertEquals(1000L, second.single().savedAt)
    }

    @Test
    fun `undoing every row away removes the entry`() {
        val typing = open("10")
        val sealed = typing.copy(sealed = true)
        val first = HistoryBook.reconcile(emptyList(), typing, sealed, now = 1000L)

        val emptied = sealed.copy(sealed = false, rows = emptyList())
        assertTrue(HistoryBook.reconcile(first, sealed, emptied, now = 2000L).isEmpty())
    }

    @Test
    fun `a tape that is still being typed is never recorded`() {
        val typing = open("10", "5")
        assertTrue(HistoryBook.reconcile(emptyList(), Tape(), typing, now = 1L).isEmpty())
    }

    @Test
    fun `a tape sealed again after the history was cleared is recorded again`() {
        val sealed = open("10").copy(sealed = true)
        val history = HistoryBook.reconcile(emptyList(), sealed, sealed, now = 5000L)

        assertEquals(1, history.size)
        assertEquals(5000L, history.single().savedAt)
    }

    @Test
    fun `a re-scaled row is not treated as an edit`() {
        // BigDecimal.equals compares scale, so 10 and 10.00 are different values to it. The
        // comparison must be numeric or a legacy entry would be rewritten on every key press.
        val entry = HistoryEntry(id = 1L, rows = listOf(TapeRow(1, null, BigDecimal("10.00"))), savedAt = 500L)
        val same = Tape(rows = listOf(TapeRow(1, null, BigDecimal("10"))), sessionId = 1L)

        val reconciled = HistoryBook.reconcile(listOf(entry), same, same, now = 900L)

        assertEquals(listOf(entry), reconciled)
    }

    @Test
    fun `an older tape keeps its own entry when a new session starts`() {
        val first = open("10")
        val firstSealed = first.copy(sealed = true)
        val history = HistoryBook.reconcile(emptyList(), first, firstSealed, now = 1000L)

        val next = open("7", sessionId = 2L).copy(sealed = true)
        val after = HistoryBook.reconcile(history, Tape(sessionId = 2L), next, now = 2000L)

        assertEquals(listOf(2L, 1L), after.map { it.id })
    }
}
