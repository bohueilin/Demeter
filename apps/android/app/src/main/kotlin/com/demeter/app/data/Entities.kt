package com.demeter.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val nickname: String,
    val createdAtEpochSec: Long,
)

/**
 * Append-only evidence log. Every update inserts a new row; the current state of a
 * logical window is its highest rowId. Corrections never rewrite history.
 */
@Entity(tableName = "window_evidence", indices = [Index("windowId"), Index("accountId")])
data class WindowEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val windowId: String,
    val accountId: String,
    val label: String,
    val kind: String,
    val capacityType: String, // KNOWN | EXHAUSTED | UNKNOWN
    val remainingPercent: Int?,
    val resetAtEpochSec: Long?,
    val durationMinutes: Long?,
    val observedAtEpochSec: Long,
    val source: String,
    val confirmation: String,
    val note: String?,
    val deleted: Boolean = false,
)

@Entity(tableName = "reminder_rules", indices = [Index(value = ["windowId"], unique = true)])
data class ReminderRuleEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val windowId: String,
    val leadMinutesCsv: String,
    val evidencePolicy: String,
    val remindWhenUnknown: Boolean,
    val minRemainingPercent: Int,
    val quietStartMinuteOfDay: Int?,
    val quietEndMinuteOfDay: Int?,
    val enabled: Boolean,
)

/**
 * Persisted logical reminder intent. The autoincrement primary key doubles as the
 * collision-free PendingIntent request code (allocation-table design). Platform
 * alarm state is derived from these rows and repairable at any time.
 */
@Entity(tableName = "scheduled_reminders", indices = [Index(value = ["logicalId"], unique = true)])
data class ScheduledReminderEntity(
    @PrimaryKey(autoGenerate = true) val requestCode: Long = 0,
    val logicalId: String,
    val ruleId: String,
    val accountId: String,
    val windowId: String,
    val leadMinutes: Int,
    val triggerAtEpochSec: Long,
    val resetAtEpochSec: Long,
    val shiftedFromEpochSec: Long?,
    val state: String, // SCHEDULED | DELIVERED | SUPPRESSED | CANCELLED | BLOCKED
    val reason: String?,
    val deliveredAtEpochSec: Long?,
)

/** Plain-language audit trail: why Demeter scheduled, delivered, suppressed, or repaired. */
@Entity(tableName = "reminder_events", indices = [Index("accountId")])
data class ReminderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atEpochSec: Long,
    val accountId: String?,
    val windowId: String?,
    val type: String,
    val message: String,
)
