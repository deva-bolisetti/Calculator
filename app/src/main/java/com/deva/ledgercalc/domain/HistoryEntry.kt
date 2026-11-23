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

    /**
     * The entire history rule set as one pure function: entries in, entries out.
     *
     * Kept here rather than in the ViewModel because it is the subtlest rule in the app — a tape
     * lands in history when it is sealed with "=", is recorded by session id so editing it or
     * pressing "=" again updates one line instead of piling up duplicates, and undoing back past
     * the "=" rewrites that line (or removes it) so an entry can never claim a total the tape no
     * longer has. Clear starts a new session, which leaves the finished tape's entry alone.
     *
     * @param previous the tape before the key press, needed to notice "=" being pressed twice.
     * @param now timestamp to store when the entry is created or re-sealed.
     */
    fun reconcile(
        entries: List<HistoryEntry>,
        previous: Tape,
        next: Tape,
        now: Long
    ): List<HistoryEntry> {
        val recorded = entries.find { it.id == next.sessionId }

        if (next.rows.isEmpty()) {
            return if (recorded == null) entries else remove(entries, next.sessionId)
        }

        if (next.sealed) {
            // Pressing "=" again on the same tape, or returning from a rotation, must not restamp
            // the entry. If the line is missing (the user cleared history), it is written again.
            if (recorded != null && previous.sealed && sameRows(previous.rows, next.rows)) return entries
            return upsert(entries, HistoryEntry(next.sessionId, next.rows, now))
        }

        // Not sealed: only keep an existing entry honest, never add a half-finished tape.
        if (recorded == null || sameRows(recorded.rows, next.rows)) return entries
        return upsert(entries, HistoryEntry(next.sessionId, next.rows, recorded.savedAt))
    }

    /**
     * Compares rows by value rather than by [BigDecimal.equals], which also compares scale: 5.0 and
     * 5.00 are different objects to it, and a re-scaled row would look like an edit.
     */
    private fun sameRows(left: List<TapeRow>, right: List<TapeRow>): Boolean =
        left.size == right.size && left.indices.all { index ->
            val a = left[index]
            val b = right[index]
            a.id == b.id &&
                a.operator == b.operator &&
                a.expression == b.expression &&
                a.amount.compareTo(b.amount) == 0
        }
}
