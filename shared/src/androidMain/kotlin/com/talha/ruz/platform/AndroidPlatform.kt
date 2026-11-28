package com.talha.ruz.platform

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.talha.ruz.ai.NanoAiEngine
import com.talha.ruz.data.SqlRuzRepository
import com.talha.ruz.db.RuzDatabase
import com.talha.ruz.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Süreç boyunca tek bağımlılık seti. Uygulama, bildirim düğmeleri ve açılış alıcısı aynı veritabanını kullanır.
 */
object RuzAndroid {
    @Volatile
    private var instance: RuzDeps? = null

    fun deps(context: Context, appVersion: String = ""): RuzDeps =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext, appVersion).also { instance = it }
        }

    private fun build(app: Context, appVersion: String): RuzDeps {
        val driver = AndroidSqliteDriver(RuzDatabase.Schema, app, "ruz.db")
        val repository = SqlRuzRepository(RuzDatabase(driver), Dispatchers.IO)
        Reminders.start(app, repository, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        return RuzDeps(
            repository = repository,
            ai = NanoAiEngine(),
            platform = AndroidPlatformActions(app),
            appVersion = appVersion,
        )
    }
}

fun createRuzDeps(context: Context, appVersion: String): RuzDeps = RuzAndroid.deps(context, appVersion)

private class AndroidPlatformActions(private val context: Context) : PlatformActions {
    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    override val remindersSupported: Boolean get() = true
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
