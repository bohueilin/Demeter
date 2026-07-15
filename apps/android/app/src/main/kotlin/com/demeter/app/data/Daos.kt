package com.demeter.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY createdAtEpochSec")
    fun accounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun account(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun accountFlow(id: String): Flow<AccountEntity?>

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM accounts WHERE provider = :provider")
    suspend fun countForProvider(provider: String): Int
}

@Dao
interface EvidenceDao {
    @Insert
    suspend fun insert(evidence: WindowEvidenceEntity)

    @Query(
        """SELECT * FROM window_evidence WHERE rowId IN
           (SELECT MAX(rowId) FROM window_evidence GROUP BY windowId)
           AND deleted = 0 ORDER BY windowId""",
    )
    fun latestWindows(): Flow<List<WindowEvidenceEntity>>

    @Query(
        """SELECT * FROM window_evidence WHERE rowId IN
           (SELECT MAX(rowId) FROM window_evidence GROUP BY windowId)
           AND deleted = 0 ORDER BY windowId""",
    )
    suspend fun latestWindowsOnce(): List<WindowEvidenceEntity>

    @Query(
        """SELECT * FROM window_evidence WHERE windowId = :windowId
           ORDER BY rowId DESC LIMIT 1""",
    )
    suspend fun latestForWindow(windowId: String): WindowEvidenceEntity?

    @Query("SELECT * FROM window_evidence WHERE accountId = :accountId ORDER BY rowId DESC")
    fun historyForAccount(accountId: String): Flow<List<WindowEvidenceEntity>>

    @Query("DELETE FROM window_evidence WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: ReminderRuleEntity)

    @Query("SELECT * FROM reminder_rules")
    suspend fun rulesOnce(): List<ReminderRuleEntity>

    @Query("SELECT * FROM reminder_rules")
    fun rules(): Flow<List<ReminderRuleEntity>>

    @Query("SELECT * FROM reminder_rules WHERE windowId = :windowId")
    suspend fun ruleForWindow(windowId: String): ReminderRuleEntity?

    @Query("SELECT * FROM reminder_rules WHERE id = :id")
    suspend fun rule(id: String): ReminderRuleEntity?

    @Query("DELETE FROM reminder_rules WHERE accountId = :accountId")
    suspend fun deleteRulesForAccount(accountId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScheduled(scheduled: ScheduledReminderEntity): Long

    @Query("SELECT * FROM scheduled_reminders WHERE state = 'SCHEDULED'")
    suspend fun activeScheduled(): List<ScheduledReminderEntity>

    @Query("SELECT * FROM scheduled_reminders WHERE logicalId = :logicalId")
    suspend fun scheduledByLogicalId(logicalId: String): ScheduledReminderEntity?

    @Query("SELECT * FROM scheduled_reminders WHERE requestCode = :requestCode")
    suspend fun scheduledByRequestCode(requestCode: Long): ScheduledReminderEntity?

    @Query("UPDATE scheduled_reminders SET state = :state, reason = :reason, deliveredAtEpochSec = :deliveredAt WHERE requestCode = :requestCode")
    suspend fun updateScheduledState(requestCode: Long, state: String, reason: String?, deliveredAt: Long?)

    @Query("SELECT * FROM scheduled_reminders WHERE accountId = :accountId ORDER BY triggerAtEpochSec DESC LIMIT 20")
    fun scheduledForAccount(accountId: String): Flow<List<ScheduledReminderEntity>>

    @Query("SELECT * FROM scheduled_reminders WHERE state = 'SCHEDULED' AND accountId = :accountId ORDER BY triggerAtEpochSec LIMIT 1")
    fun nextScheduledForAccount(accountId: String): Flow<ScheduledReminderEntity?>

    @Insert
    suspend fun insertEvent(event: ReminderEventEntity)

    @Query("SELECT * FROM reminder_events ORDER BY id DESC LIMIT 100")
    fun events(): Flow<List<ReminderEventEntity>>

    @Query("SELECT * FROM reminder_events WHERE accountId = :accountId ORDER BY id DESC LIMIT 50")
    fun eventsForAccount(accountId: String): Flow<List<ReminderEventEntity>>
}
