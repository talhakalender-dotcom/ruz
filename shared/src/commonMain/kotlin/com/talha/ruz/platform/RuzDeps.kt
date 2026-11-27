package com.talha.ruz.platform

import androidx.compose.runtime.Composable
import com.talha.ruz.ai.AiEngine
import com.talha.ruz.data.RuzRepository

/** Telefona özel küçük işler. */
interface PlatformActions {
    /** Metni paylaşma penceresiyle gönderir (WhatsApp, e-posta, not...). */
    fun share(text: String)
}

/** Uygulamanın bağımlılıkları; platform tarafı kurar, arayüz kullanır. */
class RuzDeps(
    val repository: RuzRepository,
    val ai: AiEngine,
    val platform: PlatformActions,
    val appVersion: String,
)

/** Geri tuşu. Android'de sistem geri hareketine bağlanır. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
