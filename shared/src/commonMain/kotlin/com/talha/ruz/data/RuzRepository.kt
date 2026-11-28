package com.talha.ruz.data

import com.talha.ruz.recur.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Bir öğenin düzenlenebilir alanları. */
data class ItemFields(
    val text: String,
    val categoryId: String?,
    val startAt: Long?,
    val durationMin: Int?,
    val allDay: Boolean,
    val isMilestone: Boolean,
    /** Hatırlatma: null = varsayılan (15 dk önce / tam günde 09:00), -1 = kapalı, diğerleri dakika. */
    val reminderMinBefore: Int? = null,
)

/** Bir işin Gantt'taki yeri. [startAt] null ise plandan çıkar. */
data class Schedule(val startAt: Long?, val durationMin: Int?, val allDay: Boolean)

/**
 * Verinin tek kapısı. Arayüz yalnızca bunu görür.
 * Telefonda [SqlRuzRepository] (kalıcı), tarayıcı deneme sürümünde [MemoryRuzRepository] (örnek veriler) kullanılır.
 */
interface RuzRepository {
    val categories: Flow<List<RuzCategory>>
    val items: Flow<List<RuzItem>>
    val links: Flow<List<RuzLink>>
    val series: Flow<List<RuzSeries>>

    /** İlk açılışta örnek kategorileri ekler. Kullanıcı hepsini silse bile tekrar eklemez. */
    suspend fun seedIfEmpty()

    /** Yeni öğeyi listenin en üstüne ekler ve kimliğini döndürür. */
    suspend fun addItem(fields: ItemFields): String
    suspend fun updateItem(id: String, fields: ItemFields)
    suspend fun setDone(id: String, done: Boolean)
    suspend fun setItemCategory(id: String, categoryId: String?)
    suspend fun deleteItem(id: String)

    /** Silmeyi geri alır. */
    suspend fun restoreItem(id: String)

    /** [color] verilmezse kullanılmayan ilk palet rengi seçilir. */
    suspend fun addCategory(name: String, color: Long? = null): String
    suspend fun renameCategory(id: String, name: String)
    suspend fun setCategoryColor(id: String, color: Long)

    /** Kategoriyi listede bir yukarı (-1) ya da aşağı (+1) taşır. */
    suspend fun moveCategory(id: String, direction: Int)

    /**
     * Kategoriyi siler; içindeki öğeler Gelen kutusuna döner, kaybolmaz.
     * Geri alma için taşınan öğelerin kimliklerini döndürür.
     */
    suspend fun deleteCategory(id: String): List<String>

    /** Kategori silmeyi geri alır; öğeleri eski kategorisine döndürür. */
    suspend fun restoreCategory(id: String, itemIds: List<String>)

    /** Birden çok işin zamanını tek seferde değiştirir (zamanlama motorunun sonucu). */
    suspend fun setSchedules(changes: Map<String, Schedule>)

    /** Bitiş-başlangıç bağı ekler, kimliğini döndürür. */
    suspend fun addLink(predecessorId: String, successorId: String): String
    suspend fun deleteLink(id: String)

    /**
     * Tekrar eden iş ekler: şablonu kaydeder ve ilk tekrarları üretir. [firstStart] ilk tekrarın başlangıcı.
     * Serinin kimliğini döndürür.
     */
    suspend fun addSeries(template: ItemFields, rule: Recurrence, firstStart: Long, timeZone: TimeZone): String

    /** Bütün serilerin tekrarlarını ufka kadar tamamlar (açılışta çağrılır). */
    suspend fun extendSeries(today: LocalDate, timeZone: TimeZone)

    /** Seriyi durdurur: bitmemiş, [fromMs] ve sonrasındaki tekrarlar silinir; geçmiştekiler kalır. */
    suspend fun stopSeries(seriesId: String, fromMs: Long)
}

/** Kullanılmayan ilk palet rengi; hepsi kullanıldıysa sırayla döner. */
internal fun nextCategoryColor(used: List<Long>): Long =
    CategoryPalette.firstOrNull { it !in used } ?: CategoryPalette[used.size % CategoryPalette.size]
