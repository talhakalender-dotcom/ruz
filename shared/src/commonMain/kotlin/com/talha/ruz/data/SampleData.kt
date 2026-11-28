package com.talha.ruz.data

import com.talha.ruz.recur.Freq
import com.talha.ruz.recur.Recurrence
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * Tarayıcı deneme sürümünün açılış verileri. Tarihler bugüne göre kurulur,
 * böylece plan her gün dolu görünür.
 */
fun sampleRepository(today: LocalDate, timeZone: TimeZone): MemoryRuzRepository {
    val cats = DefaultCategories.mapIndexed { i, (name, color) ->
        RuzCategory(id = "c$i", name = name, color = color, order = i.toDouble())
    }
    fun cat(name: String) = cats.first { it.name == name }.id
    fun at(dayOffset: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime(today.plus(dayOffset, DateTimeUnit.DAY), LocalTime(hour, minute))
            .toInstant(timeZone).toEpochMilliseconds()

    var order = 0.0
    fun item(
        text: String,
        category: String? = null,
        start: Long? = null,
        duration: Int? = null,
        allDay: Boolean = false,
        milestone: Boolean = false,
        done: Boolean = false,
    ) = RuzItem(
        id = "s${order.toInt()}",
        text = text,
        note = "",
        categoryId = category?.let(::cat),
        done = done,
        doneAt = if (done) at(0) else null,
        startAt = start,
        durationMin = duration,
        allDay = allDay,
        isMilestone = milestone,
        reminderMinBefore = null,
        order = order++,
        createdAt = at(0),
    )

    val items = listOf(
        item("Balkon için sürgülü cam fiyatı araştır"),
        item("Annemin doğum günü için hediye düşün"),
        item("Mutfak dolabı için usta bul"),
        item("Pompa firmasını ara", "İş", at(0, 9, 0), 30),
        item("Şantiye toplantısı", "İş", at(0, 14, 0), 90),
        item("Hakediş raporu hazırla", "İş", at(1, 9, 0), 180),
        item("Kalıp ve donatı kontrolü", "İş", at(1, 13, 0), 120),
        item("Beton dökümü", "İş", at(1, 15, 0), 360),
        item("KDV beyannamesi teslimi", "Ödemeler", at(3), 1440, allDay = true, milestone = true),
        item("Elektrik faturası", "Ödemeler", at(10), 1440, allDay = true, milestone = true),
        item("Arabanın yağını değiştirt", "Genel", at(4), 1440, allDay = true),
        item("Süt", "Alışveriş"),
        item("Yumurta", "Alışveriş"),
        item("Ekmek", "Alışveriş", done = true),
        item("Deterjan", "Alışveriş"),
        item("Suç ve Ceza'yı okumaya başla", "Fikirler"),
        item("Teras için dikey bahçe", "Fikirler"),
        item("Dünkü kontrol listesi", "İş", at(-1, 16, 0), 60),
    )
    // Örnek zincir: kalıp kontrolü bitince beton dökümü başlar.
    val kalip = items.first { it.text.startsWith("Kalıp") }.id
    val beton = items.first { it.text == "Beton dökümü" }.id
    // Örnek tekrar eden işler; tekrarları açılışta extendSeries üretir.
    val series = listOf(
        RuzSeries(
            id = "r0", rule = Recurrence(Freq.Monthly, monthDay = 15), text = "Kira", categoryId = cat("Ödemeler"),
            anchorDay = today, timeMin = null, durationMin = 1440, isMilestone = true, reminderMinBefore = null,
            generatedUntil = initialGeneratedUntil(today),
        ),
        RuzSeries(
            id = "r1", rule = Recurrence(Freq.Weekly, weekdays = setOf(1)), text = "Şantiye koordinasyon toplantısı",
            categoryId = cat("İş"), anchorDay = today, timeMin = 10 * 60, durationMin = 60, isMilestone = false,
            reminderMinBefore = null, generatedUntil = initialGeneratedUntil(today),
        ),
    )
    return MemoryRuzRepository(cats, items, listOf(RuzLink("l0", kalip, beton)), series)
}
