package com.talha.ruz.ui.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.RuzItem

/** Kategorisi olmayan, henüz yerine konmamış öğeler. */
@Composable
fun InboxPane(
    items: List<RuzItem>,
    onToggleDone: (id: String, done: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gelen kutusu boş", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aklına geleni alttaki kutuya yaz. Kategorisi olmayanlar burada bekler, sonra plana taşırsın.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        return
    }
    LazyColumn(modifier) {
        items(items, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.text) },
                leadingContent = {
                    Checkbox(checked = item.done, onCheckedChange = { onToggleDone(item.id, it) })
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
        }
    }
}
