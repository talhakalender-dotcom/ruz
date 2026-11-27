package com.talha.ruz.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.talha.ruz.platform.PlatformBackHandler
import com.talha.ruz.platform.RuzDeps
import com.talha.ruz.ui.capture.CaptureBar
import com.talha.ruz.ui.categories.CategoriesPane
import com.talha.ruz.ui.inbox.InboxPane
import com.talha.ruz.ui.nano.NanoTestScreen
import com.talha.ruz.ui.plan.PlanPane
import com.talha.ruz.ui.settings.SettingsScreen
import com.talha.ruz.ui.theme.RuzTheme
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Plan("Plan", Icons.Filled.DateRange),
    Inbox("Gelen kutusu", Icons.AutoMirrored.Filled.List),
    Categories("Kategoriler", Icons.Filled.Menu),
}

private enum class Overlay { None, Settings, NanoTest }

/** Fold'un iç ekranı (ve tabletler) bu genişlikten itibaren iki panelli düzen alır. */
private val WideLayoutMinWidth = 600.dp

@Composable
fun RuzApp(deps: RuzDeps) {
    RuzTheme {
        LaunchedEffect(Unit) {
            deps.repository.seedIfEmpty()
            deps.ai.refresh()
        }

        var overlay by rememberSaveable { mutableStateOf(Overlay.None) }

        when (overlay) {
            Overlay.Settings -> {
                PlatformBackHandler(enabled = true) { overlay = Overlay.None }
                SettingsScreen(
                    deps = deps,
                    onBack = { overlay = Overlay.None },
                    onOpenNanoTest = { overlay = Overlay.NanoTest },
                )
            }
            Overlay.NanoTest -> {
                PlatformBackHandler(enabled = true) { overlay = Overlay.Settings }
                NanoTestScreen(deps = deps, onBack = { overlay = Overlay.Settings })
            }
            Overlay.None -> MainScreen(deps = deps, onOpenSettings = { overlay = Overlay.Settings })
        }
    }
}

@Composable
private fun MainScreen(deps: RuzDeps, onOpenSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Plan) }
    var leftTab by rememberSaveable { mutableStateOf(Tab.Inbox) }
    val categories by deps.repository.categories.collectAsState(initial = emptyList())
    val items by deps.repository.items.collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayoutMinWidth

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ruz") },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column(Modifier.imePadding()) {
                    CaptureBar(
                        onSubmit = { text ->
                            if (text.trimEnd().endsWith("?")) {
                                // 5. aşamada bu soru Nano'ya gidecek.
                                scope.launch { snackbar.showSnackbar("Sorular 5. aşamada yapay zekâya gidecek.") }
                            } else {
                                scope.launch { deps.repository.addItem(text) }
                            }
                        },
                    )
                    if (!wide) {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            val onToggleDone: (String, Boolean) -> Unit = { id, done -> scope.launch { deps.repository.setDone(id, done) } }
            val inboxItems = items.filter { it.inInbox && !it.done }

            if (wide) {
                // Fold açık: solda Gelen kutusu / Kategoriler, sağda Gantt.
                Row(Modifier.padding(padding).fillMaxSize()) {
                    Column(Modifier.width(340.dp).fillMaxHeight()) {
                        LeftPaneSwitcher(
                            showInbox = leftTab == Tab.Inbox,
                            onSelect = { leftTab = if (it) Tab.Inbox else Tab.Categories },
                        )
                        if (leftTab == Tab.Inbox) {
                            InboxPane(inboxItems, onToggleDone, Modifier.fillMaxSize())
                        } else {
                            CategoriesPane(categories, items, Modifier.fillMaxSize())
                        }
                    }
                    VerticalDivider()
                    PlanPane(items, Modifier.fillMaxSize())
                }
            } else {
                val m = Modifier.padding(padding).fillMaxSize()
                when (tab) {
                    Tab.Plan -> PlanPane(items, m)
                    Tab.Inbox -> InboxPane(inboxItems, onToggleDone, m)
                    Tab.Categories -> CategoriesPane(categories, items, m)
                }
            }
        }
    }
}
