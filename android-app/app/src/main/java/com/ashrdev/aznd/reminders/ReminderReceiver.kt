package com.ashrdev.aznd.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND)
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        ReminderScheduler.scope.launch {
            try {
                runCatching { ReminderScheduler.handle(app, kind, id) }
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        ReminderScheduler.scope.launch {
            try {
                runCatching { ReminderScheduler.sync(app) }
            } finally {
                pending.finish()
            }
        }
    }
}
