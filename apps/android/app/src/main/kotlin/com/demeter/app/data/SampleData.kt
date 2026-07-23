package com.demeter.app.data

import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.ConfirmationState
import com.demeter.domain.model.Provider
import com.demeter.domain.model.SourceType
import com.demeter.domain.model.WindowKind
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Sample data is clearly marked and removable in one action (delete the accounts). */
object SampleData {

    suspend fun seed(db: DemeterDb) {
        val now = Instant.now()

        suspend fun account(provider: Provider, nickname: String): String {
            val id = UUID.randomUUID().toString()
            db.accountDao().upsert(AccountEntity(id, provider.name, nickname, now.epochSecond))
            return id
        }

        suspend fun evidence(
            accountId: String,
            label: String,
            kind: WindowKind,
            capacityType: String,
            remaining: Int?,
            resetIn: Duration?,
            observedAgo: Duration,
            source: SourceType,
        ) {
            db.evidenceDao().insert(
                WindowEvidenceEntity(
                    windowId = UUID.randomUUID().toString(),
                    accountId = accountId,
                    label = label,
                    kind = kind.name,
                    capacityType = capacityType,
                    remainingPercent = remaining,
                    resetAtEpochSec = resetIn?.let { now.plus(it).epochSecond },
                    durationMinutes = kind.defaultDuration?.toMinutes(),
                    observedAtEpochSec = now.minus(observedAgo).epochSecond,
                    source = source.name,
                    confirmation = ConfirmationState.USER_EDITED.name,
                    note = "Sample data",
                ),
            )
        }

        // 1 — urgent: plenty remaining, resets soon, fresh manual data
        val personalGpt = account(Provider.OPENAI, "Personal (sample)")
        evidence(personalGpt, "5-hour session", WindowKind.SESSION, "KNOWN", 72, Duration.ofHours(2), Duration.ofMinutes(12), SourceType.MANUAL)
        evidence(personalGpt, "Weekly · all models", WindowKind.WEEKLY, "KNOWN", 45, Duration.ofDays(3), Duration.ofHours(4), SourceType.MANUAL)

        // 2 — stale evidence: value present but old (check-usage territory)
        val workGpt = account(Provider.OPENAI, "Work (sample)")
        evidence(workGpt, "Weekly · all models", WindowKind.WEEKLY, "KNOWN", 60, Duration.ofHours(30), Duration.ofDays(2), SourceType.PASTE)

        // 3 — exhausted session + healthy weekly
        val claudePersonal = account(Provider.ANTHROPIC, "Claude personal (sample)")
        evidence(claudePersonal, "Session", WindowKind.SESSION, "EXHAUSTED", null, Duration.ofHours(3), Duration.ofMinutes(25), SourceType.MANUAL)
        evidence(claudePersonal, "Weekly limit", WindowKind.WEEKLY, "KNOWN", 81, Duration.ofDays(5), Duration.ofMinutes(25), SourceType.MANUAL)

        // 4 — unknown remaining: reset time only
        val claudeMax = account(Provider.ANTHROPIC, "Claude Max (sample)")
        evidence(claudeMax, "Weekly · Opus", WindowKind.WEEKLY, "UNKNOWN", null, Duration.ofDays(1).plusHours(6), Duration.ofHours(1), SourceType.MANUAL)

        // 5 — Gemini: daily "current usage" + weekly limit, both mostly available
        val geminiPersonal = account(Provider.GOOGLE, "Gemini (sample)")
        evidence(geminiPersonal, "Current usage", WindowKind.SESSION, "KNOWN", 89, Duration.ofHours(5), Duration.ofMinutes(3), SourceType.SCREENSHOT)
        evidence(geminiPersonal, "Weekly limit", WindowKind.WEEKLY, "KNOWN", 91, Duration.ofDays(6), Duration.ofMinutes(3), SourceType.SCREENSHOT)

        db.reminderDao().insertEvent(
            ReminderEventEntity(
                atEpochSec = now.epochSecond,
                accountId = null,
                windowId = null,
                type = "sample_seeded",
                message = "Sample accounts added. Delete them anytime from account details.",
            ),
        )
    }
}
