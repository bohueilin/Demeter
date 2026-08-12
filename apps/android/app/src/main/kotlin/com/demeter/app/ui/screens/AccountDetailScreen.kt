@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.demeter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demeter.app.platform.NotificationHelper
import com.demeter.app.platform.TimeFormat
import com.demeter.app.ui.DemeterViewModel
import com.demeter.app.ui.theme.statusColors
import com.demeter.domain.model.DashboardPolicy
import com.demeter.domain.model.FreshnessPolicy
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.model.WindowKind
import java.time.Instant

@Composable
fun AccountDetailScreen(
    viewModel: DemeterViewModel,
    accountId: String,
    onBack: () -> Unit,
    onUpdateUsage: (accountId: String, windowId: String?) -> Unit,
    onAddWindow: (accountId: String) -> Unit,
    onEditReminder: (accountId: String, windowId: String) -> Unit,
    onDeleted: () -> Unit,
) {
    val account by viewModel.repo.accountFlow(accountId).collectAsStateWithLifecycle(initialValue = null)
    val allWindows by viewModel.repo.latestWindows().collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by viewModel.repo.rules().collectAsStateWithLifecycle(initialValue = emptyList())
    val events by viewModel.repo.reminderDao.eventsForAccount(accountId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val nextScheduled by viewModel.repo.reminderDao.nextScheduledForAccount(accountId)
        .collectAsStateWithLifecycle(initialValue = null)
    val now by nowTicker()
    var confirmDelete by remember { mutableStateOf(false) }

    // Fixed order: session windows first, then weekly/monthly/etc; within a kind, the
    // soonest reset first and no-reset windows last (e.g. Current session → All models → Fable only).
    val windows = allWindows.filter { it.accountId == accountId }
        .sortedWith(
            compareBy<UsageWindow> { windowKindOrder(it.kind) }
                .thenBy(nullsLast()) { it.resetAt },
        )
    val notificationsOk = NotificationHelper.canPost(androidx.compose.ui.platform.LocalContext.current)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(account?.nickname ?: "…")
                        Text(
                            account?.provider?.displayLabel.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete account")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // "Update usage" leads — it is the main repeat action for manual sources.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onUpdateUsage(accountId, windows.firstOrNull()?.id) },
                        enabled = windows.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Update usage")
                    }
                    OutlinedButton(onClick = { onAddWindow(accountId) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add window")
                    }
                }
            }

            if (nextScheduled != null) {
                item {
                    Text(
                        "Next reminder: ${TimeFormat.untilPhrase(Instant.ofEpochSecond(nextScheduled!!.triggerAtEpochSec), now)} " +
                            "(${TimeFormat.leadPhrase(nextScheduled!!.leadMinutes)} before reset). Delivery timing is best-effort on Android.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(windows, key = { it.id }) { window ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(window.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${window.kind.displayLabel} · ${window.source.displayLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onUpdateUsage(accountId, window.id) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Update ${window.label}")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val (value, unit) = primaryMetric(window, now)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        window.resetAt?.let {
                            Text(
                                "Resets ${TimeFormat.resetLabel(it)} · ${TimeFormat.untilPhrase(it, now)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge(usageBadge(DashboardPolicy.usageAxis(window, now), window, now))
                            Badge(evidenceBadge(FreshnessPolicy.evidenceAxis(window.observedAt, window.effectiveDuration, now), window, now))
                        }
                        Spacer(Modifier.height(10.dp))
                        val rule = rules.firstOrNull { it.windowId == window.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = null,
                                // Green bell when reminders are on and deliverable — matching the
                                // Today cards; red is reserved for a rule the OS actually blocks.
                                tint = when {
                                    rule != null && rule.enabled && !notificationsOk ->
                                        MaterialTheme.colorScheme.error
                                    rule != null && rule.enabled -> statusColors().healthy
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    rule == null || !rule.enabled -> "Reminders off"
                                    !notificationsOk -> "Reminder saved — notifications off in Android Settings"
                                    else -> "Reminders on · ${rule.leadMinutes.joinToString { TimeFormat.leadPhrase(it) }} before reset"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                color = if (rule != null && rule.enabled && !notificationsOk) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            FilledTonalButton(onClick = { onEditReminder(accountId, window.id) }) {
                                Text(if (rule == null) "Set up" else "Edit")
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Activity",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Why reminders were sent, skipped, or repaired — in plain language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(events.take(30), key = { it.id }) { event ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(event.message, style = MaterialTheme.typography.bodySmall)
                    Text(
                        TimeFormat.agoPhrase(Instant.ofEpochSecond(event.atEpochSec), now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this account?") },
            text = { Text("This removes the account, its local history, and its reminders from this device. It does not touch your ${account?.provider?.displayLabel} account.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAccount(accountId) { onDeleted() }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/** Sort priority for windows: session first, then weekly, monthly, credits, custom. */
private fun windowKindOrder(kind: WindowKind): Int = when (kind) {
    WindowKind.SESSION -> 0
    WindowKind.WEEKLY -> 1
    WindowKind.MONTHLY -> 2
    WindowKind.CREDITS -> 3
    WindowKind.CUSTOM -> 4
}
