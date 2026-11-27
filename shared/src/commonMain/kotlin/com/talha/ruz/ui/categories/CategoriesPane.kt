package com.talha.ruz.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem

/** Kategoriler: dokununca liste açılır; ⋮ menüsünden düzenle, taşı, sil. */
@Composable
fun CategoriesPane(
    categories: List<RuzCategory>,
    items: List<RuzItem>,
    onOpen: (RuzCategory) -> Unit,
    onAdd: () -> Unit,
    onEdit: (RuzCategory) -> Unit,
    onMove: (RuzCategory, direction: Int) -> Unit,
    onDelete: (RuzCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openCounts = items.filter { !it.done && it.categoryId != null }.groupingBy { it.categoryId }.eachCount()
    LazyColumn(modifier) {
        item {
            TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Yeni kategori", modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
        }
        if (categories.isEmpty()) {
            item {
                Text(
                    "Hiç kategori yok. Yukarıdan ekleyebilir ya da yazarken #etiket kullanabilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        itemsIndexed(categories, key = { _, c -> c.id }) { index, cat ->
            var menu by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(cat.name) },
                supportingContent = { Text("${openCounts[cat.id] ?: 0} açık öğe") },
                leadingContent = {
                    Box(Modifier.size(14.dp).background(Color(cat.color.toInt()), CircleShape))
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Seçenekler")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Düzenle") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onEdit(cat)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Yukarı taşı") },
                                leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                                enabled = index > 0,
                                onClick = {
                                    menu = false
                                    onMove(cat, -1)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Aşağı taşı") },
                                leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                                enabled = index < categories.lastIndex,
                                onClick = {
                                    menu = false
                                    onMove(cat, 1)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menu = false
                                    onDelete(cat)
                                },
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable { onOpen(cat) },
            )
            HorizontalDivider()
        }
    }
}
