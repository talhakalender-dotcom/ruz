package com.talha.ruz.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem
import com.talha.ruz.ui.dayLabel
import com.talha.ruz.ui.epochToLocal
import com.talha.ruz.ui.gantt.GanttChart
import com.talha.ruz.ui.gantt.GanttDropTarget
import com.talha.ruz.data.RuzLink
import com.talha.ruz.gantt.DragHandle
import androidx.compose.ui.geometry.Offset
import com.talha.ruz.ui.timeRangeLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Plan alanı: Gantt şeması ya da gün gün liste (sağ alttaki düğmeyle geçilir).
 * Öğeye dokununca düzenleme paneli açılır.
 */
@Composable
fun PlanPane(
    items: List<RuzItem>,
    categories: List<RuzCategory>,
    now: LocalDateTime,
    onOpen: (RuzItem) -> Unit,
    links: List<RuzLink>,
    onReschedule: (item: RuzItem, startAt: Long, durationMin: Int, handle: DragHandle) -> Unit,
    onLink: (item: RuzItem, after: RuzItem) -> Unit,
    modifier: Modifier = Modifier,
    dropTarget: GanttDropTarget? = null,
    externalHover: Offset? = null,
) {
    var asList by rememberSaveable { mutableStateOf(false) }
    if (!asList) {
        GanttChart(
            items = items,
            categories = categories,
            now = now,
            onOpen = onOpen,
            links = links,
            onReschedule = onReschedule,
            onLink = onLink,
            modifier = modifier,
            onShowList = { asList = true },
            dropTarget = dropTarget,
            externalHover = externalHover,
        )
        return
    }
    Box(modifier) {
        AgendaList(items, categories, now, onOpen, Modifier.fillMaxSize())
        SmallFloatingActionButton(
            onClick = { asList = false },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        ) { Text("Gantt", modifier = Modifier.padding(horizontal = 12.dp)) }
    }
}

/** Gün gün liste: üstte haftalık şerit, altta günü belli öğeler. */
@Composable
private fun AgendaList(
    items: List<RuzItem>,
    categories: List<RuzCategory>,
    now: LocalDateTime,
    onOpen: (RuzItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = now.date
    val scheduled = items.filter { it.isScheduled && !it.done }.sortedBy { it.startAt }
    val colorOf = categories.associate { it.id to it.color }

    Column(modifier) {
        WeekStrip(now, scheduled, colorOf, Modifier.fillMaxWidth().height(96.dp))
        HorizontalDivider()
        if (scheduled.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Plan boş.\nAlttaki kutuya \"yarın 14:00 toplantı\" ya da \"cuma hakediş\" gibi yaz; " +
                        "günü belli öğeler buraya düşer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        // Geçmişte kalıp bitmemişler "Gecikenler" başlığında toplanır.
        val groups: List<Pair<String, List<RuzItem>>> = scheduled
            .groupBy { item -> epochToLocal(item.startAt!!).date.let { if (it < today) null else it } }
            .entries
            .sortedBy { it.key ?: LocalDate(1, 1, 1) }
            .map { (day, list) -> (day?.let { dayLabel(it, today) } ?: "Gecikenler") to list }

        LazyColumn(Modifier.weight(1f)) {
            groups.forEach { (label, list) ->
                item(key = "h-$label") {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (label == "Gecikenler") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { item ->
                    AgendaRow(item, colorOf[item.categoryId], showDate = label == "Gecikenler", today = today, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(item: RuzItem, color: Long?, showDate: Boolean, today: LocalDate, onOpen: (RuzItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val time = buildString {
            if (showDate) append(dayLabel(epochToLocal(item.startAt!!).date, today)).append(' ')
            append(timeRangeLabel(item))
        }
        Text(
            time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(if (showDate) 132.dp else 96.dp),
        )
        Box(
            Modifier.size(10.dp).background(
                color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.outline,
                CircleShape,
            ),
        )
        Text(
            (if (item.isMilestone) "◆ " else "") + item.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Bu haftanın 7 günü; bugünün sütunu renkli, kehribar çizgi şimdiki an, noktalar o günün öğeleri. */
@Composable
private fun WeekStrip(
    now: LocalDateTime,
    scheduled: List<RuzItem>,
    colorOf: Map<String, Long>,
    modifier: Modifier,
) {
    val days = weekDays(now.date)
    val todayIndex = days.indexOf(now.date)
    val nowFraction = dayFraction(now.hour, now.minute)
    val perDay = days.map { d ->
        scheduled.filter { epochToLocal(it.startAt!!).date == d }.map { colorOf[it.categoryId] }
    }

    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, color = colors.onSurfaceVariant)
    val todayStyle = labelStyle.copy(color = colors.primary)

    Canvas(modifier.padding(8.dp)) {
        val headerH = 28.dp.toPx()
        val colW = size.width / 7f
        if (todayIndex >= 0) {
            drawRect(
                color = colors.primaryContainer.copy(alpha = 0.35f),
                topLeft = Offset(colW * todayIndex, 0f),
                size = Size(colW, size.height),
            )
        }
        val dotR = 4.dp.toPx()
        days.forEachIndexed { i, d ->
            val x = colW * i
            if (i > 0) drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            val label = "${TurkishDayShort[i]} ${d.day}"
            val layout = measurer.measure(label, if (i == todayIndex) todayStyle else labelStyle)
            drawText(layout, topLeft = Offset(x + (colW - layout.size.width) / 2f, (headerH - layout.size.height) / 2f))
            // O günün öğeleri: en çok 8 nokta, iki sıra.
            perDay[i].take(8).forEachIndexed { k, c ->
                val col = k % 4
                val row = k / 4
                val cx = x + colW / 2f + (col - 1.5f) * dotR * 3f
                val cy = headerH + 16.dp.toPx() + row * dotR * 3f
                drawCircle(c?.let { Color(it.toInt()) } ?: colors.outline, radius = dotR, center = Offset(cx, cy))
            }
        }
        drawLine(colors.outlineVariant, Offset(0f, headerH), Offset(size.width, headerH), strokeWidth = 1f)
        if (todayIndex >= 0) {
            val x = colW * (todayIndex + nowFraction)
            drawLine(colors.secondary, Offset(x, headerH), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}
