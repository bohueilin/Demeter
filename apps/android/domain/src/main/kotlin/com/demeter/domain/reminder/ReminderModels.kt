package com.demeter.domain.reminder

import java.time.Instant

/**
 * Evidence policy is one control; unknown-remaining behavior is a separate opt-in.
 * They are deliberately NOT a single three-way "confidence mode".
 */
enum class EvidencePolicy(val displayLabel: String, val description: String) {
    CURRENT_ONLY(
        "Current data only",
        "Remind only when your data is fresh enough to trust. If it is too old, Demeter stays silent instead of guessing.",
    ),
    LAST_KNOWN(
        "Use last known data",
        "Remind using your most recent update, and say how old it is.",
    ),
}

val LEAD_TIME_CHOICES_MINUTES: List<Int> =
    listOf(
        7 * 24 * 60, 6 * 24 * 60, 5 * 24 * 60, 4 * 24 * 60, 3 * 24 * 60, // multi-day leads for weekly/monthly windows
        48 * 60, 24 * 60, 12 * 60, 8 * 60, 4 * 60, 2 * 60, 60,
    )

data class ReminderRule(
    val id: String,
    val accountId: String,
    val windowId: String,
    val leadMinutes: List<Int>,
    val evidencePolicy: EvidencePolicy,
    /** "Remind me about the reset even when remaining usage is unknown." */
    val remindWhenUnknown: Boolean,
    /** Suppress when remaining is below this percent (0 = any remaining counts). */
    val minRemainingPercent: Int,
    val quietStartMinuteOfDay: Int?,
    val quietEndMinuteOfDay: Int?,
    val enabled: Boolean,
)

/**
 * Two reminder intents: an allowance reminder asserts remaining capacity from
 * sufficiently current evidence; a check-usage reminder only points at the reset
 * and asks the user to update what remains.
 */
enum class ReminderIntent { ALLOWANCE, CHECK_USAGE }

enum class ReminderState { SCHEDULED, DELIVERED, SUPPRESSED, CANCELLED, BLOCKED }

data class PlannedTrigger(
    val logicalId: String,
    val ruleId: String,
    val accountId: String,
    val windowId: String,
    val leadMinutes: Int,
    val triggerAt: Instant,
    val resetAt: Instant,
    /** Non-null when quiet hours moved this trigger; kept for the audit trail. */
    val shiftedFrom: Instant? = null,
)

sealed interface DeliveryDecision {
    data class Deliver(
        val intent: ReminderIntent,
        /** Observation time of the evidence the reminder is based on. */
        val asOf: Instant,
        val remainingPercent: Int?,
    ) : DeliveryDecision

    data class Suppress(val reasonCode: String, val reason: String) : DeliveryDecision
}
