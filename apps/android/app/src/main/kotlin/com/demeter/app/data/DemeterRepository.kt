package com.demeter.app.data

import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.ConfirmationState
import com.demeter.domain.model.MonitoredAccount
import com.demeter.domain.model.Provider
import com.demeter.domain.model.SourceType
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.model.WindowKind
import com.demeter.domain.reminder.EvidencePolicy
import com.demeter.domain.reminder.ReminderRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.util.UUID

const val MAX_ACCOUNTS_PER_PROVIDER = 3

class DemeterRepository(private val db: DemeterDb) {

    // ---- mapping ----

    private fun AccountEntity.toDomain() = MonitoredAccount(
        id = id,
        provider = Provider.valueOf(provider),
        nickname = nickname,
        createdAt = Instant.ofEpochSecond(createdAtEpochSec),
    )

    private fun WindowEvidenceEntity.toDomain() = UsageWindow(
        id = windowId,
        accountId = accountId,
        label = label,
        kind = WindowKind.valueOf(kind),
        capacity = when (capacityType) {
            "EXHAUSTED" -> CapacityState.Exhausted
            "UNKNOWN" -> CapacityState.UnknownLimit()
            else -> CapacityState.Known(remainingPercent ?: 0)
        },
        resetAt = resetAtEpochSec?.let(Instant::ofEpochSecond),
        windowDuration = durationMinutes?.let(Duration::ofMinutes),
        observedAt = Instant.ofEpochSecond(observedAtEpochSec),
        source = SourceType.valueOf(source),
        confirmation = ConfirmationState.valueOf(confirmation),
    )

    private fun ReminderRuleEntity.toDomain() = ReminderRule(
        id = id,
        accountId = accountId,
        windowId = windowId,
        leadMinutes = leadMinutesCsv.split(',').filter { it.isNotBlank() }.map { it.trim().toInt() },
        evidencePolicy = EvidencePolicy.valueOf(evidencePolicy),
        remindWhenUnknown = remindWhenUnknown,
        minRemainingPercent = minRemainingPercent,
        quietStartMinuteOfDay = quietStartMinuteOfDay,
        quietEndMinuteOfDay = quietEndMinuteOfDay,
        enabled = enabled,
    )

    // ---- accounts ----

    fun accounts(): Flow<List<MonitoredAccount>> =
        db.accountDao().accounts().map { list -> list.map { it.toDomain() } }

    fun accountFlow(id: String): Flow<MonitoredAccount?> =
        db.accountDao().accountFlow(id).map { it?.toDomain() }

    suspend fun canAddAccount(provider: Provider): Boolean =
        db.accountDao().countForProvider(provider.name) < MAX_ACCOUNTS_PER_PROVIDER

    suspend fun addAccount(provider: Provider, nickname: String): String {
        val id = UUID.randomUUID().toString()
        db.accountDao().upsert(
            AccountEntity(id, provider.name, nickname.trim(), Instant.now().epochSecond),
        )
        return id
    }

    suspend fun deleteAccount(id: String) {
        db.accountDao().delete(id)
        db.evidenceDao().deleteForAccount(id)
        db.reminderDao().deleteRulesForAccount(id)
        logEvent(id, null, "account_deleted", "Account and its local history were deleted.")
    }

    // ---- evidence (append-only) ----

    fun latestWindows(): Flow<List<UsageWindow>> =
        db.evidenceDao().latestWindows().map { list -> list.map { it.toDomain() } }

    suspend fun latestWindowsOnce(): List<UsageWindow> =
        db.evidenceDao().latestWindowsOnce().map { it.toDomain() }

    suspend fun latestForWindow(windowId: String): UsageWindow? =
        db.evidenceDao().latestForWindow(windowId)?.takeIf { !it.deleted }?.toDomain()

    fun historyForAccount(accountId: String): Flow<List<UsageWindow>> =
        db.evidenceDao().historyForAccount(accountId).map { list -> list.map { it.toDomain() } }

    /** Records new evidence for a window (new windowId = new window). Never updates in place. */
    suspend fun recordEvidence(
        accountId: String,
        windowId: String?,
        label: String,
        kind: WindowKind,
        capacity: CapacityState,
        resetAt: Instant?,
        duration: Duration?,
        source: SourceType,
        note: String?,
    ): String {
        val id = windowId ?: UUID.randomUUID().toString()
        db.evidenceDao().insert(
            WindowEvidenceEntity(
                windowId = id,
                accountId = accountId,
                label = label,
                kind = kind.name,
                capacityType = when (capacity) {
                    is CapacityState.Exhausted -> "EXHAUSTED"
                    is CapacityState.UnknownLimit -> "UNKNOWN"
                    is CapacityState.Known -> "KNOWN"
                },
                remainingPercent = (capacity as? CapacityState.Known)?.remainingPercent,
                resetAtEpochSec = resetAt?.epochSecond,
                durationMinutes = (duration ?: kind.defaultDuration)?.toMinutes(),
                observedAtEpochSec = Instant.now().epochSecond,
                source = source.name,
                confirmation = ConfirmationState.USER_EDITED.name,
                note = note,
            ),
        )
        logEvent(accountId, id, "evidence_recorded", "Usage updated from ${source.displayLabel.lowercase()}.")
        return id
    }

    suspend fun deleteWindow(accountId: String, windowId: String) {
        val latest = db.evidenceDao().latestForWindow(windowId) ?: return
        db.evidenceDao().insert(
            latest.copy(rowId = 0, deleted = true, observedAtEpochSec = Instant.now().epochSecond),
        )
        logEvent(accountId, windowId, "window_removed", "Usage window removed.")
    }

    // ---- reminder rules ----

    fun rules(): Flow<List<ReminderRule>> =
        db.reminderDao().rules().map { list -> list.map { it.toDomain() } }

    suspend fun rulesOnce(): List<ReminderRule> =
        db.reminderDao().rulesOnce().map { it.toDomain() }

    suspend fun ruleForWindow(windowId: String): ReminderRule? =
        db.reminderDao().ruleForWindow(windowId)?.toDomain()

    suspend fun ruleById(id: String): ReminderRule? = db.reminderDao().rule(id)?.toDomain()

    suspend fun saveRule(rule: ReminderRule) {
        db.reminderDao().upsertRule(
            ReminderRuleEntity(
                id = rule.id,
                accountId = rule.accountId,
                windowId = rule.windowId,
                leadMinutesCsv = rule.leadMinutes.joinToString(","),
                evidencePolicy = rule.evidencePolicy.name,
                remindWhenUnknown = rule.remindWhenUnknown,
                minRemainingPercent = rule.minRemainingPercent,
                quietStartMinuteOfDay = rule.quietStartMinuteOfDay,
                quietEndMinuteOfDay = rule.quietEndMinuteOfDay,
                enabled = rule.enabled,
            ),
        )
        logEvent(rule.accountId, rule.windowId, "rule_saved", if (rule.enabled) "Reminder rule saved." else "Reminder rule saved (off).")
    }

    // ---- scheduled reminders + audit ----

    val reminderDao: ReminderDao get() = db.reminderDao()

    suspend fun logEvent(accountId: String?, windowId: String?, type: String, message: String) {
        db.reminderDao().insertEvent(
            ReminderEventEntity(
                atEpochSec = Instant.now().epochSecond,
                accountId = accountId,
                windowId = windowId,
                type = type,
                message = message,
            ),
        )
    }
}
