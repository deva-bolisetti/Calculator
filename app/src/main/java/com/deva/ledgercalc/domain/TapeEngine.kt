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
    val archived: List<TapeRow>? = null
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
        val draft = tape.draft
        if (draft.isBracketOpen) {
            val text = appendOperatorToBracket(draft.bracketText.orEmpty(), operator)
            return tape.copy(draft = draft.copy(bracketText = text), error = null)
        }
        if (draft.isEditing) {
            // While correcting a row, an operator key changes that row's operator and finishes.
            return replaceEditedRow(tape, operatorOverride = operator)
        }
        if (!draft.hasAmount) {
            // Nothing typed yet: this just chooses the sign the next row will use.
            if (tape.rows.isEmpty()) return tape.copy(draft = draft.copy(pendingOperator = operator), error = null)
            return tape.copy(draft = draft.copy(pendingOperator = operator), error = null)
        }
        val committed = commit(tape)
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
        if (body.count { it == '(' } > body.count { it == ')' }) {
            return tape.copy(draft = draft.copy(bracketText = body + ")"), error = null)
        }
        val value = Expression.evaluate(body)
            ?: return tape.copy(error = "Check the expression", draft = draft)
        return tape.copy(
            draft = draft.copy(
                digits = value.stripTrailingZeros().toPlainString(),
                expression = "$body)",
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
            val digits = draft.digits.dropLast(1)
            if (digits.isEmpty()) return cancelEdit(tape)
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
            return tape.copy(rows = archived, archived = null, sealed = false, nextId = nextIdFor(archived))
        }
        return tape.copy(rows = tape.rows.dropLast(1), sealed = false, error = null)
    }

    fun clear(tape: Tape): Tape =
        Tape(archived = tape.rows.ifEmpty { tape.archived }, nextId = tape.nextId)

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
        val operator = if (tape.rows.isEmpty()) null else (draft.pendingOperator ?: Operator.PLUS)

        val resolved = if (draft.percent) resolvePercent(raw, operator, tape.total) else raw

        if (operator == Operator.DIVIDE && Amounts.isZero(resolved)) {
            return tape.copy(error = "Cannot divide by zero")
        }

        val row = TapeRow(
            id = tape.nextId,
            operator = operator,
            amount = resolved,
            expression = draft.expression
        )
        return tape.copy(
            rows = tape.rows + row,
            draft = Draft(pendingOperator = draft.pendingOperator),
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
        if (operator == Operator.DIVIDE && Amounts.isZero(resolved)) {
            return tape.copy(error = "Cannot divide by zero")
        }

        val rows = tape.rows.toMutableList()
        rows[index] = existing.copy(operator = operator, amount = resolved, expression = draft.expression ?: existing.expression)
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
            tape.copy(rows = emptyList(), archived = tape.rows, sealed = false, draft = Draft(), nextId = 1L)
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
}
