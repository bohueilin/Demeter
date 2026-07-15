package com.demeter.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.demeter.app.DemeterApp
import com.demeter.domain.reminder.DeliveryDecision
import com.demeter.domain.reminder.ReminderPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Fired by AlarmManager. Does bounded local work only: re-evaluates full eligibility
 * from current Room state, then posts or suppresses. Scheduling staleness can only
 * suppress, never wrongly fire.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMINDER = "com.demeter.app.REMINDER"
        const val EXTRA_REQUEST_CODE = "requestCode"
        private val ON_TIME_BAND: Duration = Duration.ofMinutes(15)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val requestCode = intent.getLongExtra(EXTRA_REQUEST_CODE, -1)
        if (requestCode < 0) return
        val app = context.applicationContext as DemeterApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliver(app, requestCode)
            } catch (t: Throwable) {
                // A delivery failure must never crash the process; record it so the
                // audit trail stays honest instead of silently losing a reminder.
                runCatching {
                    app.container.repository.logEvent(
                        null, null, "delivery_error",
                        "A reminder could not be processed (${t.javaClass.simpleName}). It will be repaired on next launch.",
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun deliver(app: DemeterApp, requestCode: Long) {
        val repo = app.container.repository
        val dao = repo.reminderDao
        val now = Instant.now()

        val entry = dao.scheduledByRequestCode(requestCode) ?: return
        if (entry.state != "SCHEDULED") return

        val rule = repo.ruleById(entry.ruleId)
        val window = repo.latestForWindow(entry.windowId)
        val accountEntity = app.container.db.accountDao().account(entry.accountId)

        if (rule == null || window == null || accountEntity == null) {
            dao.updateScheduledState(requestCode, "CANCELLED", "data removed before delivery", null)
            repo.logEvent(entry.accountId, entry.windowId, "suppressed", "A reminder was cancelled because its account or window was removed.")
            return
        }

        val decision = ReminderPolicy.evaluateAtDelivery(
            rule = rule,
            window = window,
            expectedResetAt = Instant.ofEpochSecond(entry.resetAtEpochSec),
            now = now,
        )

        when (decision) {
            is DeliveryDecision.Deliver -> {
                if (!NotificationHelper.canPost(app)) {
                    dao.updateScheduledState(requestCode, "BLOCKED", "notifications_disabled", null)
                    repo.logEvent(
                        entry.accountId, entry.windowId, "blocked",
                        "A reminder was ready but Android notifications are off for Demeter. Fix it in Settings.",
                    )
                    return
                }
                val domainAccount = com.demeter.domain.model.MonitoredAccount(
                    id = accountEntity.id,
                    provider = com.demeter.domain.model.Provider.valueOf(accountEntity.provider),
                    nickname = accountEntity.nickname,
                    createdAt = Instant.ofEpochSecond(accountEntity.createdAtEpochSec),
                )
                NotificationHelper.postReminder(
                    app, entry.logicalId, domainAccount, window, decision,
                    resetAt = Instant.ofEpochSecond(entry.resetAtEpochSec),
                )
                dao.updateScheduledState(requestCode, "DELIVERED", null, now.epochSecond)
                val plannedAt = Instant.ofEpochSecond(entry.triggerAtEpochSec)
                val delay = Duration.between(plannedAt, now)
                val timing = if (delay <= ON_TIME_BAND) "on time" else "delayed ${TimeFormat.span(delay)} by Android"
                repo.logEvent(
                    entry.accountId, entry.windowId, "delivered",
                    "Reminder delivered $timing (${TimeFormat.leadPhrase(entry.leadMinutes)} before reset).",
                )
            }

            is DeliveryDecision.Suppress -> {
                dao.updateScheduledState(requestCode, "SUPPRESSED", decision.reasonCode, null)
                repo.logEvent(entry.accountId, entry.windowId, "suppressed", "Reminder not sent: ${decision.reason}")
            }
        }
    }
}

/**
 * Recomputes all future reminders from persisted rules after boot, app update, or
 * clock/time-zone change. Old platform alarm state is never trusted.
 */
class BootAndTimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app_update"
            Intent.ACTION_TIME_CHANGED -> "time_changed"
            Intent.ACTION_TIMEZONE_CHANGED -> "timezone_changed"
            else -> return
        }
        val app = context.applicationContext as DemeterApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.reconciler.reconcile(reason)
            } finally {
                pending.finish()
            }
        }
    }
}
