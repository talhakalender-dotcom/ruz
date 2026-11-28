package com.talha.ruz.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.HabitCheck
import com.talha.ruz.data.RuzHabit
import com.talha.ruz.habit.Streak
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

private val DayShort = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

/**
 * Gelen kutusunun üstündeki alışkanlıklar: bugünün kutusu, son 7 gün ve kaç gündür aralıksız.
 * Başlığa dokununca katlanır.
 */
@Composable
fun HabitsSection(
    habits: List<RuzHabit>,
    checks: List<HabitCheck>,
    today: LocalDate,
    onToggle: (habit: RuzHabit, done: Boolean) -> Unit,
    onAdd: (name: String, weekdays: Set<Int>) -> Unit,
    onDelete: (RuzHabit) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    val byHabit = checks.groupBy({ it.habitId }, { it.day }).mapValues { it.value.toSet() }
    val dueToday = habits.filter { h -> h.weekdays.isEmpty() || today.dayOfWeek.isoDayNumber in h.weekdays }
    val doneToday = dueToday.count { today in byHabit[it.id].orEmpty() }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Alışkanlıklar" + if (habits.isNotEmpty()) " · bugün $doneToday/${dueToday.size}" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Yeni alışkanlık") }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (expanded) {
            if (habits.isEmpty()) {
                Text(
                    "Her gün yapmak istediğin şeyleri (spor, kitap, ilaç…) + ile ekle; işaretledikçe seri sayılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            habits.forEach { h -> HabitRow(h, byHabit[h.id].orEmpty(), today, onToggle, onDelete) }
        }
        HorizontalDivider(thickness = 2.dp)
    }

    if (adding) {
        AddHabitDialog(
            onDismiss = { adding = false },
            onSave = { name, days ->
                adding = false
                onAdd(name, days)
            },
        )
    }
}

@Composable
private fun HabitRow(
    habit: RuzHabit,
    checked: Set<LocalDate>,
    today: LocalDate,
    onToggle: (RuzHabit, Boolean) -> Unit,
    onDelete: (RuzHabit) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val due = habit.weekdays.isEmpty() || today.dayOfWeek.isoDayNumber in habit.weekdays
    val done = today in checked
    val streak = Streak.current(checked, habit.weekdays, today)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = done, onCheckedChange = { onToggle(habit, it) }, enabled = due || done)
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            if (!due) {
                Text("Bugün yok", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        // Son 7 gün: dolu = yapıldı, halka = kaçtı, soluk = o gün yok, kenarlı = bugün
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Streak.lastDays(checked, habit.weekdays, today).forEach { s ->
                val m = Modifier.size(8.dp)
                when (s) {
                    Streak.DayState.Done -> Box(m.background(colors.primary, CircleShape))
                    Streak.DayState.Missed -> Box(m.border(1.dp, colors.outline, CircleShape))
                    Streak.DayState.Off -> Box(m.background(colors.surfaceVariant, CircleShape))
                    Streak.DayState.Today -> Box(m.border(1.5.dp, colors.primary, CircleShape))
                }
            }
        }
        Text(
            if (streak > 0) "$streak gün" else "",
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondary,
            modifier = Modifier.width(52.dp).padding(start = 8.dp),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Seçenekler") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Sil", color = colors.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.error) },
                    onClick = {
                        menu = false
                        onDelete(habit)
                    },
                )
            }
        }
    }
}

@Composable
private fun AddHabitDialog(onDismiss: () -> Unit, onSave: (String, Set<Int>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf((1..7).toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni alışkanlık") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ad (ör. Spor)") }, singleLine = true)
                Text("Günler", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayShort.forEachIndexed { i, label ->
                        val d = i + 1
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && days.isNotEmpty(),
                onClick = { onSave(name.trim(), if (days.size == 7) emptySet() else days) },
            ) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
