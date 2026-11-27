package com.talha.ruz.platform

import androidx.compose.runtime.Composable
import com.talha.ruz.ai.NoAiEngine
import com.talha.ruz.data.sampleRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Tarayıcı deneme sürümü: örnek verilerle bellekte çalışır, Nano ve mikrofon yok. */
fun createWebDeps(appVersion: String): RuzDeps {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    return RuzDeps(
        repository = sampleRepository(today, tz),
        ai = NoAiEngine(),
        platform = object : PlatformActions {
            override fun share(text: String) = Unit
        },
        appVersion = appVersion,
        isPreview = true,
    )
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun rememberSpeechInput(onText: (String) -> Unit): (() -> Unit)? = null
