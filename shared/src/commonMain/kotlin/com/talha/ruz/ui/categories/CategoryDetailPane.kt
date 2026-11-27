package com.talha.ruz.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem
import com.talha.ruz.ui.whenLabel
import kotlinx.datetime.LocalDate

/** Bir kategorinin tikli listesi (alışveriş, fikirler, okunacaklar...). Bitenler altta katlanır. */
@Composable
fun CategoryDetailPane(
    category: RuzCategory,
    items: List<RuzItem>,
    today: LocalDate,
    onBack: () -> Unit,
    onToggleDone: (id: String, done: Boolean) -> Unit,
    onOpen: (RuzItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = items.filter { !it.done }
    val done = items.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }
    var showDone by rememberSaveable(category.id) { mutableStateOf(false) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") }
            Box(Modifier.size(14.dp).background(Color(category.color.toInt()), CircleShape))
            Text(
                category.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            Text(
                "${open.size} açık",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            if (open.isEmpty()) {
                item {
                    Text(
                        "Liste boş. Bu ekran açıkken alttaki kutuya yazdıkların doğrudan buraya eklenir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(open, key = { it.id }) { item -> ItemRow(item, today, onToggleDone, onOpen) }
            if (done.isNotEmpty()) {
                item(key = "done-header") {
                    ListItem(
                        headlineContent = { Text("Tamamlananlar (${done.size})") },
                        trailingContent = {
                            Icon(
                                if (showDone) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable { showDone = !showDone },
                    )
                    HorizontalDivider()
                }
                if (showDone) items(done, key = { it.id }) { item -> ItemRow(item, today, onToggleDone, onOpen) }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: RuzItem,
    today: LocalDate,
    onToggleDone: (id: String, done: Boolean) -> Unit,
    onOpen: (RuzItem) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                item.text,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = whenLabel(item, today)?.let { w -> @Composable { Text(w) } },
        leadingContent = { Checkbox(checked = item.done, onCheckedChange = { onToggleDone(item.id, it) }) },
        modifier = Modifier.fillMaxWidth().clickable { onOpen(item) },
    )
    HorizontalDivider()
}
