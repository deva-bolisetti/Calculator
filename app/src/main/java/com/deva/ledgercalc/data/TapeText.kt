package com.deva.ledgercalc.data

import com.deva.ledgercalc.domain.Amounts
import com.deva.ledgercalc.domain.Tape
import java.math.BigDecimal

/**
 * Renders a tape as plain text, padded so the columns stay aligned in a plain-text context
 * (a chat message, a terminal, an email). Monospace output is the whole point: it is the format
 * someone pastes when asking a second person to check the figures.
 */
object TapeText {

    fun render(tape: Tape): String {
        val rows = tape.rows
        if (rows.isEmpty()) return ""

        val amounts = rows.map { Amounts.format(it.amount) }
        val total = Amounts.format(tape.total)
        val width = (amounts + total).maxOf { it.length }

        val builder = StringBuilder()
        rows.forEachIndexed { index, row ->
            val sign = if (index == 0) {
                when {
                    row.amount.signum() < 0 -> "-"
                    else -> " "
                }
            } else {
                row.operator?.symbol ?: "+"
            }
            val body = amounts[index].removePrefix("-")
            val expression = row.expression
            builder.append(sign).append(' ')
            builder.append(body.padStart(width))
            if (expression != null) builder.append("   ").append(expression)
            builder.append('\n')
        }
        builder.append('-'.repeat(width + 2)).append('\n')
        builder.append("= ").append(total.padStart(width)).append('\n')
        return builder.toString()
    }
}
