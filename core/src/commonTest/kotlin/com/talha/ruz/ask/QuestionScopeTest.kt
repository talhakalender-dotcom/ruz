package com.talha.ruz.ask

import com.talha.ruz.remind.ReminderDefaults
import com.talha.ruz.remind.reminderTriggerAt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestionScopeTest {
    /** 25 Kasım 2026 Çarşamba. Bu hafta: 23–29 Kasım. */
    private val now = LocalDateTime(2026, 11, 25, 10, 0)
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)

    @Test fun thisWeekFromToday() {
        assertEquals(DateRange(d(11, 25), d(11, 30)), QuestionScope.range("Bu hafta hangi ödemelerim var?", now))
    }

    @Test fun nextWeek() {
        assertEquals(DateRange(d(11, 30), d(12, 7)), QuestionScope.range("Gelecek hafta ne yapacağım?", now))
    }

    @Test fun weekend() {
        assertEquals(DateRange(d(11, 28), d(11, 30)), QuestionScope.range("Hafta sonu planım ne?", now))
    }

    @Test fun singleDays() {
        assertEquals(DateRange(d(11, 26), d(11, 27)), QuestionScope.range("Yarın ne var?", now))
        assertEquals(DateRange(d(11, 30), d(12, 1)), QuestionScope.range("Pazartesi sabah ne yapacağım?", now))
        assertEquals(DateRange(d(12, 4), d(12, 5)), QuestionScope.range("Haftaya cuma ne var?", now))
        assertEquals(DateRange(d(12, 15), d(12, 16)), QuestionScope.range("15 aralıkta ne var?", now))
    }

    @Test fun noRange() {
        assertNull(QuestionScope.range("Vergi dairesine ne zaman gitmem lazım?", now))
        assertNull(QuestionScope.range("Alışveriş listemde ne var?", now))
        assertNull(QuestionScope.range("Saat 3'te ne var?", now))
    }

    @Test fun keywordsDropFillers() {
        assertEquals(listOf("vergi", "daire"), QuestionScope.keywords("Vergi dairesine ne zaman gitmem lazım?"))
        assertEquals(listOf("odeme"), QuestionScope.keywords("Bu hafta hangi ödemelerim var?"))
        assertEquals(listOf("alisv"), QuestionScope.keywords("Alışveriş listemde ne var?"))
        assertEquals(emptyList(), QuestionScope.keywords("Pazartesi sabah ne yapacağım?"))
        assertEquals(emptyList(), QuestionScope.keywords("15 aralıkta ne var?"))
    }

    @Test fun matching() {
        assertTrue(QuestionScope.matches("Vergi dairesi, KDV beyannamesi", listOf("vergi")))
        assertTrue(QuestionScope.matches("Ödemeler", listOf("odeme")))
        assertTrue(QuestionScope.matches("Alışveriş", listOf("alisv")))
        assertFalse(QuestionScope.matches("Beton dökümü", listOf("vergi")))
        assertFalse(QuestionScope.matches("Beton dökümü", emptyList()))
    }

    @Test fun reminderTimes() {
        val tz = TimeZone.UTC
        val start = LocalDateTime(2026, 11, 25, 14, 0).toInstant(tz).toEpochMilliseconds()
        val min = 60_000L
        assertEquals(start - 15 * min, reminderTriggerAt(start, false, null, tz))
        assertEquals(start, reminderTriggerAt(start, false, 0, tz))
        assertEquals(start - 60 * min, reminderTriggerAt(start, false, 60, tz))
        assertNull(reminderTriggerAt(start, false, ReminderDefaults.OFF, tz))
        val dayStart = LocalDateTime(2026, 11, 25, 0, 0).toInstant(tz).toEpochMilliseconds()
        assertEquals(dayStart + 9 * 60 * min, reminderTriggerAt(dayStart, true, null, tz))
        assertNull(reminderTriggerAt(dayStart, true, ReminderDefaults.OFF, tz))
    }
}
