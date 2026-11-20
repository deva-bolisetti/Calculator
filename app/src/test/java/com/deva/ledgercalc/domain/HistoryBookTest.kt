package com.deva.ledgercalc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class HistoryBookTest {

    private fun entry(id: Long, savedAt: Long, total: String = "100") = HistoryEntry(
        id = id,
        rows = listOf(
            TapeRow(1, null, BigDecimal(total)),
            TapeRow(2, Operator.PLUS, BigDecimal("10"))
        ),
        savedAt = savedAt
    )

    @Test
    fun `newest entry comes first`() {
        var entries = emptyList<HistoryEntry>()
        entries = HistoryBook.upsert(entries, entry(1, 1000))
        entries = HistoryBook.upsert(entries, entry(2, 2000))
        entries = HistoryBook.upsert(entries, entry(3, 1500))

        assertEquals(listOf(2L, 3L, 1L), entries.map { it.id })
    }

    @Test
    fun `same session replaces its entry instead of duplicating`() {
        var entries = HistoryBook.upsert(emptyList(), entry(7, 1000, total = "100"))
        entries = HistoryBook.upsert(entries, entry(7, 2000, total = "250"))

        assertEquals(1, entries.size)
        // The fixture is "total + 10", so the replacement row evaluates to 260.
        assertEquals("260.00", Amounts.format(entries.first().total))
        assertEquals(2000L, entries.first().savedAt)
    }

    @Test
    fun `history is capped`() {
        var entries = emptyList<HistoryEntry>()
        repeat(5) { index ->
            entries = HistoryBook.upsert(entries, entry(index.toLong(), index.toLong() * 100), max = 3)
        }
        assertEquals(3, entries.size)
        assertEquals(listOf(4L, 3L, 2L), entries.map { it.id })
    }

    @Test
    fun `entry total matches the tape evaluation`() {
        val rows = listOf(
            TapeRow(1, null, BigDecimal("200.00")),
            TapeRow(2, Operator.PLUS, BigDecimal("300.00")),
            TapeRow(3, Operator.MINUS, BigDecimal("50.00"))
        )
        val entry = HistoryEntry(id = 1, rows = rows, savedAt = 0)
        assertEquals("450.00", Amounts.format(entry.total))
        assertEquals(3, entry.rowCount)
    }

    @Test
    fun `removing an entry keeps the rest`() {
        val entries = listOf(entry(1, 1000), entry(2, 2000))
        val remaining = HistoryBook.remove(entries, 1)
        assertEquals(listOf(2L), remaining.map { it.id })
        assertTrue(HistoryBook.remove(remaining, 99) == remaining)
    }
}
