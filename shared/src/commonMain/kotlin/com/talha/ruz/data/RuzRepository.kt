package com.talha.ruz.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.talha.ruz.db.Category
import com.talha.ruz.db.Item
import com.talha.ruz.db.Link
import com.talha.ruz.db.RuzDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Bir öğenin düzenlenebilir alanları. */
data class ItemFields(
    val text: String,
    val categoryId: String?,
    val startAt: Long?,
    val durationMin: Int?,
    val allDay: Boolean,
    val isMilestone: Boolean,
)

/**
 * Verinin tek kapısı. Arayüz yalnızca bu sınıfı görür; SQL ayrıntısı burada kalır.
 * [io]: veritabanı işlerinin çalışacağı bağlam (Android'de Dispatchers.IO).
 */
class RuzRepository(
    private val db: RuzDatabase,
    private val io: CoroutineContext,
) {
    val categories: Flow<List<RuzCategory>> =
        db.categoryQueries.selectActive().asFlow().mapToList(io).map { list -> list.map { it.toDomain() } }

    val items: Flow<List<RuzItem>> =
        db.itemQueries.selectActive().asFlow().mapToList(io).map { list -> list.map { it.toDomain() } }

    val links: Flow<List<RuzLink>> =
        db.linkQueries.selectActive().asFlow().mapToList(io).map { list -> list.map { it.toDomain() } }

    /** İlk açılışta örnek kategorileri ekler. Kullanıcı hepsini silse bile tekrar eklemez. */
    suspend fun seedIfEmpty() = withContext(io) {
        if (db.categoryQueries.countAll().executeAsOne() > 0L) return@withContext
        val now = nowMillis()
        db.transaction {
            DefaultCategories.forEachIndexed { i, (name, color) ->
                db.categoryQueries.insert(id = newId(), name = name, color = color, sortOrder = i.toDouble(), now = now)
            }
        }
    }

    // ---- öğeler ----

    /** Yeni öğeyi listenin en üstüne ekler ve kimliğini döndürür. */
    suspend fun addItem(fields: ItemFields): String = withContext(io) {
        val id = newId()
        val top = db.itemQueries.minOrder().executeAsOne() - 1.0
        db.itemQueries.insert(
            id = id,
            text = fields.text.trim(),
            categoryId = fields.categoryId,
            startAt = fields.startAt,
            durationMin = fields.durationMin?.toLong(),
            isMilestone = fields.isMilestone,
            sortOrder = top,
            now = nowMillis(),
            allDay = fields.allDay,
        )
        id
    }

    suspend fun updateItem(id: String, fields: ItemFields) = withContext(io) {
        db.itemQueries.update(
            text = fields.text.trim(),
            categoryId = fields.categoryId,
            startAt = fields.startAt,
            durationMin = fields.durationMin?.toLong(),
            allDay = fields.allDay,
            isMilestone = fields.isMilestone,
            now = nowMillis(),
            id = id,
        )
    }

    suspend fun setDone(id: String, done: Boolean) = withContext(io) {
        val now = nowMillis()
        db.itemQueries.setDone(done = done, doneAt = if (done) now else null, now = now, id = id)
    }

    suspend fun setItemCategory(id: String, categoryId: String?) = withContext(io) {
        db.itemQueries.setCategory(categoryId = categoryId, now = nowMillis(), id = id)
    }

    suspend fun deleteItem(id: String) = withContext(io) {
        db.itemQueries.softDelete(now = nowMillis(), id = id)
    }

    /** Silmeyi geri alır. */
    suspend fun restoreItem(id: String) = withContext(io) {
        db.itemQueries.restore(now = nowMillis(), id = id)
    }

    // ---- kategoriler ----

    suspend fun addCategory(name: String, color: Long? = null): String = withContext(io) {
        val id = newId()
        val active = db.categoryQueries.selectActive().executeAsList()
        val last = active.maxOfOrNull { it.sort_order } ?: -1.0
        val c = color ?: nextColor(active.map { it.color })
        db.categoryQueries.insert(id = id, name = name.trim(), color = c, sortOrder = last + 1.0, now = nowMillis())
        id
    }

    suspend fun renameCategory(id: String, name: String) = withContext(io) {
        db.categoryQueries.rename(name = name.trim(), now = nowMillis(), id = id)
    }

    suspend fun setCategoryColor(id: String, color: Long) = withContext(io) {
        db.categoryQueries.setColor(color = color, now = nowMillis(), id = id)
    }

    /** Kategoriyi listede bir yukarı (-1) ya da aşağı (+1) taşır. */
    suspend fun moveCategory(id: String, direction: Int) = withContext(io) {
        val list = db.categoryQueries.selectActive().executeAsList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + direction
        if (i < 0 || j !in list.indices) return@withContext
        val now = nowMillis()
        db.transaction {
            // Sıra değerlerini yeniden numaralayıp ikisini değiştir; eşit değerler sorun çıkarmasın.
            val reordered = list.toMutableList().apply { add(j, removeAt(i)) }
            reordered.forEachIndexed { k, c -> db.categoryQueries.setOrder(sortOrder = k.toDouble(), now = now, id = c.id) }
        }
    }

    /**
     * Kategoriyi siler; içindeki öğeler Gelen kutusuna döner, kaybolmaz.
     * Geri alma için taşınan öğelerin kimliklerini döndürür.
     */
    suspend fun deleteCategory(id: String): List<String> = withContext(io) {
        val now = nowMillis()
        db.transactionWithResult {
            val moved = db.itemQueries.activeIdsInCategory(categoryId = id).executeAsList()
            if (moved.isNotEmpty()) db.itemQueries.setCategoryForIds(categoryId = null, now = now, ids = moved)
            db.categoryQueries.softDelete(now = now, id = id)
            moved
        }
    }

    /** Kategori silmeyi geri alır; öğeleri eski kategorisine döndürür. */
    suspend fun restoreCategory(id: String, itemIds: List<String>) = withContext(io) {
        val now = nowMillis()
        db.transaction {
            db.categoryQueries.restore(now = now, id = id)
            if (itemIds.isNotEmpty()) db.itemQueries.setCategoryForIds(categoryId = id, now = now, ids = itemIds)
        }
    }

    private fun nextColor(used: List<Long>): Long =
        CategoryPalette.firstOrNull { it !in used } ?: CategoryPalette[used.size % CategoryPalette.size]
}

private fun Category.toDomain() = RuzCategory(id = id, name = name, color = color, order = sort_order)

private fun Item.toDomain() = RuzItem(
    id = id,
    text = text,
    note = note,
    categoryId = category_id,
    done = done,
    doneAt = done_at,
    startAt = start_at,
    durationMin = duration_min?.toInt(),
    allDay = all_day,
    isMilestone = is_milestone,
    reminderMinBefore = reminder_min_before?.toInt(),
    order = sort_order,
    createdAt = created_at,
)

private fun Link.toDomain() = RuzLink(id = id, predecessorId = predecessor_id, successorId = successor_id)
