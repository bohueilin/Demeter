package com.demeter.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant

/**
 * One-shot INEXACT alarms only. No exact-alarm permissions exist in this app, and no
 * UI copy ever claims exact-to-the-minute delivery. Platform alarm state is derived
 * from the scheduled_reminders table and is repairable at any time.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(requestCode: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun schedule(requestCode: Long, triggerAt: Instant) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toEpochMilli(),
            pendingIntent(requestCode),
        )
    }

    fun cancel(requestCode: Long) {
        alarmManager.cancel(pendingIntent(requestCode))
    }
}
