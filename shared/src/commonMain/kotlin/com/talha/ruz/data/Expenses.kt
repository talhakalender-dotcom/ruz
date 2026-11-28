package com.talha.ruz.data

import com.talha.ruz.ui.epochToLocal

/**
 * Bir ayın harcama kaydı. Tarih: planlıysa işin günü, değilse yazıldığı gün.
 * [pending]: planlı ama henüz bitmemiş ödeme (ör. ayın 15'indeki kira).
 */
data class ExpenseEntry(val item: RuzItem, val dateMs: Long, val pending: Boolean) {
    val amount: Long get() = item.amountKurus ?: 0L
}

/** Ay özeti: gerçekleşen, bekleyen ve kategori kategori toplamlar. */
data class MonthExpenses(
    val entries: List<ExpenseEntry>,
    val spent: Long,
    val pending: Long,
    /** Kategori kimliği (null = Gelen kutusu) → toplam (gerçekleşen + bekleyen), büyükten küçüğe. */
    val byCategory: List<Pair<String?, Long>>,
)

fun monthExpenses(items: List<RuzItem>, year: Int, month: Int): MonthExpenses {
    val entries = items.asSequence()
        .filter { (it.amountKurus ?: 0L) != 0L }
        .mapNotNull { i ->
            val ms = i.startAt ?: i.createdAt
            val d = epochToLocal(ms).date
            if (d.year == year && d.month.ordinal + 1 == month) ExpenseEntry(i, ms, pending = i.startAt != null && !i.done) else null
        }
        .sortedBy { it.dateMs }
        .toList()
    val byCategory = entries.groupBy { it.item.categoryId }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
    return MonthExpenses(
        entries = entries,
        spent = entries.filter { !it.pending }.sumOf { it.amount },
        pending = entries.filter { it.pending }.sumOf { it.amount },
        byCategory = byCategory,
    )
}
