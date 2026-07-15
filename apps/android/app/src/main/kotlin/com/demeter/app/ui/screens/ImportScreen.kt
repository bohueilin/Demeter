@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.demeter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demeter.app.data.PasteParser
import com.demeter.app.ui.DemeterViewModel
import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.SourceType
import com.demeter.domain.model.WindowKind
import java.time.Instant

/**
 * Landing screen for a screenshot shared into Demeter (ACTION_SEND). The image was already
 * OCR'd on-device; here the user confirms the recognized text, picks which account it belongs
 * to, and saves. Recognized values only prefill — everything stays editable, nothing is forced.
 */
@Composable
fun ImportScreen(
    viewModel: DemeterViewModel,
    onSaved: (String) -> Unit,
    onAddAccount: () -> Unit,
    onBack: () -> Unit,
) {
    val accounts by viewModel.repo.accounts().collectAsStateWithLifecycle(initialValue = emptyList())
    var text by remember { mutableStateOf(viewModel.pendingImport.value.orEmpty()) }
    var selected by remember { mutableStateOf<String?>(null) }
    val parsed = remember(text) { PasteParser.parse(text) }

    LaunchedEffect(accounts) {
        if (selected == null) selected = accounts.firstOrNull()?.id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import screenshot") },
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
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Read on-device from your screenshot", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (parsed.recognized.isNotEmpty()) {
                            "Recognized: ${parsed.recognized.joinToString(" · ")}. You can edit before saving."
                        } else {
                            "Couldn't read usage details automatically. Edit the text below, or fill it in on the next screen."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Recognized text") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (accounts.isEmpty()) {
                Text(
                    "Add an account to attach this usage to.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAddAccount, modifier = Modifier.fillMaxWidth()) {
                    Text("Add an account")
                }
            } else {
                Text("Attach to account", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a ->
                        FilterChip(
                            selected = selected == a.id,
                            onClick = { selected = a.id },
                            label = { Text("${a.nickname} · ${a.provider.displayLabel}") },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val accId = selected ?: return@Button
                        val kind = parsed.kind ?: WindowKind.SESSION
                        val capacity = parsed.effectiveRemaining?.let { CapacityState.Known(it) }
                            ?: CapacityState.UnknownLimit()
                        val resetAt = parsed.resetIn?.let { Instant.now().plus(it) }
                        viewModel.onboarded = true
                        viewModel.recordEvidence(
                            accountId = accId,
                            windowId = null,
                            label = defaultLabelFor(kind),
                            kind = kind,
                            capacity = capacity,
                            resetAt = resetAt,
                            duration = kind.defaultDuration,
                            source = SourceType.SCREENSHOT,
                            note = if (parsed.recognized.isNotEmpty()) {
                                "Recognized: ${parsed.recognized.joinToString(", ")}"
                            } else {
                                null
                            },
                        ) {
                            viewModel.clearPendingImport()
                            onSaved(accId)
                        }
                    },
                    enabled = selected != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save to account")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun defaultLabelFor(kind: WindowKind): String = when (kind) {
    WindowKind.SESSION -> "5-hour session"
    WindowKind.WEEKLY -> "Weekly limit"
    WindowKind.MONTHLY -> "Monthly budget"
    WindowKind.CREDITS -> "Credits"
    WindowKind.CUSTOM -> "Custom window"
}
