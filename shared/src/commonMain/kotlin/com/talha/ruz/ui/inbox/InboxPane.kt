package com.talha.ruz.ui.inbox

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.talha.ruz.ui.whenLabel
import kotlinx.datetime.LocalDate

/** Kategorisi olmayan, henüz yerine konmamış öğeler. Dokununca düzenleme paneli açılır. */
@Composable
fun InboxPane(
    items: List<RuzItem>,
    today: LocalDate,
    onToggleDone: (id: String, done: Boolean) -> Unit,
    onOpen: (RuzItem) -> Unit,
    modifier: Modifier = Modifier,
    drag: ItemDragCallbacks? = null,
    onAddToPlan: ((RuzItem) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val dragCb by rememberUpdatedState(drag)
    val addCb by rememberUpdatedState(onAddToPlan)
    LazyColumn(modifier) {
        if (header != null) item(key = "header") { header() }
        if (items.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gelen kutusu boş", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Aklına geleni alttaki kutuya yaz. #kategori yazmadıkların burada bekler.\n" +
                            "Örnek: \"yarın 14:00 vergi dairesi #iş\", \"süt al #alışveriş\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        items(items, key = { it.id }) { item ->
            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var menu by remember { mutableStateOf(false) }
            Box {
                ListItem(
                    headlineContent = { Text(item.text) },
                    supportingContent = whenLabel(item, today)?.let { w -> @Composable { Text(w) } },
                    leadingContent = {
                        Checkbox(checked = item.done, onCheckedChange = { onToggleDone(item.id, it) })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords = it }
                        .clickable { onOpen(item) }
                        .pointerInput(item.id) {
                            // Basılı tut: Fold açıkken Gantt'a sürükle; bırakmadan kaldırırsan menü açılır.
                            var start = Offset.Zero
                            var moved = Offset.Zero
                            detectDragGesturesAfterLongPress(
                                onDragStart = { local ->
                                    start = (coords?.positionInRoot() ?: Offset.Zero) + local
                                    moved = Offset.Zero
                                    dragCb?.onStart?.invoke(item, start)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    moved += amount
                                    dragCb?.onMove?.invoke(start + moved)
                                },
                                onDragEnd = {
                                    val handled = dragCb?.onEnd?.invoke(item, start + moved) ?: false
                                    if (!handled && moved.getDistance() < 24f && addCb != null) menu = true
                                },
                                onDragCancel = { dragCb?.onCancel?.invoke() },
                            )
                        },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Plana ekle (bugün ilk boş saat)") },
                        onClick = {
                            menu = false
                            onAddToPlan?.invoke(item)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Düzenle") },
                        onClick = {
                            menu = false
                            onOpen(item)
                        },
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
