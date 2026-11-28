package com.talha.ruz.gantt

import kotlinx.datetime.TimeZone

/** Zamanlamaya giren iş: kimlik, zaman, tam gün mü. */
data class SchedTask(val id: String, val span: Span, val allDay: Boolean)

/** Bitiş-başlangıç bağı: öncül bitince ardıl başlar. */
data class SchedLink(val predecessorId: String, val successorId: String)

/**
 * Gantt zamanlama motoru (saf Kotlin, arayüzden bağımsız).
 *
 * Kural: bağlı her iş, öncüllerinin en geç bitişinde başlar ("birlikte hareket eder").
 * Öncül uzarsa ya da ileri kayarsa ardıllar ileri, kısalırsa ya da geri kayarsa geri gelir.
 * Tam gün işler bir sonraki gün başına oturur.
 */
object Scheduler {
    private const val DAY_MS = 86_400_000L

    /** pred → succ bağı döngü yaratır mı? (succ'tan pred'e zaten bir yol varsa ya da aynı işse.) */
    fun createsCycle(links: List<SchedLink>, predecessorId: String, successorId: String): Boolean {
        if (predecessorId == successorId) return true
        val next = links.groupBy({ it.predecessorId }, { it.successorId })
        val seen = HashSet<String>()
        val stack = ArrayDeque<String>().apply { add(successorId) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n == predecessorId) return true
            if (seen.add(n)) next[n]?.let { stack.addAll(it) }
        }
        return false
    }

    /**
     * Bütün bağları uygular; başlangıcı değişen işlerin yeni zamanlarını döndürür.
     * Bilinmeyen (silinmiş/planlanmamış) işlere giden bağlar yok sayılır.
     */
    fun propagate(tasks: List<SchedTask>, links: List<SchedLink>, timeZone: TimeZone): Map<String, Span> {
        val byId = tasks.associateBy { it.id }
        val valid = links.filter { it.predecessorId in byId && it.successorId in byId }
        val preds = valid.groupBy({ it.successorId }, { it.predecessorId })
        val succs = valid.groupBy({ it.predecessorId }, { it.successorId })

        // Kahn: öncülsüzlerden başlayıp sırayla ilerle (döngüdeki işler atlanır).
        val indegree = byId.keys.associateWith { preds[it]?.size ?: 0 }.toMutableMap()
        val queue = ArrayDeque(byId.keys.filter { indegree[it] == 0 })
        val order = ArrayList<String>(byId.size)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            order += n
            succs[n]?.forEach { s ->
                val d = indegree.getValue(s) - 1
                indegree[s] = d
                if (d == 0) queue.addLast(s)
            }
        }

        val spans = byId.mapValues { it.value.span }.toMutableMap()
        val changed = LinkedHashMap<String, Span>()
        for (id in order) {
            val p = preds[id] ?: continue
            var start = p.maxOf { spans.getValue(it).endMs }
            if (byId.getValue(id).allDay) {
                val dayStart = localDayStart(start, timeZone)
                if (dayStart != start) start = localDayStart(dayStart + DAY_MS + 3_600_000L, timeZone)
            }
            val current = spans.getValue(id)
            if (start != current.startMs) {
                val moved = current.copy(startMs = start)
                spans[id] = moved
                changed[id] = moved
            }
        }
        return changed
    }

    /**
     * [from] anından sonra [busy] işlerle çakışmayan ilk [durationMin] dakikalık boşluğun başlangıcı.
     * [until]'e kadar sığmazsa null.
     */
    fun firstFreeSlot(busy: List<Span>, from: Long, durationMin: Int, until: Long): Long? {
        val need = durationMin * 60_000L
        var t = from
        for (b in busy.filter { it.endMs > from }.sortedBy { it.startMs }) {
            if (b.startMs >= t + need) break
            if (b.endMs > t) t = b.endMs
        }
        return if (t + need <= until) t else null
    }
}
