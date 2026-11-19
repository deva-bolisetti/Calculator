package com.deva.ledgercalc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.deva.ledgercalc.data.TapeStore
import com.deva.ledgercalc.domain.Draft
import com.deva.ledgercalc.domain.Operator
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the tape. Every key press funnels through here, so the persisted
 * tape and the on-screen tape can never disagree.
 */
class TapeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = TapeStore(application)

    private val _tape = MutableStateFlow(store.load())
    val tape: StateFlow<Tape> = _tape.asStateFlow()

    private fun update(transform: (Tape) -> Tape) {
        val next = transform(_tape.value)
        if (next == _tape.value) return
        _tape.value = next
        store.save(next)
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
}
