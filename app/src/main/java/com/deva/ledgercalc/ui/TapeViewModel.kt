package com.deva.ledgercalc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.deva.ledgercalc.data.TapeStore
import com.deva.ledgercalc.domain.Draft
import com.deva.ledgercalc.domain.HistoryBook
import com.deva.ledgercalc.domain.HistoryEntry
import com.deva.ledgercalc.domain.Operator
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeEngine
import com.deva.ledgercalc.domain.TapeRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the tape and its history. Every key press funnels through here, so
 * the persisted tape, the history and the on-screen tape can never disagree.
 */
class TapeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = TapeStore(application)

    private val _tape = MutableStateFlow(store.load())
    val tape: StateFlow<Tape> = _tape.asStateFlow()

    private val _history = MutableStateFlow(store.loadHistory())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private fun update(transform: (Tape) -> Tape) {
        val previous = _tape.value
        val next = transform(previous)
        if (next == previous) return
        _tape.value = next
        store.save(next)
        syncHistory(previous, next)
    }

    /**
     * History always agrees with the tape it describes.
     *
     * A tape lands in history when it is sealed with "=", and is recorded by session id so editing
     * it or pressing "=" again updates one line instead of piling up duplicates. Undoing back past
     * the "=" rewrites that line to the shorter tape, and undoing every row away removes it, so an
     * entry can never claim a total the tape no longer has. Clear starts a new session, which
     * leaves the finished tape's entry alone.
     */
    private fun syncHistory(previous: Tape, next: Tape) {
        val recorded = _history.value.find { it.id == next.sessionId }

        if (next.rows.isEmpty()) {
            if (recorded != null) dropHistory(next.sessionId)
            return
        }

        if (next.sealed) {
            if (previous.sealed && previous.rows == next.rows) return
            remember(next.sessionId, next.rows, System.currentTimeMillis())
            return
        }

        // Not sealed: only keep an existing entry honest, never add a half-finished tape.
        if (recorded == null || recorded.rows == next.rows) return
        remember(next.sessionId, next.rows, recorded.savedAt)
    }

    private fun remember(sessionId: Long, rows: List<TapeRow>, savedAt: Long) {
        val entry = HistoryEntry(id = sessionId, rows = rows, savedAt = savedAt)
        val updated = HistoryBook.upsert(_history.value, entry)
        _history.value = updated
        store.saveHistory(updated)
    }

    fun digit(char: Char) = update { TapeEngine.digit(it, char) }

    fun decimal() = update { TapeEngine.decimal(it) }

    fun operator(operator: Operator) = update { TapeEngine.operator(it, operator) }

    fun equals() = update { TapeEngine.equals(it) }

    fun percent() = update { TapeEngine.percent(it) }

    fun openBracket() = update { TapeEngine.openBracket(it) }

    fun closeBracket() = update { TapeEngine.closeBracket(it) }

    fun backspace() = update { TapeEngine.backspace(it) }

    fun undo() = update { TapeEngine.undo(it) }

    fun clear() = update { TapeEngine.clear(it) }

    fun editRow(id: Long) = update { TapeEngine.editRow(it, id) }

    fun cancelEdit() = update { TapeEngine.cancelEdit(it) }

    /** The tape restored by an undo after Clear, used to drive the snackbar's undo action. */
    fun canUndoClear(): Boolean = _tape.value.rows.isEmpty() && _tape.value.archived != null

    fun dismissError() = update { it.copy(error = null) }

    fun emptyDraft(): Draft = Draft()

    // --- history -----------------------------------------------------------------------------

    /** Loads a past tape back onto the calculator so it can be re-checked or continued. */
    fun openFromHistory(id: Long) {
        val entry = _history.value.find { it.id == id } ?: return
        val restored = TapeEngine.open(_tape.value, entry.rows, entry.id)
        if (restored == _tape.value) return
        _tape.value = restored
        store.save(restored)
    }

    fun deleteHistory(id: Long) = dropHistory(id)

    private fun dropHistory(id: Long) {
        val updated = HistoryBook.remove(_history.value, id)
        if (updated == _history.value) return
        _history.value = updated
        store.saveHistory(updated)
    }

    fun clearHistory() {
        if (_history.value.isEmpty()) return
        _history.value = emptyList()
        store.saveHistory(emptyList())
    }
}
