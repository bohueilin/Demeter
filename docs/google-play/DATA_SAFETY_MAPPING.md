# Demeter — Google Play Data Safety Form Mapping (v0.1)

Status: draft for review. This document maps Demeter v0.1 (local-only, Kotlin/Compose,
package `com.demeter.app`) to the Play Console **Data safety** form, with the evidence behind
each answer. Companion listing copy: `docs/google-play/STORE_LISTING.md`.

Governing principle (PRD v2.1 §22.1.4): answers must reflect **actual app and SDK behavior**,
re-verified against the release manifest and dependency list before every submission.

---

## 1. Headline answer: No data collected, no data shared

**Form question: "Does your app collect or share any of the required user data types?"**
**Answer: No.**

### Why "No" is the honest answer, per the form's own definitions

Google Play's Data safety guidance (Play Console Help, "Provide information for Google Play's
Data safety section") defines the key terms roughly as follows (paraphrased — re-read the live
help page at submission time):

- **Collected** — user data the app transmits **off the user's device** (to the developer or a
  service provider). Data that is only processed and stored **on the device** and never sent off
  it does **not** count as collected. (Google's on-device-access exemption.)
- **Ephemeral processing** — even off-device data can be exempt if processed only in memory; not
  relevant here, since nothing goes off-device at all.
- **Shared** — user data transferred **to a third party**, either off the device or via
  on-device transfer to another app.

Demeter v0.1 stores everything the user enters (account nicknames, usage percentages, reset
times, reminder settings, history) in a **local Room database** and transmits nothing anywhere.
Under Play's definitions, that means **no data is collected and no data is shared** — even
though the app obviously *processes* user-entered data on-device. This is not a loophole; it is
exactly the case the on-device exemption was written for.

### Evidence

| Claim | Evidence to verify at release time |
|---|---|
| No network transmission is possible | `INTERNET` permission absent from the merged release manifest (it is being removed for v1); no `usesCleartextTraffic`, no network-security-config needed |
| No network code paths | No HTTP client (OkHttp/Retrofit/Ktor), no Firebase, no Play Services SDKs in the dependency graph; grep release APK/AAB for socket/HTTP symbols |
| No third-party data-collecting SDKs | Dependency inventory is AndroidX only (Compose, Room, WorkManager/AlarmManager, Lifecycle); zero analytics, crash-reporting, or ads SDKs (hard product rule) |
| No accounts or identity | No sign-in of any kind; app never asks for provider passwords or API keys (hard product rule) |
| Data cannot leak via backup | `android:allowBackup="false"` plus `dataExtractionRules` excluding app data (backups disabled) |
| Manifest stays clean | Release CI fails if an unapproved dangerous/special permission appears after manifest merging (PRD §22.2) |

Keep this table's verification output in the release evidence package (PRD §22.1.20).

### Per-category walkthrough (all "Not collected")

For every Play data category — Location; Personal info; Financial info; Health and fitness;
Messages; Photos and videos; Audio; Files and docs; Calendar; Contacts; App activity; Web
browsing; App info and performance (crash logs, diagnostics); Device or other IDs — the answer
is **Not collected**, because nothing is transmitted off the device.

Two categories deserve an explicit internal note:

- **Personal info / App activity:** account nicknames and usage percentages are user-entered
  app content. They live only in the local database and in locally rendered notifications.
  Never transmitted → not "collected" under the form's definition.
- **App info and performance:** there is no crash reporting or diagnostics collection of any
  kind.

## 2. Security practices section

When the headline answer is "No data collected or shared," the Console form does not present
the security-practices questions. Documenting our position anyway (useful for reviewer notes
and for the privacy policy):

- **Data encrypted in transit:** **Not applicable — there is no transit.** The app performs no
  network transmission whatsoever (no `INTERNET` permission).
- **Data deletion path:**
  1. **In-app:** Settings → delete all data (wipes the local Room database and preferences).
     Per-account deletion also removes that account's data and history.
  2. **Uninstall:** removes the app's entire sandbox. Because backups are disabled
     (`allowBackup="false"` + data-extraction rules), no copy survives in Google or device
     backups — uninstall is genuinely complete deletion.

## 3. Account creation and deletion requirement

**Form question: "Does your app allow users to create an account?"**
**Answer: No.**

Demeter v0.1 has no app account, no sign-in, and no cloud identity. The Play account-deletion
policy (in-app deletion path + public web deletion URL) therefore **does not apply**, and no
deletion web form is required. "Accounts" inside Demeter are just local records the user
labels (nicknames for their own ChatGPT/Claude accounts) — they are app content, not
authentication accounts, and never touch the actual provider services.

If a future version adds any cloud/email/sync feature, this answer, the headline collection
answer, and the account-deletion URL requirement must all be redone before that release
(PRD §22.1.4–5).

## 4. Consistency obligations

- A **privacy policy URL is still mandatory** for every app, even with "No data collected."
  The policy must state the local-only behavior, the notification permission use, and the
  deletion paths above.
- The store listing, privacy policy, and this form must agree. The listing already states:
  local-only, no accounts, no analytics, no ads, notifications optional
  (see `STORE_LISTING.md`).
- Permissions declared must match: `POST_NOTIFICATIONS` (requested contextually when the user
  enables reminders) and `RECEIVE_BOOT_COMPLETED` (reschedule reminders after reboot). Neither
  involves data collection. No exact-alarm permission in v1 (PRD §22.2).

---

## Footer — Play launch realities checklist

- [ ] **Closed-test gate for new personal accounts.** Personal developer accounts created after
  Nov 13, 2023 must run a **closed test with at least 12 testers continuously opted in for
  14 days** before they can apply for production access. Recruit testers early; the 14-day
  clock only counts days with the required tester count opted in, and Google also reviews
  engagement before granting production. Verify the exact current threshold shown in your
  Play Console — Google has adjusted these numbers over time.
- [ ] **App-signing key choices.** Play App Signing is mandatory for new apps. Decide up front:
  let **Google generate the app signing key** (recommended; supports key upgrade later) or
  upload your own. Either way you sign uploads with a separate **upload key** — keep that
  keystore and its passwords backed up offline; an upload key can be reset via support, but
  losing organizational access to the developer account cannot. This choice is effectively
  permanent for the app's package name.
- [ ] **Target API requirements.** New apps and app updates must target an Android API level no
  more than one year older than the latest Android release (check the current floor and
  deadline in Play Console before submitting). PRD §22.1.7 mandates **targetSdk 37**; the
  demo build currently compiles/targets **SDK 35** and must be raised before any Play upload.
- [ ] **Format and native-lib requirements.** Upload an **Android App Bundle (.aab)**; if any
  64-bit native libraries are ever packaged, they must support **16 KB page sizes**
  (PRD §22.1.8). v0.1 should ship with no native code.
- [ ] **Pre-submission bundle:** privacy policy URL live; content-rating questionnaire
  submitted; this Data safety form entered exactly as above; store listing final; release
  evidence package (manifest diff, permission list, dependency/SDK inventory, this mapping)
  archived per PRD §22.1.20.

---

## Update (v1.1.0) — On-device screenshot OCR

The app added a screenshot-import feature backed by **Google ML Kit bundled Text Recognition** (`com.google.mlkit:text-recognition`). This does **not** change any Data safety answer:

- **Data collected / shared: still NONE (off device).** OCR runs entirely **on the device** with the model bundled in the APK — no image, text, or derived value is transmitted. Verified: the merged manifest has no `INTERNET` permission, so nothing can leave the device over the network; logcat confirms the recognizer loads the local model from `base.apk` with no Play Services download.
- **Images:** the app reads a user-picked (Photo Picker) or user-shared (share sheet) image once to extract text, then discards it. It is never stored in app storage or uploaded. No `READ_MEDIA_IMAGES` / storage permission is requested — the Android Photo Picker grants access only to the single chosen image.
- **Third-party SDK note:** ML Kit bundles Google's `datatransport` telemetry component. Because the app declares **no INTERNET permission**, this component is structurally unable to send any data. If a reviewer asks about ML Kit/Google SDKs in the SDK index, the honest position is: on-device inference only, no data transmitted, network egress blocked at the manifest level.
- **Permissions unchanged:** `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED` only.
