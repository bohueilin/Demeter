@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.demeter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demeter.app.data.PasteParser
import com.demeter.app.data.UsageScreenParser
import com.demeter.app.ui.DemeterViewModel
import com.demeter.app.ui.ScreenshotResult
import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.SourceType
import com.demeter.domain.model.WindowKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private enum class CapacityChoice(val label: String) {
    KNOWN("I know the %"),
    UNKNOWN("Not shown"),
    EXHAUSTED("Used up"),
}

private data class ResetChoice(val label: String, val duration: Duration?)

/**
 * Recording evidence is the product's main repeat action, so this screen is built
 * for speed: chips over pickers, paste-assist over typing, everything editable.
 * Saving appends new evidence — history is never rewritten.
 */
@Composable
fun WindowEditorScreen(
    viewModel: DemeterViewModel,
    accountId: String,
    windowId: String?,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    onOpenMultiImport: () -> Unit = {},
) {
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(WindowKind.SESSION) }
    var capacityChoice by remember { mutableStateOf(CapacityChoice.KNOWN) }
    var remaining by remember { mutableStateOf(50f) }
    var resetChoiceIdx by remember { mutableStateOf<Int?>(1) }
    var customResetOverride by remember { mutableStateOf<Instant?>(null) }
    var existingResetAt by remember { mutableStateOf<Instant?>(null) }
    var pasteText by remember { mutableStateOf("") }
    var recognized by remember { mutableStateOf<List<String>>(emptyList()) }
    var source by remember { mutableStateOf(SourceType.MANUAL) }
    var loadedExisting by remember { mutableStateOf(false) }
    var labelEdited by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val resetChoices = remember {
        listOf(
            ResetChoice("2h", Duration.ofHours(2)),
            ResetChoice("5h", Duration.ofHours(5)),
            ResetChoice("Tonight", null), // resolved to 21:00 local below
            ResetChoice("1d", Duration.ofDays(1)),
            ResetChoice("3d", Duration.ofDays(3)),
            ResetChoice("7d", Duration.ofDays(7)),
            ResetChoice("No reset time", null),
        )
    }

    // Editing an existing window: prefill from its latest evidence.
    LaunchedEffect(windowId) {
        if (windowId != null && !loadedExisting) {
            loadedExisting = true
            viewModel.repo.latestForWindow(windowId)?.let { w ->
                label = w.label
                labelEdited = true
                kind = w.kind
                when (val cap = w.capacity) {
                    is CapacityState.Known -> {
                        capacityChoice = CapacityChoice.KNOWN
                        remaining = cap.remainingPercent.toFloat()
                    }
                    is CapacityState.Exhausted -> capacityChoice = CapacityChoice.EXHAUSTED
                    is CapacityState.UnknownLimit -> capacityChoice = CapacityChoice.UNKNOWN
                }
                existingResetAt = w.resetAt
                resetChoiceIdx = null // keep their existing reset unless they pick a new one
            }
        }
    }

    fun resolveReset(): Instant? {
        val idx = resetChoiceIdx ?: return null
        val choice = resetChoices[idx]
        return when {
            choice.label == "Tonight" -> {
                val zone = ZoneId.systemDefault()
                val tonight = LocalDate.now(zone).atTime(LocalTime.of(21, 0)).atZone(zone)
                (if (tonight.toInstant().isAfter(Instant.now())) tonight else tonight.plusDays(1)).toInstant()
            }
            choice.duration != null -> Instant.now().plus(choice.duration)
            else -> null
        }
    }

    // Assistive parse shared by "Read pasted text" and screenshot OCR: recognized values
    // prefill the editable fields below, and nothing is ever force-matched.
    fun applyParsed(text: String, src: SourceType) {
        val result = PasteParser.parse(text)
        recognized = result.recognized
        result.effectiveRemaining?.let {
            capacityChoice = CapacityChoice.KNOWN
            remaining = it.toFloat()
        }
        result.kind?.let { kind = it }
        result.resetIn?.let { d ->
            customResetOverride = Instant.now().plus(d)
            resetChoiceIdx = null
        }
        if (result.recognized.isNotEmpty()) source = src
    }

    // Photo Picker: no storage permission, and OCR runs on-device (see OcrReader).
    val pickScreenshot = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            importStatus = null
            viewModel.readScreenshot(uri) { result ->
                when (result) {
                    is ScreenshotResult.Multi -> {
                        viewModel.stagePendingWindows(accountId, result.windows)
                        onOpenMultiImport()
                    }
                    is ScreenshotResult.Single -> {
                        pasteText = result.text
                        applyParsed(result.text, SourceType.SCREENSHOT)
                    }
                    ScreenshotResult.Failed ->
                        importStatus =
                            "Couldn't read that screenshot. Try a tighter crop of the usage text, or type it in below."
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (windowId == null) "Record usage" else "Update usage") },
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
            // Paste assist — assistive autofill, never force-matched, always editable.
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Paste text or import a screenshot (optional)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        placeholder = { Text("e.g. \"Weekly limit: 62% used, resets in 2 days 4 hours\"") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                // Multi-window paste (e.g. Claude's settings text) → preview screen;
                                // otherwise fall back to single-window paste-assist.
                                val multi = UsageScreenParser.parseText(pasteText)
                                if (multi.size >= 2) {
                                    viewModel.stagePendingWindows(accountId, multi)
                                    onOpenMultiImport()
                                } else {
                                    applyParsed(pasteText, SourceType.PASTE)
                                }
                            },
                            enabled = pasteText.isNotBlank(),
                        ) { Text("Read pasted text") }
                        OutlinedButton(
                            onClick = {
                                pickScreenshot.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import screenshot")
                        }
                    }
                    if (recognized.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Recognized: ${recognized.joinToString(" · ")}. Everything below stays editable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    importStatus?.let { status ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Window", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WindowKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = {
                            kind = k
                            if (!labelEdited) label = ""
                        },
                        label = { Text(k.displayLabel) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = label.ifEmpty { defaultLabel(kind) },
                onValueChange = { label = it; labelEdited = true },
                label = { Text("Label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(20.dp))
            Text("Remaining capacity", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CapacityChoice.entries.forEach { c ->
                    FilterChip(
                        selected = capacityChoice == c,
                        onClick = { capacityChoice = c },
                        label = { Text(c.label) },
                    )
                }
            }
            when (capacityChoice) {
                CapacityChoice.KNOWN -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${remaining.toInt()}% remaining",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(value = remaining, onValueChange = { remaining = it }, valueRange = 0f..100f)
                }
                CapacityChoice.UNKNOWN -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Demeter will show the reset countdown and never invent a percentage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CapacityChoice.EXHAUSTED -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Marked as used up until the next reset or your next update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Resets", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                resetChoices.forEachIndexed { idx, c ->
                    FilterChip(
                        selected = resetChoiceIdx == idx,
                        onClick = { resetChoiceIdx = idx; customResetOverride = null },
                        label = { Text(c.label) },
                    )
                }
            }
            if (customResetOverride != null && resetChoiceIdx == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Using reset time read from pasted text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (windowId != null && resetChoiceIdx == null && customResetOverride == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Keeping the window's existing reset time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    val capacity = when (capacityChoice) {
                        CapacityChoice.KNOWN -> CapacityState.Known(remaining.toInt())
                        CapacityChoice.UNKNOWN -> CapacityState.UnknownLimit()
                        CapacityChoice.EXHAUSTED -> CapacityState.Exhausted
                    }
                    val finalLabel = label.ifEmpty { defaultLabel(kind) }
                    // resolve reset: explicit chip > pasted override > (editing) keep existing
                    val save: (Instant?) -> Unit = { resetAt ->
                        viewModel.recordEvidence(
                            accountId = accountId,
                            windowId = windowId,
                            label = finalLabel,
                            kind = kind,
                            capacity = capacity,
                            resetAt = resetAt,
                            duration = kind.defaultDuration,
                            source = source,
                            note = if (recognized.isNotEmpty()) "Recognized: ${recognized.joinToString(", ")}" else null,
                        ) { onDone(accountId) }
                    }
                    when {
                        resetChoiceIdx != null -> save(resolveReset())
                        customResetOverride != null -> save(customResetOverride)
                        windowId != null -> save(existingResetAt)
                        else -> save(null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text("Save evidence")
            }
        }
    }
}

private fun defaultLabel(kind: WindowKind): String = when (kind) {
    WindowKind.SESSION -> "5-hour session"
    WindowKind.WEEKLY -> "Weekly limit"
    WindowKind.MONTHLY -> "Monthly budget"
    WindowKind.CREDITS -> "Credits"
    WindowKind.CUSTOM -> "Custom window"
}
