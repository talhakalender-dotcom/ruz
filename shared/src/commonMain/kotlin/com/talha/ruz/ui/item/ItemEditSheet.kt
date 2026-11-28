package com.talha.ruz.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.ItemFields
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem
import com.talha.ruz.data.RuzLink
import com.talha.ruz.remind.ReminderDefaults
import com.talha.ruz.ui.dayLabel
import com.talha.ruz.ui.durationLabel
import com.talha.ruz.ui.epochToLocal
import com.talha.ruz.ui.hhmm
import com.talha.ruz.ui.localNow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val DurationChoices = listOf(15, 30, 60, 120, 180, 240, 480)

/**
 * Öğeye dokununca açılan düzenleme paneli: metin, kategori, gün/saat, süre, son tarih, bitti, sil.
 * Gantt (3. aşama) gelene kadar zamanı buradan ayarlarsın.
 */
@Composable
fun ItemEditSheet(
    item: RuzItem,
    categories: List<RuzCategory>,
    onDismiss: () -> Unit,
    onSave: (ItemFields) -> Unit,
    onToggleDone: (Boolean) -> Unit,
    onDelete: () -> Unit,
    predecessors: List<Pair<RuzLink, RuzItem>> = emptyList(),
    successors: List<Pair<RuzLink, RuzItem>> = emptyList(),
    onRemoveLink: (RuzLink) -> Unit = {},
    remindersSupported: Boolean = false,
    seriesLabel: String? = null,
    onStopSeries: () -> Unit = {},
) {
    val start = remember(item.id) { item.startAt?.let(::epochToLocal) }
    var text by remember(item.id) { mutableStateOf(item.text) }
    var categoryId by remember(item.id) { mutableStateOf(item.categoryId) }
    var date by remember(item.id) { mutableStateOf(start?.date) }
    var time by remember(item.id) { mutableStateOf(if (item.allDay) null else start?.time) }
    var duration by remember(item.id) { mutableStateOf(if (item.allDay) null else item.durationMin) }
    var milestone by remember(item.id) { mutableStateOf(item.isMilestone) }
    var reminder by remember(item.id) { mutableStateOf(item.reminderMinBefore) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val today = localNow().date

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.done, onCheckedChange = onToggleDone)
                Text(
                    if (item.done) "Tamamlandı" else "Açık",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Metin") },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Kategori")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("Gelen kutusu") })
                categories.forEach { c ->
                    FilterChip(
                        selected = categoryId == c.id,
                        onClick = { categoryId = c.id },
                        label = { Text(c.name) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(c.color.toInt()), CircleShape)) },
                    )
                }
            }

            SectionLabel("Zaman")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { pickDate = true },
                    label = { Text(date?.let { dayLabel(it, today) } ?: "Gün seç") },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, Modifier.size(18.dp)) },
                )
                if (date != null) {
                    AssistChip(
                        onClick = { pickTime = true },
                        label = { Text(time?.let(::hhmm) ?: "Tam gün · saat ekle") },
                    )
                    IconButton(onClick = {
                        date = null
                        time = null
                        duration = null
                        milestone = false
                    }) { Icon(Icons.Filled.Close, contentDescription = "Zamanı kaldır") }
                }
            }
            if (time != null) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Süre", style = MaterialTheme.typography.labelLarge)
                    DurationChoices.forEach { d ->
                        FilterChip(selected = (duration ?: 60) == d, onClick = { duration = d }, label = { Text(durationLabel(d)) })
                    }
                    TextButton(onClick = { time = null; duration = null }) { Text("Tam gün yap") }
                }
            }
            if (date != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = milestone, onCheckedChange = { milestone = it })
                    Text(
                        "Son tarih (◆ kilometre taşı)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            if (date != null && remindersSupported) {
                SectionLabel("Hatırlatma")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Tam gün işler o sabah 09:00'da hatırlatır; saati olanlar seçilen kadar önce.
                    val choices = if (time == null) {
                        listOf(null to "Sabah 09:00", ReminderDefaults.OFF to "Kapalı")
                    } else {
                        listOf(
                            null to "15 dk önce",
                            0 to "Başlarken",
                            30 to "30 dk önce",
                            60 to "1 sa önce",
                            ReminderDefaults.OFF to "Kapalı",
                        )
                    }
                    choices.forEach { (value, label) ->
                        val selected = reminder == value ||
                            (time == null && value == null && reminder != ReminderDefaults.OFF)
                        FilterChip(selected = selected, onClick = { reminder = value }, label = { Text(label) })
                    }
                }
            }

            if (item.seriesId != null) {
                SectionLabel("Tekrar")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "↻ " + (seriesLabel ?: "Tekrar eden iş") + "\nDeğişiklikler yalnızca bu tekrara uygulanır.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onStopSeries) { Text("Tekrarı durdur") }
                }
            }

            if (predecessors.isNotEmpty() || successors.isNotEmpty()) {
                SectionLabel("Bağlar")
                predecessors.forEach { (l, other) ->
                    LinkRow("Şu bitince başlar: ${other.text}") { onRemoveLink(l) }
                }
                successors.forEach { (l, other) ->
                    LinkRow("Bu bitince başlar: ${other.text}") { onRemoveLink(l) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val tz = TimeZone.currentSystemDefault()
                        val d = date
                        val t = time
                        onSave(
                            ItemFields(
                                text = text,
                                categoryId = categoryId,
                                startAt = d?.let { LocalDateTime(it, t ?: LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds() },
                                durationMin = when {
                                    t != null -> duration ?: 60
                                    d != null -> 1440
                                    else -> item.durationMin
                                },
                                allDay = d != null && t == null,
                                isMilestone = d != null && milestone,
                                // Tam günde yalnız "açık/kapalı" var; dakika değeri saat silinince varsayılana döner.
                                reminderMinBefore = if (t == null && reminder != ReminderDefaults.OFF) null else reminder,
                            ),
                        )
                    },
                ) { Text("Kaydet") }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: today).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                    }
                    pickDate = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Vazgeç") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(
            initialHour = time?.hour ?: 9,
            initialMinute = time?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime(state.hour, state.minute)
                    if (duration == null) duration = 60
                    pickTime = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Vazgeç") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun LinkRow(text: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Bağı kaldır") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
