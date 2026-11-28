package com.talha.ruz.data

import com.talha.ruz.recur.Freq
import com.talha.ruz.recur.Recurrence
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** Tekrar eden iş şablonu. Tekrarları ayrı [RuzItem]'lardır ([RuzItem.seriesId]). */
data class RuzSeries(
    val id: String,
    val rule: Recurrence,
    val text: String,
    val categoryId: String?,
    /** İlk tekrarın günü. */
    val anchorDay: LocalDate,
    /** Günün dakikası; null = tam gün. */
    val timeMin: Int?,
    val durationMin: Int?,
    val isMilestone: Boolean,
    val reminderMinBefore: Int?,
    /** Bu güne kadar (dahil) tekrarlar üretildi. */
    val generatedUntil: LocalDate,
    val amountKurus: Long? = null,
)

/** Tekrarların önceden ne kadar ileriye üretileceği. */
fun RuzSeries.horizonDays(): Int = when (rule.freq) {
    Freq.Daily -> 60
    Freq.Weekly -> 182
    Freq.Monthly -> 366
    Freq.Yearly -> 3 * 366
}

/** Henüz üretilmemiş, [today] + ufuk içine düşen tekrarların başlangıçları ve yeni "üretildi" günü. */
fun RuzSeries.pendingStarts(today: LocalDate, timeZone: TimeZone): Pair<List<Long>, LocalDate> {
    val until = today.plus(horizonDays(), DateTimeUnit.DAY)
    if (generatedUntil >= until) return emptyList<Long>() to generatedUntil
    val from = generatedUntil.plus(1, DateTimeUnit.DAY)
    val starts = rule.occurrences(anchorDay, from, until).map { d ->
        val t = timeMin?.let { LocalTime(it / 60, it % 60) } ?: LocalTime(0, 0)
        LocalDateTime(d, t).toInstant(timeZone).toEpochMilliseconds()
    }
    return starts to until
}

/** Yeni seri için "hiç üretilmedi" günü. */
fun initialGeneratedUntil(anchor: LocalDate): LocalDate = anchor.minus(1, DateTimeUnit.DAY)

/**
 * Listelerde (Gelen kutusu, kategori) bir serinin yalnızca sıradaki bitmemiş tekrarı görünsün;
 * diğer tekrarlar Gantt'ta ve planda durur.
 */
fun collapseSeries(items: List<RuzItem>): List<RuzItem> {
    val nextOf = items.filter { it.seriesId != null && !it.done }
        .groupBy { it.seriesId }
        .mapValues { (_, list) -> list.minByOrNull { it.startAt ?: Long.MAX_VALUE }?.id }
    return items.filter { it.seriesId == null || it.done || nextOf[it.seriesId] == it.id }
}
