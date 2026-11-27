package com.talha.ruz.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AiStatus(val label: String) {
    Checking("Kontrol ediliyor…"),
    Available("Hazır"),
    Downloadable("Telefonda var, indirilmesi gerekiyor"),
    Downloading("İndiriliyor…"),
    Unavailable("Bu telefonda kullanılamıyor"),
    Error("Hata"),
}

/**
 * Yapay zekâ motoru. Android'de telefonun sistemindeki Gemini Nano kullanılır.
 * Arayüz bu sözleşmeyi görür; motor değişirse (iOS, başka model) arayüz değişmez.
 */
interface AiEngine {
    val status: StateFlow<AiStatus>

    /** İndirme sırasında inen bayt sayısı. */
    val downloadedBytes: StateFlow<Long>

    /** Son hatanın açıklaması (varsa). */
    val lastError: StateFlow<String?>

    suspend fun refresh()

    suspend fun download()

    /** Tek seferlik metin üretimi. Hata olursa istisna fırlatır. */
    suspend fun generate(prompt: String, temperature: Float = 0.2f, topK: Int = 10): String
}

/** Yapay zekânın olmadığı platformlar için. */
class NoAiEngine : AiEngine {
    override val status: StateFlow<AiStatus> = MutableStateFlow(AiStatus.Unavailable).asStateFlow()
    override val downloadedBytes: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    override val lastError: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
    override suspend fun refresh() = Unit
    override suspend fun download() = Unit
    override suspend fun generate(prompt: String, temperature: Float, topK: Int): String =
        error("Yapay zekâ bu platformda yok")
}
