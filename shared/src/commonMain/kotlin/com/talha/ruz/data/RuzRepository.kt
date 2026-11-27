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

    /** Yeni öğeyi listenin en üstüne ekler ve kimliğini döndürür. */
    suspend fun addItem(
        text: String,
        categoryId: String? = null,
        startAt: Long? = null,
        durationMin: Int? = null,
        isMilestone: Boolean = false,
    ): String = withContext(io) {
        val id = newId()
        val top = db.itemQueries.minOrder().executeAsOne() - 1.0
        db.itemQueries.insert(
            id = id,
            text = text.trim(),
            categoryId = categoryId,
            startAt = startAt,
            durationMin = durationMin?.toLong(),
            isMilestone = isMilestone,
            sortOrder = top,
            now = nowMillis(),
        )
        id
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

    suspend fun addCategory(name: String, color: Long): String = withContext(io) {
        val id = newId()
        val last = db.categoryQueries.selectActive().executeAsList().maxOfOrNull { it.sort_order } ?: -1.0
        db.categoryQueries.insert(id = id, name = name.trim(), color = color, sortOrder = last + 1.0, now = nowMillis())
        id
    }

    /** Kategoriyi siler; içindeki öğeler Gelen kutusuna döner, kaybolmaz. */
    suspend fun deleteCategory(id: String) = withContext(io) {
        val now = nowMillis()
        db.transaction {
            db.itemQueries.moveAllFromCategory(toCategoryId = null, now = now, fromCategoryId = id)
            db.categoryQueries.softDelete(now = now, id = id)
        }
    }
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
    isMilestone = is_milestone,
    reminderMinBefore = reminder_min_before?.toInt(),
    order = sort_order,
    createdAt = created_at,
)

private fun Link.toDomain() = RuzLink(id = id, predecessorId = predecessor_id, successorId = successor_id)
