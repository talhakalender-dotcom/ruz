package com.talha.ruz.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem

/** Kategoriler ve içlerindeki açık öğe sayısı. Ekleme/silme 2. aşamada. */
@Composable
fun CategoriesPane(
    categories: List<RuzCategory>,
    items: List<RuzItem>,
    modifier: Modifier = Modifier,
) {
    val openCounts = items.filter { !it.done && it.categoryId != null }.groupingBy { it.categoryId }.eachCount()
    LazyColumn(modifier) {
        items(categories, key = { it.id }) { cat ->
            ListItem(
                headlineContent = { Text(cat.name) },
                supportingContent = { Text("${openCounts[cat.id] ?: 0} açık öğe") },
                leadingContent = {
                    Box(Modifier.size(14.dp).background(Color(cat.color.toInt()), CircleShape))
                },
            )
            HorizontalDivider()
        }
    }
}
