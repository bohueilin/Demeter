package com.demeter.app.platform

import com.demeter.app.data.DemeterRepository
import com.demeter.app.data.ScheduledReminderEntity
import com.demeter.domain.reminder.ReminderPolicy
import java.time.Instant
import java.time.ZoneId

/**
 * The single reconciliation path. Recomputes the full desired reminder schedule from
 * persisted rules + latest evidence, diffs it against the scheduled_reminders table,
 * and repairs platform alarms. Idempotent: running it twice changes nothing. Old
 * PendingIntent state is never treated as authoritative — every entry point (app
 * launch, boot, package update, time/zone change, data change) funnels through here.
 */
class Reconciler(
    private val repo: DemeterRepository,
    private val alarmScheduler: AlarmScheduler,
) {

    suspend fun reconcile(reason: String) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val rules = repo.rulesOnce()
        val windowsById = repo.latestWindowsOnce().associateBy { it.id }
        val dao = repo.reminderDao

        val desired = rules.flatMap { rule ->
            val window = windowsById[rule.windowId] ?: return@flatMap emptyList()
            ReminderPolicy.desiredTriggers(rule, window, now, zone)
        }.associateBy { it.logicalId }

        val active = dao.activeScheduled()
        val activeByLogical = active.associateBy { it.logicalId }

        var scheduled = 0
        var cancelled = 0

        // Cancel schedules no longer desired (rule change, reset moved, window gone).
        for (entry in active) {
            if (entry.logicalId !in desired) {
                alarmScheduler.cancel(entry.requestCode)
                dao.updateScheduledState(entry.requestCode, "CANCELLED", "superseded by $reason", null)
                cancelled++
            }
        }

        // Create + arm new desired schedules; re-arm surviving ones (boot wipes alarms,
        // and setAndAllowWhileIdle on an existing PendingIntent just replaces it).
        for (trigger in desired.values) {
            val existing = activeByLogical[trigger.logicalId]
            if (existing != null) {
                alarmScheduler.schedule(existing.requestCode, Instant.ofEpochSecond(existing.triggerAtEpochSec))
                continue
            }
            // A logicalId that was already delivered/suppressed/cancelled stays consumed:
            // the unique index makes this insert a no-op, so reminders never re-fire.
            val rowId = dao.insertScheduled(
                ScheduledReminderEntity(
                    logicalId = trigger.logicalId,
                    ruleId = trigger.ruleId,
                    accountId = trigger.accountId,
                    windowId = trigger.windowId,
                    leadMinutes = trigger.leadMinutes,
                    triggerAtEpochSec = trigger.triggerAt.epochSecond,
                    resetAtEpochSec = trigger.resetAt.epochSecond,
                    shiftedFromEpochSec = trigger.shiftedFrom?.epochSecond,
                    state = "SCHEDULED",
                    reason = null,
                    deliveredAtEpochSec = null,
                ),
            )
            if (rowId > 0) {
                alarmScheduler.schedule(rowId, trigger.triggerAt)
                scheduled++
                if (trigger.shiftedFrom != null) {
                    repo.logEvent(
                        trigger.accountId, trigger.windowId, "quiet_hours_shift",
                        "A reminder was moved out of quiet hours to ${TimeFormat.untilPhrase(trigger.triggerAt, now)} before reset.",
                    )
                }
            }
        }

        if (scheduled > 0 || cancelled > 0 || reason != "app_launch") {
            repo.logEvent(
                null, null, "reconciled",
                "Schedules repaired ($reason): $scheduled created, $cancelled cancelled, ${desired.size} active.",
            )
        }
    }
}
