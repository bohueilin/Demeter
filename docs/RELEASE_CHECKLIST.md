# Demeter — Google Play Release Checklist

**App:** Demeter — AI Usage Monitor (`com.demeter.app`, minSdk 29, targetSdk 35)
**Starting point:** debug APK verified on emulator. Release build type currently has `isMinifyEnabled = false`, no signing config, `versionCode = 1`, `versionName = "0.1.0-demo"`.
**Module:** `apps/android/app/build.gradle.kts` is the only Gradle file that changes below.

Work through the phases in order. Each phase assumes the previous one is done.

---

## Phase 1 — Version and build configuration

- [ ] **1.1 Set the release version.** In `apps/android/app/build.gradle.kts`, change `versionName` from `"0.1.0-demo"` to `"0.1.0"`. Keep `versionCode = 1` for the first upload.
- [ ] **1.2 Adopt the versionCode bump policy** (record it here; enforce it forever):
  - `versionCode` increments by **+1 on every artifact uploaded to Play Console**, even internal-testing builds and even if `versionName` is unchanged. Play rejects any AAB whose versionCode it has seen before — there is no reuse.
  - `versionName` follows semver: patch (`0.1.1`) for fixes, minor (`0.2.0`) for features, `1.0.0` when leaving early access. `versionName` is cosmetic to Play; `versionCode` is the real ordering.
  - Never decrement. If a build is broken, abandon that versionCode and move on.
- [ ] **1.3 Confirm the manifest is release-correct** (`apps/android/app/src/main/AndroidManifest.xml`):
  - Only `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED` are requested; `INTERNET` and `ACCESS_NETWORK_STATE` carry `tools:node="remove"`.
  - `android:allowBackup="false"` is present.
- [ ] **1.4 Add data extraction rules** (currently missing — memory/PRD calls for them alongside `allowBackup=false`): create `apps/android/app/src/main/res/xml/data_extraction_rules.xml` excluding all device-transfer and cloud-backup content, and reference it via `android:dataExtractionRules` on `<application>`. On Android 12+ `allowBackup` alone does not govern device-to-device transfer.
- [ ] **1.5 Verify the merged manifest** after any change: `./gradlew :app:processReleaseMainManifest`, then inspect `apps/android/app/build/intermediates/merged_manifests/release/` — confirm INTERNET is absent (this is the app's headline privacy claim; a library must not smuggle it back in).

## Phase 2 — Release signing (upload keystore)

- [ ] **2.1 Create the upload keystore locally** at `apps/android/keystore/upload.jks`:
  ```
  keytool -genkeypair -v -keystore "apps/android/keystore/upload.jks" \
    -alias upload -keyalg RSA -keysize 2048 -validity 10000
  ```
  Use a strong password; the same or separate key password is fine — record both.
- [ ] **2.2 Back up the keystore immediately.** Copy `upload.jks` + its passwords to at least one location off this machine (password manager attachment, encrypted external drive). With Play App Signing, Google holds the *app signing* key and a lost *upload* key can be reset via support, but that is a multi-day outage — treat the backup as mandatory anyway.
- [ ] **2.3 Never commit the keystore.** Add to `apps/android/.gitignore` (create it if absent):
  ```
  keystore/
  keystore.properties
  ```
  (`local.properties` should already be ignored; verify.)
- [ ] **2.4 Create `apps/android/keystore.properties`** (also excluded from VCS by 2.3):
  ```
  storeFile=keystore/upload.jks
  storePassword=<store password>
  keyAlias=upload
  keyPassword=<key password>
  ```
- [ ] **2.5 Wire signing into the build.** In `apps/android/app/build.gradle.kts`, load `keystore.properties` from the root project dir, define `signingConfigs.create("release")` from it, and set `signingConfig = signingConfigs.getByName("release")` on the `release` build type. Guard with a file-exists check so CI/clean checkouts still build debug.
- [ ] **2.6 Sanity-check:** `git status` must show no keystore or properties file as trackable before proceeding.

## Phase 3 — R8/minify + resource shrinking

- [ ] **3.1 Enable shrinking** in the `release` block:
  ```kotlin
  isMinifyEnabled = true
  isShrinkResources = true
  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
  ```
  Create an empty `apps/android/app/proguard-rules.pro` if it does not exist. Room (with KSP) and Compose ship consumer keep rules, so it should stay empty unless 3.4 fails.
- [ ] **3.2 Build the release APK for on-device verification:** `./gradlew :app:assembleRelease`.
- [ ] **3.3 Install on the emulator (or a Pixel):** `adb install -r apps/android/app/build/outputs/apk/release/app-release.apk`. If a debug build is installed, uninstall it first (`adb uninstall com.demeter.app`) — signatures differ.
- [ ] **3.4 Walk the full loop on the minified build** (this is the R8 smoke test — every step exercises code R8 could have stripped):
  - [ ] Fresh install → onboarding completes without crash.
  - [ ] Load/enter the sample account data (ChatGPT + Claude accounts with usage) → dashboard renders; Room reads/writes work (a Room R8 break crashes here).
  - [ ] Create a reminder → notification permission prompt appears contextually (first reminder, not app launch); grant it.
  - [ ] Set the reminder near-term, background the app → the reminder notification fires with the expected nickname/percentage text (`ReminderAlarmReceiver` survives R8; it's manifest-registered so it should, but verify).
  - [ ] Reboot the emulator (`adb reboot`) → reminders reschedule (`BootAndTimeReceiver` fires; check with `adb logcat` or a due reminder).
  - [ ] Kill and relaunch the app → all entered data persists.
- [ ] **3.5 Confirm zero network:** with the release build in the foreground, `adb shell dumpsys package com.demeter.app | grep -i internet` returns nothing granted.
- [ ] **3.6 If anything in 3.4 broke,** read `apps/android/app/build/outputs/mapping/release/missing_rules.txt` and add only the suggested keeps to `proguard-rules.pro`; re-run 3.2–3.4.

## Phase 4 — Build the release AAB

- [ ] **4.1** `./gradlew :app:bundleRelease` → `apps/android/app/build/outputs/bundle/release/app-release.aab`.
- [ ] **4.2 Keep the mapping file** (`build/outputs/mapping/release/mapping.txt`) with this versionCode — you upload it to Play for readable crash stacks, and you cannot regenerate it later for the same bytes.

## Phase 5 — Play Console: app setup

- [ ] **5.1 Create the app** in Play Console: name **"Demeter — AI Usage Monitor"**, app (not game), **free**, default language en-US.
- [ ] **5.2 Host the privacy policy** at a public URL. Source of truth is `docs/PRIVACY_POLICY.md` — publish it (GitHub Pages / any static host) and paste the URL in **App content → Privacy policy**. The URL must be reachable at review time and cannot be a Google Doc behind auth.
- [ ] **5.3 Complete the Data safety form** using `docs/google-play/DATA_SAFETY_MAPPING.md` as the answer key (write that mapping doc first if it doesn't exist yet). For this app the truthful answers are: **no data collected, no data shared, no data transmitted off device** — there are no network calls, no accounts, no analytics, no crash SDKs. Do not reflexively check "app activity"; user-entered allowances never leave the device, and Data safety only covers data leaving the device.
- [ ] **5.4 Content rating questionnaire:** utility/productivity, no user-generated content, no violence/gambling/etc. Expect "Everyone".
- [ ] **5.5 Target-audience declaration:** 18+ (or 13+ at minimum). Do **not** include children — that triggers Families policy review this app has no reason to enter. Answer "not appealing to children".
- [ ] **5.6 Ads declaration:** no ads. **News app:** no. **COVID app:** no. **Data deletion:** app is local-only; deletion = uninstall / in-app delete (state this in the form).
- [ ] **5.7 Store listing:** short + full description (lead with local-only/no-account privacy — it is the differentiator), screenshots from the Pixel emulator (portrait, min 2), 512×512 icon, 1024×500 feature graphic. Use text-only "compatible with ChatGPT/Claude" wording — **no OpenAI/Anthropic/Google logos or implied affiliation** anywhere in listing assets.

## Phase 6 — Internal testing first (always)

- [ ] **6.1 Upload `app-release.aab` to the Internal testing track** — never straight to production. Accept Play App Signing enrollment on first upload (Google generates the app signing key; your `upload.jks` remains the upload key).
- [ ] **6.2 Upload `mapping.txt`** for this artifact (App bundle explorer → this version → upload deobfuscation file).
- [ ] **6.3 Add yourself (bohueilin@gmail.com) as an internal tester**, opt in via the tester link, and install from Play — this catches Play-delivered-split issues an adb install cannot.
- [ ] **6.4 Re-run the Phase 3.4 loop on the Play-installed build.**

## Phase 7 — Closed testing gate (new personal developer accounts)

- [ ] **7.1 Check whether the rule applies:** personal Play developer accounts created after Nov 13, 2023 must run a **closed test with at least 12 testers continuously enrolled for at least 14 days** before applying for production access. Organization accounts are exempt. Assume it applies to a new personal account.
- [ ] **7.2 Create a Closed testing track**, promote the internal build to it, and recruit 12+ real testers (friends/colleagues; a Google Group email list is the easiest management path). All 12 must stay opted-in for the full 14 days — recruit 15+ for buffer.
- [ ] **7.3 During the 14 days,** ship at least one update if you have fixes (bump versionCode per 1.2), and note tester feedback — Play asks about testing learnings in the production-access application.
- [ ] **7.4 Apply for production access** in Play Console once eligible, answering the questionnaire about your test.

## Phase 8 — Pre-launch report

- [ ] **8.1 Review the pre-launch report** (generated automatically for each track upload) after every upload: crashes, ANRs, security warnings, screenshots across devices.
- [ ] **8.2 Specifically check:** no crash on devices without the app's notification permission granted; layout sanity on small screens (app was built against Pixel 10 Pro XL); no accessibility blockers flagged.
- [ ] **8.3 Ignore expected noise:** the report's robo-crawler cannot meaningfully complete onboarding data entry — "no content" warnings on data-entry screens are acceptable; crashes are not.

## Phase 9 — Production with staged rollout

- [ ] **9.1 Promote the tested build to Production with a staged rollout** — start at **10%** (small first release; 20% is also fine). Do not use 100% on the first production release.
- [ ] **9.2 Hold each stage ~2–3 days** while watching vitals (Phase 10). Escalate 10% → 25% → 50% → 100% only if crash/ANR rates stay clean.
- [ ] **9.3 If a bad bug surfaces,** halt the rollout (Play Console → Releases → halt), fix, bump versionCode, and start a new staged release. You cannot re-expand a halted release.
- [ ] **9.4 Write user-facing release notes** for the listing ("What's new") — keep the local-only framing consistent.

## Phase 10 — Post-launch monitoring

- [ ] **10.1 Android vitals** (Play Console → Quality → Android vitals), weekly at minimum for the first month:
  - Crash rate: stay under the 1.09% bad-behavior threshold (over it, Play demotes discoverability).
  - **ANR rate: stay under 0.47%.** Demeter's risk surface is the two broadcast receivers — `BootAndTimeReceiver` rescheduling reminders on `BOOT_COMPLETED`/`TIME_SET` must not do Room I/O on the main thread; if ANRs cluster there, move work to `goAsync()` + coroutine or WorkManager.
- [ ] **10.2 Crash triage without a crash SDK:** the app deliberately has no crash reporting, so Play Console crash reports (deobfuscated via the uploaded mapping.txt) are the **only** signal. Check them; there is no backup channel.
- [ ] **10.3 Ratings/reviews:** reply to reviews (Play notifies on new ones). Expected themes for this app: inexact reminder timing (Doze batching — by design, document in listing/FAQ) and requests for auto-fetching usage (out of scope for v1's no-network rule; do not quietly add INTERNET in a patch).
- [ ] **10.4 Before every subsequent release:** re-run Phase 3.4's loop on the release build, re-check the merged manifest for INTERNET (1.5), bump versionCode (+1), and archive the new mapping.txt.

---

*Standing rules for every release of this app: no INTERNET permission, no credential prompts, no analytics/crash SDKs, allowBackup stays false. Any change to those is a product decision, not a release task.*
