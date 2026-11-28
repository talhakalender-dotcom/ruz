package com.talha.ruz.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Bellekte duran veri. Tarayıcı deneme sürümü kullanır: sayfa yenilenince başa (örnek verilere) döner.
 * Davranışı [SqlRuzRepository] ile aynıdır (yumuşak silme, geri alma, sıralama).
 */
class MemoryRuzRepository(
    initialCategories: List<RuzCategory> = emptyList(),
    initialItems: List<RuzItem> = emptyList(),
    initialLinks: List<RuzLink> = emptyList(),
) : RuzRepository {
    private data class Cat(val c: RuzCategory, val deleted: Boolean = false)
    private data class It(val i: RuzItem, val deleted: Boolean = false)

    private val cats = MutableStateFlow(initialCategories.map { Cat(it) })
    private val its = MutableStateFlow(initialItems.map { It(it) })

    override val categories: Flow<List<RuzCategory>> =
        cats.map { list -> list.filter { !it.deleted }.map { it.c }.sortedBy { it.order } }

    override val items: Flow<List<RuzItem>> =
        its.map { list -> list.filter { !it.deleted }.map { it.i }.sortedWith(compareBy({ it.order }, { it.createdAt })) }

    private val linkState = MutableStateFlow(initialLinks)
    override val links: Flow<List<RuzLink>> = linkState

    override suspend fun seedIfEmpty() {
        if (cats.value.isNotEmpty()) return
        cats.value = DefaultCategories.mapIndexed { i, (name, color) ->
            Cat(RuzCategory(id = newId(), name = name, color = color, order = i.toDouble()))
        }
    }

    override suspend fun addItem(fields: ItemFields): String {
        val id = newId()
        val top = (its.value.filter { !it.deleted }.minOfOrNull { it.i.order } ?: 0.0) - 1.0
        val item = RuzItem(
            id = id,
            text = fields.text.trim(),
            note = "",
            categoryId = fields.categoryId,
            done = false,
            doneAt = null,
            startAt = fields.startAt,
            durationMin = fields.durationMin,
            allDay = fields.allDay,
            isMilestone = fields.isMilestone,
            reminderMinBefore = fields.reminderMinBefore,
            order = top,
            createdAt = nowMillis(),
        )
        its.update { it + It(item) }
        return id
    }

    private fun editItem(id: String, change: (It) -> It) {
        its.update { list -> list.map { if (it.i.id == id) change(it) else it } }
    }

    override suspend fun updateItem(id: String, fields: ItemFields) = editItem(id) {
        it.copy(
            i = it.i.copy(
                text = fields.text.trim(),
                categoryId = fields.categoryId,
                startAt = fields.startAt,
                durationMin = fields.durationMin,
                allDay = fields.allDay,
                isMilestone = fields.isMilestone,
                reminderMinBefore = fields.reminderMinBefore,
            ),
        )
    }

    override suspend fun setDone(id: String, done: Boolean) = editItem(id) {
        it.copy(i = it.i.copy(done = done, doneAt = if (done) nowMillis() else null))
    }

    override suspend fun setItemCategory(id: String, categoryId: String?) = editItem(id) {
        it.copy(i = it.i.copy(categoryId = categoryId))
    }

    override suspend fun deleteItem(id: String) = editItem(id) { it.copy(deleted = true) }

    override suspend fun restoreItem(id: String) = editItem(id) { it.copy(deleted = false) }

    override suspend fun addCategory(name: String, color: Long?): String {
        val id = newId()
        val active = cats.value.filter { !it.deleted }.map { it.c }
        val order = (active.maxOfOrNull { it.order } ?: -1.0) + 1.0
        val c = color ?: nextCategoryColor(active.map { it.color })
        cats.update { it + Cat(RuzCategory(id = id, name = name.trim(), color = c, order = order)) }
        return id
    }

    private fun editCategory(id: String, change: (Cat) -> Cat) {
        cats.update { list -> list.map { if (it.c.id == id) change(it) else it } }
    }

    override suspend fun renameCategory(id: String, name: String) = editCategory(id) {
        it.copy(c = it.c.copy(name = name.trim()))
    }

    override suspend fun setCategoryColor(id: String, color: Long) = editCategory(id) {
        it.copy(c = it.c.copy(color = color))
    }

    override suspend fun moveCategory(id: String, direction: Int) {
        val active = cats.value.filter { !it.deleted }.map { it.c }.sortedBy { it.order }
        val i = active.indexOfFirst { it.id == id }
        val j = i + direction
        if (i < 0 || j !in active.indices) return
        val reordered = active.toMutableList().apply { add(j, removeAt(i)) }
        val newOrder = reordered.mapIndexed { k, c -> c.id to k.toDouble() }.toMap()
        cats.update { list -> list.map { cat -> newOrder[cat.c.id]?.let { cat.copy(c = cat.c.copy(order = it)) } ?: cat } }
    }

    override suspend fun deleteCategory(id: String): List<String> {
        val moved = its.value.filter { !it.deleted && it.i.categoryId == id }.map { it.i.id }
        its.update { list -> list.map { if (it.i.id in moved) it.copy(i = it.i.copy(categoryId = null)) else it } }
        editCategory(id) { it.copy(deleted = true) }
        return moved
    }

    override suspend fun setSchedules(changes: Map<String, Schedule>) {
        its.update { list ->
            list.map { e ->
                val s = changes[e.i.id] ?: return@map e
                e.copy(i = e.i.copy(startAt = s.startAt, durationMin = s.durationMin, allDay = s.allDay))
            }
        }
    }

    override suspend fun addLink(predecessorId: String, successorId: String): String {
        val id = newId()
        linkState.update { list ->
            list.filterNot { it.predecessorId == predecessorId && it.successorId == successorId } +
                RuzLink(id, predecessorId, successorId)
        }
        return id
    }

    override suspend fun deleteLink(id: String) {
        linkState.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun restoreCategory(id: String, itemIds: List<String>) {
        editCategory(id) { it.copy(deleted = false) }
        its.update { list -> list.map { if (it.i.id in itemIds) it.copy(i = it.i.copy(categoryId = id)) else it } }
    }
}
