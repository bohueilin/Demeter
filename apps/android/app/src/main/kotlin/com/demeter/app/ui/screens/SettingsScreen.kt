@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.demeter.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demeter.app.platform.EmailComposer
import com.demeter.app.platform.NotificationHelper
import com.demeter.app.platform.TimeFormat
import com.demeter.app.ui.DemeterViewModel
import java.time.Instant

@Composable
fun SettingsScreen(viewModel: DemeterViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val events by viewModel.repo.reminderDao.events().collectAsStateWithLifecycle(initialValue = emptyList())
    val now by nowTicker()
    val notificationsOk = NotificationHelper.canPost(context)
    var email by remember { mutableStateOf(viewModel.reminderEmail) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Reminder health", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (notificationsOk) "Notifications: allowed" else "Notifications: off — reminders cannot appear",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notificationsOk) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Delivery is best-effort. Android can delay reminders under battery saving; Demeter records what actually happened in each account's activity log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                FilledTonalButton(onClick = { viewModel.testNotification() }, enabled = notificationsOk) {
                    Text("Send test notification")
                }
                Spacer(Modifier.padding(4.dp))
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text("Fix in Settings") }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text("Email reminders", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                "Reminders can hand a pre-filled email to your mail app — you tap Send. Demeter never " +
                    "sends mail itself and never signs in to your account, which is why it still needs no " +
                    "network permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    viewModel.reminderEmail = it
                },
                label = { Text("Send to") },
                placeholder = { Text("you@example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = {
                    viewModel.buildSummaryEmail { subject, body ->
                        EmailComposer.compose(context, viewModel.reminderEmail, subject, body)
                    }
                },
                enabled = EmailComposer.looksValid(email),
            ) { Text("Email usage summary") }
            if (EmailComposer.looksValid(email)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Reminder notifications now show an \"Email\" button that opens a draft to this address.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text("Privacy", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            // The whole row toggles and carries the label, so TalkBack announces
            // "Privacy mode, switch, off" instead of an anonymous "off, switch".
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = viewModel.privacySecure.value,
                        role = Role.Switch,
                        onValueChange = { viewModel.setPrivacySecure(it) },
                    ),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Privacy mode", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Hides Demeter's content in the app switcher and blocks screenshots of the app. Your own screenshots of other apps are unaffected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = viewModel.privacySecure.value,
                    onCheckedChange = null,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Everything stays on this device. Demeter has no network permission, no analytics, and never asks for provider passwords or API keys. Reminders hide details on the lock screen until you unlock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text("Your data", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Row {
                FilledTonalButton(onClick = {
                    viewModel.exportData { json ->
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_SUBJECT, "Demeter data export")
                            .putExtra(Intent.EXTRA_TEXT, json)
                        context.startActivity(Intent.createChooser(send, "Export Demeter data"))
                    }
                }) { Text("Export data") }
                Spacer(Modifier.padding(4.dp))
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete all data", color = MaterialTheme.colorScheme.error)
                }
            }
            if (confirmDelete) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete all data?") },
                    text = { Text("Every account, usage window, reminder, and activity record on this device will be permanently deleted, along with your saved reminder email address. This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            viewModel.deleteAllData()
                            // The field mirrors the pref; show the deletion immediately.
                            email = ""
                        }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text("Demo", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { viewModel.seedSamples() }) { Text("Add sample accounts") }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text("About", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                "Demeter — AI Usage Monitor v${com.demeter.app.BuildConfig.VERSION_NAME}. Local-first: your data stays on this device. " +
                    "Demeter is an independent app and is not affiliated with or endorsed by OpenAI, Anthropic, or Google. " +
                    "Values you enter are your evidence, not provider-authoritative numbers, and reminders are planning aids — not alarms.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text("Recent activity (all accounts)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            events.take(20).forEach { event ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(event.message, style = MaterialTheme.typography.bodySmall)
                    Text(
                        TimeFormat.agoPhrase(Instant.ofEpochSecond(event.atEpochSec), now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
