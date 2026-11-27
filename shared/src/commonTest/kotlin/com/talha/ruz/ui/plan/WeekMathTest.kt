package com.talha.ruz.ui.plan

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekMathTest {
    @Test
    fun weekStartIsMonday() {
        // 27 Kasım 2026 Perşembe
        assertEquals(LocalDate(2026, 11, 23), weekStart(LocalDate(2026, 11, 27)))
        // Pazartesi kendisi
        assertEquals(LocalDate(2026, 11, 23), weekStart(LocalDate(2026, 11, 23)))
        // Pazar bir önceki pazartesiye bağlanır
        assertEquals(LocalDate(2026, 11, 23), weekStart(LocalDate(2026, 11, 29)))
        // Ay ve yıl geçişi
        assertEquals(LocalDate(2026, 12, 28), weekStart(LocalDate(2027, 1, 1)))
    }

    @Test
    fun weekHasSevenConsecutiveDays() {
        val days = weekDays(LocalDate(2026, 11, 27))
        assertEquals(7, days.size)
        assertEquals(LocalDate(2026, 11, 29), days.last())
    }

    @Test
    fun dayFractionBounds() {
        assertEquals(0f, dayFraction(0, 0))
        assertEquals(0.5f, dayFraction(12, 0))
    }
}
