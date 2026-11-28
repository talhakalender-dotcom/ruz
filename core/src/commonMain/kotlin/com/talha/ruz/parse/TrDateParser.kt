package com.talha.ruz.parse

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.talha.ruz.recur.Freq
import com.talha.ruz.recur.Recurrence
import kotlin.time.Duration.Companion.minutes

/** Yakalama kutusunda anlaşılan parçaların türleri. Önizlemede her biri bir etiket olur. */
enum class ChipKind { Date, Time, Duration, Category, Milestone, Repeat, Amount }

/** Yakalama kutusuna yazılan cümlenin kurallarla ayrıştırılmış hâli. */
data class ParsedCapture(
    val original: String,
    /** Tarih, saat ve #etiket çıkarıldıktan sonra kalan metin. */
    val title: String,
    /** Cümle "?" ile bitiyor: kaydedilmez, soru olarak yapay zekâya gider. */
    val isQuestion: Boolean = false,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val durationMin: Int? = null,
    /** "son gün", "son tarih", "en geç" gibi: ◆ kilometre taşı. */
    val isMilestone: Boolean = false,
    val categoryName: String? = null,
    /** #etiket var olan bir kategoriye uymadı; bu adla yeni kategori açılacak. */
    val categoryIsNew: Boolean = false,
    /** "her ayın 15'i", "her pazartesi" gibi tekrar; [date] ilk tekrarın günüdür. */
    val recurrence: Recurrence? = null,
    /** "350 TL", "₺120,50", "2 bin TL" gibi tutar (kuruş). */
    val amountKurus: Long? = null,
) {
    /** Gün belli ama saat yok: Gantt'ta tam gün çubuğu. */
    val allDay: Boolean get() = date != null && time == null
    val found: Set<ChipKind>
        get() = buildSet {
            if (date != null) add(ChipKind.Date)
            if (time != null) add(ChipKind.Time)
            if (durationMin != null) add(ChipKind.Duration)
            if (categoryName != null) add(ChipKind.Category)
            if (isMilestone) add(ChipKind.Milestone)
            if (recurrence != null) add(ChipKind.Repeat)
            if (amountKurus != null) add(ChipKind.Amount)
        }
}

/**
 * Türkçe tarih/saat ayrıştırıcı. Yapay zekâ kullanmaz; kurallar sabittir ve testlerle korunur.
 *
 * Anladıkları (örnekler):
 * - Gün: bugün, yarın, öbür gün, cuma, cumaya, cuma günü, haftaya cuma, bu pazartesi, hafta sonu, ay sonu
 * - Tarih: 15 aralık, 3 ocak 2027, 15.12, 15/12/2026, ayın 15'i, 15'inde
 * - Saat: 14:00, 9.30, saat 3, 9'da, akşam 7, sabah, öğleden sonra, akşam, gece
 * - Aralık ve süre: 14:00-16:00, saat 14-16, 2 saat, 45 dk, yarım saat
 * - Göreli: 2 gün sonra, 3 saat sonra, yarım saat sonra, bir hafta sonra
 * - Kategori: #iş, #alış (Alışveriş'e uyar), #kitaplar (yoksa yeni kategori)
 * - Son tarih: son gün, son tarih, son ödeme, en geç
 * - Tutar: 350 TL, 1.250,50 TL, ₺120, 2 bin TL, 80 lira
 * - Tekrar: her gün, her sabah, hafta içi, her pazartesi ve perşembe, her hafta cuma, iki haftada bir,
 *   her ayın 15'i, her ayın son günü, 3 ayda bir, her yıl 15 aralık
 *
 * [ignore]: kullanıcının önizlemede kaldırdığı etiketler; o tür kelimeler başlıkta kalır.
 */
object TrDateParser {

    fun parse(
        text: String,
        now: LocalDateTime,
        categories: List<String>,
        ignore: Set<ChipKind> = emptySet(),
    ): ParsedCapture {
        val trimmed = text.trim()
        if (trimmed.endsWith("?")) return ParsedCapture(original = text, title = trimmed, isQuestion = true)
        return Run(text, now, categories, ignore).execute()
    }

    /** Ayrıştırılmış sonucu Gantt başlangıcına (epoch ms) çevirir. */
    fun startEpochMs(p: ParsedCapture, timeZone: TimeZone): Long? {
        val d = p.date ?: return null
        return LocalDateTime(d, p.time ?: LocalTime(0, 0)).toInstant(timeZone).toEpochMilliseconds()
    }

    private class Run(
        val text: String,
        val now: LocalDateTime,
        val categories: List<String>,
        val ignore: Set<ChipKind>,
    ) {
        val low = trLower(text)
        val used = BooleanArray(text.length)
        val today: LocalDate = now.date

        var date: LocalDate? = null
        var time: LocalTime? = null
        var duration: Int? = null
        var dayPart: DayPart? = null
        var milestone = false
        var categoryName: String? = null
        var categoryIsNew = false
        var recurrence: Recurrence? = null
        var amount: Long? = null

        fun execute(): ParsedCapture {
            if (ChipKind.Category !in ignore) category()
            if (ChipKind.Repeat !in ignore) repeats()
            // Tutar tarih/saatten önce: "12.50 TL" saat, "1.250 TL" tarih sanılmasın.
            if (ChipKind.Amount !in ignore) amounts()
            if (ChipKind.Time !in ignore) {
                timeRanges()
                relativeOffsets(timeUnits = true)
            }
            if (ChipKind.Date !in ignore) {
                relativeOffsets(timeUnits = false)
                numericDates()
                monthNameDates()
                dayOfMonth()
                weekAndMonthEnds()
                weekdays()
                relativeDays()
            }
            if (ChipKind.Time !in ignore) {
                dayPartWithHour()
                clockTimes()
                bareHours()
                dayPartAlone()
            }
            if (ChipKind.Duration !in ignore) durations()

            // Saat var, gün yok: saat geçmediyse bugün, geçtiyse yarın.
            if (time == null && dayPart != null) time = dayPart!!.default
            val t = time
            if (t != null && dayPart != null && t.hour < 12) time = dayPart!!.shift(t)
            if (time != null && date == null) {
                date = if (time!! >= now.time) today else today.plus(1, DateTimeUnit.DAY)
            }
            recurrence?.let { finishRecurrence(it) }
            if (ChipKind.Milestone !in ignore && date != null) milestone()

            return ParsedCapture(
                original = text,
                title = buildTitle(),
                date = date,
                time = time,
                durationMin = duration,
                isMilestone = milestone,
                categoryName = categoryName,
                categoryIsNew = categoryIsNew,
                recurrence = recurrence,
                amountKurus = amount,
            )
        }

        // ---- yardımcılar ----

        /** [regex] eşleşmelerinden kullanılmamış olanları [handle]'a verir; true dönerse metni "kullanıldı" işaretler. */
        fun scan(regex: Regex, handle: (MatchResult) -> Boolean) {
            for (m in regex.findAll(low)) {
                val r = m.range
                if (r.isEmpty()) continue
                if ((r.first..r.last).any { used[it] }) continue
                if (handle(m)) for (i in r) used[i] = true
            }
        }

        fun buildTitle(): String {
            val sb = StringBuilder()
            text.forEachIndexed { i, c -> sb.append(if (used[i]) ' ' else c) }
            val cleaned = sb.toString()
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim(',', ';', '-', '–', ':', '.', ' ')
                .replace(Regex("\\s+([,;.])"), "$1")
            return cleaned.ifEmpty { text.trim() }
        }

        fun preceded(start: Int, word: String): Boolean {
            val from = start - word.length - 1
            return from >= 0 && low.substring(from, start) == "$word "
        }

        // ---- kurallar ----

        fun category() {
            scan(Regex("#([\\p{L}\\p{N}_-]+)")) { m ->
                if (categoryName == null) {
                    val tag = text.substring(m.groups[1]!!.range)
                    val match = matchCategory(tag, categories)
                    if (match != null) {
                        categoryName = match
                    } else {
                        categoryName = newCategoryName(tag)
                        categoryIsNew = true
                    }
                }
                true
            }
        }

        fun timeRanges() {
            val hm = "(\\d{1,2})(?:[:.](\\d{2}))?"
            val patterns = listOf(
                // 14:00-16:00, 14.00 - 16
                Regex("$B(?:saat\\s+)?(\\d{1,2})[:.](\\d{2})\\s*[-–]\\s*$hm(?:\\s*(?:arası|arasında))?$E"),
                // saat 14-16
                Regex("${B}saat\\s+$hm\\s*[-–]\\s*$hm(?:\\s*(?:arası|arasında))?$E"),
                // 14-16 arası
                Regex("$B$hm\\s*[-–]\\s*$hm\\s+(?:arası|arasında)$E"),
                // 14:00'ten 16:00'ya kadar
                Regex("$B(\\d{1,2})[:.](\\d{2})['’]?(?:den|dan|ten|tan)\\s+(\\d{1,2})[:.](\\d{2})['’]?(?:e|a|ye|ya)(?:\\s+kadar)?$E"),
            )
            for (p in patterns) {
                scan(p) { m ->
                    if (time != null) return@scan false
                    val g = m.groupValues
                    val start = hourMinute(g[1], g[2]) ?: return@scan false
                    var end = hourMinute(g[3], g[4]) ?: return@scan false
                    var span = end.minutes() - start.minutes()
                    if (span <= 0 && end.hour < 12) {
                        end = LocalTime(end.hour + 12, end.minute)
                        span = end.minutes() - start.minutes()
                    }
                    if (span <= 0) return@scan false
                    time = start
                    duration = span
                    true
                }
            }
        }

        /** "3 saat sonra", "yarım saat sonra" (timeUnits) ya da "2 gün sonra", "bir hafta sonra". */
        fun relativeOffsets(timeUnits: Boolean) {
            val units = if (timeUnits) "dakika|dk|saat" else "gün|hafta|ay"
            scan(Regex("$B($NUM)\\s*($units)\\s+sonra(?:ya)?$E")) { m ->
                val n = parseNumber(m.groupValues[1]) ?: return@scan false
                when (m.groupValues[2]) {
                    "dakika", "dk", "saat" -> {
                        if (time != null || date != null) return@scan false
                        val mins = if (m.groupValues[2] == "saat") (n * 60).toInt() else n.toInt()
                        val tz = TimeZone.UTC
                        val target = (now.toInstant(tz) + mins.minutes).toLocalDateTime(tz)
                        date = target.date
                        time = LocalTime(target.hour, target.minute)
                    }
                    "gün" -> setDate(today.plus(n.toInt(), DateTimeUnit.DAY))
                    "hafta" -> setDate(today.plus(n.toInt() * 7, DateTimeUnit.DAY))
                    "ay" -> setDate(today.plus(n.toInt(), DateTimeUnit.MONTH))
                }
                true
            }
        }

        fun setDate(d: LocalDate): Boolean {
            if (date != null) return false
            date = d
            return true
        }

        /** 15.12, 15/12, 15.12.2026, 15/12/26 */
        fun numericDates() {
            scan(Regex("$B(\\d{1,2})([./])(\\d{1,2})(?:\\2(\\d{4}|\\d{2}))?(?:['’]\\p{L}+)?$E")) { m ->
                if (date != null) return@scan false
                if (preceded(m.range.first, "saat")) return@scan false
                // "1.5 saat" bir süredir, tarih değil.
                if (UNIT_AFTER.containsMatchIn(low.substring(m.range.last + 1))) return@scan false
                val g = m.groupValues
                val day = g[1].toInt()
                val month = g[3].toInt()
                // "14.30" gibi saatler buraya düşmesin: ay 1-12 olmalı.
                if (month !in 1..12 || day !in 1..31) return@scan false
                val year = g[4].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
                val d = resolveDayMonth(day, month, year) ?: return@scan false
                date = d
                true
            }
        }

        /** 15 aralık, 3 ocak 2027, 15 Aralık'ta */
        fun monthNameDates() {
            scan(Regex("$B(\\d{1,2})\\s+($MONTHS)['’]?\\p{L}*(?:\\s+(\\d{4}))?(?:\\s+kadar)?$E")) { m ->
                if (date != null) return@scan false
                val g = m.groupValues
                val month = monthNumber(g[2]) ?: return@scan false
                val year = g[3].takeIf { it.isNotEmpty() }?.toInt()
                val d = resolveDayMonth(g[1].toInt(), month, year) ?: return@scan false
                date = d
                true
            }
        }

        /** ayın 15'i, 15'inde, 15'ine kadar */
        fun dayOfMonth() {
            val possessive = "(?:i|ı|u|ü|si|sı|inde|ında|unde|ünde|sinde|sında|ine|ına|une|üne)"
            val patterns = listOf(
                Regex("${B}ayın\\s+(\\d{1,2})(?:['’]?$possessive)?(?:\\s+kadar)?$E"),
                Regex("$B(\\d{1,2})['’]$possessive(?:\\s+kadar)?$E"),
            )
            for (p in patterns) {
                scan(p) { m ->
                    if (date != null) return@scan false
                    val day = m.groupValues[1].toInt()
                    if (day !in 1..31) return@scan false
                    var d = clampDay(today.year, today.month.ordinal + 1, day)
                    if (d < today) {
                        val next = today.plus(1, DateTimeUnit.MONTH)
                        d = clampDay(next.year, next.month.ordinal + 1, day)
                    }
                    date = d
                    true
                }
            }
        }

        fun weekAndMonthEnds() {
            scan(Regex("${B}hafta\\s*sonu(?:nda|na|ya)?$E")) {
                // Cumartesi; bugün cumartesi ya da pazarsa bugün.
                val iso = today.dayOfWeek.isoDayNumber
                setDate(if (iso >= 6) today else today.plus(6 - iso, DateTimeUnit.DAY))
            }
            scan(Regex("$B(?:ay\\s*sonu|ayın\\s+sonu)(?:nda|na|ya)?$E")) {
                setDate(lastDayOfMonth(today))
            }
        }

        fun weekdays() {
            val suffix = "(?:['’]?(?:ya|ye|yı|yi|da|de|dan|den|ları|leri))?"
            val regex = Regex(
                "$B(?:(bu|gelecek|önümüzdeki|haftaya)\\s+)?" +
                    "(?:(pazartesi|salı|çarşamba|perşembe|cumartesi|cuma)$suffix|(pazar)(?:['’](?:a|ı|da|dan))?)" +
                    "(?:\\s+günü(?:ne|nde|)?)?(?:\\s+kadar)?$E",
            )
            scan(regex) { m ->
                if (date != null) return@scan false
                val g = m.groupValues
                val name = g[2].ifEmpty { g[3] }
                val iso = WEEKDAYS.indexOf(name) + 1
                if (iso <= 0) return@scan false
                val todayIso = today.dayOfWeek.isoDayNumber
                date = when (g[1]) {
                    "gelecek", "önümüzdeki", "haftaya" ->
                        today.minus(todayIso - 1, DateTimeUnit.DAY).plus(7 + iso - 1, DateTimeUnit.DAY)
                    else -> today.plus((iso - todayIso + 7) % 7, DateTimeUnit.DAY)
                }
                true
            }
        }

        fun relativeDays() {
            val table = listOf(
                Regex("${B}(?:bugün|bu gün)(?:e\\s+kadar|e|kü|den)?$E") to 0,
                Regex("${B}(?:öbür\\s*gün|ertesi gün|yarından sonra)(?:e)?$E") to 2,
                Regex("${B}yarın(?:a\\s+kadar|a|ki|dan)?$E") to 1,
                Regex("${B}haftaya$E") to 7,
            )
            for ((regex, days) in table) scan(regex) { setDate(today.plus(days, DateTimeUnit.DAY)) }
            scan(Regex("$B(?:gelecek|önümüzdeki)\\s+hafta(?:ya)?$E")) {
                setDate(today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY).plus(7, DateTimeUnit.DAY))
            }
            scan(Regex("$B(?:gelecek|önümüzdeki)\\s+ay(?:a)?$E")) {
                val next = today.plus(1, DateTimeUnit.MONTH)
                setDate(LocalDate(next.year, next.month.ordinal + 1, 1))
            }
        }

        /** akşam 7, sabah 8:30, gece 11'de, öğleden sonra 3 */
        fun dayPartWithHour() {
            scan(Regex("$B($DAY_PARTS)\\s+(?:saat\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?(?:['’]?(?:de|da|te|ta|e|a|ye|ya))?$E")) { m ->
                if (time != null) return@scan false
                val part = DayPart.of(m.groupValues[1]) ?: return@scan false
                val t = hourMinute(m.groupValues[2], m.groupValues[3]) ?: return@scan false
                dayPart = part
                time = part.shift(t)
                true
            }
        }

        /** 14:00, 9.30, saat 14:30'da */
        fun clockTimes() {
            scan(Regex("$B(saat\\s+)?(\\d{1,2})([:.])(\\d{2})(?:['’]?(?:de|da|te|ta|e|a|ye|ya|den|dan|ten|tan))?$E")) { m ->
                if (time != null) return@scan false
                val g = m.groupValues
                // "12.50 TL" saat sayılmasın: noktalı yazımda "saat" ya da çeyrek dakika (00/15/30/45) gerekir.
                val minute = g[4].toInt()
                if (g[3] == "." && g[1].isEmpty() && minute % 15 != 0) return@scan false
                time = hourMinute(g[2], g[4]) ?: return@scan false
                true
            }
        }

        /** saat 3, saat 14'te, 9'da */
        fun bareHours() {
            val patterns = listOf(
                Regex("${B}saat\\s+(\\d{1,2})(?:['’]?(?:de|da|te|ta|e|a|ye|ya|den|dan|ten|tan))?$E"),
                Regex("$B(\\d{1,2})['’](?:de|da|te|ta)$E"),
            )
            for (p in patterns) {
                scan(p) { m ->
                    if (time != null) return@scan false
                    val h = m.groupValues[1].toInt()
                    if (h !in 0..23) return@scan false
                    // Gün içi varsayım: 1-6 öğleden sonradır (saat 3 → 15:00).
                    time = LocalTime(if (h in 1..6) h + 12 else h, 0)
                    true
                }
            }
        }

        fun dayPartAlone() {
            scan(Regex("$B(?:bu\\s+)?($DAY_PARTS)(?:a|e|ları|leri)?$E")) { m ->
                if (dayPart != null) return@scan false
                dayPart = DayPart.of(m.groupValues[1]) ?: return@scan false
                true
            }
        }

        /** 2 saat, 45 dk, yarım saat, 1,5 saatlik */
        fun durations() {
            scan(Regex("$B($NUM)\\s*(saat|dakika|dk)(?:lik|lık)?$E")) { m ->
                if (duration != null) return@scan false
                val n = parseNumber(m.groupValues[1]) ?: return@scan false
                val mins = if (m.groupValues[2] == "saat") (n * 60).toInt() else n.toInt()
                if (mins <= 0) return@scan false
                duration = mins
                true
            }
        }

        fun milestone() {
            scan(Regex("$B(?:son\\s+ödeme(?:\\s+(?:günü|tarihi))?|son\\s+gün(?:ü)?|son\\s+tarih(?:i)?|en\\s+geç)$E")) {
                milestone = true
                true
            }
        }

        /** Tekrar ifadeleri. Gün/ay ayrıntısı eksikse [finishRecurrence] tarih kurallarının sonucundan tamamlar. */
        fun repeats() {
            val possessive = "(?:i|ı|u|ü|si|sı|inde|ında|unde|ünde|sinde|sında|ine|ına|une|üne)"
            val wd = "(?:pazartesi|salı|çarşamba|perşembe|cumartesi|cuma|pazar)"
            // her ayın 15'i, her ay 15'inde
            scan(Regex("${B}her\\s+ay(?:ın)?\\s+(\\d{1,2})(?:['’]?$possessive)?$E")) { m ->
                val day = m.groupValues[1].toInt()
                if (day !in 1..31) return@scan false
                recurrence = Recurrence(Freq.Monthly, monthDay = day)
                true
            }
            // her ayın son günü, her ay sonu
            scan(Regex("${B}her\\s+(?:ayın\\s+son\\s+günü|ay\\s*sonu(?:nda)?)$E")) {
                recurrence = Recurrence(Freq.Monthly, monthDay = -1)
                true
            }
            // hafta içi (her gün)
            scan(Regex("${B}(?:her\\s+)?hafta\\s+içi(?:\\s+her\\s+gün)?$E")) {
                if (recurrence != null) return@scan false
                recurrence = Recurrence(Freq.Weekly, weekdays = setOf(1, 2, 3, 4, 5))
                true
            }
            // her pazartesi ve perşembe
            scan(Regex("${B}her\\s+($wd(?:\\s*(?:,|ve)\\s*$wd)*)(?:\\s+günü|\\s+günleri)?$E")) { m ->
                if (recurrence != null) return@scan false
                val days = Regex(wd).findAll(m.groupValues[1]).map { WEEKDAYS.indexOf(it.value) + 1 }.filter { it > 0 }.toSet()
                if (days.isEmpty()) return@scan false
                recurrence = Recurrence(Freq.Weekly, weekdays = days)
                true
            }
            // iki haftada bir, 3 günde bir, 2 ayda bir
            scan(Regex("$B($NUM)\\s*(gün|hafta|ay|yıl|sene)(?:de|da|te|ta)\\s+bir$E")) { m ->
                if (recurrence != null) return@scan false
                val n = parseNumber(m.groupValues[1])?.toInt()?.takeIf { it >= 1 } ?: return@scan false
                recurrence = Recurrence(freqOf(m.groupValues[2]), interval = n)
                true
            }
            // her gün, her hafta, her ay, her yıl
            scan(Regex("${B}her\\s*(gün|hafta|ay|yıl|sene)$E")) { m ->
                if (recurrence != null) return@scan false
                recurrence = Recurrence(freqOf(m.groupValues[1]))
                true
            }
            // her sabah / her akşam: günlük; saat kelimesi saat kuralına kalır
            scan(Regex("${B}her(?=\\s+(?:sabah|öğle|öğlen|akşam|gece)$E)")) {
                if (recurrence != null) return@scan false
                recurrence = Recurrence(Freq.Daily)
                true
            }
        }

        fun amounts() {
            val num = "(\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?"
            fun kurus(whole: String, frac: String): Long? {
                val lira = whole.replace(".", "").toLongOrNull() ?: return null
                return lira * 100 + (if (frac.isEmpty()) 0L else frac.padEnd(2, '0').toLong())
            }
            // 2 bin TL
            scan(Regex("$B(\\d+)\\s*bin\\s*(?:tl|₺|lira)(?:['’]?\\p{L}*)?$E")) { m ->
                if (amount != null) return@scan false
                amount = (m.groupValues[1].toLongOrNull() ?: return@scan false) * 100_000
                true
            }
            // 350 TL, 1.250,50 TL, 80 lira, 350tl'lik
            scan(Regex("$B$num\\s*(?:tl|₺|lira)(?:['’]?\\p{L}*)?$E")) { m ->
                if (amount != null) return@scan false
                amount = kurus(m.groupValues[1], m.groupValues[2]) ?: return@scan false
                true
            }
            // ₺350
            scan(Regex("₺\\s*$num$E")) { m ->
                if (amount != null) return@scan false
                amount = kurus(m.groupValues[1], m.groupValues[2]) ?: return@scan false
                true
            }
        }

        fun freqOf(word: String): Freq = when (word) {
            "gün" -> Freq.Daily
            "hafta" -> Freq.Weekly
            "ay" -> Freq.Monthly
            else -> Freq.Yearly
        }

        /** Eksik gün/ay bilgisini tamamlar ve ilk tekrarın gününü bulur (saat geçtiyse bir sonraki). */
        fun finishRecurrence(rule: Recurrence) {
            val ref = date ?: today
            val filled = when (rule.freq) {
                Freq.Weekly -> if (rule.weekdays.isEmpty()) rule.copy(weekdays = setOf(ref.dayOfWeek.isoDayNumber)) else rule
                Freq.Monthly -> if (rule.monthDay == null) rule.copy(monthDay = ref.day) else rule
                Freq.Yearly -> rule.copy(month = rule.month ?: (ref.month.ordinal + 1), monthDay = rule.monthDay ?: ref.day)
                Freq.Daily -> rule
            }
            val anchorFrom = if (rule.freq == Freq.Daily && date != null) date!! else today
            val candidates = filled.occurrences(anchorFrom, today, today.plus(800, DateTimeUnit.DAY))
            val t = time
            val first = candidates.firstOrNull { d -> !(d == today && t != null && t < now.time) } ?: candidates.firstOrNull()
            recurrence = filled
            if (first != null) date = first
        }

        // ---- tarih hesapları ----

        /** Yıl verilmediyse bu yıl; tarih geçtiyse gelecek yıl. */
        fun resolveDayMonth(day: Int, month: Int, year: Int?): LocalDate? {
            val d = runCatching { LocalDate(year ?: today.year, month, day) }.getOrNull() ?: return null
            return if (year == null && d < today) runCatching { LocalDate(today.year + 1, month, day) }.getOrNull() else d
        }
    }

    // ---- ortak sabitler ----

    /** Kelime sınırı (Türkçe harfleri de kelime sayar). */
    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}\\p{N}])"

    private val UNIT_AFTER = Regex("^\\s*(?:saat|dakika|dk|gün|hafta|ay)(?![\\p{L}\\p{N}])")

    private const val NUM ="\\d+(?:[.,]5)?|bir buçuk|yarım|bir|iki|üç|dört|beş|altı|yedi|sekiz|dokuz|on"

    private const val MONTHS =
        "ocak|ocağ|şubat|mart|nisan|mayıs|haziran|temmuz|ağustos|eylül|ekim|kasım|aralık|aralığ"

    private const val DAY_PARTS = "öğleden\\s+sonra|akşam\\s*üstü|sabah|öğlen|öğleyin|öğle|akşam|gece"

    private val WEEKDAYS = listOf("pazartesi", "salı", "çarşamba", "perşembe", "cuma", "cumartesi", "pazar")

    private enum class DayPart(val default: LocalTime) {
        Morning(LocalTime(9, 0)),
        Noon(LocalTime(12, 0)),
        Afternoon(LocalTime(15, 0)),
        Evening(LocalTime(19, 0)),
        LateAfternoon(LocalTime(17, 0)),
        Night(LocalTime(22, 0));

        /** "akşam 7" → 19:00; "gece 11" → 23:00, "gece 2" → 02:00. */
        fun shift(t: LocalTime): LocalTime {
            val h = t.hour
            val nh = when (this) {
                Morning -> h
                Noon -> if (h in 1..6) h + 12 else h
                Afternoon, Evening, LateAfternoon -> if (h < 12) h + 12 else h
                Night -> if (h in 7..11) h + 12 else h
            }
            return LocalTime(nh % 24, t.minute)
        }

        companion object {
            fun of(word: String): DayPart? {
                val w = word.replace(Regex("\\s+"), " ")
                return when {
                    w.startsWith("öğleden") -> Afternoon
                    w.startsWith("akşam üstü") || w.startsWith("akşamüstü") -> LateAfternoon
                    w.startsWith("sabah") -> Morning
                    w.startsWith("öğle") -> Noon
                    w.startsWith("akşam") -> Evening
                    w.startsWith("gece") -> Night
                    else -> null
                }
            }
        }
    }

    private fun monthNumber(word: String): Int? = when (word) {
        "ocak", "ocağ" -> 1
        "şubat" -> 2
        "mart" -> 3
        "nisan" -> 4
        "mayıs" -> 5
        "haziran" -> 6
        "temmuz" -> 7
        "ağustos" -> 8
        "eylül" -> 9
        "ekim" -> 10
        "kasım" -> 11
        "aralık", "aralığ" -> 12
        else -> null
    }

    private fun parseNumber(s: String): Double? = when (s) {
        "yarım" -> 0.5
        "bir buçuk" -> 1.5
        "bir" -> 1.0
        "iki" -> 2.0
        "üç" -> 3.0
        "dört" -> 4.0
        "beş" -> 5.0
        "altı" -> 6.0
        "yedi" -> 7.0
        "sekiz" -> 8.0
        "dokuz" -> 9.0
        "on" -> 10.0
        else -> s.replace(',', '.').toDoubleOrNull()
    }

    private fun hourMinute(h: String, m: String): LocalTime? {
        val hour = h.toIntOrNull() ?: return null
        val minute = if (m.isEmpty()) 0 else m.toIntOrNull() ?: return null
        if (hour !in 0..24 || minute !in 0..59) return null
        if (hour == 24 && minute != 0) return null
        return LocalTime(hour % 24, minute)
    }

    private fun LocalTime.minutes(): Int = hour * 60 + minute

    private fun lastDayOfMonth(d: LocalDate): LocalDate =
        LocalDate(d.year, d.month.ordinal + 1, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val last = lastDayOfMonth(LocalDate(year, month, 1)).day
        return LocalDate(year, month, minOf(day, last))
    }
}

/** Türkçe küçük harf: İ→i, I→ı. Uzunluk değişmez, böylece konumlar asıl metinle eşleşir. */
fun trLower(s: String): String = buildString(s.length) {
    s.forEach { c ->
        append(
            when (c) {
                'İ' -> 'i'
                'I' -> 'ı'
                else -> c.lowercaseChar()
            },
        )
    }
}

/** Karşılaştırma için sadeleştirme: küçük harf, Türkçe harfler Latin'e, boşluk/alt çizgi yok. */
fun foldForMatch(s: String): String = buildString {
    trLower(s).forEach { c ->
        when (c) {
            'ı', 'î' -> append('i')
            'ş' -> append('s')
            'ğ' -> append('g')
            'ü', 'û' -> append('u')
            'ö' -> append('o')
            'ç' -> append('c')
            'â' -> append('a')
            ' ', '_', '-' -> Unit
            else -> append(c)
        }
    }
}

/** #etiketi var olan kategoriye eşler: önce tam eşleşme, sonra tek bir kategoriye uyan baş (en az 3 harf). */
fun matchCategory(tag: String, categories: List<String>): String? {
    val t = foldForMatch(tag)
    if (t.isEmpty()) return null
    categories.firstOrNull { foldForMatch(it) == t }?.let { return it }
    if (t.length < 3) return null
    val prefixed = categories.filter { foldForMatch(it).startsWith(t) }
    return prefixed.singleOrNull()
}

/** "#okunacak_kitaplar" → "Okunacak kitaplar" */
fun newCategoryName(tag: String): String {
    val spaced = tag.replace('_', ' ').replace('-', ' ').trim()
    if (spaced.isEmpty()) return spaced
    val first = when (val c = spaced[0]) {
        'i' -> 'İ'
        'ı' -> 'I'
        else -> c.uppercaseChar()
    }
    return first + spaced.substring(1)
}
