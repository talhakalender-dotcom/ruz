package com.talha.ruz.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.CategoryPalette
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.parse.ChipKind
import com.talha.ruz.parse.ParsedCapture
import com.talha.ruz.parse.TrDateParser
import com.talha.ruz.platform.rememberSpeechInput
import com.talha.ruz.ui.dayLabel
import com.talha.ruz.ui.durationLabel
import com.talha.ruz.ui.hhmm
import com.talha.ruz.ui.icons.RuzIcons
import com.talha.ruz.ui.localNow

/**
 * Her ekranın altındaki tek satırlık yakalama kutusu.
 * Yazarken Ruz'un anladıkları (gün, saat, süre, kategori, son tarih) üstte etiket olarak görünür;
 * yanlış anlaşılan etikete dokununca kaldırılır ve o kelimeler başlıkta kalır.
 *
 * [defaultCategory]: bir kategorinin listesi açıkken #etiketsiz yazılanlar oraya gider.
 */
@Composable
fun CaptureBar(
    categories: List<RuzCategory>,
    defaultCategory: RuzCategory?,
    onSubmit: (ParsedCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var ignored by remember { mutableStateOf(emptySet<ChipKind>()) }
    val names = remember(categories) { categories.map { it.name } }
    val parsed = remember(text, ignored, names) {
        if (text.isBlank()) null else TrDateParser.parse(text, localNow(), names, ignored)
    }
    val speech = rememberSpeechInput { spoken ->
        text = if (text.isBlank()) spoken else text.trimEnd() + " " + spoken
    }
    val submit = {
        val p = parsed
        if (p != null) {
            onSubmit(p)
            text = ""
            ignored = emptySet()
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            if (parsed != null) {
                PreviewChips(
                    parsed = parsed,
                    categories = categories,
                    defaultCategory = defaultCategory,
                    ignored = ignored,
                    onRemove = { ignored = ignored + it },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        if (it.isBlank()) ignored = emptySet()
                    },
                    placeholder = {
                        Text(
                            if (defaultCategory != null) "${defaultCategory.name} listesine ekle…"
                            else "Aklına geleni yaz… (soru için sonuna ?)",
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                if (speech != null) {
                    IconButton(onClick = speech) {
                        Icon(RuzIcons.Mic, contentDescription = "Sesle yaz")
                    }
                }
                IconButton(onClick = submit, enabled = text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ekle")
                }
            }
        }
    }
}

@Composable
private fun PreviewChips(
    parsed: ParsedCapture,
    categories: List<RuzCategory>,
    defaultCategory: RuzCategory?,
    ignored: Set<ChipKind>,
    onRemove: (ChipKind) -> Unit,
) {
    val today = localNow().date
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (parsed.isQuestion) {
            AssistChip(onClick = {}, label = { Text("Soru · notlarına ve planına sorulacak") })
            return@Row
        }
        parsed.date?.let { d ->
            // Gün etiketi kaldırıldıysa ve saat yine de bir gün verdiyse, bu etiket kaldırılamaz.
            RemovableChip(dayLabel(d, today), removable = ChipKind.Date !in ignored) { onRemove(ChipKind.Date) }
        }
        parsed.time?.let { t ->
            val label = parsed.durationMin?.let { dur ->
                val endMin = t.hour * 60 + t.minute + dur
                "${hhmm(t)}–${hhmm(kotlinx.datetime.LocalTime((endMin / 60) % 24, endMin % 60))}"
            } ?: hhmm(t)
            RemovableChip(label) { onRemove(ChipKind.Time) }
        }
        if (parsed.time == null) {
            parsed.durationMin?.let { RemovableChip(durationLabel(it)) { onRemove(ChipKind.Duration) } }
        }
        if (parsed.isMilestone) RemovableChip("◆ Son tarih") { onRemove(ChipKind.Milestone) }

        val catName = parsed.categoryName
        if (catName != null) {
            val existing = categories.firstOrNull { it.name == catName }
            val color = existing?.color ?: CategoryPalette.firstOrNull { c -> categories.none { it.color == c } }
                ?: CategoryPalette[0]
            RemovableChip(
                label = if (existing == null) "$catName (yeni)" else catName,
                dotColor = color,
            ) { onRemove(ChipKind.Category) }
        } else {
            Text(
                "→ ${defaultCategory?.name ?: "Gelen kutusu"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun RemovableChip(
    label: String,
    removable: Boolean = true,
    dotColor: Long? = null,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = true,
        onClick = { if (removable) onRemove() },
        label = { Text(label) },
        leadingIcon = dotColor?.let { c ->
            @Composable { Box(Modifier.size(10.dp).background(Color(c.toInt()), CircleShape)) }
        },
        trailingIcon = if (removable) {
            @Composable { Icon(Icons.Filled.Close, contentDescription = "Kaldır", Modifier.size(InputChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}
