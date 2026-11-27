package com.talha.ruz.gantt

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Instant

private const val MS_PER_MIN = 60_000.0

/**
 * Gantt'ın zaman ekseni. [viewStartMs]: zaman alanının sol kenarındaki an (epoch ms),
 * [pxPerMin]: bir dakikanın piksel genişliği (yakınlık).
 */
data class TimeScale(val viewStartMs: Double, val pxPerMin: Float) {
    /** Zaman alanında, sol kenardan itibaren x konumu. */
    fun x(ms: Long): Float = ((ms - viewStartMs) / MS_PER_MIN * pxPerMin).toFloat()

    fun time(x: Float): Double = viewStartMs + x / pxPerMin * MS_PER_MIN

    /** Ekseni [dx] piksel kaydırır (parmak sağa giderse geçmişe bakılır). */
    fun pan(dx: Float): TimeScale = copy(viewStartMs = viewStartMs - dx / pxPerMin * MS_PER_MIN)

    /** [anchorX]'teki an yerinde kalacak şekilde yakınlaştırır. */
    fun zoomAround(anchorX: Float, factor: Float, minPxPerMin: Float, maxPxPerMin: Float): TimeScale {
        val anchorTime = time(anchorX)
        val p = (pxPerMin * factor).coerceIn(minPxPerMin, maxPxPerMin)
        return TimeScale(anchorTime - anchorX / p * MS_PER_MIN, p)
    }
}

/**
 * Yakınlığa göre ızgara adımı (dakika): 15 dk, 1 saat ya da 1 gün.
 * [minCellPx]: bir adımın ekranda en az kaç piksel olması gerektiği.
 */
fun snapStepMinutes(pxPerMin: Float, minCellPx: Float): Int = when {
    15 * pxPerMin >= minCellPx -> 15
    60 * pxPerMin >= minCellPx -> 60
    else -> 1440
}

/** Yerel saate göre en yakın adıma yuvarlar (1440 → en yakın gece yarısı). */
fun snapToStep(ms: Long, stepMin: Int, timeZone: TimeZone): Long {
    val dayStart = localDayStart(ms, timeZone)
    val minutes = (ms - dayStart) / MS_PER_MIN
    val rounded = (minutes / stepMin).roundToLong() * stepMin
    return dayStart + rounded * 60_000L
}

/** O anın yerel gününün gece yarısı (epoch ms). */
fun localDayStart(ms: Long, timeZone: TimeZone): Long =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone).toEpochMilliseconds()

/** Etiket aralığı: en az [minLabelPx] genişlikteki en küçük adım; hiçbiri sığmazsa null (yalnız günler yazılır). */
fun hourLabelStepMinutes(pxPerMin: Float, minLabelPx: Float): Int? =
    listOf(15, 30, 60, 120, 180, 360, 720).firstOrNull { it * pxPerMin >= minLabelPx }

/** Çubuğun neresinden tutulduğu. */
enum class DragHandle { Move, Start, End }

/** Bir işin zamanı: başlangıç (epoch ms) ve süre (dakika). */
data class Span(val startMs: Long, val durationMin: Int) {
    val endMs: Long get() = startMs + durationMin * 60_000L
}

/**
 * Sürükleme sonucu yeni zaman. Taşıma başlangıcı, uçlar başlangıcı ya da bitişi ızgaraya oturtur.
 * Süre hiçbir zaman [stepMin]'den kısa olmaz; başlangıç ucu bitişi geçemez.
 */
fun applyDrag(original: Span, handle: DragHandle, deltaMs: Double, stepMin: Int, timeZone: TimeZone): Span {
    val delta = deltaMs.roundToLong()
    val minMs = stepMin * 60_000L
    return when (handle) {
        DragHandle.Move -> original.copy(startMs = snapToStep(original.startMs + delta, stepMin, timeZone))
        DragHandle.End -> {
            val end = snapToStep(original.endMs + delta, stepMin, timeZone).coerceAtLeast(original.startMs + minMs)
            original.copy(durationMin = ((end - original.startMs) / 60_000L).toInt())
        }
        DragHandle.Start -> {
            val start = snapToStep(original.startMs + delta, stepMin, timeZone).coerceAtMost(original.endMs - minMs)
            Span(start, ((original.endMs - start) / 60_000L).toInt())
        }
    }
}
