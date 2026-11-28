package com.talha.ruz.ui

/**
 * Uygulamanın dışından (widget) gelen istek. [nonce] her istekte değişir; aynı istek tekrar gelse de çalışsın diye.
 */
data class LaunchAction(val kind: String, val nonce: Long) {
    companion object {
        /** Yakalama kutusunu aç (klavye). */
        const val CAPTURE = "capture"

        /** Sesle yazmayı başlat. */
        const val VOICE = "voice"
    }
}
