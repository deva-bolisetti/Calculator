package com.deva.ledgercalc.data

import com.deva.ledgercalc.domain.Amounts
import com.deva.ledgercalc.domain.Tape

/**
 * Renders a tape as plain text, padded so the columns stay aligned in a plain-text context
 * (a chat message, a terminal, an email). Monospace output is the whole point: it is the format
 * someone pastes when asking a second person to check the figures.
 *
 * The gutter carries the row's operator and the amount carries its own sign. Those are two
 * different facts — "subtract" and "negative" — and printing only one of them makes a row like
 * "- 5.00" indistinguishable from "+ 5.00", which is exactly the mistake a paper tape exists to
 * prevent.
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
            val gutter = row.operator?.symbol ?: " "
            builder.append(gutter).append(' ')
            builder.append(amounts[index].padStart(width))
            val expression = row.expression
            if (expression != null) builder.append("   ").append(expression)
            builder.append('\n')
        }
        builder.append("-".repeat(width + 2)).append('\n')
        builder.append("= ").append(total.padStart(width)).append('\n')
        return builder.toString()
    }
}
