@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.demeter.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demeter.app.platform.NotificationHelper
import com.demeter.app.platform.TimeFormat
import com.demeter.app.ui.DemeterViewModel
import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.reminder.DeliveryDecision
import com.demeter.domain.reminder.EvidencePolicy
import com.demeter.domain.reminder.LEAD_TIME_CHOICES_MINUTES
import com.demeter.domain.reminder.ReminderIntent
import com.demeter.domain.reminder.ReminderPolicy
import com.demeter.domain.reminder.ReminderRule
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Order per the accepted design: timing first, then evidence policy, then
 * unknown-remaining behavior — with a live preview of what would actually happen.
 * Reset-timing-only is an opt-in, deliberately NOT a third "confidence mode".
 */
@Composable
fun ReminderEditorScreen(
    viewModel: DemeterViewModel,
    accountId: String,
    windowId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var window by remember { mutableStateOf<UsageWindow?>(null) }
    var leads by remember { mutableStateOf(setOf(240, 60)) }
    var evidencePolicy by remember { mutableStateOf(EvidencePolicy.LAST_KNOWN) }
    var remindWhenUnknown by remember { mutableStateOf(false) }
    var threshold by remember { mutableStateOf(10) }
    var quietHours by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var ruleId by remember { mutableStateOf<String?>(null) }
    var showRationale by remember { mutableStateOf(false) }
    var savedBlocked by remember { mutableStateOf(false) }
    val now by nowTicker()

    LaunchedEffect(windowId) {
        val w = viewModel.repo.latestForWindow(windowId)
        window = w
        val existing = viewModel.repo.ruleForWindow(windowId)
        if (existing != null) {
            ruleId = existing.id
            leads = existing.leadMinutes.toSet()
            evidencePolicy = existing.evidencePolicy
            remindWhenUnknown = existing.remindWhenUnknown
            threshold = existing.minRemainingPercent
            quietHours = existing.quietStartMinuteOfDay != null
            enabled = existing.enabled
        } else {
            // New rule: suggest leads scaled to how far out the reset is, so a window that
            // resets days away defaults to day-based reminders instead of just 4h/1h.
            w?.resetAt?.let { reset ->
                val days = Duration.between(Instant.now(), reset).toDays()
                leads = when {
                    days >= 5 -> setOf(3 * 24 * 60, 24 * 60) // 3d, 1d
                    days >= 1 -> setOf(24 * 60, 4 * 60)      // 1d, 4h
                    else -> setOf(240, 60)                   // 4h, 1h
                }
            }
        }
    }

    fun buildRule() = ReminderRule(
        id = ruleId ?: UUID.randomUUID().toString(),
        accountId = accountId,
        windowId = windowId,
        leadMinutes = leads.sortedDescending(),
        evidencePolicy = evidencePolicy,
        remindWhenUnknown = remindWhenUnknown,
        minRemainingPercent = threshold,
        quietStartMinuteOfDay = if (quietHours) 22 * 60 else null,
        quietEndMinuteOfDay = if (quietHours) 7 * 60 else null,
        enabled = enabled && leads.isNotEmpty(),
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.logPermissionResult(granted)
        savedBlocked = !granted
        viewModel.saveRule(buildRule()) { if (granted) onBack() }
    }

    fun saveWithPermissionFlow() {
        val needsAsk = enabled && Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canPost(context)
        if (needsAsk) {
            showRationale = true
        } else {
            viewModel.saveRule(buildRule()) { onBack() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders · ${window?.label ?: "…"}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Remind me before the reset", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEAD_TIME_CHOICES_MINUTES.forEach { lead ->
                    FilterChip(
                        selected = lead in leads,
                        onClick = { leads = if (lead in leads) leads - lead else leads + lead },
                        label = { Text(TimeFormat.leadPhrase(lead) + " before") },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "These are planning reminders, not alarms — Android may deliver them a little late.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text("Evidence policy", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            EvidencePolicy.entries.forEach { policy ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = evidencePolicy == policy, onClick = { evidencePolicy = policy })
                    Column {
                        Text(policy.displayLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            policy.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remind me even when remaining usage is unknown", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "The reminder will only mention the reset time — never a made-up percentage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = remindWhenUnknown, onCheckedChange = { remindWhenUnknown = it })
            }

            Spacer(Modifier.height(16.dp))
            Text("Only remind me when at least this much is left", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 10, 25, 50).forEach { t ->
                    FilterChip(
                        selected = threshold == t,
                        onClick = { threshold = t },
                        label = { Text(if (t == 0) "Any" else "$t%") },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quiet hours 22:00 – 07:00", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Reminders shift to the morning, but never past 15 minutes before the reset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = quietHours, onCheckedChange = { quietHours = it })
            }

            Spacer(Modifier.height(20.dp))
            // Live preview: run the real delivery policy against current evidence.
            window?.let { w ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("If a reminder fired right now", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        val resetAt = w.resetAt
                        if (resetAt == null) {
                            Text(
                                "No reset time recorded — nothing can be scheduled until you add one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            when (val d = ReminderPolicy.evaluateAtDelivery(buildRule().copy(enabled = true), w, resetAt, now)) {
                                is DeliveryDecision.Deliver -> {
                                    val remaining = (w.capacity as? CapacityState.Known)?.remainingPercent
                                    val title = when (d.intent) {
                                        ReminderIntent.ALLOWANCE -> "${remaining}% left · resets ${TimeFormat.untilPhrase(resetAt, now)}"
                                        ReminderIntent.CHECK_USAGE ->
                                            if (remaining != null) {
                                                "Resets ${TimeFormat.untilPhrase(resetAt, now)} — you had $remaining% left as of ${TimeFormat.agoPhrase(d.asOf, now)}"
                                            } else {
                                                "Resets ${TimeFormat.untilPhrase(resetAt, now)} — check what remains"
                                            }
                                    }
                                    Text("🔔 $title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                is DeliveryDecision.Suppress -> Text(
                                    "🔕 Would stay silent: ${d.reason}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reminders on", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            if (savedBlocked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Saved — but Android notifications are off for Demeter, so nothing can appear. Enable notifications in Android Settings to activate this rule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { saveWithPermissionFlow() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) { Text("Save reminders") }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow reminders?") },
            text = {
                Text(
                    "Demeter needs Android's permission to show reminder notifications — nothing fires without it. " +
                        "Delivery is best-effort: Android may hold a reminder briefly to save battery.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Allow reminders") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    savedBlocked = true
                    viewModel.saveRule(buildRule())
                }) { Text("Not now") }
            },
        )
    }
}
