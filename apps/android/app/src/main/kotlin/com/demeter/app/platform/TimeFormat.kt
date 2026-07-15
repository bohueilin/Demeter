package com.demeter.app.platform

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormat {

    private val DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val DATE_ONLY = DateTimeFormatter.ofPattern("MMM d, yyyy")

    /** Absolute reset, e.g. "Jul 21, 2026 3:50 PM" (or date-only when there's no meaningful time). */
    fun resetLabel(target: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val zdt = target.atZone(zone)
        val hasTime = zdt.hour != 0 || zdt.minute != 0
        return zdt.format(if (hasTime) DATE_TIME else DATE_ONLY)
    }

    fun untilPhrase(target: Instant, now: Instant = Instant.now()): String {
        val d = Duration.between(now, target)
        if (d.isNegative) return "already passed"
        return "in " + span(d)
    }

    fun agoPhrase(past: Instant, now: Instant = Instant.now()): String {
        val d = Duration.between(past, now)
        if (d.isNegative || d < Duration.ofMinutes(1)) return "just now"
        return span(d) + " ago"
    }

    fun span(d: Duration): String {
        val days = d.toDays()
        val hours = d.toHours() % 24
        val minutes = (d.toMinutes() % 60).coerceAtLeast(0)
        return when {
            days >= 2 -> "${days}d"
            days >= 1 -> "${days}d ${hours}h"
            hours >= 1 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            else -> "${minutes.coerceAtLeast(1)}m"
        }
    }

    fun leadPhrase(minutes: Int): String = when {
        minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)}d"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes}m"
    }
}
