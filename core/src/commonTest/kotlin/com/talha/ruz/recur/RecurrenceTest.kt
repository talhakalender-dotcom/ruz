package com.talha.ruz.recur

import com.talha.ruz.parse.TrDateParser
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceTest {
    /** 25 Kasım 2026 Çarşamba, 10:00. */
    private val now = LocalDateTime(2026, 11, 25, 10, 0)
    private val cats = listOf("Genel", "İş", "Alışveriş", "Fikirler", "Ödemeler")
    private fun p(text: String) = TrDateParser.parse(text, now, cats)
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate(y, m, day)

    @Test fun monthlyRent() {
        val r = p("her ayın 15'i kira #ödemeler")
        assertEquals(Recurrence(Freq.Monthly, monthDay = 15), r.recurrence)
        assertEquals(d(12, 15), r.date)
        assertTrue(r.allDay)
        assertEquals("kira", r.title)
        assertEquals("Ödemeler", r.categoryName)
    }

    @Test fun weeklyTwoDays() {
        val r = p("her pazartesi ve perşembe 09:00 toplantı")
        assertEquals(Recurrence(Freq.Weekly, weekdays = setOf(1, 4)), r.recurrence)
        assertEquals(d(11, 26), r.date)
        assertEquals(LocalTime(9, 0), r.time)
        assertEquals("toplantı", r.title)
    }

    @Test fun dailyWithPassedTimeStartsTomorrow() {
        val r = p("her gün 08:00 ilaç")
        assertEquals(Freq.Daily, r.recurrence?.freq)
        assertEquals(d(11, 26), r.date)
        assertEquals("ilaç", r.title)
        val m = p("her sabah ilaç")
        assertEquals(Freq.Daily, m.recurrence?.freq)
        assertEquals(LocalTime(9, 0), m.time)
        assertEquals("ilaç", m.title)
    }

    @Test fun everyOtherWeek() {
        val r = p("iki haftada bir cuma halı saha")
        assertEquals(Recurrence(Freq.Weekly, interval = 2, weekdays = setOf(5)), r.recurrence)
        assertEquals(d(11, 27), r.date)
        assertEquals("halı saha", r.title)
    }

    @Test fun monthEndAndYearly() {
        val r = p("her ayın son günü kira")
        assertEquals(Recurrence(Freq.Monthly, monthDay = -1), r.recurrence)
        assertEquals(d(11, 30), r.date)
        val y = p("her yıl 15 aralık sigorta")
        assertEquals(Recurrence(Freq.Yearly, monthDay = 15, month = 12), y.recurrence)
        assertEquals(d(12, 15), y.date)
        assertEquals("sigorta", y.title)
    }

    @Test fun weekdaysOnly() {
        val r = p("hafta içi her gün 07:30 spor")
        assertEquals(Recurrence(Freq.Weekly, weekdays = setOf(1, 2, 3, 4, 5)), r.recurrence)
        assertEquals(d(11, 26), r.date)
        assertEquals("spor", r.title)
    }

    @Test fun weeklyFromWeekday() {
        val r = p("her hafta cuma rapor")
        assertEquals(Recurrence(Freq.Weekly, weekdays = setOf(5)), r.recurrence)
        assertEquals(d(11, 27), r.date)
    }

    @Test fun plainTextHasNoRecurrence() {
        assertNull(p("herkese mesaj at").recurrence)
        assertNull(p("yarın 14:00 toplantı").recurrence)
    }

    @Test fun labels() {
        assertEquals("Her ayın 15'i", Recurrence(Freq.Monthly, monthDay = 15).label())
        assertEquals("Her ayın 1'i", Recurrence(Freq.Monthly, monthDay = 1).label())
        assertEquals("Her ayın 20'si", Recurrence(Freq.Monthly, monthDay = 20).label())
        assertEquals("Her ayın son günü", Recurrence(Freq.Monthly, monthDay = -1).label())
        assertEquals("Her pazartesi, perşembe", Recurrence(Freq.Weekly, weekdays = setOf(1, 4)).label())
        assertEquals("2 haftada bir cuma", Recurrence(Freq.Weekly, interval = 2, weekdays = setOf(5)).label())
        assertEquals("Hafta içi her gün", Recurrence(Freq.Weekly, weekdays = setOf(1, 2, 3, 4, 5)).label())
        assertEquals("Her gün", Recurrence(Freq.Daily).label())
        assertEquals("Her yıl 15 aralık", Recurrence(Freq.Yearly, monthDay = 15, month = 12).label())
    }

    @Test fun encodeDecode() {
        listOf(
            Recurrence(Freq.Monthly, monthDay = 15),
            Recurrence(Freq.Weekly, interval = 2, weekdays = setOf(1, 4)),
            Recurrence(Freq.Yearly, monthDay = 29, month = 2),
            Recurrence(Freq.Daily, interval = 3),
        ).forEach { assertEquals(it, Recurrence.decode(it.encode())) }
        assertNull(Recurrence.decode(""))
    }

    @Test fun occurrences() {
        assertEquals(
            listOf(d(1, 31, 2027), d(2, 28, 2027), d(3, 31, 2027)),
            Recurrence(Freq.Monthly, monthDay = 31).occurrences(d(1, 31, 2027), d(1, 1, 2027), d(3, 31, 2027)),
        )
        assertEquals(
            listOf(d(11, 27), d(12, 11), d(12, 25)),
            Recurrence(Freq.Weekly, interval = 2, weekdays = setOf(5)).occurrences(d(11, 27), d(11, 25), d(12, 31)),
        )
        assertEquals(
            listOf(d(11, 26), d(11, 27), d(11, 28)),
            Recurrence(Freq.Daily).occurrences(d(11, 26), d(11, 20), d(11, 28)),
        )
    }
}
