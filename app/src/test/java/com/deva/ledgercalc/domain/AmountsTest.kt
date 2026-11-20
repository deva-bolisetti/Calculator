package com.deva.ledgercalc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AmountsTest {

    @Test
    fun `always shows two decimals`() {
        assertEquals("1,000.00", Amounts.format(BigDecimal("1000")))
        assertEquals("494.00", Amounts.format(BigDecimal("494.000")))
        assertEquals("0.50", Amounts.format(BigDecimal("0.5")))
        assertEquals("-50.00", Amounts.format(BigDecimal("-50")))
        assertEquals("12,345.67", Amounts.format(BigDecimal("12345.67")))
        assertEquals("0.00", Amounts.format(BigDecimal.ZERO))
    }

    @Test
    fun `rounds to two decimals half up`() {
        assertEquals("0.13", Amounts.format(BigDecimal("0.125")))
        assertEquals("2.68", Amounts.format(BigDecimal("2.675")))
        assertEquals("-0.13", Amounts.format(BigDecimal("-0.125")))
    }

    @Test
    fun `decimal addition is exact`() {
        val sum = Amounts.add(BigDecimal("0.1"), BigDecimal("0.2"))
        assertEquals("0.30", Amounts.format(sum))
    }

    @Test
    fun `division is rounded for display`() {
        assertEquals("3.33", Amounts.format(Amounts.divide(BigDecimal(10), BigDecimal(3))))
        assertEquals("5.00", Amounts.format(Amounts.divide(BigDecimal(10), BigDecimal(2))))
    }

    @Test
    fun `invalid input is rejected`() {
        assertNull(Amounts.of("abc"))
        assertNull(Amounts.of(""))
        assertEquals(BigDecimal("12.5"), Amounts.of("12.5"))
    }
}
