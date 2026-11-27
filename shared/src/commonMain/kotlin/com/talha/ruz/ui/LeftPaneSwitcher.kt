package com.talha.ruz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Fold açıkken sol panelde Gelen kutusu / Kategoriler arasında geçiş. */
@Composable
internal fun LeftPaneSwitcher(showInbox: Boolean, onSelect: (inbox: Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = showInbox, onClick = { onSelect(true) }, label = { Text("Gelen kutusu") })
        FilterChip(selected = !showInbox, onClick = { onSelect(false) }, label = { Text("Kategoriler") })
    }
}
