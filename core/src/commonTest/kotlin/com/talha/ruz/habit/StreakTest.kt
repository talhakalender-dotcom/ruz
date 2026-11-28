package com.talha.ruz.habit

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakTest {
    /** 25 Kasım 2026 Çarşamba. */
    private val today = LocalDate(2026, 11, 25)
    private fun d(day: Int) = LocalDate(2026, 11, day)
    private val everyDay = (1..7).toSet()

    @Test fun countsBackFromToday() {
        assertEquals(3, Streak.current(setOf(d(23), d(24), d(25)), everyDay, today))
    }

    @Test fun todayNotDoneYetKeepsStreak() {
        assertEquals(2, Streak.current(setOf(d(23), d(24)), everyDay, today))
    }

    @Test fun gapBreaks() {
        assertEquals(1, Streak.current(setOf(d(21), d(22), d(25)), everyDay, today))
        assertEquals(0, Streak.current(setOf(d(20)), everyDay, today))
    }

    @Test fun weekdaysSkipWeekend() {
        // Cuma 20, Pzt 23, Sal 24, Çar 25 — hafta sonu sayılmaz.
        assertEquals(4, Streak.current(setOf(d(20), d(23), d(24), d(25)), setOf(1, 2, 3, 4, 5), today))
    }

    @Test fun lastSevenDays() {
        val s = Streak.lastDays(setOf(d(24)), setOf(1, 2, 3, 4, 5), today)
        assertEquals(7, s.size)
        // 19 Per, 20 Cum, 21 Cmt, 22 Paz, 23 Pzt, 24 Sal, 25 Çar
        assertEquals(
            listOf(
                Streak.DayState.Missed, Streak.DayState.Missed, Streak.DayState.Off, Streak.DayState.Off,
                Streak.DayState.Missed, Streak.DayState.Done, Streak.DayState.Today,
            ),
            s,
        )
    }
}
