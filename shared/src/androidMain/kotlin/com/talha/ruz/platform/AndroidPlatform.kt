package com.talha.ruz.platform

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.talha.ruz.ai.NanoAiEngine
import com.talha.ruz.data.RuzRepository
import com.talha.ruz.db.RuzDatabase
import kotlinx.coroutines.Dispatchers

fun createRuzDeps(context: Context, appVersion: String): RuzDeps {
    val app = context.applicationContext
    val driver = AndroidSqliteDriver(RuzDatabase.Schema, app, "ruz.db")
    return RuzDeps(
        repository = RuzRepository(RuzDatabase(driver), Dispatchers.IO),
        ai = NanoAiEngine(),
        platform = AndroidPlatformActions(app),
        appVersion = appVersion,
    )
}

private class AndroidPlatformActions(private val context: Context) : PlatformActions {
    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
