package com.talha.ruz.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.talha.ruz.data.RuzItem
import com.talha.ruz.ui.plan.TurkishDayShort
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

val TurkishMonthShort = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")

fun localNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** Dakikada bir yenilenen şimdiki zaman; etiketler ("Bugün", "Yarın") gece yarısı kendiliğinden değişir. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(localNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = localNow()
        }
    }
    return now
}

/** "Bugün", "Yarın", "Cmt 29 Kas", gelecek yılsa "Pzt 4 Oca 2027". */
fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Bugün"
    today.plus(1, DateTimeUnit.DAY) -> "Yarın"
    today.minus(1, DateTimeUnit.DAY) -> "Dün"
    else -> buildString {
        append(TurkishDayShort[d.dayOfWeek.isoDayNumber - 1]).append(' ')
        append(d.day).append(' ').append(TurkishMonthShort[d.month.ordinal])
        if (d.year != today.year) append(' ').append(d.year)
    }
}

fun hhmm(t: LocalTime): String = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

fun durationLabel(min: Int): String = when {
    min % 60 == 0 -> "${min / 60} sa"
    min > 60 -> "${min / 60} sa ${min % 60} dk"
    else -> "$min dk"
}

fun epochToLocal(ms: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())

/** "14:00–15:00" ya da "Tam gün". */
fun timeRangeLabel(item: RuzItem): String {
    val start = item.startAt ?: return ""
    if (item.allDay) return "Tam gün"
    val s = epochToLocal(start)
    val d = item.durationMin ?: return hhmm(s.time)
    val e = epochToLocal(start + d.minutes.inWholeMilliseconds)
    return "${hhmm(s.time)}–${hhmm(e.time)}"
}

/** Listelerde öğenin altına yazılan zaman: "◆ Yarın 14:00–15:00", "Cuma · tam gün". */
fun whenLabel(item: RuzItem, today: LocalDate): String? {
    val start = item.startAt ?: return null
    val s = epochToLocal(start)
    val prefix = if (item.isMilestone) "◆ " else ""
    val day = dayLabel(s.date, today)
    return if (item.allDay) "$prefix$day" else "$prefix$day ${timeRangeLabel(item)}"
}
