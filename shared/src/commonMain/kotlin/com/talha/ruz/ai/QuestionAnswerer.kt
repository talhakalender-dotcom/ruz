package com.talha.ruz.ai

import com.talha.ruz.ask.QuestionScope
import com.talha.ruz.money.Money
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem
import com.talha.ruz.ui.epochToLocal
import com.talha.ruz.ui.hhmm
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.minutes

/** Sorunun cevabı: Nano'nun metni (yoksa null), dayandığı işler ve varsa neden cevap üretilemediği. */
data class QuestionAnswer(val text: String?, val sources: List<RuzItem>, val note: String?)

/**
 * "?" ile biten soruyu cevaplar. Bağlamı kurallar hazırlar (zaman aralığı + anahtar kelimeler),
 * Nano yalnızca bu işlere bakarak kısa bir Türkçe cümle kurar. Nano yoksa bulunan işler gösterilir.
 */
object QuestionAnswerer {
    private const val MAX_ITEMS = 25
    private val MonthTr = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    private val DayTr = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    private val MonthEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val DayEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** Soruya ilgili işleri seçer. */
    fun relevantItems(question: String, items: List<RuzItem>, categories: List<RuzCategory>, now: LocalDateTime): List<RuzItem> {
        val range = QuestionScope.range(question, now)
        val stems = QuestionScope.keywords(question)
        val matchedCategories = categories.filter { QuestionScope.matches(it.name, stems) }.map { it.id }.toSet()
        fun hit(i: RuzItem) = QuestionScope.matches(i.text, stems) || i.categoryId in matchedCategories
        fun day(i: RuzItem): LocalDate? = i.startAt?.let { epochToLocal(it).date }

        val open = items.filter { !it.done }
        val picked = if (range != null) {
            val inRange = open.filter { i -> day(i)?.let { it in range } == true }
            if (stems.isEmpty()) inRange else inRange.filter(::hit)
        } else {
            val hits = open.filter(::hit)
            hits.ifEmpty {
                // Anahtar kelime yoksa önümüzdeki 7 günün planı.
                if (stems.isEmpty()) {
                    val end = now.date.plus(7, DateTimeUnit.DAY)
                    open.filter { i -> day(i)?.let { it >= now.date && it < end } == true }
                } else {
                    emptyList()
                }
            }
        }
        return picked.sortedBy { it.startAt ?: Long.MAX_VALUE }.take(MAX_ITEMS)
    }

    fun prompt(question: String, items: List<RuzItem>, categories: List<RuzCategory>, now: LocalDateTime): String {
        val names = categories.associate { it.id to it.name }
        val lines = items.joinToString("\n") { i ->
            buildString {
                append("- ").append(i.text).append(" (").append(names[i.categoryId] ?: "Gelen kutusu").append(")")
                i.startAt?.let { append(" — ").append(dateText(i, it)) }
                if (i.isMilestone) append(" [son tarih]")
                i.amountKurus?.let { append(" [tutar: ").append(Money.format(it)).append("]") }
            }
        }
        val d = now.date
        val todayEn = "${DayEn[d.dayOfWeek.isoDayNumber - 1]}, ${d.day} ${MonthEn[d.month.ordinal]} ${d.year}"
        val todayTr = "${d.day} ${MonthTr[d.month.ordinal]} ${d.year} ${DayTr[d.dayOfWeek.isoDayNumber - 1]}"
        return "Today is $todayEn ($todayTr). The user's notes and plan items (in Turkish):\n$lines\n" +
            "Answer the question briefly in Turkish, using only these items. " +
            "If the answer is not in them, say \"Bu konuda notun yok.\"\n" +
            "Question: $question"
    }

    private fun dateText(i: RuzItem, start: Long): String {
        val s = epochToLocal(start)
        val day = "${s.date.day} ${MonthTr[s.date.month.ordinal]} ${DayTr[s.date.dayOfWeek.isoDayNumber - 1]}"
        if (i.allDay) return "$day (tam gün)"
        val end = i.durationMin?.let { epochToLocal(start + it.minutes.inWholeMilliseconds) }
        return "$day ${hhmm(s.time)}" + (end?.let { "-" + hhmm(it.time) } ?: "")
    }

    suspend fun answer(
        question: String,
        items: List<RuzItem>,
        categories: List<RuzCategory>,
        now: LocalDateTime,
        ai: AiEngine,
    ): QuestionAnswer {
        val sources = relevantItems(question, items, categories, now)
        if (sources.isEmpty()) return QuestionAnswer("Bu konuda notun yok.", emptyList(), null)
        if (ai.status.value != AiStatus.Available) {
            return QuestionAnswer(null, sources, "Yapay zekâ şu an kullanılamıyor (${ai.status.value.label}). Bulduğum işler:")
        }
        return try {
            val text = ai.generate(prompt(question, sources, categories, now))
            QuestionAnswer(text.ifBlank { null }, sources, if (text.isBlank()) "Boş cevap geldi. Bulduğum işler:" else null)
        } catch (e: Exception) {
            QuestionAnswer(null, sources, "Yapay zekâ cevap veremedi (${e.message ?: e::class.simpleName}). Bulduğum işler:")
        }
    }
}
