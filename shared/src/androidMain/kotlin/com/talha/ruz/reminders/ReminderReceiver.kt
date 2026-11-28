package com.talha.ruz.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.talha.ruz.platform.RuzAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Alarm çalınca bildirim gösterir; bildirimdeki "Bitti" / "Ertele" düğmelerini işler. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = RuzAndroid.deps(app).repository
                when (intent.action) {
                    Reminders.ACTION_FIRE -> {
                        val item = repo.items.first().firstOrNull { it.id == id }
                        if (item != null && !item.done) Reminders.show(app, item)
                    }
                    Reminders.ACTION_DONE -> {
                        repo.setDone(id, true)
                        NotificationManagerCompat.from(app).cancel(Reminders.notificationId(id))
                    }
                    Reminders.ACTION_SNOOZE -> {
                        NotificationManagerCompat.from(app).cancel(Reminders.notificationId(id))
                        Reminders.snooze(app, id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Telefon yeniden açılınca, uygulama güncellenince ya da saat değişince alarmları yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val items = RuzAndroid.deps(app).repository.items.first()
                Reminders.apply(app, Reminders.plan(items))
            } finally {
                pending.finish()
            }
        }
    }
}
