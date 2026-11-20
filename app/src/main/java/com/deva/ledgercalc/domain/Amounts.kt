package com.deva.ledgercalc.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Formatting and arithmetic rules shared by the whole app.
 *
 * Amounts are always [BigDecimal]: 0.1 + 0.2 must read 0.30 on a tool whose entire purpose is
 * letting a person re-check the figures by eye. Every committed row is rounded to [SCALE] places,
 * so the rows a user reads always add up to the total a user reads.
 */
object Amounts {

    /** Display and storage precision. Rows are quantised to this many decimal places. */
    const val SCALE = 2

    /** Division precision. Kept at 10 dp internally; the committed row is then rounded. */
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
        a.divide(b, DIVISION_SCALE, RoundingMode.HALF_UP)

    /** Rounds to [SCALE] places, half away from zero. Applied to every committed row. */
    fun round(value: BigDecimal): BigDecimal = value.setScale(SCALE, RoundingMode.HALF_UP)

    fun isZero(value: BigDecimal): Boolean = value.signum() == 0

    /**
     * Display text: always exactly [SCALE] decimals, grouped in threes, no exponent.
     * 1000 -> "1,000.00", 494 -> "494.00", 0.5 -> "0.50".
     */
    fun format(value: BigDecimal): String = group(round(value).toPlainString())

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
