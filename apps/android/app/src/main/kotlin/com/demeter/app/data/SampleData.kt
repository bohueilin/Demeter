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

        // One sample account per provider. Between them the three still showcase every
        // card state: fresh manual data, stale pasted evidence, an exhausted session,
        // an unknown-remaining window (reset countdown only), and screenshot evidence.

        // 1 — ChatGPT: fresh session, but the weekly value was pasted 2 days ago (stale)
        val personalGpt = account(Provider.OPENAI, "Personal (sample)")
        evidence(personalGpt, "5-hour session", WindowKind.SESSION, "KNOWN", 72, Duration.ofHours(2), Duration.ofMinutes(12), SourceType.MANUAL)
        evidence(personalGpt, "Weekly · all models", WindowKind.WEEKLY, "KNOWN", 45, Duration.ofDays(3), Duration.ofDays(2), SourceType.PASTE)

        // 2 — Claude: exhausted session + healthy weekly + unknown-remaining Opus window
        val claudePersonal = account(Provider.ANTHROPIC, "Claude (sample)")
        evidence(claudePersonal, "Session", WindowKind.SESSION, "EXHAUSTED", null, Duration.ofHours(3), Duration.ofMinutes(25), SourceType.MANUAL)
        evidence(claudePersonal, "Weekly limit", WindowKind.WEEKLY, "KNOWN", 81, Duration.ofDays(5), Duration.ofMinutes(25), SourceType.MANUAL)
        evidence(claudePersonal, "Weekly · Opus", WindowKind.WEEKLY, "UNKNOWN", null, Duration.ofDays(1).plusHours(6), Duration.ofHours(1), SourceType.MANUAL)

        // 3 — Gemini: daily "current usage" + weekly limit, both from a screenshot
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
