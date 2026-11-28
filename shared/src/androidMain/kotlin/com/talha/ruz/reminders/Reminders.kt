package com.talha.ruz.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.talha.ruz.data.RuzItem
import com.talha.ruz.data.RuzRepository
import com.talha.ruz.remind.reminderTriggerAt
import com.talha.ruz.ui.dayLabel
import com.talha.ruz.ui.epochToLocal
import com.talha.ruz.ui.localNow
import com.talha.ruz.ui.timeRangeLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Hatırlatıcılar: işler değiştikçe alarmları yeniden kurar, zamanı gelince bildirim gösterir.
 * Bildirimde "Bitti" ve "10 dk ertele" düğmeleri var. Telefon yeniden açılınca [BootReceiver] alarmları geri kurar.
 */
internal object Reminders {
    const val CHANNEL = "hatirlatici"
    const val ACTION_FIRE = "com.talha.ruz.REMINDER"
    const val ACTION_DONE = "com.talha.ruz.REMINDER_DONE"
    const val ACTION_SNOOZE = "com.talha.ruz.REMINDER_SNOOZE"
    const val EXTRA_ID = "itemId"
    private const val PREFS = "reminders"
    private const val KEY_IDS = "scheduled"
    private const val SNOOZE_MIN = 10
    private const val HORIZON_DAYS = 60L

    /** İşler her değiştiğinde alarmları eşitler (uygulama açıkken). */
    fun start(context: Context, repository: RuzRepository, scope: CoroutineScope) {
        ensureChannel(context)
        scope.launch {
            repository.items
                .map { plan(it) }
                .distinctUntilChanged()
                .collect { apply(context, it) }
        }
    }

    /** İş kimliği → alarm anı. Yalnız bitmemiş, gelecekteki (60 gün içinde) hatırlatmalar. */
    fun plan(items: List<RuzItem>): Map<String, Long> {
        val tz = TimeZone.currentSystemDefault()
        val now = System.currentTimeMillis()
        val horizon = now + HORIZON_DAYS * 86_400_000L
        return items.asSequence()
            .filter { !it.done && it.startAt != null }
            .mapNotNull { i -> reminderTriggerAt(i.startAt!!, i.allDay, i.reminderMinBefore, tz)?.let { i.id to it } }
            .filter { (_, at) -> at > now && at < horizon }
            .toMap()
    }

    fun apply(context: Context, plan: Map<String, Long>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        (old - plan.keys).forEach { cancel(context, it) }
        plan.forEach { (id, at) -> schedule(context, id, at, snooze = false) }
        prefs.edit().putStringSet(KEY_IDS, plan.keys.toSet()).apply()
    }

    fun snooze(context: Context, id: String) {
        schedule(context, id, System.currentTimeMillis() + SNOOZE_MIN * 60_000L, snooze = true)
    }

    private fun schedule(context: Context, id: String, at: Long, snooze: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = firePending(context, id, snooze, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(context: Context, id: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        firePending(context, id, false, PendingIntent.FLAG_NO_CREATE)?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    private fun firePending(context: Context, id: String, snooze: Boolean, flags: Int): PendingIntent? {
        val kind = if (snooze) "ertele" else "hatirlatici"
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("ruz://$kind/$id"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionPending(context: Context, action: String, id: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("ruz://${action.substringAfterLast('.')}/$id"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Hatırlatıcılar", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "İşlerin başlama ve son tarih hatırlatmaları"
                },
            )
        }
    }

    fun notificationId(id: String): Int = id.hashCode()

    fun show(context: Context, item: RuzItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val start = item.startAt ?: return
        val day = dayLabel(epochToLocal(start).date, localNow().date)
        val text = buildString {
            if (item.isMilestone) append("◆ Son tarih · ")
            append(day)
            append(if (item.allDay) " · tam gün" else " " + timeRangeLabel(item))
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = launch?.let {
            PendingIntent.getActivity(context, notificationId(item.id), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val icon = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
            .takeIf { it != 0 } ?: context.applicationInfo.icon
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(item.text)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Bitti", actionPending(context, ACTION_DONE, item.id))
            .addAction(0, "$SNOOZE_MIN dk ertele", actionPending(context, ACTION_SNOOZE, item.id))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(item.id), n)
    }
}
