package com.talha.ruz.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test fun format() {
        assertEquals("350 ₺", Money.format(35_000))
        assertEquals("1.250,50 ₺", Money.format(125_050))
        assertEquals("15.000 ₺", Money.format(1_500_000))
        assertEquals("0,05 ₺", Money.format(5))
        assertEquals("1.000.000 ₺", Money.format(100_000_000))
    }

    @Test fun parse() {
        assertEquals(125_050L, Money.parse("1.250,50"))
        assertEquals(35_000L, Money.parse("350"))
        assertEquals(35_050L, Money.parse("350.5"))
        assertEquals(35_000L, Money.parse("350 ₺"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }
}
