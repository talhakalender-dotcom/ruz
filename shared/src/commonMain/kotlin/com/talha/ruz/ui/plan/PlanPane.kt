package com.talha.ruz.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talha.ruz.data.RuzItem
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Plan alanı. 1. aşamada yalnızca haftalık zaman ekseni ve "bugün" çizgisi var;
 * sürükle-bırak Gantt 3. ve 4. aşamada bu bileşenin yerine gelecek.
 */
@Composable
fun PlanPane(items: List<RuzItem>, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Clock.System.now()
        }
    }
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val days = weekDays(local.date)
    val todayIndex = days.indexOf(local.date)
    val nowFraction = dayFraction(local.hour, local.minute)

    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, color = colors.onSurfaceVariant)
    val todayStyle = labelStyle.copy(color = colors.primary)

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val headerH = 28.dp.toPx()
                val colW = size.width / 7f
                // Bugünün sütunu
                if (todayIndex >= 0) {
                    drawRect(
                        color = colors.primaryContainer.copy(alpha = 0.35f),
                        topLeft = Offset(colW * todayIndex, 0f),
                        size = Size(colW, size.height),
                    )
                }
                days.forEachIndexed { i, d ->
                    val x = colW * i
                    if (i > 0) {
                        drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                    val label = "${TurkishDayShort[i]} ${d.day}"
                    val layout = measurer.measure(label, if (i == todayIndex) todayStyle else labelStyle)
                    drawText(
                        layout,
                        topLeft = Offset(x + (colW - layout.size.width) / 2f, (headerH - layout.size.height) / 2f),
                    )
                }
                drawLine(colors.outlineVariant, Offset(0f, headerH), Offset(size.width, headerH), strokeWidth = 1f)
                // Bugün çizgisi: haftalık görünümde sütun içindeki saat konumu
                if (todayIndex >= 0) {
                    val x = colW * (todayIndex + nowFraction)
                    drawLine(colors.secondary, Offset(x, headerH), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            }
            Text(
                "Gantt şeması burada olacak.\nSürükle-bırak 3. aşamada geliyor.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        val scheduled = items.count { it.isScheduled && !it.done }
        Text(
            "Planlanmış öğe: $scheduled",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
