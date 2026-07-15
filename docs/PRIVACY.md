# Demeter — Privacy Engineering Data Inventory (v0.1)

Engineering-facing companion to [PRIVACY_POLICY.md](./PRIVACY_POLICY.md). This is the source of truth for what data exists, where it lives, and what the app's privacy posture is. Any change to the facts below requires updating both documents and the Play Data safety form before release.

**Ground rules (v1, local-only):**

- No `INTERNET` permission. Zero network calls. No accounts, no analytics, no crash reporting, no ads.
- No provider credentials are ever collected, parsed, or stored — no passwords, API keys, or tokens. Paste-import flows must reject/ignore anything that looks like a secret; nicknames are free-text labels, never used for authentication.
- All persistence is on-device.

## 1. Data classes stored

| Data class | Contents | Source | Sensitivity notes |
|---|---|---|---|
| Monitored accounts | Provider type (ChatGPT/OpenAI, Claude/Anthropic), user-chosen nickname, display preferences | User-entered | Nickname is user-chosen free text; user may type PII into it (e.g. an email). Treat as potentially sensitive; never surface on lock screen at the generic detail level. |
| Usage evidence / snapshots | Usage percentage or remaining amount, reset/window times, entry timestamp, raw pasted text (if paste-import used), normalized values, source-confidence/freshness metadata | User-entered or user-pasted | Pasted text is arbitrary user content; store only what the parser needs, discard transient parse buffers. |
| Reminder rules | Thresholds, lead times, quiet hours, snooze state, per-account enable flags, notification detail level | User-configured | Low sensitivity. |
| Reminder schedules | Pending logical trigger times, requested alarm times | Derived | Low sensitivity. |
| Audit events | Why a reminder was scheduled/suppressed/replaced/fired; timestamps (logical trigger, requested alarm, observed receiver, notification post, user open) | App-generated | May reference account nickname and usage values. Deleted with the owning account and by delete-all. |
| App settings | Onboarding-complete flag, theme, privacy-mode flag, notification detail level default, last-used UI state | User-configured / app-generated | Low sensitivity. |

**Not stored, ever (v1):** credentials of any kind, provider emails/org IDs, screenshots or images, contacts, location, device identifiers used for tracking, network logs.

## 2. Storage locations

| Store | Name | Contents |
|---|---|---|
| Room database | `demeter.db` | Accounts, usage snapshots, reminder rules, reminder schedules, audit events |
| SharedPreferences | `"demeter"` | Lightweight settings/flags (onboarding, theme, detail level, privacy mode) |

- Both live in the app's private internal storage (`/data/data/com.demeter.app/`). No external/shared storage is used.
- No data in `BuildConfig`, resources, or native code. No Android Keystore usage in v1 (nothing secret to protect; revisit if that changes).
- Logging: no data-class contents (nicknames, usage values, pasted text, notification content) in logcat or any persisted log. Audit events live only in Room.

## 3. Notifications and lock screen

- Local notifications only (no FCM — no network).
- User-selectable detail levels:
  1. **Generic** — e.g. "An AI usage window resets soon. Open Demeter for details." No nickname, no values.
  2. **Nickname + provider.**
  3. **Remaining value + reset time + nickname.**
- `VISIBILITY_PRIVATE` by default: lock screen shows only the generic redacted form unless the user relaxes it in system settings.
- Never include anything credential-like or cost detail in notification content (nothing of the sort is stored, but this is a contract for future fields).
- Respect user-modified channel sound/vibration/visibility settings. Channels named for purpose (reminders, reminder-health).
- Notification content is composed at post time from Room data and is not persisted anywhere except the audit event's reason/timing fields.

## 4. Backup / extraction posture

- `android:allowBackup="false"` in the manifest.
- `android:dataExtractionRules` (API 31+) and `android:fullBackupContent` (legacy) both exclude everything — belt and suspenders, covers cloud backup and device-to-device transfer.
- Consequence to document in-app and in support docs: uninstall or device loss = data loss. This is intentional; there is no recovery path in v1.
- Test: backup extraction rules verified per release (adb backup/restore and D2D simulation).

## 5. Permission inventory

| Permission | Why | When requested |
|---|---|---|
| `POST_NOTIFICATIONS` | Show reminder notifications | Contextually, at the moment the user first enables a reminder — never at first launch |
| `RECEIVE_BOOT_COMPLETED` | Re-register inexact alarms/reminders after reboot | Install-time (normal permission, no prompt) |

- **Removed/absent:** `INTERNET` (removed for v1 — verify absence in merged-manifest diff each release), `ACCESS_NETWORK_STATE`, exact-alarm permissions (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` — reminders are inexact by design), all storage/media/location/contacts permissions.
- Release gate: merged-manifest permission audit must show exactly the two permissions above.

## 6. What would change if cloud ever ships

None of this applies to v1. If a cloud/sync/email feature is ever built, it is a separate, explicitly opt-in mode, and at minimum:

- **Manifest:** `INTERNET` returns; the "no network" claims in PRIVACY_POLICY.md, the Play listing, and the Data safety form must all be rewritten *before* release.
- **Consent:** cloud sync and any email delivery are separately consented flows; local-only mode keeps working without login (PRD 21.7).
- **Data minimization:** collect the minimum account metadata required; publish a retention schedule; provide in-app export, in-app cloud-account deletion, and a web deletion route (Play requirement).
- **Deletion semantics:** account deletion must revoke device tokens, cancel pending email jobs, delete cloud snapshots and email destinations, and record only a non-identifying deletion audit event.
- **Notifications:** any remote (FCM) payloads must be data-minimal; render sensitive content in-app after authorization, not in the push payload.
- **Still prohibited regardless:** collecting provider credentials (passwords/API keys) in any store — Room, DataStore, SharedPreferences, Keystore, native code, resources, or build config. This rule survives any architecture change.
- **Backups:** re-evaluate the backup posture (some cloud designs make backup exclusion unnecessary; others make it more important).
- **New review gates:** Data safety form remap, retention schedule publication, deletion-flow tests, and a fresh security review of every new network surface.

## 7. Play Data safety mapping (v1)

- Data collected: **none** (user-entered data is stored on-device only and never transmitted; per Play's definitions, on-device-only data that never leaves the device is not "collected").
- Data shared: **none**.
- Security practices: data is not encrypted in transit (no transit exists); users can request deletion via in-app delete-all and uninstall.
- Keep the release evidence package per PRD section 22: manifest diff, permission list, Data safety mapping, SDK inventory (should list zero third-party data SDKs), target API, 16 KB page-size report.
