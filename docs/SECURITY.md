# Demeter — Security Posture & Threat Model (v1)

**Scope:** Demeter v0.1 for Android (package `com.demeter.app`, minSdk 29) — a local-only app where the user manually enters (or pastes as text) AI usage allowances for their ChatGPT/OpenAI and Claude/Anthropic accounts, and receives best-effort, inexact reminder notifications before allowances reset.

**Posture in one sentence:** v1 has no network capability, no accounts, no credentials, and no exported attack surface beyond the launcher activity; the security job is to keep it that way and to not leak the modest local data it holds.

This document describes what is actually implemented in v1. Features that ship later (cloud email reminders, the user-owned bridge, screenshot OCR) have their own controls specified in the PRD (v2.1, §21) and the v2.2 design addendum; those are summarized in [Planned controls](#planned-controls-when-cloudbridge-ship) and are **not** implemented today.

---

## Assets

All assets live in the app-private Room database (plus notification content derived from it). Sensitivity is **low-to-moderate, but private** — nothing here grants access to any account, yet users reasonably consider it personal.

| Asset | Description | Sensitivity |
|---|---|---|
| Usage evidence | User-entered remaining-percentage values, observation timestamps, and window/reset schedules per account | Low-moderate. Reveals which AI providers the user pays for and how heavily they use them. |
| Account nicknames | Free-text labels the user chooses (e.g. "work ChatGPT", a client name) | Low-moderate. User-chosen; could contain names the user considers private. |
| Reminder schedule | Which accounts have reminders, lead times, next scheduled fire times | Low. Behavioral metadata. |
| Reminder audit log | Local record of reminder decisions/deliveries | Low. |

**Explicitly not assets, because they never exist in v1:** provider passwords, session cookies, API keys, OAuth tokens, emails, payment data, contact data, location, device identifiers used for tracking. The app has no field, schema column, preference, or UI that could hold a provider credential.

## Trust boundaries

v1 has a single trust boundary: **the device itself.**

- All data stays in app-private storage on one Android device. There is no cloud, no sync, no account, no analytics, no crash reporting, no ads, and no network I/O of any kind.
- Inside the boundary, we rely on the Android app sandbox (per-app UID, scoped app-private storage) and the user's device lock.
- The only data that crosses out of the sandbox is what Android itself renders: notification content (goes to the system notification surface, including the lock screen per the user's channel/lock-screen settings) and normal screen output.
- Inbound surface: the launcher intent for `MainActivity`, system broadcasts (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`) to a non-exported receiver, and text the user pastes into an in-app text field.

There is no Demeter server, no bridge, and no third-party service in the v1 trust picture.

## Threat model

| # | Threat | Vector | Mitigation | Status |
|---|---|---|---|---|
| 1 | Device theft / shoulder surfing | Someone with physical access opens the app or glances at the screen | Android device lock is the primary control; data is low-moderate sensitivity by design (no credentials to steal). PRD specifies optional privacy mode / recents-obscuring and optional BiometricPrompt as future controls. | **Accepted for v1** (relies on device lock). Privacy mode / biometric gate not implemented in v1. |
| 2 | Malicious app reading exported components | Another app sends crafted intents to receivers/activities to trigger behavior or extract data | Both receivers (`ReminderAlarmReceiver`, `BootAndTimeReceiver`) are `android:exported="false"`. Only `MainActivity` is exported, with just the MAIN/LAUNCHER filter. No content providers, no services, no deep links, no inbound share targets in v1. All PendingIntents are `FLAG_IMMUTABLE`, so notification/alarm intents cannot be mutated by other apps. | **Mitigated** (implemented). |
| 3 | Backup extraction | Local data pulled from a cloud backup or device-to-device transfer | `android:allowBackup="false"` in the manifest disables backup/transfer of app data. | **Mitigated** (implemented). Adding an explicit `android:dataExtractionRules` resource for API 31+ is on the release checklist as belt-and-suspenders. |
| 4 | Notification leakage on lock screen | Reminder text (nickname + remaining %) visible on a locked device | Reminder content deliberately contains only the user-entered nickname, window label, remaining percentage, and reset timing — never emails, org IDs, or costs (none exist in v1). Lock-screen visibility follows the user's Android channel and lock-screen settings; channels are clearly named so users can restrict them. | **Partially mitigated.** Content is minimized by construction, but v1 does not yet implement the PRD's generic-by-default detail levels (§21.6); users who want fully generic lock-screen text must use Android's "hide sensitive content" setting. Detail-level control is planned. |
| 5 | Log leakage | Nicknames/usage values written to logcat, readable by tooling or captured in bug reports | No `Log`/`Timber` calls carrying user data exist in the app source; the hard rule below forbids logging PII. There is no crash-reporting SDK to exfiltrate logs. | **Mitigated** (implemented; enforced by review). |
| 6 | Clipboard snooping | The paste-as-text feature exposing data via the clipboard | Demeter never programmatically reads or writes the clipboard. Paste happens only when the user manually pastes into a standard text field; Android 10+ already blocks background clipboard reads by other apps, and the pasted content is the user's own usage text, not a credential. Clipboard is never part of any credential flow (no credentials exist). | **Mitigated / accepted** (residual risk is the OS clipboard itself, outside the app boundary). |
| 7 | Network exfiltration | App (or an embedded SDK) sending data off-device | The manifest declares **no `INTERNET` permission** and no network-adjacent permission; the OS therefore blocks all network I/O by the app process. No networking library, analytics, crash reporter, or ad SDK is included. | **Mitigated** (implemented — strongest control in v1; verify at every release, see checklist). |

## Hard rules

These are invariants, not preferences. A change that violates one is a product change requiring a PRD revision and threat-model review, not a code review.

1. **No provider credentials, ever.** The app never asks for, accepts, stores, or transmits provider passwords, session cookies, or API keys. No schema column, DataStore key, resource, `BuildConfig` field, or debug menu may hold one. This holds in every future version, not just v1. (PRD §21.3)
2. **No network permission in v1.** The manifest must not declare `INTERNET` or any network-adjacent permission until the cloud/bridge phases ship with their own reviewed threat model.
3. **Receivers are not exported.** Every receiver stays `android:exported="false"`; new components default to non-exported, and exporting anything requires a documented reason and review. (PRD §21.4)
4. **PendingIntents are immutable.** `FLAG_IMMUTABLE` on every PendingIntent; a mutable PendingIntent requires a documented justification and review.
5. **No PII in logs.** Never log nicknames, usage values, reset times tied to an account, or database contents. Log identifiers, not content.
6. **Backups stay disabled** (`allowBackup="false"`) until a deliberate, reviewed export/restore design exists.
7. **Permission minimalism.** Only `POST_NOTIFICATIONS` (requested contextually, when the user first enables a reminder) and `RECEIVE_BOOT_COMPLETED`. Deliberately absent: `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`, foreground services, camera/media, and anything network-adjacent.
8. **No third-party data SDKs.** No analytics, crash reporting, advertising, or tracking SDKs; no advertising-ID permission.

## Secure-coding checklist for contributors

Before merging, confirm:

- [ ] No new manifest permission; no `INTERNET`; no new `<queries>` or exported component. If a component must be exported, the intent filter is narrowed (action/category/scheme/MIME) and inputs are validated even for explicit intents.
- [ ] No new dependency that performs network I/O, telemetry, or dynamic code loading; new dependencies are reviewed and version-pinned.
- [ ] All `PendingIntent`s use `FLAG_IMMUTABLE`; intents targeting app components are explicit.
- [ ] Broadcast handling validates input and is idempotent (reminders reconcile from the database as the source of truth, never from PendingIntent state).
- [ ] No log statement contains nicknames, usage values, or Room row contents.
- [ ] No field anywhere could plausibly hold a provider secret — including "temporary" debug fields.
- [ ] No clipboard reads/writes via `ClipboardManager`; paste remains user-initiated in standard text fields.
- [ ] User data stays in app-private storage (Room/DataStore); nothing written to shared/external storage; no `file://` URIs.
- [ ] Notification content sticks to the minimized template (nickname, window label, percent, reset timing) — nothing new added to notification text without review.
- [ ] Release build check: inspect the merged manifest (`build/intermediates/merged_manifests`) to confirm no library injected `INTERNET` or other permissions, and that `allowBackup="false"` survived the merge.
- [ ] No `WebView`, no reflection-based or dynamic code loading, no debug/inspection endpoints in release builds.

## Planned controls (when cloud/bridge ship)

None of the following exists in v1. Recorded here so future phases start from decided positions (PRD §21, addendum P0.8 and decisions 11–12, 16):

- **Android Keystore device keys.** Bridge pairing binds a non-exportable Keystore-backed device key. **P-256 ECDSA is the mandatory baseline signing algorithm** (documented Keystore support across API 29–37); Ed25519 is optional and only after Keystore-backed availability is verified across the device matrix. Algorithm selection is bound into the signed pairing transcript; downgrade is prohibited. Keystore invalidation/recovery must be tested before release.
- **Signed snapshots.** Every bridge payload is signed and attributable to a paired bridge key; payload signature validation is **independent of TLS** in all cases. A compromised normalized snapshot must not permit provider account access (bridge keeps provider secrets in its own environment/secret manager; secrets are never returned by bridge APIs).
- **TLS / pinning decisions.** Public HTTPS is the supported default bridge path. LAN bridges are gated/beta: they ship only behind a reviewed **per-bridge certificate pin delivered out-of-band in the QR pairing ceremony** with a rotation/recovery story — or LAN is deferred from public release. No generic trust bypass, no "trust anyway" UI, no private-CA installation by ordinary users, no permissive trust managers or hostname verifiers, cleartext disabled in production.
- **Cloud identity & consent.** Cloud email reminders are separately consented from any future history sync; changing an email destination, deleting the cloud account, and revoking devices are step-up-authentication actions. Minimum reminder payload; verified destinations; retention and deletion published.
- **Local encryption stance.** No Room field-level encryption in v1 absent a threat-model finding; app-private storage + backup exclusions + (future) privacy mode and Keystore-protected tokens are the baseline. Revisit when tokens exist on-device.
- **OCR phase gate.** A signed, privacy-safe, accountless remote configuration channel able to disable a broken parser template must exist before screenshot OCR reaches external alpha.
- **Release gates** (PRD §21.8): exported-component tests, PendingIntent mutability audit, backup-extraction tests, network-security-config tests, dependency/SBOM/secret scans — all blocking before any networked release.

---

*Sources: `Demeter_PRD_Android_Pixel_10_Pro_XL_v2.1.md` §21 (security & privacy requirements), `Demeter_PRD_v2.2_Design_Revision_Addendum.md` (decisions P0.8, 11–12, 16), and the v1 implementation under `apps/android/app/src/main/` (manifest, `platform/AlarmScheduler.kt`, `platform/NotificationHelper.kt`, `platform/Reconciler.kt`).*

---

## Update (v1.1.0) — Screenshot OCR capture

New capability: import a screenshot of the user's own provider usage screen and read the numbers via **on-device** OCR (bundled ML Kit). Security posture:

- **No new network surface.** OCR is fully on-device (model bundled in the APK; verified via logcat loading the local `.so` model, and via the merged manifest still having no `INTERNET`). The image never leaves the device.
- **No new sensitive permission.** Capture uses the Android Photo Picker (no storage permission) and an `ACTION_SEND image/*` receiver on the exported launcher activity. No `READ_MEDIA_IMAGES`, no camera, no MediaProjection.
- **Image handling:** read once to extract text, never persisted or copied. Recognized text flows into the same append-only, user-confirmed evidence path as manual/paste entry (nothing is force-matched).
- **Third-party SDK containment:** ML Kit's bundled `datatransport` telemetry cannot transmit — the absent INTERNET permission blocks it at the OS level. This is an intentional defense-in-depth benefit of the zero-network posture.
- **Deliberately NOT built** (documented for reviewers/contributors): AccessibilityService or NotificationListener scraping of the ChatGPT/Claude apps — technically possible but a Google Play policy violation (accessibility-API misuse; 2026 enforcement) and against provider ToS. Demeter never reads another app's screen or notifications.
