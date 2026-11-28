package com.talha.ruz.remind

import com.talha.ruz.gantt.localDayStart
import kotlinx.datetime.TimeZone

/** Hatırlatma ayarları. İşteki değer null ise varsayılan kullanılır. */
object ReminderDefaults {
    /** Saati olan işler: başlangıçtan kaç dakika önce. */
    const val MINUTES_BEFORE = 15

    /** Bu değer "hatırlatma kapalı" demektir. */
    const val OFF = -1

    /** Tam gün işler ve son tarihler o sabah bu saatte hatırlatır. */
    const val ALL_DAY_HOUR = 9
}

/**
 * Hatırlatmanın çalacağı an (epoch ms); kapalıysa null.
 * Saati olan iş: başlangıçtan [reminderMinBefore] (yoksa 15) dakika önce. Tam gün iş: o sabah 09:00.
 */
fun reminderTriggerAt(startAt: Long, allDay: Boolean, reminderMinBefore: Int?, timeZone: TimeZone): Long? {
    if (reminderMinBefore == ReminderDefaults.OFF) return null
    return if (allDay) {
        localDayStart(startAt, timeZone) + ReminderDefaults.ALL_DAY_HOUR * 3_600_000L
    } else {
        startAt - (reminderMinBefore ?: ReminderDefaults.MINUTES_BEFORE) * 60_000L
    }
}
