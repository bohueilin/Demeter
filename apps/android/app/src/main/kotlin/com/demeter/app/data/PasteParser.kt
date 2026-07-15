package com.demeter.app.data

import com.demeter.domain.model.WindowKind
import java.time.Duration
import java.time.Instant

/**
 * Assistive autofill for pasted usage text. Best-effort by design: anything it cannot
 * read stays blank for the user to fill in, and every recognized value remains editable.
 * It never force-matches.
 */
object PasteParser {

    data class ParseResult(
        val remainingPercent: Int?,
        val usedPercent: Int?,
        val resetIn: Duration?,
        val kind: WindowKind?,
        val recognized: List<String>,
    ) {
        val effectiveRemaining: Int?
            get() = remainingPercent ?: usedPercent?.let { (100 - it).coerceIn(0, 100) }
    }

    private val remainingRegex = Regex("""(\d{1,3})\s?%\s*(remaining|left)""", RegexOption.IGNORE_CASE)
    private val usedRegex = Regex("""(\d{1,3})\s?%\s*(used|consumed)?""", RegexOption.IGNORE_CASE)
    private val resetInRegex = Regex(
        """resets?\s+in\s+(?:(\d+)\s*(?:days?|d))?\s*(?:(\d+)\s*(?:hours?|hrs?|h))?\s*(?:(\d+)\s*(?:minutes?|mins?|m))?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String, now: Instant = Instant.now()): ParseResult {
        val recognized = mutableListOf<String>()

        val remaining = remainingRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
        if (remaining != null) recognized += "$remaining% remaining"

        val used = if (remaining == null) {
            usedRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
                ?.also { recognized += "$it% used" }
        } else {
            null
        }

        val resetIn = resetInRegex.find(text)?.let { m ->
            val days = m.groupValues[1].toLongOrNull() ?: 0
            val hours = m.groupValues[2].toLongOrNull() ?: 0
            val minutes = m.groupValues[3].toLongOrNull() ?: 0
            val d = Duration.ofDays(days).plusHours(hours).plusMinutes(minutes)
            if (d.isZero) null else d.also { recognized += "resets in ${format(it)}" }
        }

        val kind = when {
            text.contains("week", ignoreCase = true) -> WindowKind.WEEKLY
            text.contains("session", ignoreCase = true) || text.contains(Regex("""5[\s-]?hour""", RegexOption.IGNORE_CASE)) -> WindowKind.SESSION
            text.contains("month", ignoreCase = true) -> WindowKind.MONTHLY
            text.contains("credit", ignoreCase = true) -> WindowKind.CREDITS
            else -> null
        }
        if (kind != null) recognized += kind.displayLabel.lowercase() + " window"

        return ParseResult(remaining, used, resetIn, kind, recognized)
    }

    private fun format(d: Duration): String {
        val days = d.toDays()
        val hours = d.toHours() % 24
        val minutes = d.toMinutes() % 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m")
        }.trim()
    }
}
