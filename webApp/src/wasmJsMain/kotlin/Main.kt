import androidx.compose.ui.window.ComposeViewport
import com.talha.ruz.platform.createWebDeps
import com.talha.ruz.ui.RuzApp

fun main() {
    val deps = createWebDeps(appVersion = "web")
    ComposeViewport {
        RuzApp(deps)
    }
}
