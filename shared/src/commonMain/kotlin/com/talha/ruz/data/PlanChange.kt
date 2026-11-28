package com.talha.ruz.data

import com.talha.ruz.gantt.SchedLink
import com.talha.ruz.gantt.SchedTask
import com.talha.ruz.gantt.Scheduler
import com.talha.ruz.gantt.Span
import kotlinx.datetime.TimeZone

/**
 * Planda yapılacak bir değişiklik: yazılacak zamanlar, eklenecek/kaldırılacak bağlar
 * ve "Geri al" için eski zamanlar.
 */
class PlanChange(
    val schedules: Map<String, Schedule>,
    val previous: Map<String, Schedule>,
    val removeLinks: List<RuzLink>,
    val addLink: Pair<String, String>?,
)

fun RuzItem.schedule(): Schedule = Schedule(startAt, durationMin, allDay)

fun RuzItem.spanOrNull(): Span? = startAt?.let { Span(it, durationMin ?: if (allDay) 1440 else 60) }

/**
 * Kullanıcının elle verdiği zamanları ([overrides]) ve bağ değişikliklerini uygular,
 * zamanlama motoruyla ardılları yerleştirir ve hepsini tek değişiklik olarak döndürür.
 */
fun planChange(
    items: List<RuzItem>,
    links: List<RuzLink>,
    overrides: Map<String, Schedule>,
    timeZone: TimeZone,
    removeLinks: List<RuzLink> = emptyList(),
    addLink: Pair<String, String>? = null,
): PlanChange {
    val byId = items.associateBy { it.id }
    val tasks = items.mapNotNull { item ->
        val s = overrides[item.id] ?: item.schedule()
        s.startAt?.let { start -> SchedTask(item.id, Span(start, s.durationMin ?: if (s.allDay) 1440 else 60), s.allDay) }
    }
    val removedIds = removeLinks.map { it.id }.toSet()
    val schedLinks = links.filter { it.id !in removedIds }.map { SchedLink(it.predecessorId, it.successorId) } +
        listOfNotNull(addLink?.let { SchedLink(it.first, it.second) })
    val moved = Scheduler.propagate(tasks, schedLinks, timeZone)

    val result = LinkedHashMap(overrides)
    moved.forEach { (id, span) ->
        val base = result[id] ?: byId.getValue(id).schedule()
        result[id] = base.copy(startAt = span.startMs, durationMin = span.durationMin)
    }
    val previous = result.keys.filter { it in byId }.associateWith { byId.getValue(it).schedule() }
    return PlanChange(result, previous, removeLinks, addLink)
}
