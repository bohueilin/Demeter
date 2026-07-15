# Demeter — Android app (demo build)

Local-first Android app that monitors AI usage allowances and reset windows across
ChatGPT (OpenAI) and Claude (Anthropic) accounts, with truthful, freshness-aware
reset reminders. Built per `Demeter_PRD_Android_Pixel_10_Pro_XL_v2.1.md` +
`Demeter_PRD_v2.2_Design_Revision_Addendum.md` (external design review incorporated).

## What this demo build covers (the Tier 1 local consumer loop)

- Onboarding with zero permission prompts; local-first (no account, no cloud).
- Add up to 3 accounts per provider; providers shown as text labels (no logos).
- Evidence entry: manual chips/slider + paste-assist parser (assistive autofill —
  best-effort, never force-matched, everything editable).
- Append-only evidence history in Room; corrections never rewrite history.
- Today dashboard: orthogonal status axes (usage / evidence freshness / source /
  reminder health), stable card order, "Suggested next" banner (no auto-reorder).
- Unknown limits show the reset countdown — never an invented percentage.
- Reminder rules per window: lead times {48,24,12,8,4,2,1}h, two-control model
  (evidence policy: current-only vs last-known + separate unknown-remaining opt-in),
  thresholds, quiet hours (22:00–07:00) with a 15-min-before-reset floor.
- Two reminder intents: allowance reminders (current data) vs check-usage reminders
  (stale/unknown data) — with a live preview in the editor.
- Real scheduling: one-shot inexact `AlarmManager` + delivery-time re-evaluation from
  Room (staleness can only suppress, never wrongly fire), contextual two-beat
  `POST_NOTIFICATIONS` ask, boot/update/time-change reconciliation, notification
  tag-reuse (retries update instead of duplicating).
- Plain-language audit trail: delivered (on time / delayed by Android), suppressed
  (with reason), blocked, repaired.
- Sample data mode for instant demo.

## Deliberately out of demo scope (per plan Phases 6–8)

Screenshot OCR (ML Kit), Sharesheet import, cloud/email/Sign-in-with-Google,
user-owned bridge, widgets, Play release hardening.

## Demo-build deviations from the v1 plan (temporary, logged)

- Two Gradle modules (`:app`, `:domain` pure JVM) instead of the slim 8-module graph.
- Manual DI (AppContainer) instead of Hilt; SharedPreferences instead of Proto DataStore.
- compileSdk/targetSdk 35 (not 37); strings not yet externalized; simplified fit
  heuristics (adaptive grid) instead of the full candidate/measurement engine.

## Build & run

```bash
export JAVA_HOME=$(/opt/homebrew/bin/brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
./gradlew :domain:test          # pure reminder-policy + dashboard-policy tests
./gradlew :app:assembleDebug    # APK at app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Demo walkthrough: onboarding → "Try with sample data" → open the stale Work account →
Update usage → set a reminder (2h lead, "Use last known data") → allow notifications →
Settings → "Send test notification" → check the account's Activity log for the audit trail.
