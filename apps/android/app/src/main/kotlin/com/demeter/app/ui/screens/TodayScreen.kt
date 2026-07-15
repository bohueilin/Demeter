@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.demeter.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demeter.app.R
import com.demeter.app.platform.TimeFormat
import com.demeter.app.ui.DemeterViewModel
import com.demeter.domain.model.DashboardPolicy
import com.demeter.domain.model.MonitoredAccount
import com.demeter.domain.model.Provider
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.reminder.ReminderRule
import java.time.Instant

@Composable
fun TodayScreen(
    viewModel: DemeterViewModel,
    onAddAccount: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateUsage: (accountId: String, windowId: String) -> Unit,
) {
    val accounts by viewModel.repo.accounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val windows by viewModel.repo.latestWindows().collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by viewModel.repo.rules().collectAsStateWithLifecycle(initialValue = emptyList())
    val now by nowTicker()
    var confirmDelete by remember { mutableStateOf<MonitoredAccount?>(null) }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${target.nickname}?") },
            text = {
                Text(
                    "This removes the account, its usage history, and reminders from this device. " +
                        "It does not touch your ${target.provider.displayLabel} account.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(target.id)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today", fontWeight = FontWeight.SemiBold)
                        Text(
                            summaryLine(accounts, windows, now),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            if (accounts.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddAccount,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add account") },
                )
            }
        },
    ) { padding ->
        if (accounts.isEmpty()) {
            EmptyToday(
                modifier = Modifier.padding(padding),
                onAddAccount = onAddAccount,
                onSampleData = { viewModel.seedSamples() },
            )
        } else {
            val windowsByAccount = windows.groupBy { it.accountId }
            val suggestion = DashboardPolicy.suggestedNext(windows, now)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (suggestion != null) {
                    item(key = "suggested", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        SuggestedNextBanner(
                            suggestion = suggestion,
                            account = accounts.firstOrNull { it.id == suggestion.accountId },
                            now = now,
                            onOpen = { onOpenAccount(suggestion.accountId) },
                        )
                    }
                }
                items(accounts, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        windows = windowsByAccount[account.id].orEmpty(),
                        rules = rules,
                        hero = accounts.size == 1,
                        now = now,
                        onOpen = { onOpenAccount(account.id) },
                        onUpdateUsage = onUpdateUsage,
                        onDelete = { confirmDelete = account },
                    )
                }
            }
        }
    }
}

private fun summaryLine(accounts: List<MonitoredAccount>, windows: List<UsageWindow>, now: Instant): String {
    if (accounts.isEmpty()) return "No accounts yet"
    val atRisk = windows.count { DashboardPolicy.capacityAtRisk(it, now) > 0 && DashboardPolicy.usageAxis(it, now).let { a -> a == com.demeter.domain.model.UsageAxis.URGENT || a == com.demeter.domain.model.UsageAxis.USE_SOON } }
    return when {
        atRisk == 0 -> "${accounts.size} account${if (accounts.size == 1) "" else "s"} · nothing urgent right now"
        atRisk == 1 -> "1 window may reset with capacity remaining"
        else -> "$atRisk windows may reset with capacity remaining"
    }
}

@Composable
private fun SuggestedNextBanner(
    suggestion: UsageWindow,
    account: MonitoredAccount?,
    now: Instant,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Suggested next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(2.dp))
            val remaining = (suggestion.capacity as? com.demeter.domain.model.CapacityState.Known)?.remainingPercent
            Text(
                buildString {
                    append(account?.nickname ?: "Account")
                    append(" · ")
                    append(suggestion.label)
                    if (remaining != null) append(" — $remaining% left")
                    suggestion.resetAt?.let { append(", resets ${TimeFormat.untilPhrase(it, now)}") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyToday(modifier: Modifier = Modifier, onAddAccount: () -> Unit, onSampleData: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("🌱", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            Text("Add your first AI account.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Demeter shows what capacity remains, when it resets, and which account deserves attention next — all from evidence you provide.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            ExtendedFloatingActionButton(
                onClick = onAddAccount,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add account") },
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onSampleData) { Text("Try with sample data") }
        }
    }
}

@Composable
private fun AccountCard(
    account: MonitoredAccount,
    windows: List<UsageWindow>,
    rules: List<ReminderRule>,
    hero: Boolean,
    now: Instant,
    onOpen: () -> Unit,
    onUpdateUsage: (accountId: String, windowId: String) -> Unit,
    onDelete: () -> Unit,
) {
    val primary = windows.maxByOrNull { DashboardPolicy.capacityAtRisk(it, now) }
        ?: windows.firstOrNull()

    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Provider logo so ChatGPT vs Claude accounts are recognizable at a glance.
                val logoRes = when (account.provider) {
                    Provider.OPENAI -> R.drawable.ic_provider_openai
                    Provider.ANTHROPIC -> R.drawable.ic_provider_anthropic
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(logoRes),
                        contentDescription = account.provider.displayLabel,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    account.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (primary != null) {
                    // Updating usage is the main repeat action; keep it reachable without opening details.
                    IconButton(onClick = { onUpdateUsage(account.id, primary.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Update usage for ${account.nickname}")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete ${account.nickname}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (primary == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No usage windows yet — tap to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                val axis = DashboardPolicy.usageAxis(primary, now)
                val badge = usageBadge(axis, primary, now)
                val ringPercent = when (val cap = primary.capacity) {
                    is com.demeter.domain.model.CapacityState.Known -> cap.remainingPercent
                    is com.demeter.domain.model.CapacityState.Exhausted -> 0
                    is com.demeter.domain.model.CapacityState.UnknownLimit -> null
                }
                val (value, unit) = primaryMetric(primary, now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsageRing(
                        percent = ringPercent,
                        color = badge.color,
                        centerTop = value,
                        centerBottom = unit,
                        diameter = if (hero) 104.dp else 88.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(primary.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        primary.resetAt?.let {
                            Text(
                                "Resets ${TimeFormat.resetLabel(it)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                TimeFormat.untilPhrase(it, now).replaceFirstChar { c -> c.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Badge(badge)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Badge(evidenceBadge(com.demeter.domain.model.FreshnessPolicy.evidenceAxis(primary.observedAt, primary.effectiveDuration, now), primary, now))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        primary.source.displayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (windows.size > 1) {
                        Text(
                            "· ${windows.size} windows",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val hasRule = rules.any { it.accountId == account.id && it.enabled }
                    Text(
                        if (hasRule) "· Reminders on" else "· Reminders off",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
