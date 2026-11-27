plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Tarayıcı deneme sürümü. Derlenmiş hâli her main derlemesinde GitHub Pages'e (ruz-web) konur.
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.time.ExperimentalTime")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
            }
        }
        wasmJsMain.dependencies {
            implementation(project(":shared"))
        }
    }
}
