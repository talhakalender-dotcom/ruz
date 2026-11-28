package com.talha.ruz.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import com.talha.ruz.data.CategoryPalette
import com.talha.ruz.data.ItemFields
import com.talha.ruz.data.PlanChange
import com.talha.ruz.data.Schedule
import com.talha.ruz.data.planChange
import com.talha.ruz.data.spanOrNull
import com.talha.ruz.gantt.DragHandle
import com.talha.ruz.gantt.SchedLink
import com.talha.ruz.gantt.Scheduler
import com.talha.ruz.gantt.localDayStart
import com.talha.ruz.ui.gantt.GanttDrop
import com.talha.ruz.ui.gantt.GanttDropTarget
import com.talha.ruz.ui.inbox.ItemDragCallbacks
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.time.Clock
import com.talha.ruz.data.RuzCategory
import com.talha.ruz.data.RuzItem
import com.talha.ruz.parse.ParsedCapture
import com.talha.ruz.parse.TrDateParser
import com.talha.ruz.platform.PlatformBackHandler
import com.talha.ruz.platform.RuzDeps
import com.talha.ruz.ui.capture.CaptureBar
import com.talha.ruz.ui.categories.CategoriesPane
import com.talha.ruz.ui.categories.CategoryDetailPane
import com.talha.ruz.ui.categories.CategoryDialog
import com.talha.ruz.ui.inbox.InboxPane
import com.talha.ruz.ui.item.ItemEditSheet
import com.talha.ruz.ui.nano.NanoTestScreen
import com.talha.ruz.ui.plan.PlanPane
import com.talha.ruz.ui.settings.SettingsScreen
import com.talha.ruz.ui.theme.RuzTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

private enum class Tab(val label: String, val icon: ImageVector) {
    Plan("Plan", Icons.Filled.DateRange),
    Inbox("Gelen kutusu", Icons.AutoMirrored.Filled.List),
    Categories("Kategoriler", Icons.Filled.Menu),
}

private enum class Overlay { None, Settings, NanoTest }

/** Fold'un iç ekranı (ve tabletler) bu genişlikten itibaren iki panelli düzen alır. */
private val WideLayoutMinWidth = 600.dp

/** Kategori penceresi: yeni kategori ya da düzenlenen kategorinin kimliği. */
private const val NewCategory = "__new__"

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
    val repo = deps.repository
    var tab by rememberSaveable { mutableStateOf(Tab.Plan) }
    var leftTab by rememberSaveable { mutableStateOf(Tab.Inbox) }
    var openCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryDialog by rememberSaveable { mutableStateOf<String?>(null) }

    val categories by repo.categories.collectAsState(initial = emptyList())
    val items by repo.items.collectAsState(initial = emptyList())
    val links by repo.links.collectAsState(initial = emptyList())
    // Gelen kutusundan Gantt'a sürüklenen öğe ve parmağın pencere konumu.
    var dragItem by remember { mutableStateOf<RuzItem?>(null) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    val dropTarget = remember { GanttDropTarget() }
    val tz = remember { TimeZone.currentSystemDefault() }
    val now = rememberNow()
    val today = now.date
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val openCategory = categories.firstOrNull { it.id == openCategoryId }
    val editingItem = items.firstOrNull { it.id == editingItemId }

    PlatformBackHandler(enabled = openCategory != null) { openCategoryId = null }

    /** Kısa bildirim; "Geri al"a basılırsa [undo] çalışır. */
    fun notify(message: String, undo: (suspend () -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = if (undo != null) "Geri al" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    fun toggleDone(id: String, done: Boolean) {
        scope.launch { repo.setDone(id, done) }
        if (done) notify("Tamamlandı") { repo.setDone(id, false) }
    }

    fun deleteItem(item: RuzItem) {
        editingItemId = null
        scope.launch { repo.deleteItem(item.id) }
        notify("Silindi: ${item.text}") { repo.restoreItem(item.id) }
    }

    fun deleteCategory(cat: RuzCategory) {
        if (openCategoryId == cat.id) openCategoryId = null
        scope.launch {
            val moved = repo.deleteCategory(cat.id)
            val extra = if (moved.isNotEmpty()) " · ${moved.size} öğe Gelen kutusuna taşındı" else ""
            notify("${cat.name} silindi$extra") { repo.restoreCategory(cat.id, moved) }
        }
    }

    /** Gantt'ta taşınan/uzatılan iş; "Geri al" eski zamana döndürür. */
    /** Plan değişikliğini yazar; "Geri al" zamanları ve bağları eski hâline döndürür. */
    fun commit(change: PlanChange, message: String) {
        scope.launch {
            change.removeLinks.forEach { repo.deleteLink(it.id) }
            val newLinkId = change.addLink?.let { (a, b) -> repo.addLink(a, b) }
            repo.setSchedules(change.schedules)
            notify(message) {
                newLinkId?.let { repo.deleteLink(it) }
                change.removeLinks.forEach { repo.addLink(it.predecessorId, it.successorId) }
                repo.setSchedules(change.previous)
            }
        }
    }

    fun movedSuffix(change: PlanChange, mainId: String): String {
        val others = change.schedules.keys.count { it != mainId }
        return if (others > 0) " · $others bağlı iş kaydı" else ""
    }

    /** Gantt'ta taşınan/uzatılan iş. Bağlı bir iş elle taşınırsa önceki işten ayrılır. */
    fun reschedule(item: RuzItem, startAt: Long, durationMin: Int, handle: DragHandle) {
        val incoming = links.filter { it.successorId == item.id }
        val detach = handle != DragHandle.End && incoming.isNotEmpty()
        val change = planChange(
            items, links, mapOf(item.id to Schedule(startAt, durationMin, item.allDay)), tz,
            removeLinks = if (detach) incoming else emptyList(),
        )
        val label = whenLabel(item.copy(startAt = startAt, durationMin = durationMin), today) ?: ""
        commit(change, "${item.text} · $label" + (if (detach) " · bağı kaldırıldı" else "") + movedSuffix(change, item.id))
    }

    /** [item]'ı [after]'ın arkasına bağlar (o bitince başlar). Eski öncül bağları kalkar. */
    fun link(item: RuzItem, after: RuzItem, overrides: Map<String, Schedule> = emptyMap()) {
        val incoming = links.filter { it.successorId == item.id }
        val rest = links.filter { it.successorId != item.id }.map { SchedLink(it.predecessorId, it.successorId) }
        if (Scheduler.createsCycle(rest, after.id, item.id)) {
            notify("Bağlanamaz: \"${after.text}\" zaten \"${item.text}\" işine bağlı.")
            return
        }
        val change = planChange(items, links, overrides, tz, removeLinks = incoming, addLink = after.id to item.id)
        commit(change, "${item.text} → ${after.text} bitince başlar" + movedSuffix(change, item.id))
    }

    /** Gelen kutusundaki öğeyi bugünün ilk boş saatine (yoksa yarın sabaha) koyar. */
    fun addToPlan(item: RuzItem) {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val quarter = 15 * 60_000L
        val from = (nowMs / quarter + 1) * quarter
        val todayStart = localDayStart(nowMs, tz)
        val busy = items.filter { !it.done && !it.allDay && it.id != item.id }.mapNotNull { it.spanOrNull() }
        val day = 86_400_000L
        val slot = Scheduler.firstFreeSlot(busy, from, 60, todayStart + 22 * 3_600_000L)
            ?: Scheduler.firstFreeSlot(busy, todayStart + day + 9 * 3_600_000L, 60, todayStart + day + 22 * 3_600_000L)
            ?: (todayStart + day + 9 * 3_600_000L)
        val change = planChange(items, links, mapOf(item.id to Schedule(slot, 60, false)), tz)
        val label = whenLabel(item.copy(startAt = slot, durationMin = 60, allDay = false), today) ?: ""
        commit(change, "Plana eklendi · $label")
    }

    /** Gelen kutusundan Gantt'a bırakılan öğe: çubuğun üstüne bırakıldıysa onun arkasına bağlanır. */
    fun dropFromInbox(item: RuzItem, drop: GanttDrop) {
        val schedule = Schedule(drop.startMs, if (drop.allDay) 1440 else 60, drop.allDay)
        val onto = drop.onto
        if (onto != null) {
            link(item, onto, overrides = mapOf(item.id to schedule))
        } else {
            val change = planChange(items, links, mapOf(item.id to schedule), tz)
            val label = whenLabel(item.copy(startAt = schedule.startAt, durationMin = schedule.durationMin, allDay = schedule.allDay), today) ?: ""
            commit(change, "Plana eklendi · $label")
        }
    }

    val inboxDrag = ItemDragCallbacks(
        onStart = { item, pos ->
            dragItem = item
            dragPos = pos
        },
        onMove = { pos -> dragPos = pos },
        onEnd = { item, pos ->
            val drop = dropTarget.resolve(pos)
            dragItem = null
            dragPos = null
            if (drop != null) dropFromInbox(item, drop)
            drop != null
        },
        onCancel = {
            dragItem = null
            dragPos = null
        },
    )

    fun submit(p: ParsedCapture, defaultCategory: RuzCategory?) {
        if (p.isQuestion) {
            // 5. aşamada bu soru Nano'ya gidecek.
            notify("Sorular 5. aşamada yapay zekâya gidecek.")
            return
        }
        scope.launch {
            val tagName = p.categoryName
            val categoryId = when (tagName) {
                null -> defaultCategory?.id
                else -> categories.firstOrNull { it.name == tagName }?.id ?: repo.addCategory(tagName)
            }
            val categoryName = p.categoryName ?: defaultCategory?.name
            val duration = p.durationMin ?: when {
                p.time != null -> 60
                p.date != null -> 1440
                else -> null
            }
            val id = repo.addItem(
                ItemFields(
                    text = p.title,
                    categoryId = categoryId,
                    startAt = TrDateParser.startEpochMs(p, TimeZone.currentSystemDefault()),
                    durationMin = duration,
                    allDay = p.allDay,
                    isMilestone = p.isMilestone,
                ),
            )
            val whenText = p.date?.let { d ->
                dayLabel(d, today) + (p.time?.let { " " + hhmm(it) } ?: "")
            }
            val summary = listOfNotNull(whenText, categoryName ?: "Gelen kutusu").joinToString(" · ")
            notify("Eklendi · $summary") { repo.deleteItem(id) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayoutMinWidth
        // Bir kategorinin listesi görünürken yazılanlar o kategoriye gider.
        val categoryVisible = if (wide) leftTab == Tab.Categories else tab == Tab.Categories
        val defaultCategory = if (categoryVisible) openCategory else null

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("Ruz") },
                        actions = {
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                            }
                        },
                    )
                    if (deps.isPreview) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Deneme sürümü · örnek veriler · sayfayı yenileyince başa döner",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column(Modifier.imePadding()) {
                    CaptureBar(
                        categories = categories,
                        defaultCategory = defaultCategory,
                        onSubmit = { submit(it, defaultCategory) },
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
            val inboxItems = items.filter { it.inInbox && !it.done }

            @Composable
            fun CategoriesArea(m: Modifier) {
                if (openCategory != null) {
                    CategoryDetailPane(
                        category = openCategory,
                        items = items.filter { it.categoryId == openCategory.id },
                        today = today,
                        onBack = { openCategoryId = null },
                        onToggleDone = ::toggleDone,
                        onOpen = { editingItemId = it.id },
                        modifier = m,
                    )
                } else {
                    CategoriesPane(
                        categories = categories,
                        items = items,
                        onOpen = { openCategoryId = it.id },
                        onAdd = { categoryDialog = NewCategory },
                        onEdit = { categoryDialog = it.id },
                        onMove = { c, dir -> scope.launch { repo.moveCategory(c.id, dir) } },
                        onDelete = ::deleteCategory,
                        modifier = m,
                    )
                }
            }

            @Composable
            fun Inbox(m: Modifier) {
                InboxPane(
                    inboxItems,
                    today,
                    ::toggleDone,
                    onOpen = { editingItemId = it.id },
                    modifier = m,
                    drag = inboxDrag,
                    onAddToPlan = ::addToPlan,
                )
            }

            @Composable
            fun Plan(m: Modifier) {
                PlanPane(
                    items = items,
                    categories = categories,
                    now = now,
                    onOpen = { editingItemId = it.id },
                    links = links,
                    onReschedule = ::reschedule,
                    onLink = { item, after -> link(item, after) },
                    modifier = m,
                    dropTarget = dropTarget,
                    externalHover = dragPos,
                )
            }

            if (wide) {
                // Fold açık: solda Gelen kutusu / Kategoriler, sağda plan.
                Row(Modifier.padding(padding).fillMaxSize()) {
                    Column(Modifier.width(340.dp).fillMaxHeight()) {
                        LeftPaneSwitcher(
                            showInbox = leftTab == Tab.Inbox,
                            onSelect = { leftTab = if (it) Tab.Inbox else Tab.Categories },
                        )
                        if (leftTab == Tab.Inbox) Inbox(Modifier.fillMaxSize()) else CategoriesArea(Modifier.fillMaxSize())
                    }
                    VerticalDivider()
                    Plan(Modifier.fillMaxSize())
                }
            } else {
                val m = Modifier.padding(padding).fillMaxSize()
                when (tab) {
                    Tab.Plan -> Plan(m)
                    Tab.Inbox -> Inbox(m)
                    Tab.Categories -> CategoriesArea(m)
                }
            }
        }

        // Sürüklenen öğenin parmağı izleyen etiketi.
        val ghost = dragItem
        val gp = dragPos
        if (ghost != null && gp != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp,
                modifier = Modifier.offset { IntOffset(gp.x.roundToInt() - 24, gp.y.roundToInt() - 64) },
            ) {
                Text(
                    ghost.text,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (editingItem != null) {
        ItemEditSheet(
            item = editingItem,
            categories = categories,
            onDismiss = { editingItemId = null },
            onSave = { fields ->
                editingItemId = null
                scope.launch {
                    repo.updateItem(editingItem.id, fields)
                    // Zamanı değiştiyse arkasındaki bağlı işler de kayar.
                    val override = Schedule(fields.startAt, fields.durationMin, fields.allDay)
                    val change = planChange(items, links, mapOf(editingItem.id to override), tz)
                    val others = change.schedules - editingItem.id
                    if (others.isNotEmpty()) repo.setSchedules(others)
                }
            },
            predecessors = links.filter { it.successorId == editingItem.id }
                .mapNotNull { l -> items.firstOrNull { it.id == l.predecessorId }?.let { l to it } },
            successors = links.filter { it.predecessorId == editingItem.id }
                .mapNotNull { l -> items.firstOrNull { it.id == l.successorId }?.let { l to it } },
            onRemoveLink = { l ->
                commit(planChange(items, links, emptyMap(), tz, removeLinks = listOf(l)), "Bağ kaldırıldı")
            },
            onToggleDone = { done -> toggleDone(editingItem.id, done) },
            onDelete = { deleteItem(editingItem) },
        )
    }

    categoryDialog?.let { key ->
        val editing = categories.firstOrNull { it.id == key }
        CategoryDialog(
            initial = editing,
            suggestedColor = CategoryPalette.firstOrNull { c -> categories.none { it.color == c } } ?: CategoryPalette[0],
            onDismiss = { categoryDialog = null },
            onSave = { name, color ->
                categoryDialog = null
                scope.launch {
                    if (editing == null) {
                        repo.addCategory(name, color)
                    } else {
                        repo.renameCategory(editing.id, name)
                        repo.setCategoryColor(editing.id, color)
                    }
                }
            },
        )
    }
}
