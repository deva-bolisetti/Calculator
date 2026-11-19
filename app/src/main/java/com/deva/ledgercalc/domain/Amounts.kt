package com.deva.ledgercalc.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Formatting and arithmetic rules shared by the whole app.
 *
 * Amounts are always [BigDecimal]: 0.1 + 0.2 must read 0.3 on a tool whose entire purpose is
 * letting a person re-check the figures by eye. Division keeps 10 decimal places and the result
 * is stripped of trailing zeros so 10 / 3 reads 3.3333333333 while 10 / 2 reads 5.
 */
object Amounts {

    /** Division precision. Kept at 10 dp so a tape of quotients cannot drift visibly. */
    const val DIVISION_SCALE = 10

    private val MATH = MathContext(34, RoundingMode.HALF_UP)

    fun of(value: String): BigDecimal? =
        try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            null
        }

    fun add(a: BigDecimal, b: BigDecimal): BigDecimal = a.add(b, MATH)

    fun subtract(a: BigDecimal, b: BigDecimal): BigDecimal = a.subtract(b, MATH)

    fun multiply(a: BigDecimal, b: BigDecimal): BigDecimal = a.multiply(b, MATH)

    /** @throws ArithmeticException when [b] is zero; callers must reject the row first. */
    fun divide(a: BigDecimal, b: BigDecimal): BigDecimal =
        a.divide(b, DIVISION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()

    fun isZero(value: BigDecimal): Boolean = value.signum() == 0

    /** True when the value needs no fractional digits at all, e.g. 200 or -494. */
    fun isWhole(value: BigDecimal): Boolean = value.stripTrailingZeros().scale() <= 0

    /**
     * Display text: no exponent, no trailing fractional zeros, grouped in threes.
     * 1000 -> "1,000", 0.50 -> "0.5", 494.000 -> "494".
     */
    fun format(value: BigDecimal): String {
        val plain = value.stripTrailingZeros().toPlainString()
        return group(plain)
    }

    /** [+1.0, +0.5, -0.25] -> "+1, +0.5, -0.25"; zero keeps its sign so a row is never blank. */
    fun formatSigned(value: BigDecimal): String {
        val text = format(value)
        return when {
            text.startsWith("-") -> text
            text.startsWith("+") -> text
            else -> "+$text"
        }
    }

    /** Inserts thousands separators into the integer part only. */
    private fun group(plain: String): String {
        val negative = plain.startsWith("-")
        val body = if (negative) plain.substring(1) else plain
        val dot = body.indexOf('.')
        val integer = if (dot >= 0) body.substring(0, dot) else body
        val fraction = if (dot >= 0) body.substring(dot) else ""
        val grouped = buildString {
            for ((index, char) in integer.withIndex()) {
                if (index > 0 && (integer.length - index) % 3 == 0) append(',')
                append(char)
            }
        }
        return buildString {
            if (negative) append('-')
            append(grouped)
            append(fraction)
        }
    }
}
