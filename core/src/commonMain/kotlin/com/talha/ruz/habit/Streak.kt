package com.talha.ruz.habit

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/** Alışkanlık hesapları (saf Kotlin). [weekdays]: ISO gün numaraları, 1 = pazartesi. */
object Streak {
    /**
     * Kaç gündür aralıksız yapılıyor. Yalnız alışkanlığın günleri sayılır (ör. hafta içi ise hafta sonu atlanır).
     * Bugün henüz işaretlenmediyse seri bozulmaz; dünden geriye sayılır.
     */
    fun current(checked: Set<LocalDate>, weekdays: Set<Int>, today: LocalDate): Int {
        val days = weekdays.ifEmpty { (1..7).toSet() }
        var d = if (today in checked) today else today.minus(1, DateTimeUnit.DAY)
        var count = 0
        var guard = 0
        while (guard++ < 3660) {
            if (d.dayOfWeek.isoDayNumber in days) {
                if (d in checked) count++ else break
            }
            d = d.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    /** Son [n] gün (eskiden yeniye) ve her günün durumu. */
    fun lastDays(checked: Set<LocalDate>, weekdays: Set<Int>, today: LocalDate, n: Int = 7): List<DayState> {
        val days = weekdays.ifEmpty { (1..7).toSet() }
        return (n - 1 downTo 0).map { back ->
            val d = today.minus(back, DateTimeUnit.DAY)
            when {
                d in checked -> DayState.Done
                d.dayOfWeek.isoDayNumber !in days -> DayState.Off
                d == today -> DayState.Today
                else -> DayState.Missed
            }
        }
    }

    enum class DayState { Done, Missed, Off, Today }
}
