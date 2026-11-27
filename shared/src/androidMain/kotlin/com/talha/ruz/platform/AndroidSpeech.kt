package com.talha.ruz.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Android'in konuşma tanıma penceresi (Türkçe). Mikrofon izni istemez; tanımayı sistem yapar.
 * Söylenen metin kutuya düşer, kullanıcı önizlemeyi görüp kendisi gönderir.
 */
@Composable
actual fun rememberSpeechInput(onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) callback(text)
        }
    }
    return remember(launcher, context) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Aklına geleni söyle")
            }
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Bu telefonda konuşma tanıma bulunamadı.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
