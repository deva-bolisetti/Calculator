package com.deva.ledgercalc.data

import com.deva.ledgercalc.domain.HistoryEntry
import com.deva.ledgercalc.domain.Tape

/**
 * The storage the ViewModel depends on.
 *
 * It exists so the ViewModel's history rules can be driven by a fake in a unit test: Android's
 * `SharedPreferences` cannot be constructed on the JVM, and `TapeStore` is the only thing in the
 * app that needs a `Context` to persist anything.
 */
interface TapeRepository {

    fun load(): Tape

    fun save(tape: Tape)

    fun loadHistory(): List<HistoryEntry>

    fun saveHistory(entries: List<HistoryEntry>)
}
