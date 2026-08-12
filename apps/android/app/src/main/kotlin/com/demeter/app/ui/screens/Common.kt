package com.demeter.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.demeter.app.R
import com.demeter.app.platform.TimeFormat
import com.demeter.domain.model.EvidenceAxis
import com.demeter.domain.model.Provider
import com.demeter.domain.model.UsageAxis
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.model.WindowKind
import com.demeter.app.ui.theme.statusColors
import kotlinx.coroutines.delay
import java.time.Instant

/** Shared 30-second clock so countdowns and freshness labels stay honest on screen. */
@Composable
fun nowTicker(): State<Instant> = produceState(initialValue = Instant.now()) {
    while (true) {
        delay(30_000)
        value = Instant.now()
    }
}

data class AxisBadge(val text: String, val color: Color, val icon: ImageVector)

@Composable
fun usageBadge(axis: UsageAxis, window: UsageWindow, now: Instant): AxisBadge {
    val colors = statusColors()
    return when (axis) {
        UsageAxis.HEALTHY -> AxisBadge("Healthy", colors.healthy, Icons.Filled.Check)
        UsageAxis.USE_SOON -> AxisBadge("Use soon", colors.useSoon, Icons.Filled.Schedule)
        UsageAxis.URGENT -> AxisBadge(
            "Use it — resets ${window.resetAt?.let { TimeFormat.untilPhrase(it, now) } ?: "soon"}",
            colors.urgent,
            Icons.Filled.PriorityHigh,
        )
        UsageAxis.EXHAUSTED -> AxisBadge("Exhausted", colors.exhausted, Icons.Filled.HourglassEmpty)
        UsageAxis.RESET_PASSED -> AxisBadge("Reset likely happened", colors.useSoon, Icons.Filled.Refresh)
        UsageAxis.UNKNOWN -> AxisBadge("Remaining not available", colors.unknown, Icons.Filled.Help)
    }
}

@Composable
fun evidenceBadge(axis: EvidenceAxis, window: UsageWindow, now: Instant): AxisBadge {
    val colors = statusColors()
    val updated = "Updated ${TimeFormat.agoPhrase(window.observedAt, now)}"
    return when (axis) {
        EvidenceAxis.CURRENT -> AxisBadge(updated, colors.healthy, Icons.Filled.Check)
        EvidenceAxis.AGING -> AxisBadge(updated, colors.useSoon, Icons.Filled.Schedule)
        EvidenceAxis.STALE -> AxisBadge("Stale · $updated", colors.staleEvidence, Icons.Filled.Error)
    }
}

@Composable
fun blockedBadge(): AxisBadge =
    AxisBadge("Notifications off", statusColors().urgent, Icons.Filled.NotificationsOff)

@Composable
fun Badge(badge: AxisBadge, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(badge.color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(badge.icon, contentDescription = null, tint = badge.color, modifier = Modifier.size(14.dp))
        // Status hue lives in the icon + pill; the words stay onSurface so the product's
        // truth claims ("Updated 25m ago", "Stale") read at >= 7.8:1 instead of 2.3-3.7:1.
        Text(badge.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The value a card leads with. Unknown limits lead with the reset countdown, never a fake %. */
fun primaryMetric(window: UsageWindow, now: Instant): Pair<String, String> {
    return when (val cap = window.capacity) {
        is com.demeter.domain.model.CapacityState.Known ->
            "${cap.remainingPercent}%" to "left"

        is com.demeter.domain.model.CapacityState.Exhausted ->
            "0%" to "left"

        is com.demeter.domain.model.CapacityState.UnknownLimit ->
            (window.resetAt?.let { TimeFormat.untilPhrase(it, now).removePrefix("in ") } ?: "—") to "until reset"
    }
}

/**
 * Circular capacity gauge. A known percentage fills the ring in its status color; an
 * unknown limit draws only the track and leads with the reset countdown in the center —
 * the ring never invents a fill for capacity we don't actually know.
 */
@Composable
fun UsageRing(
    percent: Int?,
    color: Color,
    centerTop: String,
    centerBottom: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 88.dp,
    strokeWidth: Dp = 9.dp,
) {
    // Unknown-limit rings are ONLY a track, so it must read against the card
    // (outline: 3.06:1 light / 3.44:1 dark). Known-percentage rings keep the quiet
    // surfaceVariant track instead — there the legibility that matters is the
    // fill-vs-track boundary (3.3–7.3:1), which an outline track would collapse
    // to ~1:1 against the healthy fill.
    val track = if (percent == null) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    // The box grows with the user's font scale so the sp-sized center text never
    // collides with the ring stroke at accessibility text sizes.
    val ringScale = LocalDensity.current.fontScale
    Box(modifier.size(diameter * ringScale), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val topLeft = Offset(sw / 2f, sw / 2f)
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            if (percent != null) {
                drawArc(
                    color = color, startAngle = -90f,
                    sweepAngle = percent.coerceIn(0, 100) / 100f * 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerTop,
                // Percentages ("81%") get the big size step; longer countdowns
                // ("23h 59m") stay at titleMedium so they never paint over the
                // ring stroke or wrap inside it.
                style = if (centerTop.length <= 5) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                // Short values stay on one line; longer phrases ("already passed")
                // may stack on two rather than clip mid-word.
                maxLines = if (centerTop.length <= 5) 1 else 2,
            )
            Text(
                centerBottom,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Which window a card leads with ----

/**
 * Prefers the broad recurring budget (the weekly "all models" allowance) over the short
 * 5-hour session. The session churns every few hours and is noisy at a glance; the weekly
 * limit is the number people actually plan around. Everything else still shows as a note.
 */
fun headlineWindow(windows: List<UsageWindow>): UsageWindow? =
    windows.minWithOrNull(
        compareBy<UsageWindow> { headlineRank(it.kind) }
            // Within a kind, the aggregate ("All models") outranks a model-specific limit.
            .thenBy { if (it.label.contains("all", ignoreCase = true)) 0 else 1 }
            .thenBy { it.label },
    )

fun headlineRank(kind: WindowKind): Int = when (kind) {
    WindowKind.WEEKLY -> 0
    WindowKind.MONTHLY -> 1
    WindowKind.CREDITS -> 2
    WindowKind.SESSION -> 3
    WindowKind.CUSTOM -> 4
}

// ---- Provider identity & launch ----

@DrawableRes
fun providerLogoRes(provider: Provider): Int = when (provider) {
    Provider.OPENAI -> R.drawable.ic_provider_openai
    Provider.ANTHROPIC -> R.drawable.ic_provider_anthropic
    Provider.GOOGLE -> R.drawable.ic_provider_google
}

private fun providerHomeUrl(provider: Provider): String = when (provider) {
    Provider.OPENAI -> "https://chatgpt.com"
    Provider.ANTHROPIC -> "https://claude.ai"
    Provider.GOOGLE -> "https://gemini.google.com/app"
}

/**
 * Hands the provider's URL to the system, which opens their official app if installed (or the
 * browser otherwise). Demeter never loads it itself — that is why this needs no INTERNET
 * permission and leaves the app's no-network guarantee intact.
 */
fun openProvider(context: Context, provider: Provider) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(providerHomeUrl(provider)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** Circular provider logo badge, on white so both marks read against any theme. */
@Composable
fun ProviderBadge(provider: Provider, modifier: Modifier = Modifier, diameter: Dp = 40.dp) {
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(providerLogoRes(provider)),
            contentDescription = provider.displayLabel,
            modifier = Modifier.size(diameter * 0.75f),
        )
    }
}
