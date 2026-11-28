package com.talha.ruz.ask

import com.talha.ruz.parse.ChipKind
import com.talha.ruz.parse.TrDateParser
import com.talha.ruz.parse.foldForMatch
import com.talha.ruz.parse.trLower
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Gün aralığı: [start] dahil, [endExclusive] hariç. */
data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {
    operator fun contains(d: LocalDate): Boolean = d >= start && d < endExclusive
}

/**
 * "?" ile biten sorudan, cevabın hangi zamana ve hangi kelimelere bakacağını kurallarla çıkarır.
 * Yapay zekâ tarih hesabında zayıf olduğu için aralığı burada biz belirleriz; Nano yalnızca cümleyi kurar.
 */
object QuestionScope {
    private val STOP = setOf(
        "ne", "neler", "zaman", "hangi", "var", "varmı", "mı", "mi", "mu", "mü", "benim", "bana", "bu", "şu",
        "için", "gerek", "gerekiyor", "lazım", "nedir", "kaç", "nerede", "neyi", "kim", "nasıl", "ile", "ve",
        "yapacağım", "yapmam", "yapmalıyım", "yapmam", "etmem", "gitmem", "gün", "günü", "hafta", "haftaya",
        "ay", "bugün", "yarın", "sabah", "akşam", "öğlen", "gece", "listemde", "listem", "planım", "planımda",
        "olan", "olarak", "daha", "önce", "sonra", "kadar", "tüm", "bütün", "hepsi", "notum", "notlarım",
    )

    /** Sorunun baktığı gün aralığı; zaman ifadesi yoksa null. */
    fun range(question: String, now: LocalDateTime): DateRange? {
        val q = trLower(question)
        val today = now.date
        val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        fun has(vararg words: String) = words.any { Regex("(?<![\\p{L}])$it").containsMatchIn(q) }
        return when {
            has("gelecek hafta", "önümüzdeki hafta", "haftaya") && !has("haftaya (pazartesi|salı|çarşamba|perşembe|cuma|cumartesi|pazar)") ->
                DateRange(monday.plus(7, DateTimeUnit.DAY), monday.plus(14, DateTimeUnit.DAY))
            has("bu hafta sonu", "hafta sonu") -> {
                val sat = monday.plus(5, DateTimeUnit.DAY)
                DateRange(sat, sat.plus(2, DateTimeUnit.DAY))
            }
            has("bu hafta", "hafta içi") -> DateRange(today, monday.plus(7, DateTimeUnit.DAY))
            has("gelecek ay", "önümüzdeki ay") -> {
                val first = LocalDate(today.year, today.month.ordinal + 1, 1).plus(1, DateTimeUnit.MONTH)
                DateRange(first, first.plus(1, DateTimeUnit.MONTH))
            }
            has("bu ay", "ay sonuna", "ay sonu") -> {
                val first = LocalDate(today.year, today.month.ordinal + 1, 1)
                DateRange(today, first.plus(1, DateTimeUnit.MONTH))
            }
            else -> {
                // Tek gün: "yarın", "cuma", "15 aralık", "pazartesi sabah"...
                // Saat kuralları kapalı: yalnızca saat ("saat 3") bir gün sayılmasın.
                val ignore = setOf(ChipKind.Time, ChipKind.Duration, ChipKind.Category, ChipKind.Milestone)
                val d = TrDateParser.parse(question.trimEnd('?', ' '), now, emptyList(), ignore).date ?: return null
                DateRange(d, d.plus(1, DateTimeUnit.DAY))
            }
        }
    }

    /** Zaman kelimeleri (aralığı zaten [range] çıkarır); anahtar kelime sayılmaz. */
    private val DATE_STEMS = listOf(
        "pazar", "sali", "carsa", "perse", "cuma", "ocak", "ocag", "subat", "mart", "nisan", "mayis", "hazir",
        "temmu", "agust", "eylul", "ekim", "kasim", "arali", "yarin", "bugun", "hafta", "sabah", "aksam",
        "ogle", "gece", "gelec", "onumu", "gecen", "sonu", "saat",
    )

    /** Sorudaki anlamlı kelimelerin sadeleştirilmiş kökleri (en çok 5 harf): "ödemelerim" → "odeme". */
    fun keywords(question: String): List<String> =
        trLower(question)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in STOP }
            .map { foldForMatch(it) }
            .filter { w -> DATE_STEMS.none { w.startsWith(it) } }
            .map { it.take(5) }
            .filter { it.length >= 3 }
            .distinct()

    /** Metin, köklerden biriyle başlayan bir kelime içeriyor mu? */
    fun matches(text: String, stems: List<String>): Boolean {
        if (stems.isEmpty()) return false
        val words = trLower(text).split(Regex("[^\\p{L}\\p{N}]+")).map { foldForMatch(it) }
        return stems.any { s -> words.any { w -> w.startsWith(s) || (w.length >= 3 && s.startsWith(w.take(5))) } }
    }
}
