rootProject.name = "Ruz"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// shared: ortak kod (veri, mantık, arayüz). androidApp: yalnızca Android kabuğu.
// iOS geldiğinde iosApp ayrıca eklenecek.
include(":shared", ":androidApp")
