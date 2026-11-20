package com.deva.ledgercalc.domain

import java.math.BigDecimal

/**
 * The number currently being typed, plus the state that decides what happens when it lands.
 *
 * [pendingOperator] belongs to the row that has *not* been committed yet: the operator tapped
 * before the number was typed. The first row of a tape has no operator, which is why it is
 * nullable here and on [TapeRow].
 */
data class Draft(
    val digits: String = "",
    val pendingOperator: Operator? = null,
    val bracketText: String? = null,
    val expression: String? = null,
    val editingId: Long? = null,
    val percent: Boolean = false
) {
    val isBracketOpen: Boolean get() = bracketText != null
    val hasAmount: Boolean get() = digits.isNotEmpty() && digits != "-" && digits != "."
    val isEditing: Boolean get() = editingId != null
}

/**
 * An immutable tape: committed rows, the live draft, and one level of history so a Clear or a
 * post-equals restart cannot silently destroy a tape the user was checking.
 */
data class Tape(
    val rows: List<TapeRow> = emptyList(),
    val draft: Draft = Draft(),
    val error: String? = null,
    val nextId: Long = 1L,
    val sealed: Boolean = false,
    val archived: List<TapeRow>? = null,
    /**
     * Session of [archived], so restoring a cleared tape puts it back under the history line it
     * already had instead of re-recording the same rows as a new calculation.
     */
    val archivedSessionId: Long? = null,
    /**
     * Identity of the calculation currently on the tape. It survives row edits and undo, and only
     * changes when a new tape is started, so a finished tape always maps to one history entry.
     */
    val sessionId: Long = 1L,
    /** Monotonic counter behind [sessionId]; drawn from the clock so ids stay unique across runs. */
    val nextSessionId: Long = 1L
) {
    /** Strictly top to bottom: every row acts on the total built by the rows above it. */
    val total: BigDecimal get() = evaluate(rows)

    val isEmpty: Boolean get() = rows.isEmpty() && !draft.hasAmount

    val preview: String
        get() = when {
            draft.isBracketOpen -> draft.bracketText.orEmpty()
            else -> draft.digits
        }

    companion object {
        fun evaluate(rows: List<TapeRow>): BigDecimal {
            var total = BigDecimal.ZERO
            rows.forEachIndexed { index, row ->
                total = if (index == 0) row.amount else applyOperator(total, row.operator, row.amount)
            }
            return total
        }

        fun applyOperator(total: BigDecimal, operator: Operator?, amount: BigDecimal): BigDecimal =
            when (operator) {
                null, Operator.PLUS -> Amounts.add(total, amount)
                Operator.MINUS -> Amounts.subtract(total, amount)
                Operator.TIMES -> Amounts.multiply(total, amount)
                Operator.DIVIDE -> Amounts.divide(total, amount)
            }
    }
}

/**
 * All tape transitions, expressed as pure functions returning a new [Tape].
 *
 * Keeping this free of Android types means the whole calculation model is unit-testable, which
 * matters more than usual here: the app's only job is producing numbers a human can trust.
 */
object TapeEngine {

    private const val MAX_DIGITS = 15

    fun digit(tape: Tape, char: Char): Tape {
        if (!char.isDigit()) return tape
        val start = restartIfSealed(tape)
        val draft = start.draft

        if (draft.isEditing) {
            val digits = replaceDigits(draft.digits, char)
            return start.copy(draft = draft.copy(digits = digits, expression = null, percent = false), error = null)
        }

        if (draft.isBracketOpen) {
            val text = appendToBracket(draft.bracketText.orEmpty(), char)
            return start.copy(draft = draft.copy(bracketText = text), error = null)
        }

        val digits = appendDigit(draft.digits, char)
        if (digits.length > MAX_DIGITS) return tape
        return start.copy(draft = draft.copy(digits = digits, expression = null, percent = false), error = null)
    }

    fun decimal(tape: Tape): Tape {
        val start = restartIfSealed(tape)
        val draft = start.draft
        if (draft.isEditing) {
            if (draft.digits.contains('.')) return start
            val digits = if (draft.digits.isEmpty()) "0." else draft.digits + "."
            return start.copy(draft = draft.copy(digits = digits, expression = null, percent = false))
        }
        if (draft.isBracketOpen) {
            val body = draft.bracketText.orEmpty()
            val lastNumber = body.takeLastWhile { it.isDigit() || it == '.' }
            if (lastNumber.contains('.')) return start
            return start.copy(draft = draft.copy(bracketText = body + "."))
        }
        if (draft.digits.contains('.')) return start
        val digits = if (draft.digits.isEmpty()) "0." else draft.digits + "."
        return start.copy(draft = draft.copy(digits = digits, expression = null, percent = false))
    }

    fun operator(tape: Tape, operator: Operator): Tape {
        // An operator right after "=" continues from the total, as on a desk calculator. Unsealing
        // here is what stops the next digit from archiving the tape and starting over.
        val start = if (tape.sealed) tape.copy(sealed = false) else tape
        val draft = start.draft
        if (draft.isBracketOpen) {
            val text = appendOperatorToBracket(draft.bracketText.orEmpty(), operator)
            return start.copy(draft = draft.copy(bracketText = text), error = null)
        }
        if (draft.isEditing) {
            // While correcting a row, an operator key changes that row's operator and finishes.
            return replaceEditedRow(start, operatorOverride = operator)
        }
        if (!draft.hasAmount) {
            // Nothing typed yet: this just chooses the sign the next row will use.
            return start.copy(draft = draft.copy(pendingOperator = operator), error = null)
        }
        val committed = commit(start)
        if (committed.error != null) return committed
        return committed.copy(
            draft = committed.draft.copy(pendingOperator = operator),
            sealed = false
        )
    }

    fun equals(tape: Tape): Tape {
        val draft = tape.draft
        if (draft.isBracketOpen) {
            return tape.copy(error = "Close the bracket first", sealed = false)
        }
        val committed = if (draft.isEditing) replaceEditedRow(tape) else commit(tape)
        if (committed.error != null) return committed
        return committed.copy(sealed = true, draft = committed.draft.copy(pendingOperator = null))
    }

    fun percent(tape: Tape): Tape {
        val draft = tape.draft
        if (draft.isBracketOpen) return tape.copy(error = "Finish the bracket first")
        if (!draft.hasAmount) return tape
        return tape.copy(draft = draft.copy(percent = true, expression = draft.digits + "%"), error = null)
    }

    fun openBracket(tape: Tape): Tape {
        val start = restartIfSealed(tape)
        val draft = start.draft
        if (draft.isBracketOpen) {
            return start.copy(draft = draft.copy(bracketText = draft.bracketText.orEmpty() + "("))
        }
        if (draft.hasAmount) return start
        return start.copy(draft = draft.copy(bracketText = "(", digits = "", expression = null, percent = false))
    }

    fun closeBracket(tape: Tape): Tape {
        val draft = tape.draft
        if (!draft.isBracketOpen) return tape
        val body = draft.bracketText.orEmpty()
        val opened = body.count { it == '(' }
        val closed = body.count { it == ')' }
        if (opened == 0) return tape.copy(draft = draft.copy(bracketText = null), error = null)

        // One press closes the innermost open bracket. Nested groups stay open until balanced.
        val next = if (closed < opened) body + ")" else body
        if (next.count { it == '(' } > next.count { it == ')' }) {
            return tape.copy(draft = draft.copy(bracketText = next), error = null)
        }

        val value = Expression.evaluate(next)
            ?: return tape.copy(error = "Check the expression", draft = draft)
        return tape.copy(
            draft = draft.copy(
                digits = value.stripTrailingZeros().toPlainString(),
                expression = next,
                bracketText = null,
                percent = false
            ),
            error = null
        )
    }

    fun backspace(tape: Tape): Tape {
        val draft = tape.draft
        if (draft.isBracketOpen) {
            val body = draft.bracketText.orEmpty()
            if (body.length <= 1) {
                return tape.copy(draft = draft.copy(bracketText = null), error = null)
            }
            return tape.copy(draft = draft.copy(bracketText = body.dropLast(1)), error = null)
        }
        if (draft.isEditing) {
            // Stay in edit mode when the value empties: leaving it silently would turn the
            // correction into a brand-new row appended to the tape.
            val digits = draft.digits.dropLast(1)
            return tape.copy(draft = draft.copy(digits = digits, expression = null, percent = false), error = null)
        }
        if (draft.digits.isEmpty()) return tape
        val digits = draft.digits.dropLast(1)
        return tape.copy(
            draft = draft.copy(digits = digits, expression = null, percent = false),
            error = null
        )
    }

    /** Removes the last committed row, or restores an archived tape when the current one is empty. */
    fun undo(tape: Tape): Tape {
        if (tape.draft.hasAmount || tape.draft.isBracketOpen) {
            return tape.copy(draft = tape.draft.copy(digits = "", bracketText = null, expression = null, percent = false))
        }
        if (tape.rows.isEmpty()) {
            val archived = tape.archived ?: return tape
            // Reinstating rows restores their session too, so the entry they already own is
            // updated rather than duplicated if they are sealed again.
            return tape.copy(
                rows = archived,
                archived = null,
                sealed = false,
                nextId = nextIdFor(archived),
                sessionId = tape.archivedSessionId ?: tape.sessionId,
                archivedSessionId = null
            )
        }
        return tape.copy(rows = tape.rows.dropLast(1), sealed = false, error = null)
    }

    fun clear(tape: Tape): Tape {
        val next = freshSession(tape)
        return Tape(
            archived = tape.rows.ifEmpty { tape.archived },
            archivedSessionId = tape.sessionId.takeIf { tape.rows.isNotEmpty() } ?: tape.archivedSessionId,
            nextId = tape.nextId,
            sessionId = next,
            nextSessionId = next + 1
        )
    }

    /**
     * Puts a finished tape from history back on the calculator.
     *
     * The session id travels with it, so continuing or correcting that calculation updates its
     * existing history line instead of creating a second one for the same tape.
     *
     * A different tape that was on screen is kept in [Tape.archived], so undoing past the restored
     * rows brings back whatever the user was looking at before they opened the entry.
     */
    fun open(tape: Tape, rows: List<TapeRow>, sessionId: Long): Tape {
        if (rows.isEmpty()) return tape
        val displaced = tape.rows.takeIf { it.isNotEmpty() && it != rows }
        return tape.copy(
            rows = rows,
            draft = Draft(),
            error = null,
            sealed = false,
            archived = displaced ?: tape.archived,
            archivedSessionId = if (displaced != null) tape.sessionId else tape.archivedSessionId,
            nextId = nextIdFor(rows),
            sessionId = sessionId,
            nextSessionId = maxOf(tape.nextSessionId, sessionId + 1L)
        )
    }

    /** Loads a committed row back into the entry line for correction. */
    fun editRow(tape: Tape, id: Long): Tape {
        val row = tape.rows.find { it.id == id } ?: return tape
        return tape.copy(
            draft = Draft(
                digits = row.amount.stripTrailingZeros().toPlainString(),
                pendingOperator = row.operator,
                expression = row.expression,
                editingId = row.id
            ),
            sealed = false,
            error = null
        )
    }

    fun cancelEdit(tape: Tape): Tape =
        tape.copy(draft = Draft(pendingOperator = tape.draft.pendingOperator), error = null)

    // --- internals ---------------------------------------------------------------------------

    private fun commit(tape: Tape): Tape {
        val draft = tape.draft
        if (!draft.hasAmount) return tape

        val raw = Amounts.of(draft.digits) ?: return tape.copy(error = "That is not a number")
        val startingRow = tape.rows.isEmpty()
        val operator = if (startingRow) null else (draft.pendingOperator ?: Operator.PLUS)

        // A leading minus on an empty tape is the sign of the starting amount, not a row operator.
        val signed = if (startingRow && draft.pendingOperator == Operator.MINUS) raw.negate() else raw
        val resolved = if (draft.percent) resolvePercent(signed, operator, tape.total) else signed
        // Quantise the stored row, not just its label: the rows must add up to the shown total.
        val rounded = Amounts.round(resolved)

        if (operator == Operator.DIVIDE && Amounts.isZero(rounded)) {
            return tape.copy(error = "Cannot divide by zero")
        }

        val row = TapeRow(
            id = tape.nextId,
            operator = operator,
            amount = rounded,
            expression = draft.expression
        )
        return tape.copy(
            rows = tape.rows + row,
            draft = Draft(pendingOperator = if (startingRow) null else draft.pendingOperator),
            nextId = tape.nextId + 1,
            error = null
        )
    }

    private fun replaceEditedRow(tape: Tape, operatorOverride: Operator? = null): Tape {
        val draft = tape.draft
        val editingId = draft.editingId ?: return commit(tape)
        val index = tape.rows.indexOfFirst { it.id == editingId }
        if (index < 0) return cancelEdit(tape)
        if (!draft.hasAmount) return cancelEdit(tape)

        val raw = Amounts.of(draft.digits) ?: return tape.copy(error = "That is not a number")
        val existing = tape.rows[index]
        val operator = operatorOverride ?: existing.operator
        val resolved = if (draft.percent) resolvePercent(raw, operator, Tape.evaluate(tape.rows.take(index))) else raw
        val rounded = Amounts.round(resolved)
        if (operator == Operator.DIVIDE && Amounts.isZero(rounded)) {
            return tape.copy(error = "Cannot divide by zero")
        }

        val rows = tape.rows.toMutableList()
        rows[index] = existing.copy(operator = operator, amount = rounded, expression = draft.expression ?: existing.expression)
        return tape.copy(
            rows = rows,
            draft = Draft(pendingOperator = tape.draft.pendingOperator),
            error = null
        )
    }

    /**
     * Percent against the running total for + and -, and as a plain fraction otherwise: on a tape
     * of 200 then "+ 10%", the row resolves to 20 so the total reads 220, which is what a receipt
     * tape reader expects. For x and /, 10% means 0.1.
     */
    private fun resolvePercent(value: BigDecimal, operator: Operator?, total: BigDecimal): BigDecimal =
        if (operator == null || operator.isAdditive) {
            val base = if (operator == null) value else total
            Amounts.multiply(base, Amounts.divide(value, BigDecimal.valueOf(100)))
        } else {
            Amounts.divide(value, BigDecimal.valueOf(100))
        }

    private fun restartIfSealed(tape: Tape): Tape =
        if (tape.sealed) {
            // A new tape is a new calculation, so it gets its own history identity.
            val next = freshSession(tape)
            tape.copy(
                rows = emptyList(),
                archived = tape.rows,
                archivedSessionId = tape.sessionId,
                sealed = false,
                draft = Draft(),
                nextId = 1L,
                sessionId = next,
                nextSessionId = next + 1
            )
        } else {
            tape
        }

    private fun appendDigit(current: String, char: Char): String =
        when {
            current.isEmpty() && char == '0' -> "0"
            current == "0" -> char.toString()
            else -> current + char
        }

    private fun replaceDigits(current: String, char: Char): String =
        if (current.isEmpty()) char.toString() else current + char

    private fun appendToBracket(body: String, char: Char): String {
        if (body.endsWith(")")) return body
        return body + char
    }

    private fun appendOperatorToBracket(body: String, operator: Operator): String {
        if (body.isEmpty()) return body
        if (body.last() == '(' || body.last() == '+' || body.last() == '-' ||
            body.last() == '×' || body.last() == '÷'
        ) {
            return body.dropLast(1) + operator.symbol
        }
        return body + operator.symbol
    }

    private fun nextIdFor(rows: List<TapeRow>): Long = (rows.maxOfOrNull { it.id } ?: 0L) + 1L

    /**
     * A session id no tape is using yet.
     *
     * The counter starts equal to the first session, so the very first tape would otherwise reuse
     * the id of the tape it just archived; taking the larger of the two keeps them distinct.
     */
    private fun freshSession(tape: Tape): Long = maxOf(tape.nextSessionId, tape.sessionId + 1L)
}
