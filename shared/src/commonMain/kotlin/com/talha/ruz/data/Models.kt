package com.talha.ruz.data

import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Arayüzün gördüğü kategori. Veritabanı sınıflarından bağımsızdır. */
data class RuzCategory(
    val id: String,
    val name: String,
    /** ARGB renk. */
    val color: Long,
    val order: Double,
)

/** Aklına gelen her şey: iş, alışveriş, fikir, ödeme... */
data class RuzItem(
    val id: String,
    val text: String,
    val note: String,
    /** null ise Gelen kutusunda. */
    val categoryId: String?,
    val done: Boolean,
    val doneAt: Long?,
    /** Gantt'taki başlangıç (epoch ms). null ise planlanmamış. */
    val startAt: Long?,
    val durationMin: Int?,
    /** Gün belli, saat yok: Gantt'ta tam gün çubuğu. */
    val allDay: Boolean,
    val isMilestone: Boolean,
    val reminderMinBefore: Int?,
    val order: Double,
    val createdAt: Long,
    /** Tekrar eden bir serinin tekrarıysa serinin kimliği. */
    val seriesId: String? = null,
) {
    val isScheduled: Boolean get() = startAt != null
    val inInbox: Boolean get() = categoryId == null
}

data class RuzLink(val id: String, val predecessorId: String, val successorId: String)

/** Kategoriler için seçilebilir renkler. */
val CategoryPalette: List<Long> = listOf(
    0xFF1E5A6B, // petrol
    0xFFB7791F, // amber
    0xFF4E7D3A, // zeytin
    0xFF8E4A8C, // mor
    0xFFC0533A, // kiremit
    0xFF3F62A8, // lacivert
    0xFF6D6A63, // taş grisi
    0xFF2E8C7E, // camgöbeği
)

/** İlk açılışta gelen örnek kategoriler. Hepsi silinebilir. */
val DefaultCategories: List<Pair<String, Long>> = listOf(
    "Genel" to CategoryPalette[6],
    "İş" to CategoryPalette[0],
    "Alışveriş" to CategoryPalette[2],
    "Fikirler" to CategoryPalette[1],
    "Ödemeler" to CategoryPalette[4],
)

internal fun newId(): String = Uuid.random().toString()

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
