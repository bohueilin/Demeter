package com.demeter.domain.model

import java.time.Duration
import java.time.Instant

/** Providers are identified by text compatibility labels only — never logos. */
enum class Provider(val displayLabel: String, val accountNoun: String) {
    OPENAI("ChatGPT", "OpenAI account"),
    ANTHROPIC("Claude", "Anthropic account"),
}

/** Source is a stable fact about where evidence came from — it is not a freshness state. */
enum class SourceType(val displayLabel: String) {
    MANUAL("Entered manually"),
    PASTE("Pasted text"),
    SCREENSHOT("Screenshot"),
    BRIDGE("Bridge"),
}

/**
 * How the user reviewed this evidence. Orthogonal to extraction confidence and to
 * source authority: opening a field never makes OCR more accurate, and a user-edited
 * value becomes confirmed manual evidence rather than higher-confidence extraction.
 */
enum class ConfirmationState {
    UNREVIEWED,
    BULK_CONFIRMED,
    INDIVIDUALLY_CONFIRMED,
    USER_EDITED,
}

enum class WindowKind(val displayLabel: String, val defaultDuration: Duration?) {
    SESSION("Session", Duration.ofHours(5)),
    WEEKLY("Weekly", Duration.ofDays(7)),
    MONTHLY("Monthly", Duration.ofDays(30)),
    CREDITS("Credits", null),
    CUSTOM("Custom", null),
}

/**
 * Remaining capacity as a sealed hierarchy so "limit not exposed" and "0% left"
 * cannot be confused. Null is never used to mean zero.
 */
sealed interface CapacityState {
    /** Provider or user evidence gives a usable remaining percentage. */
    data class Known(val remainingPercent: Int) : CapacityState

    /** Evidence explicitly says no usable capacity remains. */
    data object Exhausted : CapacityState

    /** The provider does not expose a limit; remaining usage is not available. */
    data class UnknownLimit(val note: String? = null) : CapacityState
}

data class MonitoredAccount(
    val id: String,
    val provider: Provider,
    val nickname: String,
    val createdAt: Instant,
)

/**
 * One allowance measured over a period. Multiple windows per account are first-class
 * and are never aggregated into a single number.
 */
data class UsageWindow(
    val id: String,
    val accountId: String,
    val label: String,
    val kind: WindowKind,
    val capacity: CapacityState,
    val resetAt: Instant?,
    val windowDuration: Duration?,
    val observedAt: Instant,
    val source: SourceType,
    val confirmation: ConfirmationState,
) {
    val effectiveDuration: Duration? get() = windowDuration ?: kind.defaultDuration
}
