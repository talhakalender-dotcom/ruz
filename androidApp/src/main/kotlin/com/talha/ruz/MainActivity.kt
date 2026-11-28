package com.talha.ruz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.talha.ruz.ui.LaunchAction
import com.talha.ruz.ui.RuzApp

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Widget'tan gelen istek (hızlı ekleme / sesle ekleme). */
    private val launchAction = mutableStateOf<LaunchAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Hatırlatıcılar için bildirim izni (Android 13+); bir kez sorulur.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handle(intent)
        val deps = (application as RuzApplication).deps
        setContent { RuzApp(deps, launchAction.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val kind = intent?.getStringExtra(EXTRA_ACTION) ?: return
        launchAction.value = LaunchAction(kind, System.nanoTime())
        intent.removeExtra(EXTRA_ACTION)
    }

    companion object {
        const val EXTRA_ACTION = "ruz.action"
        const val ACTION_CAPTURE = LaunchAction.CAPTURE
        const val ACTION_VOICE = LaunchAction.VOICE
    }
}
