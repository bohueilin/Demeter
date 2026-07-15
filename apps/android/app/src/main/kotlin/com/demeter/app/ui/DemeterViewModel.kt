package com.demeter.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.demeter.app.DemeterApp
import com.demeter.app.data.SampleData
import com.demeter.app.platform.NotificationHelper
import com.demeter.domain.model.CapacityState
import com.demeter.domain.model.Provider
import com.demeter.domain.model.SourceType
import com.demeter.domain.model.WindowKind
import com.demeter.domain.reminder.ReminderRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Outcome of running OCR on a picked screenshot. */
sealed interface ScreenshotResult {
    /** A multi-window usage screen (e.g. Claude's Usage page) — every detected window. */
    data class Multi(val windows: List<com.demeter.app.data.UsageScreenParser.ParsedWindow>) : ScreenshotResult
    /** A single-window screen or free text — hand off to the paste-assist parser. */
    data class Single(val text: String) : ScreenshotResult
    /** The image could not be decoded or held no readable text. */
    data object Failed : ScreenshotResult
}

class DemeterViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as DemeterApp).container
    val repo = container.repository

    private val prefs = app.getSharedPreferences("demeter", Application.MODE_PRIVATE)

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit().putBoolean("onboarded", value).apply()

    /** Privacy mode: blocks screenshots and hides content in the app switcher (FLAG_SECURE). */
    val privacySecure = androidx.compose.runtime.mutableStateOf(prefs.getBoolean("privacy_secure", false))

    fun setPrivacySecure(enabled: Boolean) {
        prefs.edit().putBoolean("privacy_secure", enabled).apply()
        privacySecure.value = enabled
        viewModelScope.launch {
            repo.logEvent(
                null, null, "privacy_mode",
                if (enabled) "Privacy mode on: app content hidden in screenshots and the app switcher." else "Privacy mode off.",
            )
        }
    }

    fun addAccount(provider: Provider, nickname: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (!repo.canAddAccount(provider)) {
                onResult(null)
            } else {
                onResult(repo.addAccount(provider, nickname))
            }
        }
    }

    fun recordEvidence(
        accountId: String,
        windowId: String?,
        label: String,
        kind: WindowKind,
        capacity: CapacityState,
        resetAt: Instant?,
        duration: Duration?,
        source: SourceType,
        note: String?,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repo.recordEvidence(accountId, windowId, label, kind, capacity, resetAt, duration, source, note)
            container.reconciler.reconcile("evidence_change")
            onDone()
        }
    }

    fun saveRule(rule: ReminderRule, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveRule(rule)
            container.reconciler.reconcile("rule_change")
            onDone()
        }
    }

    fun deleteWindow(accountId: String, windowId: String) {
        viewModelScope.launch {
            repo.deleteWindow(accountId, windowId)
            container.reconciler.reconcile("window_removed")
        }
    }

    fun deleteAccount(accountId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteAccount(accountId)
            container.reconciler.reconcile("account_deleted")
            onDone()
        }
    }

    fun seedSamples(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            SampleData.seed(container.db)
            onDone()
        }
    }

    fun testNotification() {
        NotificationHelper.postTest(getApplication())
    }

    // ---- On-device screenshot OCR (no network, image never stored) ----

    /** Text recognized from a shared screenshot, awaiting the Import screen. */
    val pendingImport = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Windows detected from a screenshot, staged for the multi-window import preview. */
    var pendingWindows: List<com.demeter.app.data.UsageScreenParser.ParsedWindow> = emptyList()
        private set
    var pendingWindowsAccountId: String? = null
        private set

    /**
     * Runs on-device OCR on a user-picked screenshot. If the image is a multi-window usage
     * screen (e.g. Claude's Usage page) it returns every detected window; otherwise it returns
     * the flat recognized text for the single-window paste-assist. On-device; image never stored.
     */
    fun readScreenshot(uri: android.net.Uri, onResult: (ScreenshotResult) -> Unit) {
        viewModelScope.launch {
            val lines = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.demeter.app.platform.OcrReader.readLines(getApplication(), uri)
                }
            }.getOrNull()
            if (lines == null) {
                onResult(ScreenshotResult.Failed)
                return@launch
            }
            val windows = com.demeter.app.data.UsageScreenParser.parse(lines)
            if (windows.size >= 2) {
                onResult(ScreenshotResult.Multi(windows))
            } else {
                val text = lines.sortedWith(compareBy({ it.cy }, { it.cx }))
                    .joinToString("\n") { it.text }
                    .takeIf { it.isNotBlank() }
                onResult(if (text == null) ScreenshotResult.Failed else ScreenshotResult.Single(text))
            }
        }
    }

    fun stagePendingWindows(accountId: String, windows: List<com.demeter.app.data.UsageScreenParser.ParsedWindow>) {
        pendingWindowsAccountId = accountId
        pendingWindows = windows
    }

    /**
     * Records evidence for each detected window. If a window with the same label already exists
     * on the account, it is updated (new append-only snapshot) instead of duplicated.
     */
    fun saveDetectedWindows(
        accountId: String,
        windows: List<com.demeter.app.data.UsageScreenParser.ParsedWindow>,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val existing = repo.latestWindowsOnce().filter { it.accountId == accountId }
            windows.forEach { w ->
                val match = existing.firstOrNull { it.label.equals(w.label, ignoreCase = true) }
                val capacity = when {
                    w.exhausted -> CapacityState.Exhausted
                    w.remainingPercent != null -> CapacityState.Known(w.remainingPercent)
                    else -> CapacityState.UnknownLimit()
                }
                repo.recordEvidence(
                    accountId = accountId,
                    windowId = match?.id,
                    label = w.label,
                    kind = w.kind,
                    capacity = capacity,
                    resetAt = w.resetAt,
                    duration = w.kind.defaultDuration,
                    source = SourceType.SCREENSHOT,
                    note = "Imported from screenshot",
                )
            }
            container.reconciler.reconcile("evidence_change")
            pendingWindows = emptyList()
            pendingWindowsAccountId = null
            onDone()
        }
    }

    /** Share-sheet entry: OCR a shared screenshot, then hand off to the Import screen. */
    fun ingestSharedImage(uri: android.net.Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            val text = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.demeter.app.platform.OcrReader.readText(getApplication(), uri)
                }
            }.getOrNull().orEmpty()
            pendingImport.value = text
            onReady()
        }
    }

    fun clearPendingImport() {
        pendingImport.value = null
    }

    /** Exports all local data as JSON text through the system share sheet. No secrets exist to leak. */
    fun exportData(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = org.json.JSONObject().apply {
                put("app", "Demeter — AI Usage Monitor")
                put("exportedAt", Instant.now().toString())
                put("note", "All values are user-entered evidence, not provider-authoritative numbers.")
                put(
                    "accounts",
                    org.json.JSONArray().also { arr ->
                        container.db.accountDao().accounts().first().forEach { a ->
                            arr.put(
                                org.json.JSONObject()
                                    .put("nickname", a.nickname)
                                    .put("provider", a.provider)
                                    .put("createdAtEpochSec", a.createdAtEpochSec),
                            )
                        }
                    },
                )
                put(
                    "latestWindows",
                    org.json.JSONArray().also { arr ->
                        repo.latestWindowsOnce().forEach { w ->
                            arr.put(
                                org.json.JSONObject()
                                    .put("label", w.label)
                                    .put("kind", w.kind.name)
                                    .put(
                                        "remainingPercent",
                                        (w.capacity as? com.demeter.domain.model.CapacityState.Known)?.remainingPercent ?: org.json.JSONObject.NULL,
                                    )
                                    .put("state", w.capacity::class.simpleName)
                                    .put("resetAt", w.resetAt?.toString() ?: org.json.JSONObject.NULL)
                                    .put("observedAt", w.observedAt.toString())
                                    .put("source", w.source.name),
                            )
                        }
                    },
                )
                put(
                    "reminderRules",
                    org.json.JSONArray().also { arr ->
                        repo.rulesOnce().forEach { r ->
                            arr.put(
                                org.json.JSONObject()
                                    .put("leadMinutes", r.leadMinutes.joinToString(","))
                                    .put("evidencePolicy", r.evidencePolicy.name)
                                    .put("remindWhenUnknown", r.remindWhenUnknown)
                                    .put("enabled", r.enabled),
                            )
                        }
                    },
                )
            }
            repo.logEvent(null, null, "export", "Data exported as JSON through the share sheet.")
            onReady(json.toString(2))
        }
    }

    /** Deletes everything: cancels all platform alarms, clears every table. */
    fun deleteAllData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.reminderDao.activeScheduled().forEach { container.alarmScheduler.cancel(it.requestCode) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                container.db.clearAllTables()
            }
            repo.logEvent(null, null, "deleted_all", "All local data was deleted at the user's request.")
            onDone()
        }
    }

    fun logPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            repo.logEvent(
                null, null, "notification_permission",
                if (granted) "Notification permission granted." else "Notification permission denied. Reminder rules stay saved but cannot post until it is fixed in Settings.",
            )
        }
    }
}
