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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demeter.app.data.UsageScreenParser.ParsedWindow
import com.demeter.app.platform.TimeFormat
import com.demeter.app.ui.DemeterViewModel
import java.time.Instant

/**
 * Preview of every window detected from a multi-window usage screenshot (e.g. Claude's Usage
 * page). The user unchecks anything wrong and saves the rest — nothing is written without
 * confirmation, and re-importing updates existing windows instead of duplicating them.
 */
@Composable
fun MultiImportScreen(
    viewModel: DemeterViewModel,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val accountId = remember { viewModel.pendingWindowsAccountId }
    val windows = remember { viewModel.pendingWindows }
    val now = remember { Instant.now() }
    var checked by remember { mutableStateOf(windows.map { true }) }

    if (accountId == null || windows.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import ${windows.size} windows") },
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
            Text(
                "Read from your screenshot on-device. Uncheck anything that looks wrong — you can fine-tune each window afterwards.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            windows.forEachIndexed { i, w ->
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    // The row is the checkbox and carries the window's label, so TalkBack
                    // announces which window is being included or excluded.
                    Row(
                        Modifier
                            .toggleable(
                                value = checked.getOrElse(i) { false },
                                role = Role.Checkbox,
                                onValueChange = { on ->
                                    checked = checked.toMutableList().also { it[i] = on }
                                },
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked.getOrElse(i) { false },
                            onCheckedChange = null,
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            capacityLine(w)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                resetLine(w, now),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val count = checked.count { it }
            Button(
                onClick = {
                    val toSave = windows.filterIndexed { i, _ -> checked.getOrElse(i) { false } }
                    viewModel.saveDetectedWindows(accountId, toSave) { onSaved(accountId) }
                },
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (count == 1) "Save 1 window" else "Save $count windows")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun capacityLine(w: ParsedWindow): String? = when {
    w.exhausted -> "Used up"
    w.usedPercent != null && w.remainingPercent != null ->
        "${w.usedPercent}% used · ${w.remainingPercent}% left"
    w.remainingPercent != null -> "${w.remainingPercent}% left"
    else -> null // reset-only checkpoint — no capacity to show, just its expiry below
}

private fun resetLine(w: ParsedWindow, now: Instant): String = when {
    w.resetAt != null -> "Resets ${TimeFormat.resetLabel(w.resetAt)} · ${TimeFormat.untilPhrase(w.resetAt, now)}"
    w.resetText != null -> w.resetText
    else -> "No reset time detected"
}
