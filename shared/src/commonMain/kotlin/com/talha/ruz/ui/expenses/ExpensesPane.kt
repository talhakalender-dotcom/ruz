package com.talha.ruz.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem
import com.talha.ruz.data.monthExpenses
import com.talha.ruz.money.Money
import com.talha.ruz.ui.dayLabel
import com.talha.ruz.ui.epochToLocal
import kotlinx.datetime.LocalDate

private val MonthNames = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")

/**
 * Harcamalar: seçilen ayın gerçekleşen ve bekleyen tutarları, kategori dağılımı ve kayıtlar.
 * Tutar, yazarken "350 TL" gibi eklenir ya da düzenleme panelinden girilir.
 */
@Composable
fun ExpensesPane(
    items: List<RuzItem>,
    categories: List<RuzCategory>,
    today: LocalDate,
    onOpen: (RuzItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ay, bugünden kaç ay uzakta (0 = bu ay).
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val monthIndex = today.year * 12 + today.month.ordinal + offset
    val year = monthIndex / 12
    val month = monthIndex % 12 + 1
    val summary = monthExpenses(items, year, month)
    val catById = categories.associateBy { it.id }
    val total = (summary.spent + summary.pending).coerceAtLeast(1)

    LazyColumn(modifier) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki ay") }
                Text(
                    "${MonthNames[month - 1]} $year",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).clickable { offset = 0 },
                )
                IconButton(onClick = { offset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki ay") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("Gerçekleşen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(summary.spent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("Bekleyen ödemeler", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(summary.pending), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        if (summary.entries.isEmpty()) {
            item {
                Text(
                    "Bu ay tutarlı kayıt yok. Yazarken \"market 350 TL #alışveriş\" ya da \"kira 15.000 TL her ayın 15'i\" " +
                        "gibi tutar ekleyebilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@LazyColumn
        }
        item {
            Text(
                "Kategoriler",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        items(summary.byCategory, key = { "c-" + (it.first ?: "inbox") }) { (catId, amount) ->
            val cat = catId?.let { catById[it] }
            val color = cat?.color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.outline
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color, CircleShape))
                    Text(cat?.name ?: "Gelen kutusu", modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(Money.format(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(amount.toFloat() / total).height(6.dp).background(color))
                }
            }
        }
        item {
            Text(
                "Kayıtlar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        items(summary.entries, key = { "e-" + it.item.id }) { e ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(e.item) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.item.text, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        dayLabel(epochToLocal(e.dateMs).date, today) + if (e.pending) " · bekliyor" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (e.pending) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(e.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider()
        }
    }
}
