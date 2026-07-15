package com.demeter.domain.reminder

import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.FreshnessPolicy
import com.demeter.domain.model.UsageWindow
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure, clock-injected reminder policy. Decides what SHOULD be scheduled and,
 * independently, whether a fired trigger should actually deliver. Platform
 * scheduling state is always derived from these decisions and repairable.
 */
object ReminderPolicy {

    private val QUIET_FLOOR_BEFORE_RESET: Duration = Duration.ofMinutes(15)

    fun logicalId(accountId: String, windowId: String, resetAt: Instant, leadMinutes: Int): String =
        "demeter:$accountId:$windowId:${resetAt.epochSecond}:$leadMinutes"

    /**
     * The full set of triggers that should exist right now for one rule + window.
     * Deterministic: same inputs, same logical IDs. A moved reset time mints new
     * logical IDs, which naturally orphans (cancels) the old schedule.
     */
    fun desiredTriggers(
        rule: ReminderRule,
        window: UsageWindow,
        now: Instant,
        zone: ZoneId,
    ): List<PlannedTrigger> {
        if (!rule.enabled) return emptyList()
        val resetAt = window.resetAt ?: return emptyList()
        if (!resetAt.isAfter(now)) return emptyList()

        return rule.leadMinutes.mapNotNull { lead ->
            val nominal = resetAt.minus(Duration.ofMinutes(lead.toLong()))
            if (!nominal.isAfter(now)) return@mapNotNull null
            val (effective, shifted) = applyQuietHours(nominal, resetAt, rule, zone)
            if (!effective.isAfter(now)) return@mapNotNull null
            PlannedTrigger(
                logicalId = logicalId(rule.accountId, rule.windowId, resetAt, lead),
                ruleId = rule.id,
                accountId = rule.accountId,
                windowId = rule.windowId,
                leadMinutes = lead,
                triggerAt = effective,
                resetAt = resetAt,
                shiftedFrom = if (shifted) nominal else null,
            )
        }
    }

    /**
     * Quiet-hours shift: a trigger inside the quiet window moves to the quiet end,
     * but never to (or past) the 15-minutes-before-reset floor. If shifting would
     * cross the floor, the trigger fires at the floor instead.
     */
    private fun applyQuietHours(
        nominal: Instant,
        resetAt: Instant,
        rule: ReminderRule,
        zone: ZoneId,
    ): Pair<Instant, Boolean> {
        val start = rule.quietStartMinuteOfDay ?: return nominal to false
        val end = rule.quietEndMinuteOfDay ?: return nominal to false
        val local = nominal.atZone(zone)
        val minuteOfDay = local.toLocalTime().toSecondOfDay() / 60
        val inQuiet = if (start <= end) {
            minuteOfDay in start until end
        } else { // window spans midnight, e.g. 22:00–07:00
            minuteOfDay >= start || minuteOfDay < end
        }
        if (!inQuiet) return nominal to false

        val endToday = local.toLocalDate().atTime(LocalTime.ofSecondOfDay(end * 60L)).atZone(zone)
        val quietEnd = if (endToday.toInstant().isAfter(nominal)) endToday else endToday.plusDays(1)
        val floor = resetAt.minus(QUIET_FLOOR_BEFORE_RESET)
        val target = quietEnd.toInstant()
        return when {
            target.isBefore(floor) -> target to true
            floor.isAfter(nominal) -> floor to true
            else -> nominal to false // floor already behind us; keep the nominal time
        }
    }

    /**
     * Delivery-time re-evaluation, run by the alarm receiver against CURRENT
     * persisted evidence. Scheduling staleness can only suppress, never wrongly fire.
     */
    fun evaluateAtDelivery(
        rule: ReminderRule,
        window: UsageWindow,
        expectedResetAt: Instant,
        now: Instant,
    ): DeliveryDecision {
        if (!rule.enabled) {
            return DeliveryDecision.Suppress("rule_disabled", "This reminder was turned off before delivery.")
        }
        val resetAt = window.resetAt
        if (resetAt == null || resetAt != expectedResetAt) {
            return DeliveryDecision.Suppress(
                "reset_changed",
                "The reset time changed after this reminder was scheduled.",
            )
        }
        if (!resetAt.isAfter(now)) {
            return DeliveryDecision.Suppress("reset_passed", "The reset already happened.")
        }

        return when (val cap = window.capacity) {
            is CapacityState.Exhausted ->
                DeliveryDecision.Suppress("exhausted", "No usable capacity remains for this window.")

            is CapacityState.Known -> {
                if (cap.remainingPercent <= 0) {
                    return DeliveryDecision.Suppress("exhausted", "No usable capacity remains for this window.")
                }
                if (cap.remainingPercent < rule.minRemainingPercent) {
                    return DeliveryDecision.Suppress(
                        "below_threshold",
                        "Remaining ${cap.remainingPercent}% is below your ${rule.minRemainingPercent}% threshold.",
                    )
                }
                val current = FreshnessPolicy.isCurrent(window, now)
                when {
                    current ->
                        DeliveryDecision.Deliver(ReminderIntent.ALLOWANCE, window.observedAt, cap.remainingPercent)

                    rule.evidencePolicy == EvidencePolicy.CURRENT_ONLY ->
                        DeliveryDecision.Suppress(
                            "evidence_too_old",
                            "Your data is too old for a current-data-only reminder. Demeter stayed silent instead of guessing.",
                        )

                    else ->
                        DeliveryDecision.Deliver(ReminderIntent.CHECK_USAGE, window.observedAt, cap.remainingPercent)
                }
            }

            is CapacityState.UnknownLimit ->
                if (rule.remindWhenUnknown) {
                    DeliveryDecision.Deliver(ReminderIntent.CHECK_USAGE, window.observedAt, null)
                } else {
                    DeliveryDecision.Suppress(
                        "remaining_unknown",
                        "Remaining usage is unknown and reset-time reminders are not enabled for this window.",
                    )
                }
        }
    }
}
