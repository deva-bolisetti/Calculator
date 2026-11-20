package com.deva.ledgercalc.domain

import java.math.BigDecimal

/**
 * A finished tape, kept so a past calculation can be reopened and re-checked.
 *
 * [id] is the tape's session id, which is why upserting an entry replaces it instead of appending
 * a duplicate: pressing equals twice on the same tape must not produce two history lines.
 */
data class HistoryEntry(
    val id: Long,
    val rows: List<TapeRow>,
    val savedAt: Long
) {
    val total: BigDecimal get() = Tape.evaluate(rows)

    val rowCount: Int get() = rows.size
}

/**
 * Ordering and de-duplication rules for the history list, kept free of Android types so they can
 * be tested directly.
 */
object HistoryBook {

    const val MAX_ENTRIES = 100

    /** Newest first, one entry per session, capped so the preference file cannot grow forever. */
    fun upsert(
        entries: List<HistoryEntry>,
        entry: HistoryEntry,
        max: Int = MAX_ENTRIES
    ): List<HistoryEntry> =
        (entries.filterNot { it.id == entry.id } + entry)
            .sortedByDescending { it.savedAt }
            .take(max)

    fun remove(entries: List<HistoryEntry>, id: Long): List<HistoryEntry> =
        entries.filterNot { it.id == id }
}
