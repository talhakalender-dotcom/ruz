package com.talha.ruz.recur

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Tekrar sıklığı. */
enum class Freq(val code: String) {
    Daily("D"),
    Weekly("W"),
    Monthly("M"),
    Yearly("Y"),
}

/**
 * Tekrar kuralı: "her gün", "her pazartesi ve perşembe", "iki haftada bir cuma", "her ayın 15'i",
 * "her ayın son günü", "her yıl 15 aralık".
 *
 * [weekdays]: ISO gün numaraları (1 = pazartesi). [monthDay]: ayın günü, -1 = son gün. [month]: yıllık tekrarda ay.
 */
data class Recurrence(
    val freq: Freq,
    val interval: Int = 1,
    val weekdays: Set<Int> = emptySet(),
    val monthDay: Int? = null,
    val month: Int? = null,
) {
    /** Veritabanında saklanan biçim: "M/1//15/". */
    fun encode(): String =
        listOf(freq.code, interval.toString(), weekdays.sorted().joinToString(","), monthDay?.toString() ?: "", month?.toString() ?: "")
            .joinToString("/")

    /** [anchor] (ilk tekrar) ve sonrasındaki, [from]..[until] (ikisi dahil) arasındaki tekrar günleri. */
    fun occurrences(anchor: LocalDate, from: LocalDate, until: LocalDate): List<LocalDate> {
        if (until < from) return emptyList()
        val out = ArrayList<LocalDate>()
        fun take(d: LocalDate) {
            if (d >= anchor && d >= from && d <= until) out += d
        }
        val step = interval.coerceAtLeast(1)
        when (freq) {
            Freq.Daily -> {
                var d = anchor
                while (d <= until && out.size < 2000) {
                    take(d)
                    d = d.plus(step, DateTimeUnit.DAY)
                }
            }
            Freq.Weekly -> {
                val days = weekdays.ifEmpty { setOf(anchor.dayOfWeek.isoDayNumber) }.sorted()
                var monday = anchor.minus(anchor.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
                while (monday <= until && out.size < 2000) {
                    days.forEach { take(monday.plus(it - 1, DateTimeUnit.DAY)) }
                    monday = monday.plus(7 * step, DateTimeUnit.DAY)
                }
            }
            Freq.Monthly -> {
                var first = LocalDate(anchor.year, anchor.month.ordinal + 1, 1)
                while (first <= until && out.size < 2000) {
                    take(dayInMonth(first, monthDay ?: anchor.day))
                    first = first.plus(step, DateTimeUnit.MONTH)
                }
            }
            Freq.Yearly -> {
                val m = month ?: (anchor.month.ordinal + 1)
                var y = anchor.year
                while (y <= until.year && out.size < 500) {
                    take(dayInMonth(LocalDate(y, m, 1), monthDay ?: anchor.day))
                    y += step
                }
            }
        }
        return out.sorted()
    }

    /** Türkçe etiket: "Her gün", "Her pazartesi, perşembe", "2 haftada bir cuma", "Her ayın 15'i". */
    fun label(): String {
        val names = listOf("pazartesi", "salı", "çarşamba", "perşembe", "cuma", "cumartesi", "pazar")
        val months = listOf("ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos", "eylül", "ekim", "kasım", "aralık")
        return when (freq) {
            Freq.Daily -> if (interval == 1) "Her gün" else "$interval günde bir"
            Freq.Weekly -> {
                val days = weekdays.sorted()
                val dayText = when (days) {
                    listOf(1, 2, 3, 4, 5) -> "hafta içi her gün"
                    else -> days.joinToString(", ") { names[it - 1] }
                }
                when {
                    days == listOf(1, 2, 3, 4, 5) && interval == 1 -> "Hafta içi her gün"
                    interval == 1 -> if (days.isEmpty()) "Her hafta" else "Her $dayText"
                    else -> "$interval haftada bir" + if (days.isEmpty()) "" else " $dayText"
                }
            }
            Freq.Monthly -> {
                val day = when (monthDay) {
                    null -> ""
                    -1 -> " son günü"
                    else -> " $monthDay'${possessive(monthDay)}"
                }
                if (interval == 1) "Her ayın$day" else "$interval ayda bir$day"
            }
            Freq.Yearly -> {
                val date = if (month != null && monthDay != null) " ${monthDay} ${months[month - 1]}" else ""
                if (interval == 1) "Her yıl$date" else "$interval yılda bir$date"
            }
        }
    }

    companion object {
        fun decode(s: String?): Recurrence? {
            if (s.isNullOrBlank()) return null
            val p = s.split("/")
            if (p.size < 5) return null
            val freq = Freq.entries.firstOrNull { it.code == p[0] } ?: return null
            return Recurrence(
                freq = freq,
                interval = p[1].toIntOrNull() ?: 1,
                weekdays = p[2].split(",").mapNotNull { it.toIntOrNull() }.toSet(),
                monthDay = p[3].toIntOrNull(),
                month = p[4].toIntOrNull(),
            )
        }

        /** Ayın [day]. günü; ay kısaysa son güne yuvarlanır, -1 = son gün. */
        fun dayInMonth(firstOfMonth: LocalDate, day: Int): LocalDate {
            val last = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            return if (day == -1 || day >= last.day) last else LocalDate(firstOfMonth.year, firstOfMonth.month.ordinal + 1, day.coerceAtLeast(1))
        }

        /** "15'i", "1'i", "3'ü", "6'sı", "10'u", "20'si" — Türkçe iyelik eki. */
        fun possessive(n: Int): String = when (n % 10) {
            0 -> when (n % 100 / 10) {
                1, 3 -> "u"
                2, 5 -> "si"
                7, 8 -> "i"
                else -> "ı"
            }
            1, 5, 8 -> "i"
            2, 7 -> "si"
            3, 4 -> "ü"
            6 -> "sı"
            else -> "u"
        }
    }
}
