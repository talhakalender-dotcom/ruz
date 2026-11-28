package com.talha.ruz.ui.inbox

import androidx.compose.ui.geometry.Offset
import com.talha.ruz.data.RuzItem

/**
 * Listedeki bir öğeyi başka bir panele (Gantt'a) sürükleme. Konumlar pencereye göredir.
 * [onEnd] öğe bir yere bırakıldıysa true döner.
 */
class ItemDragCallbacks(
    val onStart: (RuzItem, Offset) -> Unit,
    val onMove: (Offset) -> Unit,
    val onEnd: (RuzItem, Offset) -> Boolean,
    val onCancel: () -> Unit,
)
