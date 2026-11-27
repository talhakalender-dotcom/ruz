rootProject.name = "Ruz"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS: Kotlin/Wasm eklentisinin kendiliğinden eklediği depolar yok sayılır,
    // Node.js / Yarn / Binaryen indirmeleri aşağıdaki tanımlardan yapılır.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Node.js"
                    url = uri("https://nodejs.org/dist")
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.nodejs", "node") }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Yarn"
                    url = uri("https://github.com/yarnpkg/yarn/releases/download")
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.yarnpkg", "yarn") }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Binaryen"
                    url = uri("https://github.com/WebAssembly/binaryen/releases/download")
                    patternLayout { artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

// core: arayüzden bağımsız saf mantık. shared: ortak kod (veri, arayüz). androidApp: yalnızca Android kabuğu.
// webApp: tarayıcı deneme sürümü (örnek verilerle). iOS geldiğinde iosApp ayrıca eklenecek.
include(":core", ":shared", ":androidApp", ":webApp")
