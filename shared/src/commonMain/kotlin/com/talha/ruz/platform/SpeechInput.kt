package com.talha.ruz.platform

import androidx.compose.runtime.Composable

/**
 * Sesle yazma. Dönen fonksiyon çağrılınca dinlemeye başlar, metni [onText]'e verir.
 * Platform desteklemiyorsa null döner (mikrofon düğmesi gizlenir).
 */
@Composable
expect fun rememberSpeechInput(onText: (String) -> Unit): (() -> Unit)?
