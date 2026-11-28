package com.talha.ruz.ui.ask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.talha.ruz.ai.QuestionAnswer
import com.talha.ruz.data.RuzItem
import com.talha.ruz.ui.whenLabel
import kotlinx.datetime.LocalDate

/** Sorunun alttan açılan cevap paneli. [answer] null iken "düşünüyor" gösterilir. */
@Composable
fun QuestionSheet(
    question: String,
    answer: QuestionAnswer?,
    today: LocalDate,
    onOpen: (RuzItem) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(question, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (answer == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Notlarına bakıyorum…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                answer.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                answer.note?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (answer.sources.isNotEmpty()) {
                    if (answer.text != null) {
                        Text(
                            "Dayandığı işler",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column {
                        answer.sources.forEach { item ->
                            Column(Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(vertical = 8.dp)) {
                                Text((if (item.isMilestone) "◆ " else "") + item.text, style = MaterialTheme.typography.bodyLarge)
                                whenLabel(item, today)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            Text(
                "Cevap yalnızca kendi notlarından ve planından üretilir; telefondaki yapay zekâ (Gemini Nano) kullanılır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}
