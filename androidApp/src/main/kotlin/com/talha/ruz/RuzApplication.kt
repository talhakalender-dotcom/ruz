package com.talha.ruz

import android.app.Application
import com.talha.ruz.platform.RuzDeps
import com.talha.ruz.platform.createRuzDeps
import com.talha.ruz.widget.RuzWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class RuzApplication : Application() {
    /** Uygulama boyunca tek veritabanı, tek yapay zekâ motoru. */
    val deps: RuzDeps by lazy { createRuzDeps(this, BuildConfig.VERSION_NAME) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Veriler değişince ana ekran widget'ı yenilensin.
        val repo = deps.repository
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(repo.items, repo.habits, repo.habitChecks) { a, b, c -> Triple(a, b, c) }
                .drop(1)
                .debounce(800)
                .collect { runCatching { RuzWidget.refresh(this@RuzApplication) } }
        }
    }
}
