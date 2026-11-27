package com.talha.ruz.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.CategoryPalette
import com.talha.ruz.data.RuzCategory

/** Yeni kategori ([initial] null) ya da var olanı düzenleme: ad ve renk. */
@Composable
fun CategoryDialog(
    initial: RuzCategory?,
    suggestedColor: Long,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Long) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableLongStateOf(initial?.color ?: suggestedColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni kategori" else "Kategoriyi düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad") },
                    singleLine = true,
                )
                CategoryPalette.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c ->
                            val selected = c == color
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.toInt()))
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                    .clickable { color = c },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Filled.Check, contentDescription = "Seçili", tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color) }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
