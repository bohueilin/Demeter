package com.demeter.domain

import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.ConfirmationState
import com.demeter.domain.model.SourceType
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.model.WindowKind
import com.demeter.domain.reminder.DeliveryDecision
import com.demeter.domain.reminder.EvidencePolicy
import com.demeter.domain.reminder.ReminderIntent
import com.demeter.domain.reminder.ReminderPolicy
import com.demeter.domain.reminder.ReminderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ReminderPolicyTest {

    private val zone = ZoneId.of("America/New_York")
    private val now: Instant = Instant.parse("2026-07-12T12:00:00Z")

    private fun rule(
        leads: List<Int> = listOf(240, 60),
        policy: EvidencePolicy = EvidencePolicy.LAST_KNOWN,
        remindWhenUnknown: Boolean = false,
        threshold: Int = 10,
        quietStart: Int? = null,
        quietEnd: Int? = null,
        enabled: Boolean = true,
    ) = ReminderRule(
        id = "rule1", accountId = "acct1", windowId = "win1",
        leadMinutes = leads, evidencePolicy = policy,
        remindWhenUnknown = remindWhenUnknown, minRemainingPercent = threshold,
        quietStartMinuteOfDay = quietStart, quietEndMinuteOfDay = quietEnd,
        enabled = enabled,
    )

    private fun window(
        capacity: CapacityState = CapacityState.Known(60),
        resetAt: Instant? = now.plus(Duration.ofHours(8)),
        observedAt: Instant = now.minus(Duration.ofMinutes(10)),
        duration: Duration? = Duration.ofHours(5),
    ) = UsageWindow(
        id = "win1", accountId = "acct1", label = "Session",
        kind = WindowKind.SESSION, capacity = capacity, resetAt = resetAt,
        windowDuration = duration, observedAt = observedAt,
        source = SourceType.MANUAL, confirmation = ConfirmationState.USER_EDITED,
    )

    @Test
    fun `desired triggers created for each future lead time with deterministic ids`() {
        val triggers = ReminderPolicy.desiredTriggers(rule(), window(), now, zone)
        assertEquals(2, triggers.size)
        val resetEpoch = window().resetAt!!.epochSecond
        assertEquals("demeter:acct1:win1:$resetEpoch:240", triggers[0].logicalId)
        assertEquals(window().resetAt!!.minus(Duration.ofMinutes(240)), triggers[0].triggerAt)
    }

    @Test
    fun `past lead times are skipped`() {
        // reset in 2h: the 4h lead is already in the past, only the 1h lead remains
        val w = window(resetAt = now.plus(Duration.ofHours(2)))
        val triggers = ReminderPolicy.desiredTriggers(rule(), w, now, zone)
        assertEquals(listOf(60), triggers.map { it.leadMinutes })
    }

    @Test
    fun `no triggers when reset unknown or rule disabled`() {
        assertTrue(ReminderPolicy.desiredTriggers(rule(), window(resetAt = null), now, zone).isEmpty())
        assertTrue(ReminderPolicy.desiredTriggers(rule(enabled = false), window(), now, zone).isEmpty())
    }

    @Test
    fun `moved reset time changes logical ids`() {
        val w1 = window()
        val w2 = window(resetAt = now.plus(Duration.ofHours(9)))
        val ids1 = ReminderPolicy.desiredTriggers(rule(), w1, now, zone).map { it.logicalId }.toSet()
        val ids2 = ReminderPolicy.desiredTriggers(rule(), w2, now, zone).map { it.logicalId }.toSet()
        assertTrue(ids1.intersect(ids2).isEmpty())
    }

    @Test
    fun `quiet hours shift trigger to quiet end but never past the floor`() {
        // Trigger would land 03:00 local; quiet hours 22:00-07:00; reset 07:30 local.
        // Quiet end (07:00) is within floor (reset-15m = 07:15), so shift to 07:00.
        val resetLocal = Instant.parse("2026-07-13T11:30:00Z") // 07:30 EDT
        val w = window(resetAt = resetLocal)
        val r = rule(leads = listOf(270), quietStart = 22 * 60, quietEnd = 7 * 60) // 4.5h lead → 03:00 EDT
        val triggers = ReminderPolicy.desiredTriggers(r, w, now, zone)
        assertEquals(1, triggers.size)
        assertEquals(Instant.parse("2026-07-13T11:00:00Z"), triggers[0].triggerAt) // 07:00 EDT
        assertEquals(Instant.parse("2026-07-13T07:00:00Z"), triggers[0].shiftedFrom) // 03:00 EDT
    }

    @Test
    fun `delivery with current evidence is an allowance reminder`() {
        val d = ReminderPolicy.evaluateAtDelivery(rule(), window(), window().resetAt!!, now)
        val deliver = d as DeliveryDecision.Deliver
        assertEquals(ReminderIntent.ALLOWANCE, deliver.intent)
        assertEquals(60, deliver.remainingPercent)
    }

    @Test
    fun `delivery with stale evidence downgrades to check-usage under last-known policy`() {
        val stale = window(observedAt = now.minus(Duration.ofHours(20)))
        val d = ReminderPolicy.evaluateAtDelivery(rule(), stale, stale.resetAt!!, now)
        val deliver = d as DeliveryDecision.Deliver
        assertEquals(ReminderIntent.CHECK_USAGE, deliver.intent)
    }

    @Test
    fun `delivery with stale evidence suppresses under current-only policy`() {
        val stale = window(observedAt = now.minus(Duration.ofHours(20)))
        val d = ReminderPolicy.evaluateAtDelivery(
            rule(policy = EvidencePolicy.CURRENT_ONLY), stale, stale.resetAt!!, now,
        )
        assertEquals("evidence_too_old", (d as DeliveryDecision.Suppress).reasonCode)
    }

    @Test
    fun `exhausted and below-threshold suppress`() {
        val exhausted = window(capacity = CapacityState.Exhausted)
        assertEquals(
            "exhausted",
            (ReminderPolicy.evaluateAtDelivery(rule(), exhausted, exhausted.resetAt!!, now) as DeliveryDecision.Suppress).reasonCode,
        )
        val low = window(capacity = CapacityState.Known(5))
        assertEquals(
            "below_threshold",
            (ReminderPolicy.evaluateAtDelivery(rule(), low, low.resetAt!!, now) as DeliveryDecision.Suppress).reasonCode,
        )
    }

    @Test
    fun `unknown remaining respects the explicit opt-in`() {
        val unknown = window(capacity = CapacityState.UnknownLimit())
        val suppressed = ReminderPolicy.evaluateAtDelivery(rule(), unknown, unknown.resetAt!!, now)
        assertEquals("remaining_unknown", (suppressed as DeliveryDecision.Suppress).reasonCode)

        val opted = ReminderPolicy.evaluateAtDelivery(rule(remindWhenUnknown = true), unknown, unknown.resetAt!!, now)
        val deliver = opted as DeliveryDecision.Deliver
        assertEquals(ReminderIntent.CHECK_USAGE, deliver.intent)
        assertNull(deliver.remainingPercent)
    }

    @Test
    fun `changed or passed reset suppresses at delivery`() {
        val w = window()
        val changed = ReminderPolicy.evaluateAtDelivery(rule(), w, w.resetAt!!.plusSeconds(600), now)
        assertEquals("reset_changed", (changed as DeliveryDecision.Suppress).reasonCode)

        val passed = window(resetAt = now.minusSeconds(60))
        val d = ReminderPolicy.evaluateAtDelivery(rule(), passed, passed.resetAt!!, now)
        assertEquals("reset_passed", (d as DeliveryDecision.Suppress).reasonCode)
    }
}
