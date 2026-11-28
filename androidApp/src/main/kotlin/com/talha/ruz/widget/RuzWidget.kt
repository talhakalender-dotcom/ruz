package com.talha.ruz.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.talha.ruz.MainActivity
import com.talha.ruz.data.HabitCheck
import com.talha.ruz.data.RuzHabit
import com.talha.ruz.data.RuzItem
import com.talha.ruz.platform.RuzAndroid
import com.talha.ruz.ui.epochToLocal
import com.talha.ruz.ui.localNow
import com.talha.ruz.ui.timeRangeLabel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** Widget'ın gösterdiği anlık durum. */
private data class WidgetState(
    val today: LocalDate,
    val todayItems: List<RuzItem>,
    val deadlines: List<Pair<RuzItem, Int>>,
    val habits: List<Pair<RuzHabit, Boolean>>,
)

private suspend fun loadState(context: Context): WidgetState {
    val repo = RuzAndroid.deps(context).repository
    val today = localNow().date
    val items = repo.items.first()
    val habits = repo.habits.first()
    val checks: List<HabitCheck> = repo.habitChecks.first()
    val todayItems = items
        .filter { !it.done && it.startAt != null && epochToLocal(it.startAt!!).date == today }
        .sortedWith(compareBy({ !it.allDay }, { it.startAt }))
    val horizon = today.plus(14, DateTimeUnit.DAY)
    val deadlines = items
        .filter { !it.done && it.isMilestone && it.startAt != null }
        .mapNotNull { i ->
            val d = epochToLocal(i.startAt!!).date
            if (d >= today && d <= horizon) i to today.daysUntil(d) else null
        }
        .sortedBy { it.second }
        .take(4)
    val dueHabits = habits
        .filter { h -> h.weekdays.isEmpty() || today.dayOfWeek.isoDayNumber in h.weekdays }
        .map { h -> h to checks.any { it.habitId == h.id && it.day == today } }
    return WidgetState(today, todayItems, deadlines, dueHabits)
}

/**
 * Ana ekran widget'ı: bugünün planı (dokununca biter), yaklaşan son tarihler, bugünün alışkanlıkları,
 * hızlı ekleme ve sesle ekleme.
 */
class RuzWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent {
            GlanceTheme { Content(context, state) }
        }
    }

    companion object {
        /** Veri değişince bütün widget'ları yeniler. */
        suspend fun refresh(context: Context) = RuzWidget().updateAll(context)
    }
}

private val ItemIdKey = ActionParameters.Key<String>("itemId")
private val HabitIdKey = ActionParameters.Key<String>("habitId")

@Composable
private fun Content(context: Context, s: WidgetState) {
    val colors = GlanceTheme.colors
    val title = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val section = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(color = colors.onSurface, fontSize = 13.sp)
    val muted = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp)

    fun open(action: String?) = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (action != null) putExtra(MainActivity.EXTRA_ACTION, action) },
    )

    Column(
        GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(20.dp).padding(12.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ruz · Bugün", style = title, modifier = GlanceModifier.defaultWeight().clickable(open(null)))
            Text(
                "🎤",
                style = TextStyle(fontSize = 18.sp),
                modifier = GlanceModifier.padding(horizontal = 8.dp).clickable(open(MainActivity.ACTION_VOICE)),
            )
            Text(
                " + ",
                style = TextStyle(color = colors.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.background(colors.primary).cornerRadius(14.dp).padding(horizontal = 6.dp)
                    .clickable(open(MainActivity.ACTION_CAPTURE)),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            item { Text("Plan", style = section, modifier = GlanceModifier.padding(top = 6.dp)) }
            if (s.todayItems.isEmpty()) {
                item { Text("Bugün planlı iş yok", style = muted) }
            }
            s.todayItems.forEach { i ->
                item {
                    CheckBox(
                        checked = false,
                        onCheckedChange = actionRunCallback<CompleteItemAction>(actionParametersOf(ItemIdKey to i.id)),
                        text = (if (i.allDay) "" else timeRangeLabel(i).substringBefore('–') + "  ") + i.text,
                        style = body,
                        maxLines = 1,
                    )
                }
            }
            if (s.deadlines.isNotEmpty()) {
                item { Text("Yaklaşan son tarihler", style = section, modifier = GlanceModifier.padding(top = 8.dp)) }
                s.deadlines.forEach { (i, days) ->
                    item {
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(open(null))) {
                            Text("◆ " + i.text, style = body, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                            Text(
                                when (days) {
                                    0 -> "bugün"
                                    1 -> "yarın"
                                    else -> "$days gün"
                                },
                                style = muted,
                            )
                        }
                    }
                }
            }
            if (s.habits.isNotEmpty()) {
                item { Text("Alışkanlıklar", style = section, modifier = GlanceModifier.padding(top = 8.dp)) }
                s.habits.forEach { (h, done) ->
                    item {
                        CheckBox(
                            checked = done,
                            onCheckedChange = actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id)),
                            text = h.name,
                            style = body,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Widget'ta işe dokununca biter. */
class CompleteItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemIdKey] ?: return
        RuzAndroid.deps(context).repository.setDone(id, true)
        RuzWidget.refresh(context)
    }
}

/** Widget'ta alışkanlığın bugünkü işaretini değiştirir. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        val repo = RuzAndroid.deps(context).repository
        val today = localNow().date
        val done = repo.habitChecks.first().any { it.habitId == id && it.day == today }
        repo.setHabitDone(id, today, !done)
        RuzWidget.refresh(context)
    }
}

class RuzWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RuzWidget()
}
