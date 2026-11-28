plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// İmza: GitHub Actions gizli ayarlardan anahtarı verir. Yoksa (yerel derleme) debug imzası kullanılır.
val keystorePath: String? = System.getenv("RUZ_KEYSTORE")
val keystorePassword: String? = System.getenv("RUZ_KEYSTORE_PASSWORD")
val hasReleaseKey = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.talha.ruz"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.talha.ruz"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = runNumber
        versionName = "0.1.$runNumber"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("ruz") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                storeType = "pkcs12"
                keyAlias = "ruz"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("ruz") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Ana ekran widget'ı
    implementation(libs.androidx.glance.appwidget)
}
