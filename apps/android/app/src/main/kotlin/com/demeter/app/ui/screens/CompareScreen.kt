@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.demeter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demeter.app.platform.NotificationHelper
import com.demeter.app.platform.TimeFormat
import com.demeter.app.ui.DemeterViewModel
import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.DashboardPolicy
import com.demeter.domain.model.FreshnessPolicy
import com.demeter.domain.model.MonitoredAccount
import com.demeter.domain.model.Provider
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.reminder.ReminderRule
import java.time.Instant

/**
 * Side-by-side (stacked) view of one Claude account and one ChatGPT account — the two-pane
 * layout, rendered natively from local evidence. Deliberately NOT embedded web views: each
 * pane's "Open" button hands off to the provider's own app, so Demeter still ships with no
 * network permission and never touches provider credentials.
 */
@Composable
fun CompareScreen(
    viewModel: DemeterViewModel,
    onBack: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
) {
    val accounts by viewModel.repo.accounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val windows by viewModel.repo.latestWindows().collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by viewModel.repo.rules().collectAsStateWithLifecycle(initialValue = emptyList())
    val now by nowTicker()

    // One pane per provider the user actually has accounts for — so Gemini appears once added,
    // and the view never shows an empty pane for a provider they don't use. Ordered Claude,
    // ChatGPT, Gemini. Falls back to all providers when there are no accounts yet.
    val providers = Provider.entries.filter { p -> accounts.any { it.provider == p } }
        .ifEmpty { Provider.entries.toList() }
    val selected = remember { mutableStateMapOf<Provider, Int>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // Content-sized panes in a scrollable column: nothing is ever clipped away,
        // at any font scale, and panes carry no dead space at default sizes.
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            providers.forEach { p ->
                val provAccounts = accounts.filter { it.provider == p }
                ComparePane(
                    provider = p,
                    accounts = provAccounts,
                    selectedIndex = (selected[p] ?: 0).coerceAtMost((provAccounts.size - 1).coerceAtLeast(0)),
                    onSelect = { selected[p] = it },
                    windows = windows,
                    rules = rules,
                    now = now,
                    onOpenAccount = onOpenAccount,
                    onAddAccount = onAddAccount,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                )
            }
        }
    }
}

@Composable
private fun ComparePane(
    provider: Provider,
    accounts: List<MonitoredAccount>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    windows: List<UsageWindow>,
    rules: List<ReminderRule>,
    now: Instant,
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val account = accounts.getOrNull(selectedIndex)

    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderBadge(provider, diameter = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account?.nickname ?: provider.displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        provider.displayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Hands off to the provider's own app/browser — no in-app web view.
                // Outlined: a rare hand-off must not outshout the capacity data, and each
                // button announces its provider so the three panes are distinguishable.
                OutlinedButton(
                    onClick = { openProvider(context, provider) },
                    modifier = Modifier.semantics { contentDescription = "Open ${provider.displayLabel}" },
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open")
                }
            }

            if (accounts.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEachIndexed { i, a ->
                        FilterChip(
                            selected = i == selectedIndex,
                            onClick = { onSelect(i) },
                            label = {
                                Text(a.nickname, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (account == null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No ${provider.displayLabel} account yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onAddAccount) { Text("Add account") }
                }
                return@Column
            }

            val mine = windows.filter { it.accountId == account.id }
            // Same rule as the Today cards: lead with the weekly budget, not the 5-hour session.
            val primary = headlineWindow(mine)

            if (primary == null) {
                Text(
                    "No usage windows yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onOpenAccount(account.id) }) { Text("Add usage") }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val axis = DashboardPolicy.usageAxis(primary, now)
                val badge = usageBadge(axis, primary, now)
                val (value, unit) = primaryMetric(primary, now)
                val percent = when (val cap = primary.capacity) {
                    is CapacityState.Known -> cap.remainingPercent
                    is CapacityState.Exhausted -> 0
                    is CapacityState.UnknownLimit -> null
                }
                UsageRing(
                    percent = percent,
                    color = badge.color,
                    centerTop = value,
                    centerBottom = unit,
                    diameter = 76.dp,
                    strokeWidth = 8.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        primary.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    primary.resetAt?.let {
                        Text(
                            "Resets ${TimeFormat.resetLabel(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                        Text(
                            TimeFormat.untilPhrase(it, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Same contract as the Today cards: status and evidence age are always
                    // carried by icon + text, never by ring color alone.
                    Spacer(Modifier.height(6.dp))
                    Badge(badge)
                    Spacer(Modifier.height(4.dp))
                    Badge(
                        evidenceBadge(
                            FreshnessPolicy.evidenceAxis(primary.observedAt, primary.effectiveDuration, now),
                            primary,
                            now,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    val hasRule = rules.any { it.accountId == account.id && it.enabled }
                    val blocked = hasRule && !NotificationHelper.canPost(context)
                    Text(
                        when {
                            blocked -> "Reminders blocked in Android Settings"
                            hasRule -> "Reminders on"
                            else -> "Reminders off"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (blocked) FontWeight.SemiBold else null,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
