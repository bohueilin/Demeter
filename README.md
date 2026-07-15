<p align="center">
  <img src="docs/demeter-hero.png" alt="Demeter — intelligent stewardship of your AI resources" width="100%">
</p>

<h1 align="center">Demeter</h1>

<p align="center"><strong>Know what remains. Use it before it resets.</strong></p>

<p align="center">
  A local-first Android app that tracks your <b>ChatGPT</b> and <b>Claude</b> subscription usage
  allowances — showing what capacity is left, when it resets, and reminding you before it slips
  away. Every number comes from evidence <b>you</b> provide, and nothing ever leaves your device.
</p>

<p align="center">
  <img alt="platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-29-3DDC84">
  <img alt="language" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="ui" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4">
  <img alt="network" src="https://img.shields.io/badge/network-none-brightgreen">
  <img alt="tests" src="https://img.shields.io/badge/tests-25%20passing-brightgreen">
</p>

---

## The idea

In Greek myth, **Demeter** is the goddess of the harvest — she who knows the seasons, tends what
grows, and gathers the yield *before it is lost*. Your AI subscription behaves the same way:
allowances accumulate and **reset on a cycle**, and whatever you don't use before the reset is
simply gone.

Demeter (the app) helps you harvest what you're already paying for:

| The myth | In the app |
|---|---|
| **Harvest** | Use your allowance before a reset takes it back |
| **Seasons** | Session · weekly · monthly reset cycles |
| **Stewardship** | Calm, truthful reminders — never nagging, never guessing |
| **Abundance & scarcity** | See exactly what remains, per account |
| **Wisdom** | The app shows *how old* its data is, and never invents a number |

> The defining constraint: **there is no public API for consumer-plan usage.** OpenAI and Anthropic
> don't expose your ChatGPT Plus / Claude Pro-Max allowance to third parties, and scraping their apps
> is against their terms. So Demeter is built around a different promise — *truth over false
> precision.* You supply the evidence; the app is honest about what it knows and how fresh it is.

---

## What it does

- **🌾 One glanceable dashboard.** Every account shows a status-colored ring for remaining capacity,
  the reset countdown, and orthogonal state at a glance — *healthy / use-soon / urgent* is separate
  from *fresh / stale*, so a card can truthfully say "60% left" **and** "last updated 2 days ago."
- **📸 Import from a screenshot.** Snap your provider's usage screen and Demeter reads it with
  **on-device OCR** — parsing multi-window screens (Claude's session/weekly/model limits, ChatGPT's
  weekly limit + reset checkpoints) into separate, editable windows. You can also paste the text or
  enter values by hand.
- **🔔 Truthful reminders.** Best-effort local reminders before each reset, with lead times from
  **7 days down to 1 hour**. A reminder based on stale data says so ("you had 60% left as of 2d
  ago") instead of pretending it's live. Nothing is ever a false alarm.
- **🗂️ Multiple accounts.** Track several ChatGPT and Claude accounts side by side, each with its
  own windows and reminder rules.

---

## Privacy is the architecture, not a setting

Demeter's privacy isn't a toggle you trust — it's enforced by what the app **structurally cannot do**:

- **No `INTERNET` permission.** Verified in the merged manifest. Data *cannot* leave the device over
  the network — not by the app, not by any bundled SDK.
- **No accounts, no sign-in, no provider credentials — ever.** The app never asks for your ChatGPT
  or Claude password or an API key. That's a hard product rule, not a default.
- **On-device OCR.** Screenshot recognition uses a **bundled** ML Kit model that ships inside the
  APK — no cloud call, no model download. The image is read once and never stored.
- **Locked down by default.** Backups/device-transfer excluded, notifications hide their details on
  the lock screen, and an optional **Privacy Mode** applies `FLAG_SECURE` (blocks screenshots and
  redacts the app in the recents switcher).
- **Your data, your call.** Everything lives in a local database you can **export** (JSON) or
  **delete** (one tap wipes every account, window, and reminder, and cancels all alarms).

The only two permissions the app requests: `POST_NOTIFICATIONS` (asked contextually, only when you
enable a reminder) and `RECEIVE_BOOT_COMPLETED` (to reschedule reminders after a reboot).

---

## How it works

```
Screenshot / paste / manual  ──▶  on-device OCR + parser  ──▶  append-only evidence (Room)
                                                                       │
      status ring · reset countdown · freshness  ◀── dashboard ◀───────┤
                                                                       │
      inexact AlarmManager  ◀── reconciler ◀── reminder policy (pure) ◀─┘
                    │
                    └─▶ receiver re-checks state ─▶ posts (or suppresses) the notification
```

Reminders use **inexact** alarms (no exact-alarm permission, no persistent foreground service), so
Android may deliver them a little late to save battery — the app is upfront that these are *planning
reminders, not alarms*. When an alarm fires, the receiver re-evaluates the latest evidence before
posting, so stale data can only *suppress* a reminder, never fire a wrong one. All of it is derived
from a single source of truth in Room and is idempotently repaired after boot, app update, or a
clock/timezone change.

---

## Architecture & stack

- **Kotlin · Jetpack Compose · Material 3** with a pinned brand palette (status colors stay correct
  regardless of dynamic theming).
- **Pure-JVM `:domain` module** — reminder policy, dashboard policy, and models compile *without*
  Android, so the logic is unit-testable against a fake clock. **16 tests.**
- **`:app` module** — Room (append-only evidence, derived reminder state), on-device OCR
  (`OcrReader` + `UsageScreenParser`), platform integration (AlarmManager, notifications, boot
  repair), and the Compose UI. The multi-provider screenshot parser has **9 tests** replaying real
  Claude and ChatGPT usage screens.

```
apps/android/
├── domain/   # pure-JVM: reminder & dashboard policy, models  (no Android deps)
└── app/      # Room, OCR, platform (alarms/notifications), Compose UI
docs/          # privacy policy, security, Google Play data-safety mapping, release checklist
```

---

## Build & run

Requires JDK 21 and the Android SDK (compileSdk 35).

```bash
cd apps/android

# Debug build + install on a connected device/emulator
./gradlew :app:installDebug

# Run the full test suite
./gradlew :domain:test :app:testDebugUnitTest

# Signed release (needs your own keystore + keystore.properties — see below)
./gradlew :app:assembleRelease :app:bundleRelease
```

**Signing:** the release keystore is intentionally **not** in this repository. To build a signed
release, create `apps/android/keystore.properties` (and a keystore it points to) with
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Debug builds need none of this.

---

## Where this is heading — the Demeter mythos

> *This section is vision and identity, not a description of shipped features.*

Demeter is meant to feel like it belongs to a **mythology-inspired family of tools** — each named for
a Greek figure whose domain mirrors its role, sharing one cinematic aesthetic (olive / bronze / gold,
Greek geometry over modern system diagrams) so the visual language tells the same story as the
software: *intelligent stewardship, elegant orchestration, and systems that cultivate resources
rather than merely consume them.*

Aspirational directions for the identity and the roadmap:

- **Seasonal releases** — each release themed around a season (Spring → Harvest → Winter),
  symbolizing maturity rather than raw feature count.
- **Consistent visual language** — hero art, a minimalist wheat + network-node emblem, the premium
  app icon, and architecture graphics that render resource pipelines as irrigation canals.
- **A wider pantheon** (ideas, not commitments): Athena (planning & reasoning), Hermes (messaging &
  routing), Hephaestus (build & execution), Hestia (runtime stability), Themis (policy & governance).

Concrete near-term product roadmap for *this* app lives in [`plans/`](plans/) and the
[design addendum](Demeter_PRD_v2.2_Design_Revision_Addendum.md): screenshot-OCR polish, optional
email reminders (which would require opt-in cloud + network, deliberately deferred), and a
user-hosted "bridge" for the org-tier usage APIs that *do* exist.

---

## Disclaimer

Demeter is an **independent** app and is **not affiliated with, endorsed by, or sponsored by OpenAI,
Anthropic, or Google.** Provider names and logos are used only to identify which service an account
belongs to. Every usage figure in the app is **your own entered evidence**, not a
provider-authoritative number, and reminders are planning aids, not guarantees.

---

<p align="center"><i>Cultivate what you're paying for. Harvest it before it resets.</i></p>
