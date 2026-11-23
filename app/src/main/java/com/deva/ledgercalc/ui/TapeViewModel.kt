package com.deva.ledgercalc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.deva.ledgercalc.data.TapeRepository
import com.deva.ledgercalc.data.TapeStore
import com.deva.ledgercalc.domain.HistoryBook
import com.deva.ledgercalc.domain.HistoryEntry
import com.deva.ledgercalc.domain.Operator
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the tape and its history. Every key press funnels through here, so
 * the persisted tape, the history and the on-screen tape can never disagree.
 */
class TapeViewModel(application: Application) : AndroidViewModel(application) {

    private val store: TapeRepository = TapeStore(application)

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
     * The rules themselves live in [HistoryBook.reconcile], which is pure and therefore testable
     * without Android; this only persists the result.
     */
    private fun syncHistory(previous: Tape, next: Tape) {
        val updated = HistoryBook.reconcile(
            entries = _history.value,
            previous = previous,
            next = next,
            now = System.currentTimeMillis()
        )
        if (updated == _history.value) return
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

    /** Dismisses the inline error, which otherwise stays until the next key press. */
    fun dismissError() = update { it.copy(error = null) }

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

    /** Puts back an entry the user just deleted, so the delete can be undone. */
    fun restoreHistory(entry: HistoryEntry) {
        val updated = HistoryBook.upsert(_history.value, entry)
        if (updated == _history.value) return
        _history.value = updated
        store.saveHistory(updated)
    }

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
