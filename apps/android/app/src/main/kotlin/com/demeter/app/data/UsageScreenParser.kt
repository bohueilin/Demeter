package com.demeter.app.data

import com.demeter.app.platform.OcrLine
import com.demeter.domain.model.WindowKind
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Parses a provider "usage" screen screenshot (e.g. Claude's Usage page) into one or more
 * windows using OCR line positions. Each window is a row: a label on the left paired with the
 * "N% used" on the same row, plus the reset line just beneath it. The status bar (clock + app
 * icons) and section headers are ignored. Everything it produces is a suggestion the user
 * confirms — nothing is force-matched.
 */
object UsageScreenParser {

    data class ParsedWindow(
        val label: String,
        val kind: WindowKind,
        val usedPercent: Int?,
        val remainingPercent: Int?,
        val resetAt: Instant?,
        val resetText: String?,
        val exhausted: Boolean,
    )

    private val PCT = Regex("""(\d{1,3})\s*%""")
    private val USED_HINT = Regex("""%\s*u""", RegexOption.IGNORE_CASE)
    private val REMAINING_HINT = Regex("""%\s*(r|l)""", RegexOption.IGNORE_CASE)
    private val RESET_REL = Regex(
        """in\s+(?:(\d+)\s*(?:days?|d)\b)?\s*(?:(\d+)\s*(?:hours?|hrs?|h)\b)?\s*(?:(\d+)\s*(?:minutes?|mins?|min|m)\b)?""",
        RegexOption.IGNORE_CASE,
    )
    private val TIME = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE)
    private val STATUS_BAR = Regex("""^\s*\d{1,2}:\d{2}\b""")
    private val PLAN_TIER = Regex("""\(\d+x\)""") // "Max (20x)", "Max (5x)"
    private val MD_DATE = Regex("""\b(\d{1,2})/(\d{1,2})\b""") // "8/11", "8/12" (ChatGPT "Expires 8/11")
    private val SHORT_DATE = java.time.format.DateTimeFormatter.ofPattern("MMM d")
    // "Jul 21, 2026 3:50 PM" — month name, day, optional year (ChatGPT weekly-limit reset).
    private val MONTH_NAME = Regex(
        """\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(\d{1,2})(?:,?\s*(\d{4}))?""",
        RegexOption.IGNORE_CASE,
    )
    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private val WEEKDAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
    )

    fun parse(
        lines: List<OcrLine>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ParsedWindow> {
        if (lines.isEmpty()) return emptyList()

        // 1) Drop the status bar (leading clock line), the screen title, section headers, blanks.
        val content = lines.filterNot { l ->
            val t = l.text.trim()
            STATUS_BAR.containsMatchIn(t) ||
                t.equals("Usage", ignoreCase = true) ||
                t.equals("Weekly limits", ignoreCase = true) ||
                t.equals("Weekly limit", ignoreCase = true) ||
                isChrome(t) ||
                t.isBlank()
        }

        // 2) Percentage lines anchor each window.
        val pctLines = content.filter { PCT.containsMatchIn(it.text) }.sortedBy { it.cy }
        if (pctLines.isEmpty()) return emptyList()

        val avgH = content.map { it.height }.filter { it > 0f }.average()
            .toFloat().takeIf { it.isFinite() && it > 0f } ?: 40f

        val result = mutableListOf<ParsedWindow>()
        pctLines.forEachIndexed { idx, pct ->
            val pctMatch = PCT.find(pct.text) ?: return@forEachIndexed
            val pctVal = pctMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
                ?: return@forEachIndexed
            // "used" → complement is remaining; "remaining"/"left" is taken as-is.
            val isUsed = USED_HINT.containsMatchIn(pct.text) || !REMAINING_HINT.containsMatchIn(pct.text)
            val used = if (isUsed) pctVal else 100 - pctVal
            val remaining = 100 - used

            // Label resolution, most reliable first:
            //  1) text on the % line itself before the number ("Weekly usage limit  100% remaining")
            //  2) nearest same-row line to the left ("Current session" | "84% used")
            //  3) nearest sensible line just above the % line
            // A real usage label has actual words. Requiring 3+ letters rejects OCR junk from
            // browser chrome / icons (e.g. ":D" → "D)") that would otherwise spawn a phantom window.
            fun usableLabel(t: String): String? =
                t.trim().takeIf {
                    it.count(Char::isLetter) >= 3 && !isResetLine(it) && !isUnusedLine(it) &&
                        !isHeaderLine(it) && !isChrome(it)
                }

            val inlineLabel = usableLabel(pct.text.substring(0, pctMatch.range.first))
            val candidates = content.filter {
                it !== pct && !PCT.containsMatchIn(it.text) && usableLabel(it.text) != null
            }
            val sameRowLabel = candidates
                .filter { abs(it.cy - pct.cy) < avgH * 1.1f }
                .minByOrNull { abs(it.cy - pct.cy) + if (it.cx < pct.cx) 0f else 1000f }
                ?.let { usableLabel(it.text) }
            val aboveLabel = candidates
                .filter { it.cy < pct.cy && pct.cy - it.cy < avgH * 2.5f }
                .maxByOrNull { it.cy }
                ?.let { usableLabel(it.text) }
            // No trustworthy label near this percentage → it's noise, not a window. Skip it.
            val label = inlineLabel ?: sameRowLabel ?: aboveLabel ?: return@forEachIndexed

            // Reset = the reset/"unused" line beneath this row but above the next window's row.
            val nextCy = pctLines.getOrNull(idx + 1)?.cy ?: Float.MAX_VALUE
            val resetLine = content
                .filter { it.cy > pct.cy - avgH * 0.4f && it.cy < nextCy - avgH * 0.4f }
                .filter { isResetLine(it.text) || isUnusedLine(it.text) }
                .minByOrNull { it.cy }
            val resetText = resetLine?.text?.trim()
            val resetAt = resetText?.let { parseReset(it, now, zone) }

            result += ParsedWindow(
                label = label,
                kind = kindFor(label),
                usedPercent = used,
                remainingPercent = remaining,
                resetAt = resetAt,
                resetText = resetText,
                exhausted = used >= 100,
            )
        }
        return result + resetWindows(content, now, zone)
    }

    /**
     * ChatGPT's Usage screen has a separate "Usage limit resets" list — several "Full reset ·
     * Expires M/D" entries. These are ADDITIONAL to the weekly usage limit (extra capacity that
     * expires on a date), so each becomes its own reset-checkpoint window: no percentage, just
     * a reset/expiry date to remind against. They are kept separate from the weekly limit — not
     * folded into it.
     */
    private fun resetWindows(
        content: List<OcrLine>,
        now: Instant,
        zone: ZoneId,
    ): List<ParsedWindow> {
        val dates = content
            .filter { (isResetLine(it.text) || it.text.contains("expire", ignoreCase = true)) && MD_DATE.containsMatchIn(it.text) }
            .mapNotNull { parseMonthDay(it.text, now, zone) }
            .distinct()
            .sorted()
        return dates.map { d ->
            val day = SHORT_DATE.format(d.atZone(zone).toLocalDate())
            ParsedWindow(
                label = "Full reset · $day",
                kind = WindowKind.CUSTOM,
                usedPercent = null,
                remainingPercent = null,
                resetAt = d,
                resetText = "Expires $day",
                exhausted = false,
            )
        }
    }

    /** "Expires 8/11" → that calendar day (this year, or next year if already past). */
    private fun parseMonthDay(text: String, now: Instant, zone: ZoneId): Instant? {
        val m = MD_DATE.find(text) ?: return null
        val month = m.groupValues[1].toIntOrNull() ?: return null
        val day = m.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val year = now.atZone(zone).year
        var date = try {
            java.time.LocalDate.of(year, month, day)
        } catch (e: Exception) {
            return null
        }
        if (date.atStartOfDay(zone).toInstant().isBefore(now.minus(Duration.ofDays(1)))) {
            date = date.plusYears(1)
        }
        return date.atStartOfDay(zone).toInstant()
    }

    /**
     * Parses pasted TEXT (e.g. copied from Claude's settings page) into windows. Unlike a
     * screenshot there are no positions — the text is linear, one item per line, in the order
     * label → reset → "N% used". A small state machine collects a label and its reset, then
     * emits a window when the "% used" line closes the block. Section headers, plan tier
     * ("Max (20x)"), and "learn more" links are ignored.
     */
    fun parseText(
        text: String,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ParsedWindow> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val result = mutableListOf<ParsedWindow>()
        var label: String? = null
        var reset: String? = null

        for (line in lines) {
            when {
                PCT.containsMatchIn(line) -> {
                    val pctVal = PCT.find(line)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
                    if (pctVal != null && label != null) {
                        val isUsed = USED_HINT.containsMatchIn(line) || !REMAINING_HINT.containsMatchIn(line)
                        val used = if (isUsed) pctVal else 100 - pctVal
                        result += ParsedWindow(
                            label = label,
                            kind = kindFor(label),
                            usedPercent = used,
                            remainingPercent = 100 - used,
                            resetAt = reset?.let { parseReset(it, now, zone) },
                            resetText = reset,
                            exhausted = used >= 100,
                        )
                    }
                    label = null
                    reset = null
                }
                isResetLine(line) || isUnusedLine(line) || isStartsLine(line) -> reset = line
                isHeaderLine(line) -> Unit // ignore section headers / links / plan tier
                else -> label = line
            }
        }
        return result
    }

    private fun isStartsLine(t: String) = t.contains("starts when", ignoreCase = true)

    private fun isHeaderLine(t: String): Boolean {
        val l = t.lowercase()
        return l.contains("learn more") ||
            PLAN_TIER.containsMatchIn(t) ||
            l.endsWith("limits") // "Plan usage limits", "Weekly limits" (plural header, not "limit")
    }

    /** Browser chrome / URLs that can leak into a screenshot but are never usage content. */
    private fun isChrome(t: String): Boolean {
        val l = t.lowercase()
        return l.contains("://") || l.contains(".com") || l.contains("/#") || l.startsWith("http")
    }

    private fun isResetLine(t: String) = t.contains("reset", ignoreCase = true)

    private fun isUnusedLine(t: String) =
        t.contains("haven't used", ignoreCase = true) || t.contains("havent used", ignoreCase = true)

    private fun kindFor(label: String): WindowKind = when {
        label.contains("session", ignoreCase = true) -> WindowKind.SESSION
        label.contains("week", ignoreCase = true) -> WindowKind.WEEKLY
        label.contains("month", ignoreCase = true) -> WindowKind.MONTHLY
        label.contains("credit", ignoreCase = true) -> WindowKind.CREDITS
        // "All models" / model-specific rows live under Weekly limits on Claude.
        else -> WindowKind.WEEKLY
    }

    private fun parseReset(text: String, now: Instant, zone: ZoneId): Instant? {
        // Relative: "Resets in 58 min", "in 3 hours 40 minutes".
        if (text.contains("in ", ignoreCase = true)) {
            RESET_REL.find(text)?.let { m ->
                val d = m.groupValues[1].toLongOrNull() ?: 0
                val h = m.groupValues[2].toLongOrNull() ?: 0
                val mi = m.groupValues[3].toLongOrNull() ?: 0
                val dur = Duration.ofDays(d).plusHours(h).plusMinutes(mi)
                if (!dur.isZero) return now.plus(dur)
            }
        }
        // Absolute with a month name: "Resets Jul 21, 2026 3:50 PM".
        MONTH_NAME.find(text)?.let { mn ->
            val month = MONTHS[mn.groupValues[1].lowercase().take(3)]
            val day = mn.groupValues[2].toIntOrNull()
            if (month != null && day != null && day in 1..31) {
                val explicitYear = mn.groupValues[3].toIntOrNull()
                val (h, min) = clockOf(text)
                val year = explicitYear ?: now.atZone(zone).year
                runCatching {
                    var zdt = java.time.LocalDate.of(year, month, day).atTime(h, min).atZone(zone)
                    if (explicitYear == null && !zdt.toInstant().isAfter(now)) zdt = zdt.plusYears(1)
                    zdt.toInstant()
                }.getOrNull()?.let { return it }
            }
        }
        // Absolute: "Resets Tue 10:59 AM" (weekday + time) or just a time.
        val time = TIME.find(text) ?: return null
        var hour = time.groupValues[1].toIntOrNull() ?: return null
        val minute = time.groupValues[2].toIntOrNull() ?: return null
        when (time.groupValues[3].uppercase()) {
            "PM" -> if (hour < 12) hour += 12
            "AM" -> if (hour == 12) hour = 0
        }
        val targetTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val dow = weekdayOf(text)
        val nowZdt = now.atZone(zone)
        var date = nowZdt.toLocalDate()
        if (dow != null) {
            while (date.dayOfWeek != dow) date = date.plusDays(1)
        }
        var target = date.atTime(targetTime).atZone(zone)
        if (!target.toInstant().isAfter(now)) {
            target = target.plusDays(if (dow != null) 7 else 1)
        }
        return target.toInstant()
    }

    private fun weekdayOf(text: String): DayOfWeek? {
        val lower = text.lowercase()
        return WEEKDAYS.entries.firstOrNull { lower.contains(it.key) }?.value
    }

    /** Extract a 24h (hour, minute) from a "3:50 PM" / "10:59 AM" style time, else midnight. */
    private fun clockOf(text: String): Pair<Int, Int> {
        val time = TIME.find(text) ?: return 0 to 0
        var hour = time.groupValues[1].toIntOrNull() ?: 0
        val minute = time.groupValues[2].toIntOrNull() ?: 0
        when (time.groupValues[3].uppercase()) {
            "PM" -> if (hour < 12) hour += 12
            "AM" -> if (hour == 12) hour = 0
        }
        return hour.coerceIn(0, 23) to minute.coerceIn(0, 59)
    }
}
