package com.deva.ledgercalc.domain

import java.math.BigDecimal

/**
 * Recursive-descent evaluator used only for the contents of a bracket group.
 *
 * The tape itself never uses precedence: each row is applied to the running total in order. Inside
 * a bracket the user is writing an ordinary sub-expression, so * and / bind tighter than + and -,
 * and nested brackets are allowed.
 */
object Expression {

    private const val MAX_LENGTH = 120

    /** @return the value, or null when the text is not a complete, valid expression. */
    fun evaluate(text: String): BigDecimal? {
        if (text.isBlank() || text.length > MAX_LENGTH) return null
        return try {
            val parser = Parser(text)
            val value = parser.expression()
            parser.skipSpaces()
            if (parser.atEnd) value else null
        } catch (_: ArithmeticException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private class Parser(private val text: String) {
        private var index = 0

        val atEnd: Boolean get() = index >= text.length

        fun skipSpaces() {
            while (index < text.length && text[index] == ' ') index++
        }

        fun expression(): BigDecimal {
            var value = term()
            while (true) {
                skipSpaces()
                if (atEnd) return value
                when (peek()) {
                    '+' -> {
                        index++
                        value = Amounts.add(value, term())
                    }
                    '−', '-' -> {
                        index++
                        value = Amounts.subtract(value, term())
                    }
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                skipSpaces()
                if (atEnd) return value
                when (peek()) {
                    '×', '*' -> {
                        index++
                        value = Amounts.multiply(value, factor())
                    }
                    '÷', '/' -> {
                        index++
                        val divisor = factor()
                        if (Amounts.isZero(divisor)) throw ArithmeticException("divide by zero")
                        value = Amounts.divide(value, divisor)
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            skipSpaces()
            if (atEnd) throw IllegalArgumentException("unexpected end")
            return when (val char = peek()) {
                '+' -> {
                    index++
                    factor()
                }
                '−', '-' -> {
                    index++
                    factor().negate()
                }
                '(' -> {
                    index++
                    val value = expression()
                    skipSpaces()
                    if (atEnd || peek() != ')') throw IllegalArgumentException("missing )")
                    index++
                    value
                }
                else -> if (char.isDigit() || char == '.') number() else throw IllegalArgumentException("bad char $char")
            }
        }

        private fun number(): BigDecimal {
            val start = index
            var dots = 0
            while (index < text.length) {
                val char = text[index]
                if (char == '.') {
                    dots++
                    if (dots > 1) throw IllegalArgumentException("two decimal points")
                    index++
                } else if (char.isDigit()) {
                    index++
                } else {
                    break
                }
            }
            val body = text.substring(start, index)
            if (body == ".") throw IllegalArgumentException("lone decimal point")
            return BigDecimal(body)
        }

        private fun peek(): Char = text[index]
    }
}
