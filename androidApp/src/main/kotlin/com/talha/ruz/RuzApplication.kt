package com.talha.ruz

import android.app.Application
import com.talha.ruz.platform.RuzDeps
import com.talha.ruz.platform.createRuzDeps

class RuzApplication : Application() {
    /** Uygulama boyunca tek veritabanı, tek yapay zekâ motoru. */
    val deps: RuzDeps by lazy { createRuzDeps(this, BuildConfig.VERSION_NAME) }
}
