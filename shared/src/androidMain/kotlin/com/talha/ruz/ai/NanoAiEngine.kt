package com.talha.ruz.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Telefonun sistemindeki Gemini Nano (ML Kit GenAI Prompt API, AICore).
 * Model uygulamaya gömülmez; gerekirse sistem kendisi indirir.
 * Bağlam penceresi küçüktür (~4000 token), istemler kısa tutulmalı.
 */
class NanoAiEngine : AiEngine {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    private val _status = MutableStateFlow(AiStatus.Checking)
    override val status: StateFlow<AiStatus> = _status.asStateFlow()

    private val _downloaded = MutableStateFlow(0L)
    override val downloadedBytes: StateFlow<Long> = _downloaded.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override suspend fun refresh() {
        _status.value = AiStatus.Checking
        _status.value = try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> AiStatus.Available
                FeatureStatus.DOWNLOADABLE -> AiStatus.Downloadable
                FeatureStatus.DOWNLOADING -> AiStatus.Downloading
                else -> AiStatus.Unavailable
            }
        } catch (e: Exception) {
            _lastError.value = e.describe()
            AiStatus.Error
        }
    }

    override suspend fun download() {
        try {
            model.download().collect { s ->
                when (s) {
                    is DownloadStatus.DownloadStarted -> _status.value = AiStatus.Downloading
                    is DownloadStatus.DownloadProgress -> _downloaded.value = s.totalBytesDownloaded
                    is DownloadStatus.DownloadCompleted -> _status.value = AiStatus.Available
                    is DownloadStatus.DownloadFailed -> {
                        _lastError.value = s.e.describe()
                        _status.value = AiStatus.Error
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            _lastError.value = e.describe()
            _status.value = AiStatus.Error
        }
    }

    override suspend fun generate(prompt: String, temperature: Float, topK: Int): String {
        val response = model.generateContent(
            generateContentRequest(TextPart(prompt)) {
                this.temperature = temperature
                this.topK = topK
            },
        )
        return response.candidates.firstOrNull()?.text.orEmpty().trim()
    }
}

private fun Throwable.describe(): String = "${this::class.simpleName}: ${message ?: "(açıklama yok)"}"
