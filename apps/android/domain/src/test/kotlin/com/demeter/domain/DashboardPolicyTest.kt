package com.demeter.domain

import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.ConfirmationState
import com.demeter.domain.model.EvidenceAxis
import com.demeter.domain.model.DashboardPolicy
import com.demeter.domain.model.FreshnessPolicy
import com.demeter.domain.model.SourceType
import com.demeter.domain.model.UsageAxis
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.model.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DashboardPolicyTest {

    private val now: Instant = Instant.parse("2026-07-12T12:00:00Z")

    private fun window(
        id: String = "w",
        capacity: CapacityState,
        resetIn: Duration? = Duration.ofHours(12),
        observedAgo: Duration = Duration.ofMinutes(5),
        duration: Duration? = Duration.ofHours(5),
    ) = UsageWindow(
        id = id, accountId = "a", label = "L", kind = WindowKind.SESSION,
        capacity = capacity, resetAt = resetIn?.let { now.plus(it) },
        windowDuration = duration, observedAt = now.minus(observedAgo),
        source = SourceType.MANUAL, confirmation = ConfirmationState.USER_EDITED,
    )

    @Test
    fun `usage axis reflects capacity at risk semantics`() {
        // 60% remaining, resets in 3h → about to lose it → URGENT
        assertEquals(UsageAxis.URGENT, DashboardPolicy.usageAxis(window(capacity = CapacityState.Known(60), resetIn = Duration.ofHours(3)), now))
        // 60% remaining, resets in 20h → USE_SOON
        assertEquals(UsageAxis.USE_SOON, DashboardPolicy.usageAxis(window(capacity = CapacityState.Known(60), resetIn = Duration.ofHours(20)), now))
        // 60% remaining, resets in 3 days → HEALTHY
        assertEquals(UsageAxis.HEALTHY, DashboardPolicy.usageAxis(window(capacity = CapacityState.Known(60), resetIn = Duration.ofHours(72)), now))
        // 5% remaining, resets in 3h → nothing meaningful to lose → HEALTHY
        assertEquals(UsageAxis.HEALTHY, DashboardPolicy.usageAxis(window(capacity = CapacityState.Known(5), resetIn = Duration.ofHours(3)), now))
        assertEquals(UsageAxis.EXHAUSTED, DashboardPolicy.usageAxis(window(capacity = CapacityState.Exhausted), now))
        assertEquals(UsageAxis.EXHAUSTED, DashboardPolicy.usageAxis(window(capacity = CapacityState.Known(0)), now))
        assertEquals(UsageAxis.UNKNOWN, DashboardPolicy.usageAxis(window(capacity = CapacityState.UnknownLimit()), now))
        assertEquals(UsageAxis.RESET_PASSED, DashboardPolicy.usageAxis(window(capacity = CapacityState.Known(40), resetIn = Duration.ofHours(-1)), now))
    }

    @Test
    fun `freshness ttl follows min-max-quarter rule`() {
        assertEquals(Duration.ofMinutes(75), FreshnessPolicy.ttl(Duration.ofHours(5)))
        assertEquals(Duration.ofHours(6), FreshnessPolicy.ttl(Duration.ofDays(7)))
        assertEquals(Duration.ofMinutes(30), FreshnessPolicy.ttl(Duration.ofHours(1)))
        assertEquals(Duration.ofHours(2), FreshnessPolicy.ttl(null))
    }

    @Test
    fun `evidence axis buckets by ttl multiples`() {
        val w5h = { ago: Duration -> window(capacity = CapacityState.Known(50), observedAgo = ago) }
        assertEquals(EvidenceAxis.CURRENT, FreshnessPolicy.evidenceAxis(w5h(Duration.ofMinutes(60)).observedAt, Duration.ofHours(5), now))
        assertEquals(EvidenceAxis.AGING, FreshnessPolicy.evidenceAxis(w5h(Duration.ofMinutes(120)).observedAt, Duration.ofHours(5), now))
        assertEquals(EvidenceAxis.STALE, FreshnessPolicy.evidenceAxis(w5h(Duration.ofHours(8)).observedAt, Duration.ofHours(5), now))
    }

    @Test
    fun `suggested next picks highest capacity at risk and ignores unknown or exhausted`() {
        val urgent = window(id = "urgent", capacity = CapacityState.Known(70), resetIn = Duration.ofHours(2))
        val healthy = window(id = "healthy", capacity = CapacityState.Known(80), resetIn = Duration.ofDays(5))
        val unknown = window(id = "unknown", capacity = CapacityState.UnknownLimit())
        val exhausted = window(id = "gone", capacity = CapacityState.Exhausted)
        assertEquals("urgent", DashboardPolicy.suggestedNext(listOf(healthy, urgent, unknown, exhausted), now)?.id)
    }

    @Test
    fun `suggested next is null when nothing stands out`() {
        val quiet = window(capacity = CapacityState.Known(10), resetIn = Duration.ofDays(6))
        assertNull(DashboardPolicy.suggestedNext(listOf(quiet), now))
    }
}
