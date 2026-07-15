package com.demeter.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Card status is modeled as concurrent orthogonal axes, not one flat enum.
 * A card can be exhausted AND stale AND manual AND notification-blocked at once.
 */
enum class UsageAxis {
    /** Capacity remains and the reset is not imminent. */
    HEALTHY,

    /** Meaningful capacity remains and the reset is within a day — plan to use it. */
    USE_SOON,

    /** Meaningful capacity remains and the reset is within hours — about to be lost. */
    URGENT,

    /** No usable capacity remains for this window. */
    EXHAUSTED,

    /** The stored reset time has passed; the window likely rolled over. */
    RESET_PASSED,

    /** Remaining usage is not available for this window. */
    UNKNOWN,
}

enum class EvidenceAxis(val displayLabel: String) {
    CURRENT("Current"),
    AGING("Aging"),
    STALE("Stale"),
}

enum class ReminderHealthAxis {
    /** At least one enabled rule and notifications can post. */
    ACTIVE,

    /** No enabled reminder rule for this account. */
    NONE,

    /** Rules exist but Android notifications are denied or the channel is blocked. */
    BLOCKED,
}

object FreshnessPolicy {
    private val MIN_TTL: Duration = Duration.ofMinutes(30)
    private val MAX_TTL: Duration = Duration.ofHours(6)
    private val UNKNOWN_DURATION_TTL: Duration = Duration.ofHours(2)

    /** freshness_ttl = min(6h, max(30m, 25% of known window duration)); 2h when duration unknown. */
    fun ttl(windowDuration: Duration?): Duration {
        if (windowDuration == null) return UNKNOWN_DURATION_TTL
        val quarter = windowDuration.dividedBy(4)
        return minOf(MAX_TTL, maxOf(MIN_TTL, quarter))
    }

    fun evidenceAxis(observedAt: Instant, windowDuration: Duration?, now: Instant): EvidenceAxis {
        val age = Duration.between(observedAt, now)
        val ttl = ttl(windowDuration)
        return when {
            age <= ttl -> EvidenceAxis.CURRENT
            age <= ttl.multipliedBy(3) -> EvidenceAxis.AGING
            else -> EvidenceAxis.STALE
        }
    }

    fun isCurrent(window: UsageWindow, now: Instant): Boolean =
        evidenceAxis(window.observedAt, window.effectiveDuration, now) == EvidenceAxis.CURRENT
}

data class WindowStatus(
    val window: UsageWindow,
    val usage: UsageAxis,
    val evidence: EvidenceAxis,
)

object DashboardPolicy {
    private val URGENT_HORIZON: Duration = Duration.ofHours(6)
    private val USE_SOON_HORIZON: Duration = Duration.ofHours(24)
    private const val MEANINGFUL_REMAINING_PERCENT = 20

    fun usageAxis(window: UsageWindow, now: Instant): UsageAxis {
        val resetAt = window.resetAt
        if (resetAt != null && resetAt.isBefore(now)) return UsageAxis.RESET_PASSED
        return when (val cap = window.capacity) {
            is CapacityState.Exhausted -> UsageAxis.EXHAUSTED
            is CapacityState.UnknownLimit -> UsageAxis.UNKNOWN
            is CapacityState.Known -> {
                if (cap.remainingPercent <= 0) return UsageAxis.EXHAUSTED
                if (resetAt == null) return UsageAxis.HEALTHY
                val untilReset = Duration.between(now, resetAt)
                val meaningful = cap.remainingPercent >= MEANINGFUL_REMAINING_PERCENT
                when {
                    meaningful && untilReset <= URGENT_HORIZON -> UsageAxis.URGENT
                    meaningful && untilReset <= USE_SOON_HORIZON -> UsageAxis.USE_SOON
                    else -> UsageAxis.HEALTHY
                }
            }
        }
    }

    fun windowStatus(window: UsageWindow, now: Instant): WindowStatus = WindowStatus(
        window = window,
        usage = usageAxis(window, now),
        evidence = FreshnessPolicy.evidenceAxis(window.observedAt, window.effectiveDuration, now),
    )

    /**
     * Capacity-at-risk score: remaining capacity that is about to be lost at reset.
     * Used only for the "Suggested next" summary line — card order itself stays stable.
     */
    fun capacityAtRisk(window: UsageWindow, now: Instant): Double {
        val cap = window.capacity as? CapacityState.Known ?: return 0.0
        val resetAt = window.resetAt ?: return 0.0
        if (resetAt.isBefore(now) || cap.remainingPercent <= 0) return 0.0
        val hoursLeft = Duration.between(now, resetAt).toMinutes() / 60.0
        val urgency = when {
            hoursLeft <= 6 -> 1.0
            hoursLeft <= 24 -> 0.6
            hoursLeft <= 72 -> 0.3
            else -> 0.1
        }
        return cap.remainingPercent * urgency
    }

    /** The single most actionable window across accounts, if any stands out. */
    fun suggestedNext(windows: List<UsageWindow>, now: Instant): UsageWindow? =
        windows
            .filter { capacityAtRisk(it, now) > 0.0 }
            .maxByOrNull { capacityAtRisk(it, now) }
            ?.takeIf { capacityAtRisk(it, now) >= MEANINGFUL_REMAINING_PERCENT * 0.3 }
}
