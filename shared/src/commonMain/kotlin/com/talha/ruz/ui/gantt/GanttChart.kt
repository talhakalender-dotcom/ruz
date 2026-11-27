package com.talha.ruz.ui.gantt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem
import com.talha.ruz.gantt.DragHandle
import com.talha.ruz.gantt.Span
import com.talha.ruz.gantt.TimeScale
import com.talha.ruz.gantt.applyDrag
import com.talha.ruz.gantt.hourLabelStepMinutes
import com.talha.ruz.gantt.localDayStart
import com.talha.ruz.gantt.snapStepMinutes
import com.talha.ruz.ui.TurkishMonthShort
import com.talha.ruz.ui.epochToLocal
import com.talha.ruz.ui.hhmm
import com.talha.ruz.ui.plan.TurkishDayShort
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val DAY_MS = 86_400_000L
private const val INBOX_GROUP = "__inbox__"

/** Gantt'taki bir satır: kategori başlığı ya da iş. */
private sealed interface GanttRow {
    val top: Float
    val height: Float

    data class Group(
        val key: String,
        val title: String,
        val color: Long?,
        val count: Int,
        val collapsed: Boolean,
        override val top: Float,
        override val height: Float,
    ) : GanttRow

    data class Task(val item: RuzItem, val color: Long?, override val top: Float, override val height: Float) : GanttRow
}

private sealed interface Hit {
    data class Bar(val item: RuzItem, val handle: DragHandle) : Hit
    data class Open(val item: RuzItem) : Hit
    data class Toggle(val key: String) : Hit
}

/** Hareket işleyicisinin her zaman güncel ölçüleri görmesi için. */
private data class Geo(val labelW: Float, val maxScroll: Float, val minPpm: Float, val maxPpm: Float)

private data class DragPreview(val itemId: String, val handle: DragHandle, val span: Span)

private fun RuzItem.span(): Span = Span(startAt!!, durationMin ?: if (allDay) 1440 else 60)

/**
 * Gantt şeması. Her iş bir satır, satırlar kategoriye göre gruplu (başlığa dokununca grup kapanır).
 * - Tek parmak: kaydırma. İki parmak: yakınlaştırma (15 dk ↔ haftalar).
 * - Çubuğu basılı tut, sonra sürükle: taşı. Uçlarını basılı tutup çek: başlangıç/süre.
 * - Çubuğa ya da ada dokun: düzenleme paneli.
 * - Fare: tekerlek dikey, Shift+tekerlek yatay kaydırır, Ctrl+tekerlek yakınlaştırır.
 */
@Composable
fun GanttChart(
    items: List<RuzItem>,
    categories: List<RuzCategory>,
    now: LocalDateTime,
    onOpen: (RuzItem) -> Unit,
    onReschedule: (item: RuzItem, startAt: Long, durationMin: Int) -> Unit,
    modifier: Modifier = Modifier,
    onShowList: (() -> Unit)? = null,
) {
    val tz = remember { TimeZone.currentSystemDefault() }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme

    val todayStart = remember(now.date) { now.date.atStartOfDayIn(tz).toEpochMilliseconds() }
    val nowMs = remember(now) { now.toInstant(tz).toEpochMilliseconds() }

    BoxWithConstraints(modifier) {
        val px = { v: Float -> with(density) { v.dp.toPx() } }
        val labelW = px(if (maxWidth >= 520.dp) 150f else 104f)
        val headerH = px(44f)
        val rowH = px(40f)
        val groupH = px(30f)
        val barH = px(26f)
        val handleW = px(16f)
        val timelineW = max(1f, with(density) { maxWidth.toPx() } - labelW)
        val viewportH = with(density) { maxHeight.toPx() }

        // Açılış: bu hafta, gün gün (bugün solda).
        val weekPpm = timelineW / (7 * 1440f)
        val minPpm = timelineW / (35 * 1440f)
        val maxPpm = px(72f) / 15f
        var scale by remember { mutableStateOf(TimeScale(todayStart.toDouble(), weekPpm)) }
        var scrollY by remember { mutableFloatStateOf(0f) }
        var collapsed by remember { mutableStateOf(emptySet<String>()) }
        var drag by remember { mutableStateOf<DragPreview?>(null) }

        // ---- satırlar ----
        val scheduled = items.filter { it.startAt != null }
        val known = categories.map { it.id }.toSet()
        val groups = buildList {
            categories.forEach { c ->
                val tasks = scheduled.filter { it.categoryId == c.id }
                if (tasks.isNotEmpty()) add(Triple(c.id, c, tasks))
            }
            val loose = scheduled.filter { it.categoryId == null || it.categoryId !in known }
            if (loose.isNotEmpty()) add(Triple(INBOX_GROUP, null, loose))
        }
        val rows = buildList {
            var y = 0f
            groups.forEach { (key, cat, tasks) ->
                val isCollapsed = key in collapsed
                add(GanttRow.Group(key, cat?.name ?: "Gelen kutusu", cat?.color, tasks.size, isCollapsed, y, groupH))
                y += groupH
                if (!isCollapsed) {
                    tasks.sortedWith(compareBy({ it.startAt }, { it.order })).forEach { t ->
                        add(GanttRow.Task(t, cat?.color, y, rowH))
                        y += rowH
                    }
                }
            }
        }
        val contentH = rows.lastOrNull()?.let { it.top + it.height } ?: 0f
        val maxScroll = max(0f, contentH - (viewportH - headerH) + px(72f))
        if (scrollY > maxScroll) scrollY = maxScroll

        fun barRect(item: RuzItem, span: Span): Pair<Float, Float> {
            val x0 = labelW + scale.x(span.startMs)
            val x1 = labelW + scale.x(span.endMs)
            return if (item.isMilestone) {
                val cx = if (item.allDay) (x0 + x1) / 2f else x0
                (cx - barH / 2f) to (cx + barH / 2f)
            } else {
                x0 to max(x1, x0 + px(4f))
            }
        }

        fun hitTest(p: Offset): Hit? {
            if (p.y < headerH) return null
            val y = p.y - headerH + scrollY
            val row = rows.firstOrNull { y >= it.top && y < it.top + it.height } ?: return null
            return when (row) {
                is GanttRow.Group -> Hit.Toggle(row.key)
                is GanttRow.Task -> {
                    if (p.x < labelW) return Hit.Open(row.item)
                    val (x0, x1) = barRect(row.item, row.item.span())
                    val tol = px(6f)
                    when {
                        p.x < x0 - tol || p.x > x1 + handleW -> null
                        row.item.isMilestone -> Hit.Bar(row.item, DragHandle.Move)
                        p.x > x1 - handleW / 2f -> Hit.Bar(row.item, DragHandle.End)
                        x1 - x0 >= handleW * 3 && p.x < x0 + handleW / 2f -> Hit.Bar(row.item, DragHandle.Start)
                        p.x <= x1 -> Hit.Bar(row.item, DragHandle.Move)
                        else -> null
                    }
                }
            }
        }

        val geo by rememberUpdatedState(Geo(labelW, maxScroll, minPpm, maxPpm))
        val latestHit by rememberUpdatedState(::hitTest)
        val latestOpen by rememberUpdatedState(onOpen)
        val latestReschedule by rememberUpdatedState(onReschedule)

        fun onTap(hit: Hit?) {
            when (hit) {
                is Hit.Toggle -> collapsed = if (hit.key in collapsed) collapsed - hit.key else collapsed + hit.key
                is Hit.Open -> latestOpen(hit.item)
                is Hit.Bar -> latestOpen(hit.item)
                null -> Unit
            }
        }

        fun zoomBy(factor: Float, anchorX: Float) {
            scale = scale.zoomAround(anchorX, factor, geo.minPpm, geo.maxPpm)
        }

        val minCell = px(14f)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hit = latestHit(down.position)
                        val slop = viewConfiguration.touchSlop
                        var moved = Offset.Zero
                        // 0: karar yok, 1: kaydır/yakınlaştır, 2: çubuğu sürükle
                        var mode = 0
                        val barHit = hit as? Hit.Bar
                        if (barHit != null) {
                            val r = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                var outcome = 0
                                while (outcome == 0) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull { it.id == down.id }
                                    outcome = when {
                                        e.changes.count { it.pressed } > 1 -> 1
                                        c == null || !c.pressed -> 3
                                        else -> {
                                            moved += c.positionChange()
                                            if (moved.getDistance() > slop) 1 else 0
                                        }
                                    }
                                }
                                outcome
                            }
                            when (r) {
                                null -> mode = 2
                                3 -> {
                                    onTap(hit)
                                    return@awaitEachGesture
                                }
                                else -> mode = 1
                            }
                        }
                        while (mode == 0) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id }
                            when {
                                e.changes.count { it.pressed } > 1 -> mode = 1
                                c == null || !c.pressed -> {
                                    onTap(hit)
                                    return@awaitEachGesture
                                }
                                else -> {
                                    moved += c.positionChange()
                                    if (moved.getDistance() > slop) mode = 1
                                }
                            }
                        }
                        if (mode == 1) {
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.changes.none { it.pressed }) break
                                val zoom = e.calculateZoom()
                                val pan = e.calculatePan()
                                if (zoom != 1f) {
                                    val centroid = e.calculateCentroid(useCurrent = true)
                                    zoomBy(zoom, centroid.x - geo.labelW)
                                }
                                scale = scale.pan(pan.x)
                                scrollY = (scrollY - pan.y).coerceIn(0f, geo.maxScroll)
                                e.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else {
                            val bh = barHit!!
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val original = bh.item.span()
                            var dx = 0f
                            drag = DragPreview(bh.item.id, bh.handle, original)
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id }
                                if (c == null || !c.pressed) break
                                dx += c.positionChange().x
                                c.consume()
                                val step = if (bh.item.allDay) 1440 else snapStepMinutes(scale.pxPerMin, minCell)
                                val deltaMs = dx / scale.pxPerMin * 60_000.0
                                drag = DragPreview(bh.item.id, bh.handle, applyDrag(original, bh.handle, deltaMs, step, tz))
                            }
                            val final = drag
                            drag = null
                            if (final != null && final.span != original) {
                                latestReschedule(bh.item, final.span.startMs, final.span.durationMin)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type != PointerEventType.Scroll) continue
                            val change = e.changes.firstOrNull() ?: continue
                            val d = change.scrollDelta
                            val step = px(48f)
                            when {
                                e.keyboardModifiers.isCtrlPressed ->
                                    zoomBy(if (d.y < 0) 1.2f else 1f / 1.2f, change.position.x - geo.labelW)
                                e.keyboardModifiers.isShiftPressed || abs(d.x) > abs(d.y) ->
                                    scale = scale.pan(-(if (abs(d.x) > abs(d.y)) d.x else d.y) * step)
                                else -> scrollY = (scrollY + d.y * step).coerceIn(0f, geo.maxScroll)
                            }
                            e.changes.forEach { it.consume() }
                        }
                    }
                },
        ) {
            val s = scale
            val ctx = DrawCtx(this, measurer, s, labelW, headerH, tz, todayStart, colors.onSurface, colors.onSurfaceVariant)

            // ---- zaman alanı: günler, ızgara ----
            clipRect(left = labelW, top = 0f) {
                drawDays(ctx, colors.primaryContainer.copy(alpha = 0.30f), colors.surfaceVariant.copy(alpha = 0.45f), colors.outlineVariant)
            }

            // ---- satırlar ----
            clipRect(left = 0f, top = headerH) {
                rows.forEach { row ->
                    val top = headerH + row.top - scrollY
                    if (top > size.height || top + row.height < headerH) return@forEach
                    when (row) {
                        is GanttRow.Group -> drawRect(
                            colors.surfaceVariant.copy(alpha = 0.6f),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, row.height),
                        )
                        is GanttRow.Task -> {
                            val preview = drag?.takeIf { it.itemId == row.item.id }
                            val span = preview?.span ?: row.item.span()
                            val (x0, x1) = barRect(row.item, span)
                            clipRect(left = labelW) {
                                drawBar(ctx, row, x0, x1, top, barH, preview != null, colors.outline)
                            }
                        }
                    }
                }
            }

            // ---- şimdi çizgisi ----
            val nx = labelW + s.x(nowMs)
            if (nx >= labelW && nx <= size.width) {
                drawLine(colors.secondary, Offset(nx, headerH), Offset(nx, size.height), strokeWidth = px(2f))
            }

            // ---- başlık: günler ve saatler ----
            drawRect(colors.surface, topLeft = Offset(labelW, 0f), size = Size(size.width - labelW, headerH))
            clipRect(left = labelW, top = 0f, bottom = headerH) {
                drawHeader(ctx, px(40f), colors.primary)
            }
            drawLine(colors.outlineVariant, Offset(0f, headerH), Offset(size.width, headerH), strokeWidth = 1f)

            // ---- sol sütun: adlar ----
            drawRect(colors.surface, topLeft = Offset(0f, headerH), size = Size(labelW, size.height - headerH))
            clipRect(left = 0f, top = headerH, right = labelW) {
                rows.forEach { row ->
                    val top = headerH + row.top - scrollY
                    if (top > size.height || top + row.height < headerH) return@forEach
                    drawLabel(ctx, row, top, px(10f))
                }
            }
            drawLine(colors.outlineVariant, Offset(labelW, 0f), Offset(labelW, size.height), strokeWidth = 1f)

            // ---- sürüklerken zaman balonu ----
            drag?.let { d ->
                val row = rows.firstOrNull { it is GanttRow.Task && it.item.id == d.itemId } as? GanttRow.Task
                if (row != null) drawDragTooltip(ctx, row, d.span, headerH + row.top - scrollY, colors.inverseSurface, colors.inverseOnSurface)
            }

            if (rows.isEmpty()) {
                val msg = measurer.measure(
                    "Plan boş. Alttaki kutuya \"yarın 14:00 toplantı\" ya da \"cuma hakediş\" gibi yaz.",
                    TextStyle(fontSize = 14.sp, color = colors.onSurfaceVariant),
                    constraints = Constraints(maxWidth = max(1, (size.width - labelW - px(48f)).toInt())),
                )
                drawText(msg, topLeft = Offset(labelW + px(24f), headerH + px(32f)))
            }
        }

        // ---- yakınlaştırma ve "Bugün" düğmeleri ----
        Row(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallFloatingActionButton(onClick = { zoomBy(1f / 1.5f, timelineW / 2f) }) { Text("−", fontSize = 20.sp) }
            SmallFloatingActionButton(onClick = { zoomBy(1.5f, timelineW / 2f) }) { Text("+", fontSize = 20.sp) }
            SmallFloatingActionButton(onClick = {
                // Günlük ve daha geniş görünümde bugün solda; saatlik görünümde şimdi ortada.
                val start = if (scale.pxPerMin * 1440 < timelineW / 2f) todayStart.toDouble()
                else nowMs - (timelineW / 2f) / scale.pxPerMin * 60_000.0
                scale = scale.copy(viewStartMs = start)
            }) { Text("Bugün", modifier = Modifier.padding(horizontal = 12.dp)) }
            if (onShowList != null) {
                SmallFloatingActionButton(onClick = onShowList) { Text("Liste", modifier = Modifier.padding(horizontal = 12.dp)) }
            }
        }
    }
}

/** Çizim için ortak değerler. */
private class DrawCtx(
    val scope: DrawScope,
    val measurer: TextMeasurer,
    val scale: TimeScale,
    val labelW: Float,
    val headerH: Float,
    val tz: TimeZone,
    val todayStart: Long,
    val text: Color,
    val muted: Color,
) {
    fun px(v: Float) = with(scope) { v.dp.toPx() }

    /** Görünen günlerin gece yarıları (biraz taşmalı). */
    fun visibleDays(): List<Long> {
        val viewEnd = scale.time(scope.size.width - labelW)
        var d = localDayStart(scale.viewStartMs.toLong(), tz) - DAY_MS
        val out = ArrayList<Long>()
        while (d < viewEnd + DAY_MS && out.size < 400) {
            out += d
            d = localDayStart(d + DAY_MS + 3_600_000L, tz)
        }
        return out
    }

    fun measure(text: String, style: TextStyle, maxWidth: Float): TextLayoutResult =
        measurer.measure(
            text,
            style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = max(1, maxWidth.toInt())),
        )
}

private fun DrawScope.drawDays(ctx: DrawCtx, todayTint: Color, weekendTint: Color, line: Color) {
    val s = ctx.scale
    ctx.visibleDays().forEach { d ->
        val x0 = ctx.labelW + s.x(d)
        val x1 = ctx.labelW + s.x(d + DAY_MS)
        val local = epochToLocal(d)
        val iso = local.date.dayOfWeek.isoDayNumber
        when {
            d == ctx.todayStart -> drawRect(todayTint, Offset(x0, 0f), Size(x1 - x0, size.height))
            iso >= 6 -> drawRect(weekendTint, Offset(x0, 0f), Size(x1 - x0, size.height))
        }
        drawLine(line, Offset(x0, 0f), Offset(x0, size.height), strokeWidth = if (iso == 1) 2f else 1f)
        // Saat çizgileri (yakınken)
        val step = hourLabelStepMinutes(s.pxPerMin, ctx.px(40f))
        if (step != null && step < 1440) {
            var m = step
            while (m < 1440) {
                val x = ctx.labelW + s.x(d + m * 60_000L)
                drawLine(line.copy(alpha = 0.45f), Offset(x, ctx.headerH), Offset(x, size.height), strokeWidth = 1f)
                m += step
            }
        }
    }
}

private fun DrawScope.drawHeader(ctx: DrawCtx, minLabelPx: Float, accent: Color) {
    val s = ctx.scale
    val dayW = s.pxPerMin * 1440
    val small = TextStyle(fontSize = 11.sp, color = ctx.muted)
    val dayStyle = TextStyle(fontSize = 12.sp, color = ctx.text, fontWeight = FontWeight.Medium)
    val hourStep = hourLabelStepMinutes(s.pxPerMin, minLabelPx)
    val dayRowH = if (hourStep != null) ctx.headerH * 0.5f else ctx.headerH

    ctx.visibleDays().forEach { d ->
        val date = epochToLocal(d).date
        val x0 = ctx.labelW + s.x(d)
        val isToday = d == ctx.todayStart
        val label = when {
            dayW >= ctx.px(96f) -> "${TurkishDayShort[date.dayOfWeek.isoDayNumber - 1]} ${date.day} ${TurkishMonthShort[date.month.ordinal]}"
            dayW >= ctx.px(56f) -> "${TurkishDayShort[date.dayOfWeek.isoDayNumber - 1]} ${date.day}"
            dayW >= ctx.px(22f) -> "${date.day}"
            date.dayOfWeek.isoDayNumber == 1 -> "${date.day} ${TurkishMonthShort[date.month.ordinal]}"
            else -> null
        } ?: return@forEach
        val style = if (isToday) dayStyle.copy(color = accent, fontWeight = FontWeight.Bold) else dayStyle
        val layout = ctx.measure(label, style, max(dayW, ctx.px(60f)))
        // Gün geniş ve kısmen ekran dışındaysa etiket görünür kısma yapışır.
        val visibleStart = max(x0, ctx.labelW)
        val x = if (dayW >= ctx.px(56f)) {
            min(visibleStart + ctx.px(6f), x0 + dayW - layout.size.width - ctx.px(6f)).coerceAtLeast(x0 + ctx.px(4f))
        } else {
            x0 + ctx.px(3f)
        }
        drawText(layout, topLeft = Offset(x, (dayRowH - layout.size.height) / 2f))

        if (hourStep != null) {
            var m = hourStep
            while (m < 1440) {
                val hx = ctx.labelW + s.x(d + m * 60_000L)
                val h = m / 60
                val mm = m % 60
                val t = if (mm == 0) h.toString().padStart(2, '0') else "${h.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}"
                val tl = ctx.measure(t, small, ctx.px(48f))
                drawText(tl, topLeft = Offset(hx - tl.size.width / 2f, dayRowH + (ctx.headerH - dayRowH - tl.size.height) / 2f))
                m += hourStep
            }
        }
    }
}

private fun DrawScope.drawBar(
    ctx: DrawCtx,
    row: GanttRow.Task,
    x0: Float,
    x1: Float,
    rowTop: Float,
    barH: Float,
    dragging: Boolean,
    fallback: Color,
) {
    val item = row.item
    val base = row.color?.let { Color(it.toInt()) } ?: fallback
    val color = if (item.done) base.copy(alpha = 0.35f) else base
    val top = rowTop + (row.height - barH) / 2f
    val textStyle = TextStyle(
        fontSize = 12.sp,
        color = Color.White,
        textDecoration = if (item.done) TextDecoration.LineThrough else null,
    )

    if (item.isMilestone) {
        val cx = (x0 + x1) / 2f
        val cy = top + barH / 2f
        val r = barH / 2f
        val path = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        }
        drawPath(path, color)
        if (dragging) drawPath(path, ctx.text, style = Stroke(width = ctx.px(2f)))
        val l = ctx.measure(item.text, textStyle.copy(color = ctx.text), ctx.px(220f))
        drawText(l, topLeft = Offset(cx + r + ctx.px(6f), cy - l.size.height / 2f))
        return
    }

    val w = x1 - x0
    val radius = CornerRadius(ctx.px(6f))
    drawRoundRect(color, topLeft = Offset(x0, top), size = Size(w, barH), cornerRadius = radius)
    if (dragging) {
        drawRoundRect(ctx.text, topLeft = Offset(x0, top), size = Size(w, barH), cornerRadius = radius, style = Stroke(width = ctx.px(2f)))
    }
    // Uç tutamakları (geniş çubuklarda ince çizgiler)
    if (w >= ctx.px(48f) && !item.done) {
        val gy0 = top + barH * 0.3f
        val gy1 = top + barH * 0.7f
        val g = Color.White.copy(alpha = 0.6f)
        drawLine(g, Offset(x0 + ctx.px(4f), gy0), Offset(x0 + ctx.px(4f), gy1), strokeWidth = ctx.px(1.5f))
        drawLine(g, Offset(x1 - ctx.px(4f), gy0), Offset(x1 - ctx.px(4f), gy1), strokeWidth = ctx.px(1.5f))
    }
    val inside = w - ctx.px(16f)
    if (inside >= ctx.px(36f)) {
        // Çubuk ekranın solundan taşıyorsa metin görünür kısımda başlar.
        val start = max(x0, ctx.labelW) + ctx.px(8f)
        val l = ctx.measure(item.text, textStyle, x1 - start - ctx.px(8f))
        if (x1 - start > ctx.px(30f)) drawText(l, topLeft = Offset(start, top + (barH - l.size.height) / 2f))
    } else {
        val l = ctx.measure(item.text, textStyle.copy(color = if (item.done) ctx.muted else ctx.text), ctx.px(220f))
        drawText(l, topLeft = Offset(x1 + ctx.px(6f), top + (barH - l.size.height) / 2f))
    }
}

private fun DrawScope.drawLabel(ctx: DrawCtx, row: GanttRow, top: Float, pad: Float) {
    when (row) {
        is GanttRow.Group -> {
            val arrow = if (row.collapsed) "▸" else "▾"
            val style = TextStyle(fontSize = 12.sp, color = ctx.text, fontWeight = FontWeight.SemiBold)
            row.color?.let { c ->
                drawCircle(Color(c.toInt()), radius = ctx.px(4f), center = Offset(pad + ctx.px(14f), top + row.height / 2f))
            }
            val arrowL = ctx.measure(arrow, style, ctx.px(14f))
            drawText(arrowL, topLeft = Offset(pad - ctx.px(4f), top + (row.height - arrowL.size.height) / 2f))
            val title = if (row.collapsed) "${row.title} (${row.count})" else row.title
            val l = ctx.measure(title, style, ctx.labelW - pad - ctx.px(26f))
            drawText(l, topLeft = Offset(pad + ctx.px(22f), top + (row.height - l.size.height) / 2f))
        }
        is GanttRow.Task -> {
            val style = TextStyle(
                fontSize = 13.sp,
                color = if (row.item.done) ctx.muted else ctx.text,
                textDecoration = if (row.item.done) TextDecoration.LineThrough else null,
            )
            val l = ctx.measure(row.item.text, style, ctx.labelW - pad * 2 - ctx.px(8f))
            drawText(l, topLeft = Offset(pad + ctx.px(8f), top + (row.height - l.size.height) / 2f))
        }
    }
}

private fun DrawScope.drawDragTooltip(ctx: DrawCtx, row: GanttRow.Task, span: Span, rowTop: Float, bg: Color, fg: Color) {
    val start = epochToLocal(span.startMs)
    val end = epochToLocal(span.endMs)
    val label = if (row.item.allDay) {
        val lastDay = epochToLocal(span.endMs - 1).date
        val a = "${TurkishDayShort[start.date.dayOfWeek.isoDayNumber - 1]} ${start.date.day} ${TurkishMonthShort[start.date.month.ordinal]}"
        if (lastDay == start.date) a
        else "$a – ${TurkishDayShort[lastDay.dayOfWeek.isoDayNumber - 1]} ${lastDay.day} ${TurkishMonthShort[lastDay.month.ordinal]}"
    } else {
        "${TurkishDayShort[start.date.dayOfWeek.isoDayNumber - 1]} ${start.date.day} ${TurkishMonthShort[start.date.month.ordinal]} " +
            "${hhmm(start.time)}–${hhmm(end.time)}"
    }
    val l = ctx.measure(label, TextStyle(fontSize = 12.sp, color = fg, fontWeight = FontWeight.Medium), ctx.px(260f))
    val padH = ctx.px(8f)
    val padV = ctx.px(4f)
    val w = l.size.width + padH * 2
    val h = l.size.height + padV * 2
    val x = (ctx.labelW + ctx.scale.x(span.startMs)).coerceIn(ctx.labelW + ctx.px(4f), max(ctx.labelW + ctx.px(4f), size.width - w - ctx.px(4f)))
    val y = max(ctx.headerH + ctx.px(2f), rowTop - h - ctx.px(2f))
    drawRoundRect(bg, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(ctx.px(6f)))
    drawText(l, topLeft = Offset(x + padH, y + padV))
}
