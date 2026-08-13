@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.demeter.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demeter.app.R
import com.demeter.app.platform.NotificationHelper
import com.demeter.app.platform.TimeFormat
import com.demeter.app.ui.DemeterViewModel
import com.demeter.app.ui.theme.statusColors
import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.DashboardPolicy
import com.demeter.domain.model.MonitoredAccount
import com.demeter.domain.model.Provider
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.reminder.ReminderRule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    viewModel: DemeterViewModel,
    onAddAccount: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateUsage: (accountId: String, windowId: String) -> Unit,
    onOpenCompare: () -> Unit = {},
    onEditReminder: (accountId: String, windowId: String) -> Unit = { _, _ -> },
) {
    val accounts by viewModel.repo.accounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val windows by viewModel.repo.latestWindows().collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by viewModel.repo.rules().collectAsStateWithLifecycle(initialValue = emptyList())
    val now by nowTicker()

    // Quick reminder picker target: an account plus its headline window.
    var quickReminders by remember { mutableStateOf<Pair<MonitoredAccount, UsageWindow>?>(null) }

    quickReminders?.let { (account, window) ->
        QuickReminderSheet(
            account = account,
            window = window,
            rules = rules,
            viewModel = viewModel,
            onAllOptions = {
                quickReminders = null
                onEditReminder(account.id, window.id)
            },
            onDismiss = { quickReminders = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            todayLine(now),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            summaryLine(accounts, windows, now),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCompare) {
                        Icon(Icons.Filled.Splitscreen, contentDescription = "Compare accounts")
                    }
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
                // Extra bottom clearance so the extended FAB never occludes the last card.
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
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
                            modifier = Modifier.animateItem(),
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
                        onQuickReminders = { window -> quickReminders = account to window },
                        // Positions animate; the numbers themselves never do.
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * Compact note for the windows the card is NOT leading with, e.g.
 * "Fable only 92% left · Current session 8% left". Only windows with a real percentage
 * appear — reset-only checkpoints would just add noise here.
 */
private fun secondaryLine(windows: List<UsageWindow>, primary: UsageWindow): String? {
    val others = windows
        .filter { it.id != primary.id }
        .filter { it.capacity is CapacityState.Known || it.capacity is CapacityState.Exhausted }
        .sortedWith(compareBy({ headlineRank(it.kind) }, { it.label }))
        .take(2)
    if (others.isEmpty()) return null
    return others.joinToString(" · ") { w ->
        val remaining = when (val cap = w.capacity) {
            is CapacityState.Known -> "${cap.remainingPercent}% left"
            else -> "used up"
        }
        "${w.label} $remaining"
    }
}

/**
 * e.g. "Today · Saturday, July 18". Derived from the shared ticker, so it rolls
 * over on its own at midnight without needing a separate clock. The middot matches
 * the app's metadata-separator convention; year and ordinal add nothing to "now".
 */
private fun todayLine(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = now.atZone(zone).toLocalDate()
    val dayOfWeek = date.format(DateTimeFormatter.ofPattern("EEEE"))
    val month = date.format(DateTimeFormatter.ofPattern("MMMM"))
    return "Today · $dayOfWeek, $month ${date.dayOfMonth}"
}

/** The three most common lead times, quick-armable straight from a card's bell. */
private val QUICK_LEAD_MINUTES = listOf(3 * 24 * 60, 2 * 24 * 60, 24 * 60)

/**
 * Bottom-sheet quick picker: multi-select 3d/2d/1d reminders for the card's headline
 * window, applied on tap through the same rule model as the full editor. Only lead
 * membership and enablement change here — evidence policy, threshold, quiet hours,
 * and any other configured leads are preserved (and disclosed in "Also active").
 * A disabled rule renders unselected; arming a chip re-enables it with its old leads.
 */
@Composable
private fun QuickReminderSheet(
    account: MonitoredAccount,
    window: UsageWindow,
    rules: List<ReminderRule>,
    viewModel: DemeterViewModel,
    onAllOptions: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val rule = rules.firstOrNull { it.windowId == window.id }
    var canPost by remember { mutableStateOf(NotificationHelper.canPost(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        canPost = granted
        viewModel.logPermissionResult(granted)
    }
    // Re-check on every resume, so returning from "Fix in Settings" heals the warning.
    LifecycleResumeEffect(Unit) {
        canPost = NotificationHelper.canPost(context)
        onPauseOrDispose { }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Reminders · ${window.label}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                account.nickname,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // FlowRow: the chips wrap instead of clipping at large font scales.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_LEAD_MINUTES.forEach { lead ->
                    val selected = rule != null && rule.enabled && lead in rule.leadMinutes
                    FilterChip(
                        selected = selected,
                        onClick = {
                            // Read-modify-write happens in the ViewModel behind a mutex,
                            // so rapid multi-select taps never drop each other's updates.
                            viewModel.toggleQuickLead(account.id, window.id, lead)
                            if (!selected && !canPost && Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        label = { Text("${TimeFormat.leadPhrase(lead)} before") },
                    )
                }
            }
            // Disclose every lead this sheet is editing around — including leads a
            // disabled rule remembers, since arming a chip restores them.
            val otherLeads = rule?.leadMinutes?.filter { it !in QUICK_LEAD_MINUTES }.orEmpty()
            if (otherLeads.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val phrase = otherLeads.joinToString(", ") { TimeFormat.leadPhrase(it) }
                Text(
                    if (rule?.enabled == true) {
                        "Also active: $phrase before reset"
                    } else {
                        "Arming also restores: $phrase before reset"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Planning reminders, not alarms — Android may deliver a little late.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!canPost && rule?.enabled == true) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Notifications are off in Android Settings — reminders can't appear.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }) { Text("Fix in Settings") }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onAllOptions) { Text("All options") }
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
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier,
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
                    // A prescriptive claim always carries the age of the evidence behind it.
                    append(" · updated ${TimeFormat.agoPhrase(suggestion.observedAt, now)}")
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
    onQuickReminders: (UsageWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Lead with the weekly budget rather than the churning 5-hour session (see headlineWindow).
    val primary = headlineWindow(windows)
    val context = LocalContext.current

    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Provider logo so ChatGPT vs Claude accounts are recognizable at a glance.
                ProviderBadge(account.provider)
                Spacer(Modifier.width(12.dp))
                Text(
                    account.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Hands off to the provider's own app/browser — no embedded web view.
                IconButton(onClick = { openProvider(context, account.provider) }) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = "Open ${account.provider.displayLabel}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (primary != null) {
                    // Updating usage is the main repeat action; keep it reachable without opening details.
                    // Deletion is rare and destructive — it lives on the detail screen, not every card.
                    IconButton(onClick = { onUpdateUsage(account.id, primary.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Update usage for ${account.nickname}")
                    }
                    // Quick reminder picker: arm 3d/2d/1d for the headline window
                    // without the full editor. Green only when armed AND deliverable —
                    // a blocked rule stays neutral (the meta row carries the warning).
                    val armed = rules.any { it.windowId == primary.id && it.enabled }
                    IconButton(onClick = { onQuickReminders(primary) }) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = "Reminders for ${account.nickname}",
                            tint = if (armed && NotificationHelper.canPost(context)) {
                                statusColors().healthy
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
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
                secondaryLine(windows, primary)?.let { note ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    // "Off" is a deliberate user choice, not an error — it stays quiet metadata.
                    // Only a rule the OS will actually suppress earns an attention signal.
                    val blocked = hasRule && !NotificationHelper.canPost(context)
                    if (blocked) {
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = statusColors().urgent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        when {
                            blocked -> "· Reminders blocked in Android Settings"
                            hasRule -> "· Reminders on"
                            else -> "· Reminders off"
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
