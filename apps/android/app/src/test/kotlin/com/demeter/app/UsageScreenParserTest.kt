package com.demeter.app

import com.demeter.app.data.UsageScreenParser
import com.demeter.app.platform.OcrLine
import com.demeter.domain.model.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Replays the exact OCR layout of the Claude "Usage" screen the user imported:
 * a two-column layout where each row's label (left) pairs with a "% used" (right),
 * a reset line beneath, and a status bar / section header that must be ignored.
 */
class UsageScreenParserTest {

    private fun line(text: String, cx: Int, cy: Int) =
        OcrLine(text, left = cx - 60, top = cy - 20, right = cx + 60, bottom = cy + 20)

    private val screen = listOf(
        line("6:01 W X", 200, 65),               // status bar — ignored
        line("Usage", 450, 197),                 // title — ignored
        line("Current session", 220, 345),
        line("84% used", 780, 345),
        line("Resets in 58 min", 160, 434),
        line("Weekly limits", 150, 531),         // section header — ignored
        line("All models", 180, 628),
        line("24% used", 780, 628),
        line("Resets Tue 10:59 AM", 200, 717),
        line("Fable only", 150, 830),
        line("0% used", 780, 830),
        line("You haven't used Fable yet", 240, 920),
    )

    private val now = Instant.parse("2026-07-13T18:00:00Z")
    private val zone = ZoneId.of("UTC")

    @Test
    fun detectsThreeWindows() {
        assertEquals(3, UsageScreenParser.parse(screen, now, zone).size)
    }

    @Test
    fun currentSessionParsedAsUsedWithRelativeReset() {
        val w = UsageScreenParser.parse(screen, now, zone)[0]
        assertEquals("Current session", w.label)
        assertEquals(WindowKind.SESSION, w.kind)
        assertEquals(84, w.usedPercent)
        assertEquals(16, w.remainingPercent)      // "% used" → remaining is the complement
        assertNotNull(w.resetAt)                  // "in 58 min" resolves to a time
    }

    @Test
    fun allModelsParsedWithWeekdayReset() {
        val w = UsageScreenParser.parse(screen, now, zone)[1]
        assertEquals("All models", w.label)
        assertEquals(WindowKind.WEEKLY, w.kind)
        assertEquals(24, w.usedPercent)
        assertEquals(76, w.remainingPercent)
        assertNotNull(w.resetAt)                  // "Tue 10:59 AM" resolves to next Tuesday
    }

    @Test
    fun fableOnlyIsZeroUsedWithNoReset() {
        val w = UsageScreenParser.parse(screen, now, zone)[2]
        assertEquals("Fable only", w.label)
        assertEquals(0, w.usedPercent)
        assertEquals(100, w.remainingPercent)     // 0% used → fully available
        assertNull(w.resetAt)                     // "You haven't used ... yet" has no time
    }

    // ChatGPT screen where the weekly limit shows its OWN reset ("Resets Jul 21, 2026 3:50 PM").
    private val chatgptWithWeeklyReset = listOf(
        line("11:01", 200, 65),
        line("Usage Limits", 200, 810),
        line("Weekly usage limit", 250, 1140),
        line("99% remaining", 760, 1140),
        line("Resets Jul 21, 2026 3:50 PM", 260, 1226),
        line("Usage limit resets", 220, 1350),
        line("Full reset", 130, 1450),
        line("Expires 8/11", 150, 1491),
        line("Full reset", 130, 1567),
        line("Expires 8/12", 150, 1628),
    )

    // Gemini "Usage limits" screen: "Current usage 11% used / Resets at 11:24 PM" and
    // "Weekly limit 9% used / Resets July 26 at 5:24 PM". Note the singular "Weekly limit" label.
    private val gemini = listOf(
        line("10:03", 150, 65),
        line("Usage limits", 200, 300),      // header (ends in "limits") — dropped
        line("PRO", 430, 300),
        line("Updated just now", 180, 520),
        line("Current usage", 200, 620),
        line("11% used", 780, 620),
        line("Resets at 11:24 PM", 190, 700),
        line("Weekly limit", 150, 810),
        line("9% used", 780, 825),
        line("Resets July 26 at 5:24 PM", 215, 875),
        line("Get 5x more usage with AI Ultra", 280, 990),
        line("$99.99/month", 145, 1035),
    )

    @Test
    fun parsesGeminiUsageScreen() {
        val windows = UsageScreenParser.parse(gemini, now, zone)
        assertEquals(2, windows.size)

        val current = windows.first { it.label.contains("current", ignoreCase = true) }
        assertEquals(WindowKind.SESSION, current.kind)
        assertEquals(11, current.usedPercent)
        assertEquals(89, current.remainingPercent)
        assertNotNull(current.resetAt) // "at 11:24 PM" today

        val weekly = windows.first { it.label.contains("weekly", ignoreCase = true) }
        assertEquals(WindowKind.WEEKLY, weekly.kind)
        assertEquals(9, weekly.usedPercent)
        assertEquals(91, weekly.remainingPercent)
        assertEquals(Instant.parse("2026-07-26T17:24:00Z"), weekly.resetAt) // "July 26 at 5:24 PM"
    }

    @Test
    fun ignoresStrayPercentageWithNoRealLabel() {
        // Browser chrome noise: a ":D" tab icon misread as "D)" and a stray "2%" nearby.
        val noisy = listOf(
            line("D)", 800, 235),
            line("2%", 860, 235),
        ) + chatgptWithWeeklyReset
        val windows = UsageScreenParser.parse(noisy, now, zone)
        // No phantom "D)" window — still just the weekly limit + 2 reset checkpoints.
        assertEquals(3, windows.size)
        assert(windows.none { it.label.contains("D)") }) { "labels: ${windows.map { it.label }}" }
        assert(windows[0].label.startsWith("Weekly usage limit"))
    }

    @Test
    fun chatgptWeeklyLimitGetsItsOwnMonthNameReset() {
        val windows = UsageScreenParser.parse(chatgptWithWeeklyReset, now, zone)
        assertEquals(3, windows.size)

        val weekly = windows[0]
        assert(weekly.label.startsWith("Weekly usage limit")) { "label was: ${weekly.label}" }
        assertEquals(99, weekly.remainingPercent)
        // "Jul 21, 2026 3:50 PM" resolves precisely.
        assertEquals(Instant.parse("2026-07-21T15:50:00Z"), weekly.resetAt)

        // The two "Full reset" checkpoints stay separate, no capacity, after the weekly reset.
        val resets = windows.drop(1)
        assertEquals(2, resets.size)
        resets.forEach { assertNull(it.remainingPercent); assertNotNull(it.resetAt) }
        assert(weekly.resetAt!!.isBefore(resets[0].resetAt!!))
    }

    // --- Pasted text (copied from Claude's settings page) ---

    private val pasted = """
        Plan usage limits
        Max (20x)
        Current session
        Starts when a message is sent
        0% used
        Weekly limits
        Learn more about usage limits
        All models
        Resets Thu 4:59 PM
        51% used
        Fable
        Resets Thu 4:59 PM
        92% used
    """.trimIndent()

    // --- ChatGPT usage screen: one "Weekly usage limit % remaining" + a resets list ---

    private val chatgpt = listOf(
        line("7:23 X", 200, 65),                  // status bar — ignored
        line("Usage Limits", 200, 810),           // section title
        line("Track usage within plan limits", 250, 855),
        line("Usage", 120, 990),                  // title — ignored
        line("Weekly usage limit", 250, 1165),
        line("100% remaining", 760, 1165),        // "% remaining", not "used"
        line("Usage limit resets", 220, 1335),
        line("Full reset", 130, 1435),
        line("Expires 8/11", 150, 1476),
        line("Full reset", 130, 1552),
        line("Expires 8/12", 150, 1613),
    )

    // Real OCR often merges the label and value into ONE line for a wide row.
    private val chatgptCombined = listOf(
        line("7:23 X", 200, 65),
        line("Usage Limits", 200, 810),
        line("Weekly usage limit 100% remaining", 450, 1165),   // label + value on one line
        line("Usage limit resets", 220, 1335),
        line("Full reset", 130, 1435),
        line("Expires 8/11", 150, 1476),
        line("Full reset", 130, 1552),
        line("Expires 8/12", 150, 1613),
    )

    @Test
    fun chatgptCombinedLineStillCapturesWeeklyLimit() {
        val windows = UsageScreenParser.parse(chatgptCombined, now, zone)
        // Weekly limit + two reset checkpoints, kept separate.
        assertEquals(3, windows.size)
        assert(windows[0].label.startsWith("Weekly usage limit")) { "label was: ${windows[0].label}" }
        assertEquals(100, windows[0].remainingPercent)
        windows.drop(1).forEach {
            assertNull(it.remainingPercent)          // reset checkpoints carry no percentage
            assertNotNull(it.resetAt)
        }
    }

    @Test
    fun chatgptSeparatesWeeklyLimitFromResetCheckpoints() {
        val windows = UsageScreenParser.parse(chatgpt, now, zone)
        // 1 weekly limit (capacity) + 2 reset checkpoints (dates) — NOT folded together.
        assertEquals(3, windows.size)

        val weekly = windows[0]
        assert(weekly.label.startsWith("Weekly usage limit")) { "label was: ${weekly.label}" }
        assertEquals(0, weekly.usedPercent)          // "100% remaining" → 0% used
        assertEquals(100, weekly.remainingPercent)

        val resets = windows.drop(1)
        assertEquals(2, resets.size)
        resets.forEach {
            assertNull(it.remainingPercent)          // a reset is a date, not a capacity
            assertNotNull(it.resetAt)
            assert(it.label.contains("reset", ignoreCase = true)) { "label was: ${it.label}" }
        }
        assert(resets[0].resetAt!!.isBefore(resets[1].resetAt!!)) // 8/11 before 8/12
    }

    @Test
    fun parsesPastedTextIntoThreeWindows() {
        val windows = UsageScreenParser.parseText(pasted, now, zone)
        assertEquals(3, windows.size)

        assertEquals("Current session", windows[0].label)
        assertEquals(WindowKind.SESSION, windows[0].kind)
        assertEquals(0, windows[0].usedPercent)
        assertEquals(100, windows[0].remainingPercent)
        assertNull(windows[0].resetAt)               // "Starts when a message is sent" — no time

        assertEquals("All models", windows[1].label)
        assertEquals(51, windows[1].usedPercent)
        assertEquals(49, windows[1].remainingPercent)
        assertNotNull(windows[1].resetAt)            // "Thu 4:59 PM"

        assertEquals("Fable", windows[2].label)
        assertEquals(92, windows[2].usedPercent)
        assertEquals(8, windows[2].remainingPercent)
        assertNotNull(windows[2].resetAt)
    }
}
