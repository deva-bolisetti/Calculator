package com.deva.ledgercalc.domain

import java.math.BigDecimal

/** An operator occupying the left gutter of a tape row. */
enum class Operator(val symbol: String) {
    PLUS("+"),
    MINUS("−"),
    TIMES("×"),
    DIVIDE("÷");

    /** True when the operator adds or removes a share of the running total. */
    val isAdditive: Boolean get() = this == PLUS || this == MINUS
}

/**
 * One line of the tape.
 *
 * [operator] is null only for the first row, which is the starting amount and is therefore drawn
 * without a sign (unless it was entered as a negative).
 *
 * [expression] is set when the row did not come from a plain keypad amount: a bracket group keeps
 * its working ("(30 × 2)") and a percent entry keeps its prefix ("10%"), so the tape can show how
 * the number was arrived at instead of just the number.
 */
data class TapeRow(
    val id: Long,
    val operator: Operator?,
    val amount: BigDecimal,
    val expression: String? = null
) {
    val isFirst: Boolean get() = operator == null
}
