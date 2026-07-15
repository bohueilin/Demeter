# Demeter — AI Usage & Reset Monitor

**Product Requirements Document (PRD) — Android Edition**  
**Version:** 2.1  
**Status:** Engineering-ready, subject to provider-access gates  
**Date:** July 12, 2026  
**Primary platform:** Android phone, native Kotlin and Jetpack Compose  
**Reference validation device:** Google Pixel 10 Pro XL, Android 17 (API level 37)  
**Compatibility target:** Android 10+ (minimum API 29); compile and target API 37  
**Secondary form factors:** Android tablets and foldables through an adaptive architecture; iOS after Android product validation  
**Audience:** Product, Design, Android, Backend, Security, Privacy, QA, and coding agents such as Claude Code or Codex

> This document supersedes the iOS platform assumptions in Demeter PRD v1.0 and the Pixel 10 reference-device assumptions in Android PRD v2.0. Product behavior that is platform-neutral remains unchanged; implementation, design, permission, background-work, release, and validation requirements are Android-specific. This v2.1 revision qualifies the Google Pixel 10 Pro XL as the owned physical reference device and adds large-handset ergonomics, runtime viewport capture, 60/120 Hz validation, display-filter checks, energy-normalized battery evidence, and mandatory smaller-phone counter-coverage.

---

## 0. Executive decision summary

Demeter is a privacy-forward Android application that gives a user one glanceable place to monitor usage allowances, reset windows, and utilization across as many as three OpenAI/ChatGPT accounts and three Anthropic/Claude accounts.

The app must provide:

1. A responsive dashboard that changes layout, card size, typography, and information density for one through six monitored accounts.
2. Reminder notifications at user-selected lead times of 48, 24, 12, 8, 4, 2, or 1 hour before a known reset.
3. Conditional reminders only when an allowance still appears to have unused capacity, with explicit freshness and confidence semantics.
4. Optional email reminders sent to a verified destination associated by the user with each monitored account.
5. A best-in-class native Android experience using Jetpack Compose, Material 3 Expressive, adaptive layouts, platform-standard permissions, and rigorous accessibility.
6. A physical-device validation path centered on the user's Google Pixel 10 Pro XL, supplemented by automated emulator coverage for older Android versions and larger form factors.

### 0.1 Binding platform decisions

| Area | Android v2.1 decision |
|---|---|
| Client | Native Android; Kotlin; Jetpack Compose |
| Design system | Material 3 Expressive with Demeter brand tokens; dynamic color is optional and user-controlled |
| Adaptive UI | Material 3 Adaptive and window size classes; no phone-model-specific hard-coded layout |
| Minimum OS | Android 10, API 29 |
| Compile/target SDK | Android 17, API 37 |
| Primary physical validation | Google Pixel 10 Pro XL on the latest stable Android 17 build available to the device |
| Reference hardware profile | 6.8-inch 20:9 LTPO OLED, 1344 × 2992, 1–120 Hz, 16 GB RAM; physical specifications inform validation only and never select a layout |
| Account capacity | Up to three OpenAI/ChatGPT and three Anthropic/Claude accounts |
| Consumer data | Manual input, pasted usage text, Android Photo Picker, and user-initiated share/import |
| API organization data | Optional user-owned bridge calling official provider APIs |
| Reminder timing | One-shot inexact `AlarmManager` alarms plus WorkManager reconciliation |
| Exact-alarm permission | Not requested in v1; Demeter must not claim exact-to-the-minute delivery |
| Notification permission | Request `POST_NOTIFICATIONS` contextually only after a user enables reminders |
| Email identity | Optional Sign in with Google through Credential Manager, plus verified email destinations |
| Local secrets | Android Keystore; no provider passwords, cookies, or administrative API keys |
| Persistence | Room for normalized history; DataStore for preferences and migration-safe settings |
| Screenshot OCR | On-device ML Kit Text Recognition; source image discarded by default |
| Distribution | Android App Bundle through Google Play staged tracks; direct debug builds for local testing |
| Quality bar | Google Play quality compliance, Android adaptive quality, accessibility, low battery impact, and polished Material interaction |

### 0.2 Critical feasibility boundary

“ChatGPT usage” and “Claude usage” are not one universal token counter. Consumer plans can have separate model, feature, session, weekly, task, credit, or dynamic allowances. Current provider documentation exposes useful usage information inside provider products, but does not document a general third-party API that lets an independent Android app automatically read every individual consumer-plan allowance and reset time.

Demeter therefore uses three explicit trust tiers:

| Tier | Data source | v1 status | Automation | Credential handling |
|---|---|---:|---:|---|
| 1. Local consumer mode | User-entered values, pasted text, Android Photo Picker, or Android Sharesheet import | Required | Assisted, not continuous | No provider credentials |
| 2. User-owned bridge | Official OpenAI or Anthropic organization usage APIs called from infrastructure controlled by the user | Required for advanced/API users | Continuous when reachable | Keys stay in the user's bridge, never in the Android app or Demeter cloud |
| 3. Provider-approved connection | Official OAuth or dedicated consumer-usage API with appropriate scopes | Future, feature-gated | Continuous | Standards-based delegated access |

Demeter must **not** collect provider passwords, copy provider session cookies, run a hidden authenticated WebView, inspect private endpoints, scrape provider interfaces, or place provider administrative keys in the Android application. Any provider adapter that lacks written authorization or a documented public API remains disabled in production.

### 0.3 Android-specific operating constraints

1. Android 13+ requires runtime notification consent for ordinary app notifications.
2. Reminder delivery is inherently best effort under Doze, App Standby, battery restrictions, device reboot, force-stop, and manufacturer power management.
3. Demeter's lead times are planning reminders, not alarm-clock-grade events; v1 therefore uses inexact alarms and does not request exact-alarm access.
4. WorkManager is for durable reconciliation and refresh, not exact notification delivery.
5. Android 17 gates broad local-area-network access for apps targeting API 37. Demeter requests local-network permission only when a user explicitly connects to a self-hosted LAN bridge; public HTTPS bridges do not require it.
6. Notification channels, permission state, battery restrictions, and reminder freshness are visible in Settings and the reminder audit trail.
7. The app remains useful in local manual mode when background work, network access, notifications, email, or the bridge is unavailable.

### 0.4 What changed from the iOS PRD

| iOS v1.0 assumption | Android v2.1 replacement |
|---|---|
| Swift and SwiftUI | Kotlin and Jetpack Compose |
| Apple design conventions | Material 3 Expressive, Android adaptive guidance, edge-to-edge layouts |
| iPhone/iPad validation | Pixel 10 Pro XL physical validation plus Android emulator matrix |
| Dynamic Type and VoiceOver | Android font scaling, TalkBack, Switch Access, contrast and animation settings |
| UserNotifications | NotificationManager, notification channels, and `POST_NOTIFICATIONS` |
| BackgroundTasks | WorkManager for durable best-effort work |
| iOS local notification scheduling | Inexact AlarmManager one-shot alarms plus WorkManager reconciliation |
| Keychain/Secure Enclave | Android Keystore, hardware-backed where available |
| Vision OCR and PhotosPicker | ML Kit Text Recognition and Android Photo Picker |
| LocalAuthentication | BiometricPrompt |
| Sign in with Apple | Sign in with Google through Credential Manager |
| APNs | Firebase Cloud Messaging only for optional remote notifications |
| App Store/TestFlight | Google Play internal, closed, open, and production tracks |
| Apple review/privacy labels | Google Play Data safety, account deletion, target API, permission, and AAB requirements |
| WidgetKit/ActivityKit | Android App Widgets and, only when justified, live-update surfaces in later phases |

### 0.5 Product naming decision

Use **“Demeter — AI Usage Monitor”** in product surfaces. “Token Usage Monitoring” may be used as an internal project description, but the user-facing product must use the broader term **usage allowance**, because consumer limits are not consistently token-denominated.

### 0.6 Pixel 10 Pro XL revision impact

Updating the owned reference device is **not** only a text replacement. The architecture, permissions, provider-access boundary, data model, reminder engine, and Google Play plan remain valid, but the reference UX and qualification plan must account for a materially taller and wider handset.

| Area | v2.1 decision |
|---|---|
| Portrait classification | Treat the device as a phone and compute the live app window. Do not infer medium width from the “XL” product name or 6.8-inch diagonal. |
| Dashboard opportunity | Use the additional portrait room to improve spacing and keep more primary account state above the fold; do not inflate decorative elements simply to consume space. |
| One-handed ergonomics | Keep frequent and primary actions reachable near the lower portion of the screen and provide alternatives to top-corner-only actions. |
| Landscape behavior | Evaluate width and height classes together. A wide but short landscape window must not automatically become a two-pane experience. |
| Display validation | Qualify default and large display/font scales, Smooth Display enabled and disabled, and relevant Pixel comfort/accessibility display filters. |
| Performance interpretation | Measure at both 60 Hz and high refresh. The 16 GB reference device must not become the minimum performance assumption. |
| Battery interpretation | Retain the percentage target but also record wakeups, CPU time, worker duration, network bytes, and estimated energy because a 5200 mAh battery can hide inefficient work. |
| Compatibility counterweight | Add a current-API compact-phone emulator near 400 dp usable width so the Pro XL does not conceal smaller-phone defects. |

Official Pixel 10 Pro XL hardware facts used for validation are recorded in Sections 11.7, 18.15, and 32.1. Runtime layout decisions remain based on the actual app window reported by Android.

---

## 1. Product vision

Demeter helps people deliberately use the AI capacity they already pay for without repeatedly opening several apps, switching accounts, or manually remembering multiple reset clocks.

The desired emotional quality is **calm control**, not alarm, gamification, or quota anxiety. The interface should feel like a beautifully designed instrument: immediate at a glance, trustworthy under scrutiny, and quiet when no action is needed.

### One-sentence value proposition

> Demeter shows what AI capacity remains, when it resets, and which account deserves attention next.

### Product thesis

Provider reports or the user supplies evidence; Demeter normalizes it; freshness and confidence determine what the app may claim; the reminder engine decides whether to alert; the audit trail explains why.

---

## 2. Problem statement

Power users increasingly maintain multiple AI accounts for personal work, employers, consulting, testing, coding, or plan separation. Usage limits and reset windows are fragmented across provider products and may differ by model, feature, account, plan, and time window.

Today the user must:

- Open each provider product separately.
- Switch among accounts or workspaces.
- Find a usage or allowance surface that may differ by plan.
- Interpret several unrelated reset windows.
- Remember to use remaining allowance before it expires.
- Manually communicate or remind themselves through another channel.

This creates four product problems:

1. **Fragmentation:** no unified, multi-account view.
2. **Lost value:** paid or limited allowance resets while meaningful capacity remains.
3. **Low trust:** limits can be dynamic, partial, or unavailable; false precision would be misleading.
4. **Notification mismatch:** a pre-scheduled notification can become stale if the user consumes capacity elsewhere after the last sync.

---

## 3. Target users and jobs to be done

### Primary persona: multi-account AI power user

A person who uses ChatGPT and Claude daily across personal, work, client, or testing accounts and wants to maximize practical value from each plan.

**Job to be done:**

> When I have several AI accounts with different usage windows, help me see which allowance is likely to reset with capacity left so I can choose where to work next.

### Secondary persona: API or platform operator

A developer or small-team operator who needs a mobile view of token consumption, cost, budgets, and reset periods from OpenAI or Anthropic organization APIs.

**Job to be done:**

> When I am away from my workstation, show me current API consumption and budget risk without exposing administrative credentials on my phone.

### Tertiary persona: privacy-sensitive professional

A user who wants utility but will not grant a third party access to AI conversations, credentials, or account sessions.

**Job to be done:**

> Give me useful monitoring and reminders while collecting the least data possible and making every data source obvious.

---

## 4. Goals, success criteria, and non-goals

### 4.1 Product goals

1. Support up to three monitored OpenAI/ChatGPT accounts and three monitored Anthropic/Claude accounts.
2. Make the next useful action understandable within three seconds of opening the app.
3. Support multiple concurrent usage windows per account rather than flattening unlike quotas into one misleading number.
4. Schedule configurable reset reminders with clear freshness and delivery-timing semantics.
5. Send optional email reminders to verified destinations.
6. Keep provider secrets out of the Android application and Demeter cloud.
7. Deliver a polished Android-native experience that adapts across account counts, font scales, window sizes, themes, navigation modes, and accessibility services.
8. Preserve a clean adapter architecture so approved provider integrations can be added without redesigning the product.
9. Make the Pixel 10 Pro XL sufficient for end-to-end physical validation while retaining automated compatibility coverage beyond that one device.

### 4.2 Launch targets

These are product targets, not external guarantees:

| Metric | Launch target |
|---|---:|
| Crash-free users | ≥ 99.8% |
| Android vitals user-perceived ANR rate | < 0.20% |
| Cached dashboard visible, P95 on Pixel 10 Pro XL | ≤ 500 ms after the app content begins rendering |
| Local-data card update, P95 | ≤ 250 ms |
| Reminder intent creation success | ≥ 99.5% of eligible requests |
| Duplicate reminder rate | < 0.1% |
| Screenshot extraction on supported synthetic/reference screens | ≥ 95% after user confirmation |
| Account-add completion among users who start the flow | ≥ 80% |
| Activated users with at least two accounts | ≥ 60% |
| Blocking TalkBack, font-scaling, or Switch Access defects | 0 |
| Plaintext provider credentials in app, logs, analytics, backups, or cloud | 0 |
| Production use of unapproved scraping or private endpoints | 0 |
| Daily background battery impact on Pixel 10 Pro XL with six accounts | < 1% under the defined normal-use test |
| 16 KB page-size compatibility failures | 0 |
| Google Play pre-launch blocking findings | 0 |

### 4.3 Non-goals

Demeter v1 will not:

- Send prompts or messages to consume allowance automatically.
- Circumvent, extend, pool, or bypass provider limits.
- Store or analyze conversation content.
- Collect ChatGPT or Claude passwords, authentication cookies, or browser sessions.
- Reverse engineer private provider APIs.
- Claim that estimates are provider-authoritative.
- Promise exact-to-the-minute Android notification delivery.
- Request `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`.
- Run a persistent foreground service for quota polling or reminder timing.
- Request broad photo-library access when the Android Photo Picker is sufficient.
- Request local-network access before a user chooses a LAN-hosted bridge.
- Manage provider subscriptions, billing methods, or plan upgrades.
- Aggregate incomparable quotas into a single mathematically false “total tokens left” value.
- Support more than six active accounts in v1.
- Ship iOS, Wear OS, Android TV, ChromeOS-specific, or desktop clients in the first public release.

---

## 5. Product principles

### 5.1 Truth over false precision

Every value must carry source, observation time, and confidence. If the provider does not expose an exact limit, Demeter says “unknown,” “estimated,” or “reset time only” rather than inventing a percentage.

### 5.2 Glanceability first

The landing screen answers three questions immediately:

1. Which account needs attention?
2. How much appears to remain?
3. When does it reset?

### 5.3 Local-first by default

A user can use the core dashboard, history, and local reminders without creating a Demeter account. Cloud features are opt-in and narrowly scoped.

### 5.4 Calm, actionable alerts

Notifications are timely, infrequent, understandable at a glance, and easy to disable. They do not shame the user or encourage meaningless usage. Android delivery uncertainty is disclosed rather than hidden.

### 5.5 Adaptive, never cramped

The dashboard must fit one through six accounts elegantly at standard font scales. It must not shrink content below legible or accessible thresholds merely to avoid scrolling. The same composables adapt to compact, medium, expanded, large, and extra-large window classes.

### 5.6 Android-native, not an iOS visual port

Use edge-to-edge surfaces, system bars, predictive back, Material motion, Android navigation patterns, notification channels, dynamic color, and platform permission conventions. Demeter's brand should be distinctive without fighting Android's system behavior.

### 5.7 Permission minimization

Ask for a capability only at the moment the user invokes the associated feature. Prefer system-mediated pickers and scanners over broad media, camera, or local-network permissions.

### 5.8 Battery restraint

No continuous polling, unnecessary wake locks, or persistent foreground service. Use AlarmManager and WorkManager only for clearly bounded, user-valued work.

### 5.9 Provider-policy alignment

Only documented, authorized, and supportable integration paths are enabled in production.

### 5.10 Accessibility is structural

TalkBack, Switch Access, large font scales, display scaling, high contrast, color correction, animation removal, touch-target sizing, and non-color status cues are component-level requirements, not post-launch polish.

### 5.11 Evidence and auditability

The model proposes normalized usage; source confidence constrains the claim; the reminder engine decides; the audit trail records why the app scheduled, suppressed, replaced, or displayed an alert.

---

## 6. Definitions and normalized concepts

| Term | Definition |
|---|---|
| Monitored account | A user-created Demeter record representing one ChatGPT/OpenAI or Claude/Anthropic identity, organization, or workspace. |
| Connection | The mechanism through which Demeter receives data for a monitored account. |
| Usage window | One allowance or budget measured over a period, such as a five-hour session, weekly model allowance, monthly API budget, task counter, or user-defined period. |
| Snapshot | A point-in-time normalized observation of one or more usage windows. |
| Remaining capacity | The amount or ratio not yet consumed, when known. |
| Exhausted | Provider evidence or a user-confirmed value indicates no usable capacity remains for that window. |
| Reset time | The next known time at which a usage window renews or rolls over. |
| Freshness | How recently the underlying value was observed relative to the duration and urgency of its usage window. |
| Confidence | High, medium, or low confidence based on source type and parsing certainty. |
| Advisory reminder | A reminder derived from the last known snapshot; it may be stale and must disclose its observation time. |
| Verified reminder | A reminder whose underlying snapshot was refreshed within the configured freshness threshold before delivery. |
| Bridge | An open-source service deployed and controlled by the user that holds provider credentials and sends signed normalized snapshots to Demeter. |

---

## 7. Integration support matrix

### 7.1 v1 support

| Provider/product | Connection | Android v1 behavior |
|---|---|---|
| ChatGPT consumer plans | Manual entry, paste, Android Photo Picker, Sharesheet import | Store exact values only when visible; support reset-only and unknown-limit states |
| Claude consumer plans | Manual entry, paste, Android Photo Picker, Sharesheet import | Support session, weekly, model, and credit windows visible in the user's Usage settings |
| OpenAI API organization | User-owned bridge using official organization usage/cost APIs | Show token, request, cost, project, model, and user-defined budget windows |
| Anthropic API organization | User-owned bridge using official Usage & Cost Admin API | Show token, request, cost, workspace, model, and user-defined budget windows |
| LAN-hosted user bridge | Explicit user pairing; Android 17 local-network permission requested only when required | Signed snapshots over TLS; no broad LAN scan in v1 |
| Public HTTPS user bridge | Explicit user pairing | No local-network permission; standard network path |
| Claude Enterprise analytics | Adapter interface and fixtures; production enablement behind entitlement and validation | Enable only when the customer has the documented analytics credential and terms permit |
| Provider-approved consumer OAuth/API | Stub adapter and feature flag | Disabled until a documented usage scope is available and approved |

### 7.2 Prohibited production integrations

The following must fail code review and release gating:

- WebView, Custom Tab, accessibility-service, VPN, or hidden-browser automation that reads authenticated provider pages.
- Session-cookie import or copy/paste.
- Password collection.
- Private endpoint calls discovered through network inspection.
- Browser extension scraping without written provider authorization.
- Reuse of Codex CLI or Claude Code authentication tokens outside their documented clients.
- Provider administrative keys stored in Room, DataStore, SharedPreferences, Android Keystore, native code, app resources, or build configuration.
- Provider keys uploaded to Demeter cloud.
- Background clipboard monitoring.
- Broad photo-library permission for the normal screenshot-import flow.
- Unbounded LAN discovery or cleartext bridge traffic.

---

## 8. End-to-end user journeys

### 8.1 First launch

1. Display a concise value proposition and a privacy promise.
2. Offer **Continue locally** as the primary action.
3. Offer **Sign in with Google** as an optional secondary action for email reminders and future sync.
4. Do not request notification, media, camera, biometric, local-network, or tracking-related permission on first launch.
5. Land on an empty Today screen with one primary action: **Add account**.
6. Respect the current system light/dark appearance and edge-to-edge configuration from the first frame.

### 8.2 Add a consumer account

1. User taps **Add account**.
2. User selects provider: OpenAI/ChatGPT or Anthropic/Claude.
3. User selects **Consumer plan**.
4. User enters a nickname, for example “Personal Claude” or “Work ChatGPT.”
5. User optionally enters the provider account email as metadata; Demeter does not claim it is verified.
6. User chooses a sync method:
   - Import a screenshot with Android Photo Picker.
   - Share an image or text into Demeter from another app.
   - Paste usage text in an explicit paste field.
   - Enter values manually.
7. For screenshot import:
   - Launch the system Photo Picker; do not request broad media access.
   - Process text on device.
   - Present a confirmation screen containing each detected window, value, unit, reset time, time zone assumption, and confidence.
   - Require explicit confirmation before saving.
   - Discard the source image and transient OCR buffers after extraction by default.
8. Offer reminder configuration.
9. Explain the value of notifications in-product.
10. Ask for `POST_NOTIFICATIONS` only after the user enables at least one reminder and taps **Allow reminders**.
11. If permission is denied, save the rule as inactive, explain how to recover in Android Settings, and preserve all non-notification functionality.
12. Return to the dashboard with the new account visible.

### 8.3 Add an API organization through a user-owned bridge

1. User selects provider and **API organization**.
2. Demeter explains that provider administrative keys must remain on a server controlled by the user.
3. User deploys or opens the Demeter Bridge setup guide.
4. The bridge generates a one-time pairing QR code containing:
   - HTTPS bridge origin.
   - Bridge public-key fingerprint.
   - One-time pairing token.
   - Expiry.
5. Demeter uses a system-mediated or Google code scanner where available; a manual URL/token fallback is always provided.
6. Before connecting, Demeter classifies the origin:
   - **Public HTTPS origin:** continue without local-network permission.
   - **Private/LAN origin:** on Android 17+, show a feature-specific explainer and request `ACCESS_LOCAL_NETWORK` only after the user confirms.
7. The app and bridge complete a challenge-response pairing flow.
8. The bridge sends a signed account identity and initial normalized snapshot.
9. The user selects organization, project, workspace, and optional budget views.
10. The app stores only the bridge identity, public key, and revocable device token in protected local storage.
11. A failed permission, TLS, signature, or fingerprint check fails closed and provides a specific recovery action.

### 8.4 Daily use

1. User opens Demeter.
2. Room-backed cached cards appear immediately.
3. Connected sources refresh in parallel when policy and connectivity allow.
4. The dashboard reorders only if the user selected automatic urgency ordering; otherwise card positions remain stable.
5. Each card shows freshness, source, and reminder-delivery status.
6. User taps a card to inspect all usage windows, history, source details, reminder rules, and audit events.
7. Pull-to-refresh starts an explicit foreground refresh and does not depend on WorkManager.
8. The Android system Back action follows the visible navigation hierarchy and predictive-back animation.

### 8.5 Reminder enablement and recovery

1. User selects one or more lead times.
2. Demeter previews examples using the account's current reset.
3. User taps **Enable reminders**.
4. On Android 13+, Demeter shows a concise rationale and then the system notification permission prompt.
5. If allowed, Demeter creates the required notification channels and schedules eligible one-shot inexact alarms.
6. If denied or channel-blocked, Demeter shows **Fix in Settings** and keeps the reminder rule without claiming it is active.
7. The account detail screen exposes:
   - Notification permission state.
   - Channel state.
   - Next logical trigger.
   - Last actual delivery or suppression.
   - Battery/background limitation warning when detected.
8. The user can send a local test notification from Settings.

### 8.6 Email reminder setup

1. User opens an account's reminder settings.
2. User enables **Email reminder**.
3. If not signed in to Demeter, prompt for Sign in with Google through Credential Manager or offer email-based identity if supported by the backend.
4. User enters or selects a destination email.
5. Demeter sends a verification link or one-time code.
6. Only a verified destination can be enabled.
7. User chooses whether the destination is global or account-specific.
8. User sees the exact data included in an email and can send a rate-limited test message.

### 8.7 Device reboot, clock change, and app update

1. After boot completion, package replacement, time change, or time-zone change, Demeter recomputes all future reminders from persisted rules and snapshots.
2. The app does not assume old `PendingIntent` state is authoritative.
3. If source data is stale, verified-only reminders remain suppressed; advisory reminders retain their “as of” wording.
4. DST and time-zone changes update local display while preserving stored UTC reset instants.

### 8.8 Disconnect and deletion

1. User can disconnect a source without deleting local history.
2. User can delete a monitored account and all associated local snapshots, reminders, and audit events.
3. Cloud users can delete their Demeter account inside the app and through the published web deletion route required by Google Play policy.
4. Account deletion revokes device tokens, cancels pending email jobs, deletes cloud snapshots and email destinations, and records only a non-identifying deletion audit event.
5. Android Auto Backup and device-to-device transfer rules must exclude secrets, ephemeral screenshot data, and bridge device tokens unless a reviewed secure migration design exists.

---

## 9. Information architecture

Use a three-destination structure:

1. **Today** — adaptive account dashboard.
2. **History** — utilization and reset history across accounts.
3. **Settings** — defaults, privacy, email, bridge management, data export, and account deletion.

Navigation adapts by available width:

| Window class | Primary navigation |
|---|---|
| Compact | Material `NavigationBar` |
| Medium | `NavigationRail` when it improves content space; otherwise `NavigationBar` |
| Expanded, large, extra-large | `NavigationRail` or persistent navigation pane |
| Foldable tabletop/separating posture | Keep primary content and controls in safe, non-occluded panes |

Global affordances:

- **Add account** as a floating action button or top-app-bar action, selected according to width and scroll context.
- Pull to refresh on Today.
- Privacy-mode quick action.
- Context menu or overflow menu on account cards for refresh, edit, reorder, pause reminders, and delete.
- Android system Back and predictive Back support.
- Deep links and verified App Links for account detail, email verification, and settings recovery.

Do not place critical functionality exclusively behind swipe gestures, long press, edge gestures, or a context menu.

---

## 10. Screen and interaction specifications

### 10.1 Onboarding

**Purpose:** Communicate value, trust boundary, and optional cloud features in under 30 seconds.

**Required elements:**

- Demeter adaptive app icon, mark, and name.
- Headline: “Know what remains. Use it before it resets.”
- Three concise benefits: unified view, smart reminders, private by design.
- Primary button: **Continue locally**.
- Secondary button: **Sign in with Google**.
- Link: **How connections work**.

**Acceptance:** No provider credential, notification, media, camera, biometric, local-network, or analytics consent prompt appears during initial onboarding.

### 10.2 Empty Today screen

- A subtle illustrated seed or field motif.
- Text: “Add your first AI account.”
- Primary action: **Add account**.
- Secondary action: **Try with sample data**.
- Content remains visually balanced on the Pixel 10 Pro XL and in wider windows.

Sample data must be clearly marked and removable with one action.

### 10.3 Add Account wizard

Steps:

1. Provider.
2. Account type.
3. Connection method.
4. Account identity and nickname.
5. First snapshot or bridge pairing.
6. Reminder rules.
7. Review.

The wizard must save non-sensitive progress locally between steps. Back navigation returns to the prior step without discarding valid input. One-time pairing secrets, imported images, and unconfirmed OCR output are not persisted beyond the minimum required lifecycle.

### 10.4 Today dashboard

#### Top app bar

- Title: **Today**.
- Summary line such as “2 accounts may reset with capacity remaining.”
- Last global refresh timestamp.
- Refresh action with progress state.
- Add account action where space permits.

Do not display a single aggregate percentage across unlike providers or quota types.

#### Account card content

Every account card contains:

- Provider text label.
- User-defined nickname.
- Primary usage-window name.
- Primary metric, preferably “% remaining” when known.
- Reset countdown or reset date.
- Freshness label: Live, Updated recently, Stale, or Manual.
- Source icon and text: Bridge, Screenshot, Manual, or Approved connection.
- One secondary line for the next-most-relevant window.
- Accessible status phrase, not color alone.
- Touch semantics that expose a single clear “Open account details” action.

#### Primary-window selection

When an account has multiple usage windows, select the card's primary window using this order:

1. A window that is exhausted and has the soonest reset.
2. A non-exhausted window with the highest “capacity-at-risk” score.
3. A window with a known reset but unknown remaining amount.
4. The most recently updated window.

Suggested capacity-at-risk score:

```text
remaining_ratio = clamp(remaining / limit, 0, 1)
time_pressure = clamp(1 - hours_to_reset / horizon_hours, 0, 1)
capacity_at_risk = remaining_ratio * (0.35 + 0.65 * time_pressure)
```

Use a horizon appropriate to the window: 6 hours for session windows, 48 hours for weekly windows, and 7 days for monthly windows. This score is an internal ordering heuristic and must not be shown as a provider metric.

### 10.5 Account detail

Sections:

1. Account header and connection health.
2. All usage windows.
3. Timeline/history chart.
4. Reminder rules and Android delivery health.
5. Email destination.
6. Data source and confidence.
7. Refresh, paste, share, and import actions.
8. Connection management.
9. Delete account.

Each usage window shows:

- Display name.
- Used, remaining, and limit values when known.
- Unit.
- Reset time and time-zone display.
- Window type.
- Observation time.
- Data source.
- Confidence and any parse warning.

### 10.6 Screenshot review

- Show the selected image only for the review lifecycle.
- Draw no misleading bounding box unless the OCR coordinates are reliable.
- Present every parsed field as editable.
- Identify low-confidence fields inline.
- Let the user add or remove usage windows.
- Show the assumed date, locale, and time zone used to parse relative reset text.
- Primary action: **Confirm and save**.
- Secondary action: **Discard**.
- On completion or discard, release the URI grant and delete temporary copies where possible.

### 10.7 Bridge pairing and health

- Explain public versus local bridge connectivity.
- Show the parsed host and certificate/security state before pairing.
- Show bridge public-key fingerprint.
- Ask for Android local-network permission only for a LAN origin on Android versions that require it.
- Never silently fall back from HTTPS to HTTP.
- Health screen shows last success, last attempt, signature result, reachable origin class, and revocation action.

### 10.8 History

MVP history duration: 30 days locally.

Views:

- Per-account utilization over time.
- Reset events.
- Reminder scheduling and delivery events.
- Data gaps and stale periods.

Charts must not interpolate across unknown periods. Use discontinuities or explicit “No data” regions. Every chart has a textual summary and a non-graphical data view accessible to TalkBack.

### 10.9 Reminder settings

Per-account and global defaults:

- Enable local notifications.
- Select one or more lead times: 48h, 24h, 12h, 8h, 4h, 2h, 1h.
- Remaining threshold:
  - Any remaining capacity.
  - At least 10%.
  - At least 25%.
  - At least 50%.
  - Custom value where supported.
- Reminder confidence mode:
  - **Verified only** — skip if the snapshot is too old.
  - **Advisory** — send from the last known snapshot and show “as of” time.
- Quiet hours.
- Lock-screen privacy:
  - Generic content.
  - Include nickname.
  - Include percentage and reset time.
- Email reminder toggle.
- Optional **Reset expected** notification, off by default.
- Android status panel:
  - App notification permission.
  - Reminder channel status.
  - Next logical trigger.
  - Delivery timing caveat.
  - Fix-in-Settings action.
  - Test notification.

### 10.10 Settings

- Default reminder lead times.
- Default threshold and confidence mode.
- Quiet hours.
- Appearance: system, light, dark.
- Dynamic color: on/off, default on when supported.
- Privacy mode and optional biometric unlock.
- Notification authorization and channel status with deep links to Android system settings.
- Battery/background status explanation without coercing battery-optimization exemption.
- Email destinations.
- Bridge connections.
- Local-network permission state when relevant.
- Data export.
- Delete all local data.
- Demeter cloud account and account deletion.
- Privacy policy, terms, support, acknowledgements.
- Compatibility disclaimer: Demeter is independent and not endorsed by OpenAI, Anthropic, or Google.

### 10.11 Android system integration behavior

- Draw edge-to-edge and correctly apply system-bar and display-cutout insets.
- Support gesture navigation and three-button navigation.
- Support predictive Back; never intercept Back merely to block exit.
- Use system dialogs for runtime permissions and app settings recovery.
- Use Android Sharesheet for inbound screenshot/text sharing and outbound data export.
- Use verified App Links for cloud verification links where domain ownership is available.
- Restore UI state after configuration changes and process recreation without duplicating network calls or reminders.

---

## 11. Adaptive dashboard layout specification

### 11.1 Design intent

The Today screen must feel intentionally composed at every supported account count rather than like a fixed grid with arbitrary empty space. “Auto scale” means selecting a deterministic composition based on account count, usable window bounds, font scale, display scale, and content—not simply multiplying every dimension by one factor.

### 11.2 Layout inputs

The layout engine receives:

- Active account count, 1–6.
- Current window width and height in density-independent pixels.
- Material adaptive window-size class.
- Font scale and display scale.
- System-bar, cutout, hinge, and navigation insets.
- TalkBack state only where behavior—not visual order—must adapt.
- Whether each card has one or multiple visible windows.
- Whether the user pinned a featured account.
- Orientation and fold posture.

Do not branch on “Pixel 10 Pro XL” or raw pixel resolution. The Pixel 10 Pro XL is a validation device, not a layout constant.

### 11.3 Candidate layouts

| Account count | Compact-width preferred layout | Medium width | Expanded and larger | Primary metric scale |
|---:|---|---|---|---|
| 1 | One centered hero card | Hero card with supporting detail | Hero card plus history/next-reset pane | Extra large |
| 2 | Two large stacked cards | Two equal columns | Two equal columns with expanded detail | Large |
| 3 | One featured plus two supporting cards, or balanced list | Three columns when width allows | Three columns or featured two-pane layout | Medium-large |
| 4 | Two-by-two grid | Two-by-two or four-column grid | Four columns when card constraints pass | Medium |
| 5 | Two-column adaptive grid; feature only when height permits | Three-column grid with one spanning item | Three-to-five columns based on width | Compact |
| 6 | Two-column dense grid or single-column list at large font scale | Three-column grid | Three or six columns based on constraints | Compact |

### 11.4 Fit algorithm

1. Compute safe available bounds after all system and posture insets.
2. Generate candidate layouts in order of visual preference.
3. Measure representative card content using actual font scale and longest visible strings.
4. Reject any candidate that violates:
   - Minimum 48-by-48 dp interactive target.
   - Minimum compact card width of 156 dp.
   - Minimum default-scale primary metric size of 22 sp.
   - Required spacing, content padding, and system insets.
   - More than two nickname lines.
   - Clipped reset time or hidden freshness state.
5. On the Pixel 10 Pro XL at default font and display scale, prefer the first candidate that meets the reference composition targets in Section 11.7 without violating card constraints.
6. At larger font or display scales, preserve semantic typography and permit scrolling or a list layout rather than shrinking below thresholds.
7. Use stable account and window keys so layout changes and refreshes never move the wrong content during animation.
8. Keep user-pinned order stable. Automatic urgency ordering may change only after a refresh completes, with a clear animated transition.
9. On foldables, avoid placing critical content or actions beneath a separating hinge.
10. On large screens, use the extra width for detail and comparison—not merely stretched phone cards.
11. Never promote the layout to a medium- or expanded-width composition because the hardware name contains “XL,” because the panel is 6.8 inches, or because the raw resolution is 1344 × 2992.
12. In landscape, reject multi-pane candidates when the available height is compact or when controls, keyboard, or system insets make either pane unusably shallow.
13. Preserve a stable, readable card width and purposeful negative space; added height should reveal useful account state before it enlarges ornamental rings or empty containers.

### 11.5 Typography tokens by density

Use scalable `sp` typography and Material roles. The numeric ranges below are default-scale design targets, not fixed pixels:

| Density | Primary metric | Reset countdown | Account name | Secondary data |
|---|---|---|---|---|
| Hero | 52–64 sp | Headline medium | Title medium | Body large |
| Large | 40–48 sp | Headline small | Title medium | Body medium |
| Medium | 30–36 sp | Title large | Title small | Body small |
| Compact | 24–28 sp | Title medium | Title small | Label large |

Rules:

- Do not use aggressive `maxLines = 1` plus scaling to make long names fit.
- Truncate the nickname after two visual lines and expose the full value through semantics and detail view.
- Tabular numerals are preferred for countdowns when the selected font supports them.
- Percentage, value, unit, and “remaining” wording must read as one coherent semantic value.
- At font scale 2.0, a single-column scrolling layout is acceptable and preferred over clipped content.

### 11.6 Card states

Each card supports:

- Healthy.
- Use soon.
- Urgent.
- Exhausted.
- Reset expected.
- Syncing.
- Stale.
- Error.
- Unknown allowance.
- Manual source.
- Notifications disabled.
- Bridge action required.

State must be communicated through icon, text, shape or container treatment, and accessibility semantics in addition to color.

### 11.7 Pixel 10 Pro XL reference-device specification

The Google Pixel 10 Pro XL is the binding owned physical-device target for the primary phone experience. Its official reference profile is:

| Property | Reference value | Product implication |
|---|---:|---|
| Display | 6.8-inch Super Actua LTPO OLED | More portrait room than the base Pixel 10, but still a phone—not a tablet classification. |
| Aspect ratio | 20:9 | Tall portrait canvas and wide, height-constrained landscape canvas. |
| Native panel resolution | 1344 × 2992 | Validate high-density assets and edge-to-edge rendering; never use raw pixels as layout breakpoints. |
| Pixel density | 486 PPI | Use dp/sp, vectors, and density-aware image decoding. |
| Refresh range | 1–120 Hz with Smooth Display | Validate interaction at 60 Hz and high refresh; animation logic must be refresh-rate independent. |
| Dimensions | 162.8 × 76.6 × 8.5 mm | Frequent controls require deliberate one-handed reachability. |
| Weight | 232 g | Avoid workflows that demand prolonged top-corner reach or repeated grip shifts. |
| Memory | 16 GB RAM | Useful for local development, but not a license to relax memory budgets or skip low-memory tests. |
| Battery | Typical 5200 mAh | Battery percentage alone is insufficient evidence of efficient background behavior. |
| Processor/security | Google Tensor G5 and Titan M2 | Appropriate for on-device OCR and Keystore-backed flows; P0 behavior may not depend on Pixel-exclusive APIs. |

#### Runtime viewport rule

The panel specification does not determine the app's usable dp bounds. Display size, font scale, system density, navigation mode, insets, orientation, multi-window state, and future OS behavior can change the app window. Demeter must capture and use:

- Current app-window width and height in dp and pixels.
- Current width and height window-size classes.
- Effective density and font scale.
- System-bar, navigation-bar, display-cutout, and IME insets.
- Current refresh rate or display mode for diagnostics only.

Use `currentWindowAdaptiveInfo()` for high-level adaptive decisions and current window metrics for diagnostics and measured layout. Do not persist a model-specific viewport constant.

At release qualification, save a redacted viewport snapshot to the test record. The expected full-screen portrait result is a compact-width phone window, but the measured result—not this expectation—is authoritative.

#### Default portrait composition targets

These are Pixel 10 Pro XL quality targets at default font and display scale using reference-length English strings. They do not override the general fit algorithm.

| Active accounts | Reference target |
|---:|---|
| 0 | Empty state is vertically balanced; primary **Add account** action is reachable without a top-corner tap. |
| 1 | A centered hero card uses purposeful negative space; the card does not stretch merely to fill the tall panel. |
| 2 | Both large cards and their primary reset/freshness state are visible without initial vertical scrolling. |
| 3 | All three primary account states are visible without initial scrolling; use featured-plus-supporting or a balanced stack according to measured fit. |
| 4 | Prefer a complete two-by-two grid with all primary metrics, reset state, and freshness visible without initial scrolling when reference strings fit. |
| 5 | Use a two-column adaptive grid with stable card widths; initial scrolling is acceptable. |
| 6 | Use a two-column dense grid or accessible list; scrolling is expected and primary metrics must not shrink below the compact token. |

When long localization strings, large text, a large display setting, IME, or an unusual inset invalidates a target, readability and complete semantics take precedence over keeping content above the fold.

#### Large-handset ergonomics

- Keep primary navigation at the bottom in compact-width portrait.
- Provide a lower-screen **Add account** affordance; a top-app-bar icon may be supplemental, not the only path.
- Provide refresh through pull-to-refresh and an accessible lower/overflow path when top reach is difficult.
- Place the most frequent card actions within the card body, not only in a top-right overflow icon.
- Avoid essential confirmations whose only action is at the top of a full-height surface.
- Do not move controls toward screen edges merely because more pixels are available; retain safe lateral margins and gesture exclusion discipline.
- Support Android one-handed mode when the device/OS exposes it; the UI must remain operable when the system temporarily shifts the visible region.

#### Pixel 10 Pro XL physical validation matrix

The physical-device test is binding for:

- Portrait and landscape.
- Gesture and three-button navigation.
- Light, dark, dynamic color enabled, and dynamic color disabled.
- Font scales 1.0, 1.3, 1.5, and 2.0.
- Display size default, largest practical setting, and smallest practical setting used for dense-layout review.
- Smooth Display enabled and disabled, including scroll, refresh, layout animation, and predictive Back.
- Any user-selectable screen-resolution modes exposed by the installed stable build; absence of such a setting is recorded rather than assumed.
- Comfort View/Night Light and **Adjust brightness for sensitive eyes** smoke review for legibility and non-color status cues.
- Account counts 0–6, long account names, maximum visible secondary text, and all major card states.
- Camera cutout, system bars, IME, notification shade return, and app-switcher restoration.
- Battery Saver, Doze entry/exit, restricted battery mode, and thermal-warm smoke testing.
- TalkBack, Switch Access, color correction, one-handed mode when available, and large text.

Passing on the Pixel 10 Pro XL does not waive smaller-phone, tablet, foldable, older-API, low-memory, 16 KB, or broader OEM acceptance criteria.

### 11.8 Large-screen and resizability requirements

- Activities must remain resizable and orientation-flexible.
- Do not lock portrait orientation.
- Use adaptive panes for list/detail experiences when width permits.
- Preserve state while moving between split screen, freeform windowing, and full screen.
- Avoid assumptions based solely on `smallestScreenWidthDp`.
- A physically large handset such as Pixel 10 Pro XL does not become a “large-screen” UI solely from diagonal size; runtime window class remains authoritative.
- In compact-width landscape with compact height, prefer reflowed single-pane content over a cramped rail-plus-two-pane composition.
- Add screenshot tests for compact, medium, expanded, large, and extra-large window classes.

---

## 12. Visual and interaction design direction

### 12.1 Brand concept

Demeter's visual language draws from cultivation, cycles, and harvest without becoming literal or ornamental. The core visual metaphor is a **growth ring** or **field cycle** representing remaining capacity and time to reset.

The emotional target is calm control: precise enough for a technical user, warm enough to feel personal, and restrained enough that usage limits do not become anxiety-inducing.

### 12.2 Android design foundation

Use Material 3 Expressive as the interaction and component foundation, not as an unmodified visual template. Build a Demeter theme with:

- Color scheme.
- Type scale.
- Shape scale.
- Spacing and elevation tokens.
- Motion tokens.
- Status semantics.
- Dynamic color mapping.
- Light, dark, high-contrast, and privacy-mode variants.

Prefer stable Material components and adaptive libraries. Experimental APIs may be isolated behind wrapper components and may not be required for core navigation or accessibility.

### 12.3 App icon and launcher treatment

- Supply a full adaptive icon with foreground and background layers.
- Supply a monochrome icon for themed launcher icons.
- Use an abstract seed, crescent, or segmented growth ring.
- No provider logos or provider-imitation marks.
- No text.
- Strong silhouette at small launcher, Settings, notification, and recent-app sizes.
- Validate safe-zone cropping across circle, squircle, rounded-square, and novelty launcher masks.

### 12.4 Color system

Use semantic colors and validate contrast in both appearances. Suggested starting palette, subject to design validation:

- Harvest: warm gold.
- Sprout: deep green.
- Soil: near-black green-brown.
- Linen: warm off-white.
- Attention: amber.
- Urgent: coral.
- Stale: neutral gray.

Dynamic color:

- Default on when the OS supports it.
- Map system accent families into Demeter semantic roles; do not let dynamic color erase status differentiation.
- Let the user disable dynamic color.
- Never rely on provider brand colors, logos, or marks without documented permission.
- Urgent, exhausted, stale, and error states remain identifiable under grayscale and color-correction modes.

### 12.5 Progress visualization

Primary visualization: a circular or rounded-segment growth ring.

- Ring fill represents **remaining** capacity by default.
- Numeric text explicitly says “left” or “remaining.”
- The countdown is separate; do not encode both time and capacity in one ambiguous arc.
- Unknown limits use a time-only orbit or neutral clock treatment rather than a fabricated percentage.
- Exhausted states show an empty ring and a prominent reset countdown.
- At compact density, preserve text before decorative ring detail.
- TalkBack receives a concise textual equivalent; the ring itself is not exposed as multiple meaningless nodes.

### 12.6 Shape, surface, and hierarchy

- Use Material containers and tonal elevation sparingly.
- Cards use a coherent family of rounded shapes, with density variants—not six unrelated designs.
- Featured cards may use stronger container contrast and larger radius.
- Status chips are secondary; the primary status is readable without interpreting a chip.
- Avoid glassmorphism, excessive blur, low-contrast translucency, and ornamental gradients that harm legibility or battery.

### 12.7 Motion

- Refresh: subtle ring sweep and value crossfade.
- Account add: restrained seed-to-card expansion.
- Reset: brief completion motion.
- Layout change: `animateContentSize` or shared bounds only when identity remains unambiguous.
- Tap: standard Material state layer and optional haptic response.
- Predictive Back: participate in the system animation.
- Avoid continuous animation on the dashboard.
- When system animations are removed or reduced, replace large spatial transforms with immediate state changes or short opacity transitions.

Motion must remain smooth at 60 Hz and must not depend on a 120 Hz display.

### 12.8 Haptics

Use Android haptic feedback for:

- Successful account pairing.
- Reminder rule save.
- Destructive confirmation.
- Explicit manual refresh success only when it is not repetitive.

Do not emit haptics for automatic background updates, every card tap, or every value change. Respect system haptic settings.

### 12.9 Best-in-class Android quality bar

Demeter should target:

1. Immediate comprehension.
2. Native, predictable interaction.
3. Distinctive but system-compatible visual identity.
4. Complete accessibility at large font and display scales.
5. Polished transitions without visual noise.
6. Honest states for stale, unknown, delayed, or permission-blocked data.
7. Battery-efficient background behavior.
8. Excellent compact-phone execution plus credible adaptive large-screen behavior.
9. Zero reliance on a single device resolution.
10. Consistent quality across launcher icon, notifications, permissions, settings recovery, empty states, errors, and offline use.

This is a design and execution ambition, not a claim of an award or endorsement by Google.

### 12.10 Pixel 10 Pro XL visual-balance and reachability rules

1. The 6.8-inch display is an opportunity to reveal useful status and increase breathing room, not to make percentages or decorative rings disproportionately large.
2. Hero and detail content use sensible maximum widths so line length and hierarchy remain controlled in landscape or larger windows.
3. The default compact-width portrait navigation remains a bottom navigation bar; do not introduce a navigation rail because the handset is branded “XL.”
4. Primary actions used every session—open account, refresh, add account, and settings navigation—must have a reachable path in the lower two-thirds of the full-screen portrait composition.
5. Destructive actions may remain behind a menu, but confirmation and cancellation controls must be obvious, 48 dp minimum, and reachable without precision grip changes.
6. In landscape, prefer a compact top/bottom navigation treatment and horizontal reflow; do not force a two-pane layout if the usable height cannot support complete cards and actions.
7. Motion, scrolling, and progress transitions are qualified with Smooth Display both on and off; no animation duration, physics constant, or completion callback may depend on frame count.
8. Hardware display filters must not be required for contrast compliance. Comfort View, Night Light, and color-correction testing are supplemental robustness checks.

---

## 13. Functional requirements

### 13.1 Account management

**FR-001** — The user can create at most three active OpenAI/ChatGPT accounts and three active Anthropic/Claude accounts.

**FR-002** — Each account has a unique local identifier, provider, account type, nickname, optional user-entered email, connection type, and state.

**FR-003** — The user can edit nickname, display order, reminder rules, email mapping, and privacy settings.

**FR-004** — The user can pause monitoring without deleting history.

**FR-005** — The user can archive or delete an account.

**FR-006** — Duplicate nicknames are allowed, but the UI warns the user and shows a disambiguating suffix.

**FR-007** — Account state survives process death, configuration change, app update, and ordinary device reboot.

**FR-008** — The app never creates a provider account, changes a provider subscription, or represents a user-entered email as provider-verified.

### 13.2 Usage ingestion

**FR-100** — All provider data enters through a `UsageProviderAdapter` interface.

**FR-101** — Every ingested window includes source type and observation timestamp.

**FR-102** — Demeter supports multiple windows per account.

**FR-103** — Missing values remain null; the system must not substitute zero.

**FR-104** — Screenshot extraction occurs on device and requires user confirmation.

**FR-105** — The default screenshot policy is delete-after-extraction.

**FR-106** — A manual value can expire automatically when its reset time passes.

**FR-107** — Bridge payloads must be signed and verified before storage.

**FR-108** — A provider response that changes the reset time invalidates and reschedules reminders tied to the previous reset.

**FR-109** — HTTP 401 or 403 marks the source as authorization-required without deleting prior snapshots.

**FR-110** — HTTP 429 honors provider retry metadata where available and applies bounded exponential backoff with jitter.

**FR-111** — Android Photo Picker is the default image-import path; Demeter must not request `READ_MEDIA_IMAGES` or legacy storage permission for that path.

**FR-112** — An inbound Android Sharesheet image or text intent is accepted only through explicitly exported, narrowly scoped components and is copied or read only for the active import session.

**FR-113** — Clipboard content is read only after a user invokes a paste action in a focused field; no clipboard monitoring is allowed.

**FR-114** — OCR output records parser/template version, locale assumption, time-zone assumption, and per-field confidence.

**FR-115** — Temporary screenshot files and URI grants are released promptly after confirmation or discard.

### 13.3 Dashboard and adaptive presentation

**FR-200** — The dashboard automatically selects a supported layout for one through six accounts.

**FR-201** — Cached data appears before network refresh completes.

**FR-202** — User-selected manual order remains stable unless automatic urgency sorting is enabled.

**FR-203** — The dashboard never shows an aggregate percentage across incompatible usage windows.

**FR-204** — Every card shows freshness.

**FR-205** — The user can refresh one account or all accounts.

**FR-206** — Pull-to-refresh must not create duplicate concurrent provider calls for the same account.

**FR-207** — Privacy mode replaces account nicknames and values with generic placeholders until authenticated or disabled.

**FR-208** — Layout selection uses measured available space and font scale, not device model or raw screen pixels.

**FR-209** — At accessibility font and display scales, the app may scroll or switch to a list but may not clip required status, reset, or action content.

**FR-210** — The UI supports edge-to-edge drawing, system insets, display cutouts, split screen, resizable windows, and foldable posture changes.

**FR-211** — Android system Back and predictive Back follow the navigation hierarchy without data loss or duplicate side effects.

**FR-212** — Device model, diagonal size, raw resolution, and marketing tier must never directly choose a dashboard composition; the current app window and measured content choose it.

**FR-213** — On the Pixel 10 Pro XL at default font/display scale, the 0–4 account compositions must attempt the reference above-the-fold targets in Section 11.7 before selecting a denser or scrolling alternative.

**FR-214** — Every frequently used top-app-bar action has an accessible alternative interaction path that does not require a top-corner touch on the Pixel 10 Pro XL.

**FR-215** — Landscape layout selection uses both width and height classes and rejects multi-pane candidates that produce incomplete primary cards or unreachable actions.

**FR-216** — UI behavior and animation completion are invariant across 60 Hz and high-refresh display modes.

**FR-217** — A debug/release-candidate diagnostics surface can export redacted app-window bounds, dp size, density, font scale, window classes, insets, navigation mode, refresh mode, app version, and OS build for qualification evidence.

### 13.4 Local notifications

**FR-300** — Supported lead times are exactly 48, 24, 12, 8, 4, 2, and 1 hour.

**FR-301** — A user can select multiple lead times per account.

**FR-302** — A reminder is eligible only when a reset time is known and the last known state is not exhausted.

**FR-303** — If remaining capacity is known, it must exceed the user's selected threshold.

**FR-304** — Lead times longer than the time remaining in the current window are not scheduled retroactively.

**FR-305** — Pending reminders are canceled when a refreshed snapshot shows exhaustion.

**FR-306** — Pending reminders are canceled and recreated when the reset changes materially.

**FR-307** — Each logical reminder has a deterministic identifier:

```text
demeter:{account_id}:{window_id}:{reset_epoch}:{lead_minutes}
```

**FR-308** — Notification content includes the observation time in advisory mode.

**FR-309** — Notification actions include **Open Demeter**, **Open account**, and **Snooze 1 hour** when the snooze would occur before reset.

**FR-310** — Quiet hours shift or suppress reminders according to Section 14.

**FR-311** — The user can disable all reminders without deleting accounts.

**FR-312** — On Android 13+, the app requests `POST_NOTIFICATIONS` only after a user enables reminders and sees a feature-specific explanation.

**FR-313** — If app notifications or the reminder channel are disabled, Demeter shows the blocked state and a system-settings recovery action; it does not report the reminder as active.

**FR-314** — Demeter creates at least two notification channels:
- **Usage reminders** — user-visible reset and remaining-capacity reminders.
- **Service and account alerts** — bridge authorization, sync failure, or security-relevant account action.

Optional channels such as **Reset expected** must remain separate and default off.

**FR-315** — v1 uses one-shot inexact alarms. It must not declare or request `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`.

**FR-316** — Alarm delivery code performs only bounded work in the broadcast receiver, posts the notification from persisted normalized state, and delegates longer reconciliation to WorkManager.

**FR-317** — Every `PendingIntent` uses deterministic request codes, explicit package/component scoping where possible, and immutable flags unless mutability is strictly required and documented.

**FR-318** — Boot completion, package replacement, time change, and time-zone change trigger reminder reconciliation from persisted rules.

**FR-319** — Reminder scheduling is idempotent across repeated refresh, process restart, and reconciliation runs.

**FR-320** — The audit trail distinguishes logical trigger time, requested alarm time, observed receiver time, notification-post time, and user-open time when available.

**FR-321** — The UI states that delivery may be delayed by Android power management and does not promise exact timing.

**FR-322** — Force-stop behavior is disclosed: no guarantee is made until the user launches Demeter again.

**FR-323** — A local test-notification function verifies permission and channel visibility without waiting for a real reset.

### 13.5 Background refresh and reconciliation

**FR-350** — WorkManager is used for durable, best-effort refresh and state reconciliation, not exact alarms.

**FR-351** — Periodic work uses the minimum frequency necessary for user value and respects network and battery constraints.

**FR-352** — Explicit foreground refresh does not wait for periodic WorkManager execution.

**FR-353** — Unique work names prevent duplicate refresh jobs for the same account or global reconciliation task.

**FR-354** — Workers are idempotent, bounded, cancellation-aware, and safe to retry.

**FR-355** — No persistent foreground service is used for polling, OCR, or notification timing in v1.

**FR-356** — The app remains correct when WorkManager runs late, is deferred, or is skipped because the app was force-stopped.

### 13.6 Bridge connectivity and Android 17 local network

**FR-370** — Bridge origins must use HTTPS in production, except explicit local-development builds.

**FR-371** — Demeter classifies a paired bridge as public HTTPS or LAN/private before attempting persistent access.

**FR-372** — For API 37+ targets, `ACCESS_LOCAL_NETWORK` is requested only after the user selects a LAN bridge and after an in-context explanation.

**FR-373** — Denial of local-network access blocks only LAN bridge connectivity; local mode, public HTTPS bridges, email, and other accounts continue to work.

**FR-374** — Demeter does not scan the LAN for bridges in v1.

**FR-375** — Bridge TLS and signed-payload verification are independent controls; passing one does not bypass the other.

**FR-376** — A bridge origin change requires explicit re-confirmation and re-pairing.

### 13.7 Email notifications

**FR-400** — Email alerts require a Demeter cloud identity and verified destination.

**FR-401** — A destination email can be global or mapped to one or more monitored accounts.

**FR-402** — Demeter must not assume the provider account email is available or verified.

**FR-403** — If an approved provider identity flow supplies an email, Demeter may prefill it but must still obtain explicit consent for reminders.

**FR-404** — Email scheduling uses the same reset, threshold, freshness, and deduplication policy as local reminders.

**FR-405** — The email includes source, observation time, remaining amount when known, reset time, and an Android App Link or HTTPS fallback.

**FR-406** — Every email has account-level and global unsubscribe controls.

**FR-407** — The backend rate-limits test and production emails.

**FR-408** — Provider credentials are never included in an email job or template.

**FR-409** — Sign in with Google uses Credential Manager; legacy Google Sign-In APIs are not introduced into a new implementation.

### 13.8 History and auditability

**FR-500** — Store 30 days of snapshots locally by default.

**FR-501** — Store reminder scheduling, cancellation, suppression, post, and open events without notification body text.

**FR-502** — The user can inspect why a reminder was or was not sent.

**FR-503** — The user can export normalized usage history as JSON or CSV without source screenshots or secrets.

**FR-504** — Export uses the Android Sharesheet or a system document destination selected by the user.

**FR-505** — Historical charts clearly mark gaps, stale periods, estimates, and manual corrections.

### 13.9 Accessibility

**FR-600** — All interactive elements have meaningful Android accessibility semantics, labels, roles, states, and values.

**FR-601** — Progress rings expose one coherent textual value, reset time, freshness, and state to TalkBack.

**FR-602** — The dashboard works at font scale 2.0 and large display scale; scrolling is allowed.

**FR-603** — All states remain distinguishable in grayscale and common color-correction modes.

**FR-604** — Motion respects system animator-duration settings and does not require animation for comprehension.

**FR-605** — Charts provide accessible summaries and a navigable non-visual representation.

**FR-606** — Switch Access and keyboard/D-pad traversal follow a logical order with visible focus.

**FR-607** — Interactive targets are at least 48 by 48 dp unless a documented Android exception applies.

### 13.10 Privacy mode and device authentication

**FR-650** — Privacy mode can hide sensitive account names and values in the app switcher and on first app open.

**FR-651** — Optional unlock uses `BiometricPrompt` with an allowed device-credential fallback selected by product policy.

**FR-652** — Biometric templates and credentials remain system-managed; Demeter stores no biometric data.

**FR-653** — Notification lock-screen detail follows the user's selected privacy level independently of in-app privacy mode.

**FR-654** — When privacy mode is active, sensitive screens may use `FLAG_SECURE`; the user is informed that this can block screenshots and screen sharing.

---

## 14. Reminder decision engine

### 14.1 Eligibility

For each account, usage window, and selected lead time:

```text
known_reset = reset_at != null
future_reset = reset_at > now
not_exhausted = exhaustion_state != exhausted
threshold_met = remaining_unknown OR remaining_ratio > configured_threshold
lead_trigger = reset_at - lead_time
trigger_is_future = lead_trigger > now
source_allowed = source is enabled and account is active
notification_capable = app_permission_granted AND channel_enabled

eligible = known_reset
        AND future_reset
        AND not_exhausted
        AND threshold_met
        AND trigger_is_future
        AND source_allowed
```

`notification_capable` determines whether the eligible rule can be scheduled locally; it is deliberately separate from product eligibility so the UI can say “Rule is eligible, but Android notifications are disabled.”

If remaining capacity is unknown, the UI must tell the user that the reminder is based on reset timing only. The default is to allow such reminders only after explicit opt-in.

### 14.2 Freshness threshold

Suggested freshness time-to-live:

```text
freshness_ttl = min(
  6 hours,
  max(30 minutes, 25% of known window duration)
)
```

Examples:

- Five-hour session: 75 minutes.
- Weekly window: capped at 6 hours.
- Unknown duration: 2 hours.

The values must be remotely configurable for bridge/cloud sources and locally defaulted for offline/manual mode. Remote configuration may make policy more conservative without changing a user's chosen lead times.

### 14.3 Verified versus advisory mode

- **Verified only:** Do not post when the latest snapshot is older than its freshness TTL.
- **Advisory:** Post when otherwise eligible, but write “Based on your last update at [time].”
- **Reset-only:** When remaining capacity is unknown and the user opted in, state “Reset timing only; remaining usage is unknown.”

A scheduled Android alarm cannot perform arbitrary provider authentication and live evaluation at an exact instant. Demeter therefore recalculates reminders after every foreground refresh, successful worker refresh, bridge update, manual change, boot/time reconciliation, and app launch. It must never imply that an advisory notification reflects a live provider state.

### 14.4 Android scheduling strategy

For every eligible future local reminder:

1. Persist a `ScheduledReminder` record in Room before registering platform work.
2. Compute `requested_trigger_at = reset_at - lead_time`.
3. Create a deterministic notification tag and deterministic integer request code derived from the logical reminder identifier.
4. Register a one-shot inexact alarm using `AlarmManager.setAndAllowWhileIdle()` when the trigger must be eligible during idle, or the least intrusive equivalent that meets the tested behavior.
5. Do not request exact-alarm access.
6. Reconcile with unique WorkManager work:
   - Periodic best-effort source refresh.
   - One-time reconciliation after material snapshot or settings changes.
   - Recovery after boot, package replacement, time, or time-zone changes.
7. On alarm receipt:
   - Read persisted state.
   - Re-evaluate account activity, reset, exhaustion, threshold, freshness, quiet hours, permission, and channel state.
   - Post only if still eligible.
   - Record observed timing.
   - Enqueue longer refresh or audit upload work if needed.
8. Never perform network authentication, OCR, database migration, or long-running work directly in `BroadcastReceiver.onReceive()`.

### 14.5 Delivery semantics

Demeter distinguishes:

| Timestamp | Meaning |
|---|---|
| `logical_trigger_at` | Exact product time derived from reset minus lead time |
| `requested_alarm_at` | Time supplied to AlarmManager |
| `receiver_observed_at` | When Android invoked the receiver |
| `notification_posted_at` | When Demeter called NotificationManager |
| `notification_opened_at` | When the user opened the notification, if observed |

The UI may show “Scheduled for 4 hours before reset” and “Delivered 12 minutes late,” but must not characterize a delayed inexact alarm as a reliability defect unless it exceeds the defined service-level window under controlled test conditions.

Recommended initial delivery-quality bands:

- On time: within 15 minutes after logical trigger.
- Delayed: more than 15 minutes and before reset.
- Missed: not posted before reset despite permission/channel remaining enabled and the app not being force-stopped.
- Suppressed: intentionally not posted due to product policy.
- Blocked: Android permission, channel, force-stop, or environment prevented delivery.

These bands are internal product semantics and can be tuned through evidence.

### 14.6 Exact-alarm policy

Demeter's reminders are “use this allowance before it resets” nudges, not calendar, alarm-clock, medication, or safety-critical alerts. v1 therefore:

- Does not declare `SCHEDULE_EXACT_ALARM`.
- Does not declare `USE_EXACT_ALARM`.
- Does not direct users to special exact-alarm access.
- Does not use an exact alarm as a workaround for poor background design.
- Does not promise a specific minute of delivery.
- Reconsiders exact alarms only after documented user research, Google Play policy review, Android guidance review, and a product decision showing that precision is core rather than merely desirable.

### 14.7 Quiet hours

If a logical trigger falls inside quiet hours:

1. Compute the next allowed local time.
2. Move the logical delivery target to that time only if it is at least 15 minutes before reset.
3. Otherwise suppress it.
4. Register the shifted target through the same inexact scheduling path.
5. Record `quiet_hours_shifted` or `quiet_hours_suppressed`.

Quiet hours use the user's current local time zone. The original reset remains an absolute `Instant`.

### 14.8 Snooze

When the user taps **Snooze 1 hour**:

1. Re-read current persisted state.
2. Reject the snooze if the proposed logical target is at or after reset.
3. Create a new deterministic snooze identifier linked to the original reminder.
4. Schedule through the same inexact path.
5. Replace or dismiss the current notification.
6. Do not create an unbounded snooze chain; default maximum is two snoozes per window/reset unless product data justifies more.

### 14.9 Deduplication and grouping

- One logical reminder per account, usage window, reset epoch, and lead time.
- Room uniqueness constraint is the source of truth; AlarmManager and NotificationManager state are derived.
- Use a common notification group key for usage reminders.
- When several accounts post near the same time, Android may display a group summary.
- Never merge or summarize accounts whose lock-screen privacy settings differ in a way that would reveal restricted information.
- Notification IDs must remain stable for update/cancel behavior but must not expose raw account identifiers in logs.

### 14.10 Reset rollover

When `now >= reset_at`:

- Mark the prior window expired.
- Cancel platform alarms and notifications tied to its reset epoch.
- Do not assume the new allowance is full until a new snapshot or user confirmation is received.
- Optionally show “Reset expected — refresh to verify.”
- Reconcile future rules only after a new reset time exists.

### 14.11 Boot, package, time, and time-zone reconciliation

Receivers or system callbacks for relevant events must enqueue a single unique reconciliation job rather than independently scheduling every item in a long broadcast callback.

Reconciliation:

1. Loads active rules and latest snapshots.
2. Cancels stale platform registrations.
3. Recomputes all future logical reminders.
4. Re-registers eligible alarms.
5. Updates the next-trigger UI.
6. Records the reason: `boot`, `package_replaced`, `time_changed`, `timezone_changed`, `app_launch`, or `manual_repair`.

On Android 17, also account for time-zone offset-change behavior when applicable to the implementation target.

### 14.12 Force-stop, app standby, and battery restrictions

- After a force-stop, Android can prevent app alarms, receivers, and work until the user launches the app again. Demeter discloses this limitation in reminder health and cannot bypass it.
- Restricted App Standby or manufacturer battery policy may delay background refresh and alarms.
- Demeter must not pressure the user to disable battery optimization during onboarding.
- A later troubleshooting surface may explain system settings only after evidence of repeated missed reminders.
- The reminder engine remains correct with stale cached data and must prefer suppression or advisory wording over false live claims.

### 14.13 Reconciliation pseudocode

```kotlin
suspend fun reconcileReminders(reason: ReconcileReason) {
    val now = clock.now()
    val permission = notificationPermissionState()
    val channelState = reminderChannelState()

    reminderTransaction {
        cancelPlatformEntriesNotBackedByActiveRules()

        activeAccounts().forEach { account ->
            latestWindows(account.id).forEach { window ->
                rulesFor(account.id, window.id).forEach { rule ->
                    rule.leadMinutes.forEach { lead ->
                        val decision = reminderPolicy.evaluate(
                            account = account,
                            window = window,
                            rule = rule,
                            leadMinutes = lead,
                            now = now,
                            permission = permission,
                            channelState = channelState
                        )

                        persistDecision(decision, reason)

                        when (decision.action) {
                            Schedule -> alarmScheduler.upsert(decision)
                            Cancel -> alarmScheduler.cancel(decision.logicalId)
                            Suppress, Blocked -> Unit
                        }
                    }
                }
            }
        }
    }
}
```

The production implementation must separate transaction-safe persistence from platform calls to avoid holding a database transaction across Android framework operations; the pseudocode communicates policy flow, not literal transaction scope.

---

## 15. Data model

### 15.1 Core entities

The domain model uses platform-neutral concepts. Kotlin types shown below are illustrative. Persistence entities may use normalized Room tables rather than embedding every object.

#### `MonitoredAccount`

```kotlin
data class MonitoredAccount(
    val id: AccountId,
    val provider: Provider, // OPENAI | ANTHROPIC
    val accountKind: AccountKind, // CONSUMER | API_ORGANIZATION | ENTERPRISE
    val nickname: String,
    val userEnteredEmail: String?,
    val connectionKind: ConnectionKind,
    val status: AccountStatus,
    val sortIndex: Int,
    val isPinned: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

#### `Connection`

```kotlin
data class Connection(
    val id: ConnectionId,
    val accountId: AccountId,
    val kind: ConnectionKind,
    val bridgeOrigin: String?,
    val bridgeOriginClass: BridgeOriginClass?, // PUBLIC_HTTPS | LOCAL_NETWORK
    val bridgePublicKey: ByteArray?,
    val credentialReference: String?, // Reference only; never a provider credential
    val providerSubjectId: String?,
    val lastSuccessAt: Instant?,
    val lastAttemptAt: Instant?,
    val lastErrorCode: String?,
    val health: ConnectionHealth
)
```

#### `UsageWindow`

```kotlin
data class UsageWindow(
    val id: WindowId,
    val accountId: AccountId,
    val providerWindowId: String?,
    val displayName: String,
    val windowType: WindowType,
    val unit: UsageUnit,
    val usedValue: BigDecimal?,
    val limitValue: BigDecimal?,
    val remainingValue: BigDecimal?,
    val usedRatio: Double?,
    val remainingRatio: Double?,
    val windowStartedAt: Instant?,
    val resetAt: Instant?,
    val windowDurationSeconds: Long?,
    val exhaustionState: ExhaustionState,
    val isEstimated: Boolean
)
```

#### `UsageSnapshot`

```kotlin
data class UsageSnapshot(
    val id: SnapshotId,
    val accountId: AccountId,
    val observedAt: Instant,
    val receivedAt: Instant,
    val sourceType: SourceType,
    val sourceVersion: String?,
    val confidence: Confidence,
    val parseWarnings: List<String>,
    val localeAssumption: String?,
    val zoneAssumption: String?,
    val windows: List<UsageWindow>,
    val payloadHash: String,
    val signatureStatus: SignatureStatus
)
```

#### `ReminderRule`

```kotlin
data class ReminderRule(
    val id: ReminderRuleId,
    val accountId: AccountId?,
    val windowSelector: WindowSelector,
    val leadMinutes: Set<Int>, // 2880, 1440, 720, 480, 240, 120, 60
    val remainingThresholdRatio: Double?,
    val allowUnknownRemaining: Boolean,
    val confidenceMode: ConfidenceMode,
    val quietHoursStart: LocalTime?,
    val quietHoursEnd: LocalTime?,
    val localEnabled: Boolean,
    val emailEnabled: Boolean,
    val emailDestinationId: EmailDestinationId?,
    val lockScreenPrivacy: LockScreenPrivacy
)
```

#### `ScheduledReminder`

```kotlin
data class ScheduledReminder(
    val logicalId: String,
    val accountId: AccountId,
    val windowId: WindowId,
    val resetEpochSeconds: Long,
    val leadMinutes: Int,
    val logicalTriggerAt: Instant,
    val requestedAlarmAt: Instant?,
    val platformRequestCode: Int,
    val notificationTag: String,
    val notificationId: Int,
    val scheduleState: ScheduleState,
    val blockReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

`logicalId` uses:

```text
demeter:{account_id}:{window_id}:{reset_epoch}:{lead_minutes}
```

The hashed or mapped platform request code must be collision-tested. Room uniqueness on `logicalId` is authoritative.

#### `ReminderEvent`

```kotlin
data class ReminderEvent(
    val id: ReminderEventId,
    val logicalReminderId: String,
    val accountId: AccountId,
    val windowId: WindowId,
    val resetEpochSeconds: Long,
    val leadMinutes: Int,
    val channel: ReminderChannel, // LOCAL | EMAIL | REMOTE_PUSH
    val event: ReminderEventType,
    val reasonCode: String?,
    val logicalTriggerAt: Instant?,
    val receiverObservedAt: Instant?,
    val postedAt: Instant?,
    val openedAt: Instant?,
    val createdAt: Instant
)
```

#### `EmailDestination`

```kotlin
data class EmailDestination(
    val id: EmailDestinationId,
    val userId: UserId,
    val email: String,
    val verificationState: VerificationState,
    val verifiedAt: Instant?,
    val createdAt: Instant
)
```

#### `AndroidCapabilityState`

```kotlin
data class AndroidCapabilityState(
    val notificationPermission: PermissionState,
    val reminderChannelEnabled: Boolean,
    val alertChannelEnabled: Boolean,
    val localNetworkPermission: PermissionState?,
    val batteryRestriction: BatteryRestrictionState,
    val lastEvaluatedAt: Instant
)
```

Capability state is an observed cache for UI and diagnostics, not the source of truth; Android state must be re-read before sensitive operations.

### 15.2 Room storage outline

Suggested tables:

```text
accounts
connections
usage_windows
usage_snapshots
snapshot_parse_warnings
reminder_rules
scheduled_reminders
reminder_events
email_destination_refs
bridge_keys
schema_metadata
```

Requirements:

- Foreign keys and cascade rules are explicit.
- Snapshot plus windows write atomically.
- `scheduled_reminders.logical_id` is unique.
- Index reset times, account IDs, observed times, and pending reminder states.
- Store decimals without floating-point loss where values represent currency or exact counters.
- Destructive migration is prohibited for production user data.
- Migration tests cover every released schema version.

### 15.3 DataStore scope

Use Preferences DataStore or Proto DataStore for:

- Theme and dynamic-color preference.
- Global reminder defaults.
- Quiet hours defaults.
- Onboarding completion.
- Privacy-mode preference.
- Non-sensitive feature flags.
- Last selected account/filter.
- Remote configuration cache with signature/version metadata where applicable.

Do not store provider credentials, bridge device private keys, raw screenshots, or canonical snapshot history in DataStore.

### 15.4 Android Keystore scope

Generate or wrap keys for:

- Bridge pairing device identity.
- Local encryption keys when application-level encryption is required.
- Cloud refresh-token protection where the authentication architecture uses a locally encrypted token.
- Export-signing or integrity functions explicitly reviewed by Security.

Keys should be non-exportable and hardware-backed where available. The design must handle key invalidation, device credential changes, restore to a new device, and lack of StrongBox without data corruption or credential leakage.

### 15.5 Data invariants

1. Null and zero are never interchangeable.
2. `usedRatio + remainingRatio` should equal approximately one only when both derive from the same provider limit.
3. A screenshot snapshot cannot have high confidence unless every saved field was user-confirmed.
4. An invalid bridge signature causes the entire payload to be rejected.
5. Reset times are stored as UTC `Instant` values and displayed in the user's current locale and time zone.
6. Historical reset instants do not change when the device time zone changes.
7. Account email metadata is distinct from a verified email destination.
8. A scheduled reminder cannot exist without an active rule and a known future reset.
9. Platform alarm state is derived and repairable; it is not the only persisted record of intent.
10. Permission denial never deletes the user's reminder rules.
11. A provider credential must not be serializable through any Android application domain or persistence model.
12. Source screenshots are not part of backup, export, analytics, or cloud payloads.
13. Public and LAN bridge origin classifications cannot silently change.
14. Manual corrections create a new snapshot or correction event; historical evidence is not silently rewritten.

---

## 16. Provider adapter architecture

### 16.1 Kotlin adapter contract

```kotlin
interface UsageProviderAdapter {
    val kind: ConnectionKind

    suspend fun validate(
        connection: ConnectionDescriptor
    ): AccountIdentity

    suspend fun fetchSnapshot(
        account: MonitoredAccount,
        since: Instant?
    ): UsageSnapshot

    suspend fun disconnect(
        account: MonitoredAccount
    )
}
```

Adapters return normalized domain models. Composables and ViewModels must not parse provider payloads, screenshots, or bridge responses directly.

Adapter requirements:

- Deterministic fixture support.
- Explicit timeout, retry, and rate-limit behavior.
- Redacted structured errors.
- Cancellation-safe coroutine behavior.
- No Android `Context` dependency in domain interfaces.
- Platform APIs isolated behind infrastructure abstractions.
- Production enablement controlled by authorization evidence and feature flags.

### 16.2 Required adapters

- `ManualUsageAdapter`
- `PastedTextUsageAdapter`
- `ScreenshotUsageAdapter`
- `DemeterBridgeAdapter`
- `MockUsageAdapter`
- `ApprovedOAuthAdapter` placeholder behind a disabled feature flag

### 16.3 Screenshot and shared-content ingestion

Supported P0 inputs:

1. Android Photo Picker image URI.
2. Android Sharesheet image URI.
3. Android Sharesheet plain text.
4. Explicit paste into a field.
5. Manual form entry.

Optional post-P0 input:

- Camera capture, only if user research shows material value and the camera permission/quality cost is justified.

### 16.4 Screenshot extraction pipeline

1. Receive a user-selected URI through Photo Picker or an explicit inbound share.
2. Validate MIME type, maximum byte size, decoded dimensions, and image-bomb protections.
3. Copy to a private bounded temporary file only when required by the OCR library.
4. Normalize orientation and downscale only when OCR accuracy is preserved.
5. Run on-device ML Kit Text Recognition.
6. Classify provider and candidate screen type.
7. Parse labels, percentages, reset dates, countdowns, and units using versioned provider-specific templates.
8. Resolve relative dates using the user-confirmed locale and time zone.
9. Assign confidence per field.
10. Present an editable review form.
11. Save normalized values only after confirmation.
12. Delete private temporary files, release URI grants, and clear transient bitmaps/buffers.
13. Record template and parser version for future diagnostics.

Do not upload screenshots for analytics, remote debugging, parser improvement, or cloud backup.

### 16.5 OCR quality policy

- OCR is an assistive transcription mechanism, not a provider-authoritative source.
- Provider UI changes may invalidate templates; parsing must fail visibly rather than force a match.
- Low-confidence reset times require explicit user confirmation.
- Relative strings such as “resets in 3 hours” store both parsed `Instant` and the original normalized text.
- The user can choose **Reset time only** when a limit cannot be extracted.
- Parser telemetry includes only template ID, success/failure category, correction count, and coarse device/API information—never image or raw recognized text.

### 16.6 Bridge contract

The bridge is an open-source, self-hosted service. It may run in Docker, a user-controlled cloud account, a NAS, or a workstation.

Bridge responsibilities:

- Hold provider credentials in environment variables or a user-controlled secret manager.
- Call only documented official provider endpoints.
- Normalize usage data.
- Sign snapshots with a bridge key.
- Expose a minimal pairing and snapshot API.
- Send optional webhooks to Demeter cloud using account-scoped tokens.
- Redact secrets and provider payload bodies from logs.
- Support public HTTPS deployment as the preferred mobile connectivity model.
- Document secure LAN setup without encouraging cleartext HTTP.

Android responsibilities:

- Validate HTTPS and hostname.
- Verify the bridge public key and signed payload.
- Store the bridge public key and revocable device token using protected local storage.
- Never receive provider credentials.
- Request Android local-network permission only when a user chooses a LAN origin and the OS requires it.
- Avoid discovery scans; connect only to the explicit paired origin.
- Expose connection health and revocation.

### 16.7 Suggested bridge endpoints

```text
POST /v1/pair/start
POST /v1/pair/complete
GET  /v1/accounts
GET  /v1/accounts/{id}/snapshot
POST /v1/devices/{id}/revoke
GET  /v1/health
```

Suggested optional endpoints:

```text
GET  /.well-known/demeter-bridge
POST /v1/webhooks/demeter-cloud
GET  /v1/capabilities
```

All request and response schemas live in `/packages/contracts` and are versioned independently of Android presentation models.

### 16.8 Pairing security

- QR pairing token lifetime: five minutes.
- One-time use.
- Challenge-response bound to the app-generated device key.
- Signed bridge identity.
- User confirms a matching short fingerprint on phone and bridge setup surface where available.
- Device token is account-scoped and revocable.
- Replay protection through nonce and timestamp validation.
- Pairing token is never logged.
- URI parser rejects unexpected schemes, user-info, fragments, oversized fields, and ambiguous host encodings.
- Origin changes invalidate the pairing.
- Certificate failures cannot be bypassed in production through a generic “trust anyway” control.

### 16.9 Android 17 local-network behavior

For an app targeting API 37:

1. Parse and display the intended bridge host before requesting access.
2. Prefer a public HTTPS origin or a system-mediated path that does not require broad local-network permission.
3. If the origin is private/LAN and direct access is necessary, declare and request `ACCESS_LOCAL_NETWORK` at runtime.
4. Explain that the permission is used only to reach the user-selected bridge.
5. Do not bundle LAN access with notification or sign-in prompts.
6. On denial, preserve the pairing draft and offer:
   - Retry permission.
   - Use a public HTTPS bridge URL.
   - Cancel pairing.
7. Re-check permission before every LAN connection attempt requiring it.
8. Remove the permission from product variants that do not include LAN bridge support, where build configuration allows.

### 16.10 QR scanning strategy

Preferred order:

1. System-mediated or Google code scanner that does not require a persistent camera permission.
2. Import pairing QR from Photo Picker.
3. Manual bridge URL, token, and fingerprint entry.
4. CameraX/ML Kit in-app scanner only if the previous paths are insufficient and camera permission is justified.

A scanner is a convenience. The product must remain operable without camera access.

### 16.11 Network client requirements

- Kotlin coroutine-compatible HTTP client.
- TLS 1.2+; TLS 1.3 where negotiated.
- No cleartext traffic in production network security configuration.
- Strict JSON schema and size validation.
- Per-operation connect, read, write, and total timeouts.
- Bounded response bodies.
- Redacted logging.
- Backoff with jitter.
- Certificate pinning is optional and requires a rotation/runbook design; it is not a substitute for bridge signature verification.
- DNS rebinding, redirect, and private-address transition behavior must be threat-modeled for user-supplied bridge origins.
- Redirects across origins are disabled for pairing and signed-snapshot endpoints.

### 16.12 Provider integration release gate

An adapter can be production-enabled only when the owner attaches:

- Official endpoint documentation or written approval.
- Permitted authentication method.
- Permitted account/data scope.
- Rate-limit and retry rules.
- Data retention obligations.
- Trademark/compatibility review.
- Threat-model delta.
- Synthetic contract fixtures.
- Kill-switch owner.
- Provider-change runbook.

---

## 17. Official organization API mapping

### 17.1 OpenAI organization usage

The bridge may use documented organization usage and cost endpoints with an administrative credential held only in the bridge environment.

Map available dimensions such as:

- Input and output tokens.
- Request counts.
- Model.
- Project.
- API key identifier.
- User identifier where returned.
- Service tier.
- Cost and line item.

Demeter should present API usage as usage or spend history. A “remaining” value is shown only when the user configures a budget or the provider returns an explicit limit. Do not infer consumer ChatGPT allowance from OpenAI API organization data.

### 17.2 Anthropic organization usage

The bridge may use the documented Usage & Cost Admin API for eligible organizations, using the provider-prescribed administrative credential.

Map available dimensions such as:

- Input and output token usage.
- Model.
- Workspace or organization grouping.
- API usage cost.
- Request or message reports.
- User-defined budget.

Do not infer Claude consumer-plan usage from Claude Platform API usage. Individual consumer accounts and API organizations are separate product surfaces.

### 17.3 Budget windows

For provider APIs that report consumption but no explicit remaining allowance, the user may define:

- Monthly dollar budget.
- Monthly token budget.
- Project-specific budget.
- Billing-cycle start and time zone.

User-defined budgets must display a **User-defined** badge and cannot be represented as provider limits.

---

## 18. Android technical architecture

### 18.1 Platform baseline

| Item | Requirement |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Design components | Material 3 Expressive; Material 3 Adaptive |
| Minimum SDK | API 29, Android 10 |
| Compile SDK | API 37, Android 17 |
| Target SDK | API 37, Android 17 |
| Primary physical device | Google Pixel 10 Pro XL |
| Build output | Android App Bundle for Play; APK for local/debug testing |
| Architecture | Single-activity, layered, unidirectional data flow |
| Async | Kotlin coroutines and Flow |
| Dependency management | Gradle version catalog and Compose BOM where appropriate |
| Dependency injection | Hilt or another approved compile-time DI framework |
| Persistence | Room plus DataStore |
| Background work | WorkManager |
| Time-based reminder wake-up | AlarmManager one-shot inexact alarms |
| Local notifications | NotificationManager and notification channels |
| OCR | ML Kit Text Recognition on device |
| Identity | Credential Manager with Sign in with Google |
| Device auth | BiometricPrompt |
| Key protection | Android Keystore |
| Media import | Android Photo Picker and Sharesheet |
| Optional remote messaging | Firebase Cloud Messaging, only for opt-in cloud-connected features |

Use stable AndroidX, Kotlin, Compose, Material, WorkManager, Room, Credential Manager, and security dependencies available when implementation begins. Alpha APIs may not be required for P0 behavior.

### 18.2 Recommended module structure

```text
:app
:core:model
:core:database
:core:datastore
:core:network
:core:security
:core:designsystem
:core:notifications
:core:testing
:feature:onboarding
:feature:today
:feature:account
:feature:history
:feature:reminders
:feature:settings
:feature:import
:feature:bridge
:data:usage
:data:cloud
:data:bridge
:sync
```

A smaller initial build may combine modules, but boundaries between UI, domain policy, data sources, persistence, Android framework adapters, and provider adapters must remain explicit.

### 18.3 App layers

```text
Presentation
  Compose screens, adaptive layouts, navigation, ViewModels, immutable UI state

Domain
  Accounts, usage windows, snapshots, freshness policy, reminder policy,
  capacity-at-risk scoring, provider-neutral use cases

Data
  Repositories, Room DAOs, DataStore, cloud and bridge clients,
  adapter orchestration, mapping

Platform infrastructure
  AlarmManager, WorkManager, NotificationManager, Photo Picker,
  Sharesheet, Credential Manager, BiometricPrompt, Android Keystore,
  boot/time receivers, network state, deep links

Cross-cutting
  Design system, logging/redaction, analytics, feature flags,
  clock abstraction, security, test fixtures
```

### 18.4 State and data flow

- ViewModels expose immutable `StateFlow<UiState>`.
- Composables render state and send user events upward.
- Repositories are the source of application data.
- Room is the source of truth for normalized snapshots and reminder intent.
- Network, OCR, and platform callbacks update repositories rather than directly mutating UI.
- `collectAsStateWithLifecycle` or its stable equivalent is used for lifecycle-aware collection.
- One-shot events are modeled explicitly; avoid fragile event wrappers that replay destructive actions.
- Saved-state handles may retain navigation/form identifiers, not secrets or image payloads.

### 18.5 Navigation

- Use Navigation Compose or the current stable Android navigation solution.
- Destinations use typed, minimal arguments; load full objects by ID.
- Support Android App Links for:
  - Email verification.
  - Account detail.
  - Reminder settings recovery.
  - Bridge pairing only when the link contract is signed and bounded.
- Validate every deep-link parameter.
- Participate in predictive Back.
- Restore navigation state after process recreation.
- Do not export internal-only activities or receivers.

### 18.6 Refresh coordinator

The coordinator must:

- Deduplicate in-flight requests by account.
- Refresh accounts concurrently with bounded parallelism.
- Persist each successful snapshot atomically.
- Recompute card state and reminder policy after persistence.
- Isolate one account's failure from all others.
- Use cached data when offline.
- Respect account-level retry/backoff state.
- Expose progress by account and globally.
- Cancel safely when the user leaves a transient flow, without corrupting persisted state.
- Trigger reminder reconciliation only after the database commit succeeds.

### 18.7 Background behavior

Background execution is best effort. The app must remain correct when Android defers or skips work.

WorkManager responsibilities:

1. Periodically refresh bridge and approved sources within policy.
2. Recompute freshness and connection health.
3. Reconcile scheduled reminders.
4. Upload normalized snapshots only when cloud sync is enabled.
5. Retry bounded transient failures.
6. Prune local history according to retention.
7. Stop promptly when constraints change or work is canceled.

Rules:

- Use unique work to prevent duplicate periodic chains.
- Apply network constraints only where work actually needs network.
- Do not mark ordinary work expedited merely to improve convenience.
- Do not run a permanent foreground service.
- Do not create a loop that re-enqueues itself at high frequency.
- Explicit user refresh remains an application coroutine/use case and is not delayed behind periodic work.

### 18.8 Alarm and notification components

Suggested components:

```text
ReminderPolicy                 Pure domain decision logic
ReminderReconciler             Converts rules/snapshots into persisted schedule intent
AndroidAlarmScheduler          Registers/cancels inexact AlarmManager alarms
ReminderAlarmReceiver          Re-evaluates and posts bounded notification
NotificationFactory            Produces privacy-aware channel-specific notifications
ReminderActionReceiver         Handles snooze/dismiss actions
BootAndTimeReceiver            Enqueues unique reconciliation work
ReminderHealthRepository       Exposes permission/channel/background diagnostics
```

Requirements:

- `ReminderAlarmReceiver` is non-exported unless the platform event requires otherwise.
- All custom intents are explicit.
- All PendingIntents use correct immutable/mutable flags.
- Notification tap opens the exact account destination.
- Notification action processing is idempotent.
- Channel creation is safe to repeat.
- User-modified channel settings are never silently overwritten.
- No exact-alarm permission appears in the manifest.

### 18.9 Local storage and backup

Room:

- Normalized accounts, connections, windows, snapshots, rules, schedules, and audit events.
- WAL and transaction behavior chosen through measurement.
- Migrations tested from every released schema.

DataStore:

- UI preferences, defaults, onboarding state, non-sensitive flags.

Files:

- Temporary imports only in app-private cache.
- Export files generated on demand and shared through `FileProvider` or Storage Access Framework.
- No permanent raw screenshot directory in v1.

Backup:

- Define `dataExtractionRules` and legacy backup rules.
- Exclude bridge device tokens, private keys, raw/transient screenshots, cloud refresh tokens, and other non-transferable secrets.
- Decide explicitly whether normalized local history may be backed up; default is excluded until privacy and restore semantics are reviewed.
- Handle restore to a different device by requiring re-authentication and bridge re-pairing.

### 18.10 Cryptography and authentication

- Generate device keys through Android Keystore.
- Prefer non-exportable hardware-backed keys when available.
- Detect and handle key invalidation.
- Do not invent custom cryptographic primitives.
- Verify bridge signatures over canonical serialized payloads.
- Protect cloud tokens according to backend threat model.
- Use BiometricPrompt only to gate local presentation or key use; it does not authenticate a provider account.
- Sign in with Google through Credential Manager and a backend-validated identity token/authorization flow.
- Account recovery and deletion remain possible without biometric availability.

### 18.11 Networking

- Production cleartext traffic disabled through network security configuration.
- User-supplied bridge origins are parsed with a strict URL library.
- Public cloud and bridge clients use separate base URLs, certificates, auth interceptors, and logging policies.
- HTTP bodies are bounded.
- Network logs are disabled or redacted in release builds.
- Certificate and hostname failures fail closed.
- Use `ConnectivityManager` only as a hint; a validated request determines actual reachability.
- Local-network permission is evaluated only for LAN bridge paths.

### 18.12 Build variants and environments

Minimum variants:

| Variant | Purpose |
|---|---|
| `debug` | Local development, synthetic fixtures, verbose redacted diagnostics |
| `staging` | Staging cloud/bridge, internal Play testing, production-like security |
| `release` | Production endpoints, minification, strict logging, signed AAB |

Optional product flavors may separate:

- Local-only build.
- Cloud-enabled build.
- Enterprise/managed build.

Every flavor must preserve the no-provider-credential invariant.

### 18.13 Release build hardening

- R8 minification/resource shrinking enabled after keep-rule validation.
- Baseline Profiles generated for startup and critical flows.
- Macrobenchmark covers startup and Today dashboard.
- StrictMode enabled in debug.
- Network and database work prohibited on main thread.
- Dependency verification and lockfiles enabled where practical.
- Reproducible build inputs documented.
- Signing keys managed outside the repository.
- Play App Signing used for production unless a reviewed distribution strategy says otherwise.
- Native dependencies, if any, are inventoried, SBOM-visible, and compatible with 16 KB pages.

### 18.14 16 KB page-size compatibility

Google Play requires new apps and updates targeting Android 15+ to support 16 KB page sizes on 64-bit devices. Demeter must:

1. Prefer pure Kotlin/Java dependencies where feasible.
2. Inventory every packaged `.so`.
3. Use a current Android Gradle Plugin and NDK when native code is present.
4. Validate alignment and load behavior in a 16 KB environment.
5. Run the official compatibility checks in CI and release qualification.
6. Block release on an incompatible transitive native library.
7. Test the signed release artifact, not only debug.

### 18.15 Pixel 10 Pro XL reference configuration

The Pixel 10 Pro XL is the primary physical validation device because it gives the user direct end-to-end control. It is a **reference lane**, not the minimum hardware profile and not a layout switch.

Reference hardware baseline:

| Attribute | Pixel 10 Pro XL baseline |
|---|---|
| Panel | 6.8-inch, 20:9, 1344 × 2992 LTPO OLED at 486 PPI |
| Refresh | 1–120 Hz Smooth Display |
| Physical body | 162.8 × 76.6 × 8.5 mm; 232 g |
| Memory | 16 GB RAM |
| Battery | Typical 5200 mAh |
| SoC/security | Tensor G5; Titan M2 |
| Supported OS lane | Stable Android 17/API 37 available for the device, plus monthly security/Play system updates |

The validation record must capture:

- Exact device model and storage variant.
- Android version, security patch, Google Play system update, build number, and whether the device is on stable or beta/QPR software.
- Demeter version code/name, git commit, signing channel, and build type.
- Actual app-window bounds in pixels and dp, effective density, font scale, width/height window classes, display cutout, and system/IME insets.
- Current display size, font size, navigation mode, Smooth Display state, and any screen-resolution option exposed by that OS build.
- Dynamic color, dark theme, Comfort View/Night Light, color correction, and **Adjust brightness for sensitive eyes** state when relevant to the test.
- Notification permission and channel state.
- Battery Saver, app battery usage mode, standby state, battery level, temperature/thermal context where available, and charging state.
- Network type and bridge origin class.
- Reproduction steps, screen recording/screenshot where privacy allows, redacted viewport export, and log export.

Implement a non-production or explicitly gated **Device & viewport diagnostics** screen that produces a copyable JSON record with the non-sensitive fields above. It must not include account names, provider identifiers, email addresses, tokens, screenshot text, or bridge secrets.

The app must not use Pixel-exclusive APIs for P0 functionality. Performance, memory, and battery requirements remain bounded by the broader compatibility matrix; the 16 GB RAM and 5200 mAh battery must not conceal leaks, excessive wakeups, or unbounded image decoding.

### 18.16 Logging and diagnostics

- Structured logs use event codes, not raw payloads.
- Release logs exclude account nickname, email, provider IDs, tokens, recognized OCR text, screenshot paths, notification body, and full bridge URL query.
- A user-triggered diagnostic export contains:
  - App/device/API metadata.
  - Redacted connection health.
  - Reminder audit timing.
  - Permission/channel state.
  - Recent error codes.
- Diagnostic export requires explicit confirmation and uses the Android Sharesheet.
- No log upload occurs automatically.

---

## 19. Demeter cloud architecture

### 19.1 Cloud scope in v1

Demeter cloud is optional and stores only:

- Demeter user identity.
- Verified email destinations.
- Registered Android devices and revocable app-instance tokens.
- Account-scoped normalized snapshots when the user opts in.
- Reminder rules needed for email.
- Delivery and suppression events.
- Minimal security and abuse telemetry.

It does not store provider credentials, provider sessions, chat content, raw recognized OCR text, or source screenshots.

### 19.2 Suggested service architecture

- TypeScript or Kotlin service with a small REST API.
- PostgreSQL with strict tenant scoping.
- Durable job queue for email scheduling.
- Transactional email provider behind an interface.
- KMS-managed encryption keys.
- Firebase Cloud Messaging integration reserved for explicitly enabled remote reminders or connection alerts.
- Google identity verification for Credential Manager Sign in with Google.
- Optional Play Integrity signals for abuse defense; never treat integrity verdicts as user identity or the sole authorization control.
- Infrastructure as code and separate development, staging, and production environments.

### 19.3 Core API

```text
POST   /v1/auth/google
POST   /v1/auth/email/start              // Optional alternative identity path
POST   /v1/auth/email/verify
POST   /v1/devices
DELETE /v1/devices/{id}
POST   /v1/email-destinations
POST   /v1/email-destinations/{id}/verify
DELETE /v1/email-destinations/{id}
PUT    /v1/reminder-rules/{id}
POST   /v1/accounts/{id}/snapshots
GET    /v1/accounts/{id}/reminder-events
POST   /v1/play-integrity/assess          // Optional abuse signal
DELETE /v1/me
```

### 19.4 Snapshot upload example

```json
{
  "account_id": "6c4f...",
  "observed_at": "2026-07-12T17:30:00Z",
  "source_type": "screenshot",
  "confidence": "medium",
  "windows": [
    {
      "window_id": "weekly-all-models",
      "display_name": "Weekly",
      "remaining_ratio": 0.42,
      "reset_at": "2026-07-14T08:00:00Z",
      "unit": "percent",
      "is_estimated": false
    }
  ]
}
```

### 19.5 Device registration

Android device registration includes only:

```text
device_id
user_id
app_instance_id
platform = android
api_level
app_version_code
fcm_token?                 // Only when remote notifications are enabled
locale
time_zone
created_at
last_seen_at
revoked_at?
```

Do not use hardware identifiers, advertising ID, phone number, serial number, or IMEI.

### 19.6 API requirements

- Short-lived access tokens and rotating refresh tokens.
- Backend validation of Google identity assertions.
- Idempotency keys for snapshot and reminder writes.
- Server-side tenant authorization on every object.
- Request body limits.
- Strict schema validation.
- Rate limits by user, device/app instance, destination, and IP.
- No secrets or raw usage payloads in structured logs.
- Device and session revocation.
- Account deletion must immediately revoke access and stop future jobs; internal physical deletion may complete asynchronously within the published retention window.
- Web deletion entry point aligned with Google Play account-deletion requirements.
- FCM token rotation and invalid-token cleanup.
- No dependence on FCM for core local reminders.
- Provider adapters remain outside Demeter cloud unless explicitly authorized; the preferred architecture keeps provider administrative keys in the user-owned bridge.

### 19.7 Remote configuration and feature flags

Remote configuration may control:

- Freshness TTL bounds.
- Provider-adapter enablement.
- Email and cloud kill switches.
- Parser-template availability.
- Minimum supported app version warnings.
- Experiment assignment when the user has consented.

It must not:

- Secretly enable a prohibited provider integration.
- Weaken signature or TLS validation.
- Enable exact alarms.
- Change a user's reminder lead times without consent.
- Override notification privacy.
- Collect new data categories without updated disclosure and consent.

### 19.8 Cloud failure behavior

- Local dashboard, manual import, local history, and local reminders continue when cloud is unavailable.
- Email surfaces show delayed/degraded state without blocking local use.
- FCM failure does not suppress a locally eligible reminder.
- Snapshot upload retries are bounded and idempotent.
- A server-side email kill switch stops new delivery jobs while preserving user rules for recovery.

---

## 20. Email design and behavior

### 20.1 Subject examples

- “Demeter: 42% remains on Personal Claude — resets in 4 hours”
- “Demeter: Work ChatGPT resets tomorrow; remaining amount unknown”
- “Demeter: API budget is 81% used”

### 20.2 Body structure

1. Account nickname.
2. Usage window.
3. Remaining value or “unknown.”
4. Reset date and countdown.
5. Observation timestamp and source.
6. Clear caveat for advisory data.
7. Open Demeter Android App Link with HTTPS fallback.
8. Account-level and global unsubscribe links.

### 20.3 Email eligibility

Email uses the same product decision as local reminders:

```text
known future reset
AND active account/rule
AND not exhausted
AND threshold met or unknown explicitly allowed
AND freshness policy met
AND destination verified
AND not already sent
AND quiet-hours policy permits
```

Email delivery occurs server-side and is not dependent on Android app background execution.

### 20.4 Privacy

- Do not include provider account email unless the user explicitly enables it.
- Do not include API key IDs by default.
- Do not include organization names in an email subject unless explicitly enabled.
- Do not use tracking pixels by default.
- Aggregate email engagement only if the user has consented to product analytics.
- Email links contain short-lived, purpose-bound tokens and do not expose account IDs or reminder content in query strings.
- The email provider receives only the fields required to render and deliver the message.

### 20.5 Delivery and abuse controls

- Verify every destination.
- Rate-limit verification, test, resend, and reminder messages.
- Deduplicate by destination, account, window, reset, and lead time.
- Process bounces, complaints, and suppression lists.
- Provide one-click account-level and global unsubscribe.
- Stop pending jobs before completing cloud-account deletion.
- Record provider message ID and delivery state without storing message body.
- Test messages are visibly labeled and cannot be used as an arbitrary email-sending primitive.

---

## 21. Security and privacy requirements

### 21.1 Security objectives

1. A compromise of the Android app or Demeter cloud must not expose provider passwords, session cookies, or administrative API keys because those secrets are never present there.
2. A compromised normalized snapshot must not permit provider account access.
3. Every bridge payload must be attributable to a paired bridge key.
4. Notification and email content must follow user-selected privacy controls.
5. The product must fail closed on unverified integrations, invalid signatures, TLS errors, malformed deep links, and unauthorized origin changes.
6. Android permissions and exported components must be minimized to the feature actually invoked.
7. Local data, tokens, and device keys must not leak through backups, logs, screenshots, app-switcher previews, implicit intents, or insecure file sharing.
8. Background and notification components must be resistant to intent spoofing, replay, duplicate execution, and unsafe mutable PendingIntents.

### 21.2 Threat model

| Asset/risk | Threat | Required controls | Residual risk owner |
|---|---|---|---|
| Provider credentials in bridge | Bridge or host compromise | User-owned deployment, secret manager, minimal scopes, egress allowlist, no secret logs, rotation guide | User/Bridge operator |
| Demeter cloud account | Account takeover | Credential Manager/Google identity, short-lived tokens, device/session revocation, rate limits, optional step-up | Backend/Security |
| Normalized usage data | Tenant isolation failure | Object-level authorization, tenant-scoped queries, encryption at rest, authorization tests | Backend/Security |
| Android local database | Lost/unlocked device or app sandbox compromise | OS sandbox, privacy mode, optional BiometricPrompt, protected keys, minimal retention, no provider secrets | Android/Security |
| Bridge pairing | QR interception or replay | Short expiry, one-time token, challenge-response, device key binding, fingerprint confirmation | Android/Bridge |
| User-supplied bridge URL | SSRF-like behavior, DNS rebinding, malicious redirects, cleartext downgrade | Strict URL policy, HTTPS, redirect controls, origin pinning, LAN classification, bounded requests | Android/Bridge/Security |
| Screenshot import | Sensitive content retained, backed up, shared, or uploaded | Photo Picker, on-device OCR, private bounded temp storage, delete by default, backup exclusion, no analytics upload | Android/Privacy |
| Inbound Sharesheet | Malicious URI, oversized image, unexpected MIME, permission abuse | Explicit validation, bounded decode, temporary URI access, exported-component review | Android/Security |
| Notification privacy | Account data visible on lock screen | Generic-by-default content, per-account control, private visibility/category, channel settings education | Product/Android |
| Notification intent | Intent spoofing or replay | Explicit intents, immutable PendingIntent, deterministic idempotency, non-exported receivers | Android/Security |
| Stale reminder | Incorrect “unused” alert | Freshness TTL, advisory wording, re-evaluation at receiver, audit reason | Product/Reliability |
| Alarm/background delay | User assumes exact timing | Inexact scheduling, delivery disclosure, observed-timing audit, no exact-alarm claim | Product/Android |
| OCR misparse | Incorrect percentage or reset | Field confidence, mandatory confirmation, parser fixtures, no external action | Android/QA |
| Local-network permission | Broad LAN access beyond user intent | Request only for explicit LAN bridge, no discovery, host-scoped product behavior, revoke guidance | Product/Android |
| Deep/App Link | Hostile parameters or account confusion | Verified domains, strict allowlist/schema, server-bound tokens, re-auth for sensitive actions | Android/Backend |
| Backup/restore | Tokens or keys restored to a new device | Explicit backup exclusions, re-authentication, bridge re-pairing, key invalidation handling | Android/Security |
| Provider policy change | Integration becomes noncompliant | Feature flags, kill switch, terms review, adapter isolation | Product/Legal |
| Email abuse | Spam or destination misuse | Verification, rate limits, unsubscribe, bounce/complaint handling | Backend/Trust |
| Supply chain | Malicious or vulnerable dependency | Dependency review, lock/verification, SBOM, scanning, minimal native code | Engineering/Security |
| Release signing | Signing key compromise | Play App Signing, access control, audit, offline/restricted upload key | Release/Security |

### 21.3 Secret-handling rules

- No provider key field exists in the Android production domain model, Room schema, DataStore schema, resources, manifest metadata, BuildConfig, native library, or debug menu.
- No provider key field exists in Demeter cloud schemas.
- The bridge reads provider secrets only from environment variables or a supported secret manager.
- Secret values are never returned by bridge APIs.
- Device private keys are generated through Android Keystore and are non-exportable where supported.
- Cloud refresh tokens are revocable and protected according to the authentication threat model.
- Logs redact authorization headers, cookies, query credentials, one-time pairing tokens, App Link tokens, emails, and values matching secret patterns.
- CI and screenshots use synthetic fixtures only.
- Repository and release-artifact secret scanning is blocking.
- Clipboard never stores or reads a provider credential as part of a supported flow.
- Crash reports and ANR traces are reviewed for accidental PII/secret fields before enabling production collection.

### 21.4 Android component hardening

- Set `android:exported="false"` by default.
- Export only components required for launcher entry, verified App Links, inbound share, or system broadcasts.
- Narrow intent filters by action, category, scheme, host, path, and MIME type.
- Validate all incoming data even when the intent is explicit or the link is verified.
- Protect internal broadcasts with explicit package/component targeting.
- Use immutable PendingIntents by default.
- Do not use a WebView for provider authentication or account scraping.
- If a future approved OAuth flow uses Custom Tabs, validate redirect state, PKCE, nonce, and claimed HTTPS domain.
- Disable cleartext traffic in production.
- Do not install a permissive trust manager or hostname verifier.
- Do not expose local files with `file://` URIs.
- Use a narrowly configured `FileProvider` for exports.
- Disable or remove debug and inspection endpoints in release builds.
- Ensure notification actions cannot be invoked to access another user's/account's data without application authorization checks.

### 21.5 Local data protection

- Store normalized history in app-private Room storage.
- Store transient images only in app-private cache for the active import lifecycle.
- Exclude secrets and transient content from Android backup/device transfer.
- Use Android Keystore for cryptographic keys; do not treat Keystore as a place to store provider administrative keys.
- Optional database field encryption must have a documented key lifecycle, migration, and loss/recovery model.
- Privacy mode can obscure the recent-apps preview using a secure/window policy or a dedicated recents-safe state.
- `FLAG_SECURE` may be used for explicitly selected privacy-sensitive surfaces; it must not be applied without explaining screenshot/screen-share effects.
- Rooted-device detection is not an authorization control and must not block local use by default.
- Play Integrity may inform cloud abuse controls but cannot replace authentication or tenant authorization.

### 21.6 Notification privacy

Default local notification content:

```text
Demeter reminder
An AI usage window resets soon. Open Demeter for details.
```

User-selectable detail levels:

1. Generic.
2. Account nickname and provider.
3. Remaining value, reset time, and account nickname.

Requirements:

- Use private notification visibility by default.
- Never include provider email, organization ID, API key ID, bridge host, or cost detail on the lock screen by default.
- Notification channels make their purpose clear.
- A channel's user-modified sound, vibration, and visibility settings are respected.
- Remote FCM payloads should be data-minimal; sensitive message rendering should occur in-app after authorization where feasible.
- Notification content is not persisted in analytics or general logs.

### 21.7 Privacy requirements

- Core local mode works without login.
- Cloud sync and email are separately consented.
- Collect the minimum account metadata required.
- Provide in-app normalized data export and deletion.
- Provide in-app cloud-account deletion and the required web deletion route.
- Publish a clear retention schedule.
- Do not sell or use account usage data for advertising.
- Do not train models on user usage data, recognized screenshot text, or screenshots.
- Analytics events use random Demeter identifiers and exclude account nicknames, emails, provider IDs, raw values, bridge origin, and OCR text unless a narrowly justified metric is consented and documented.
- The Android manifest must not include advertising ID permission unless a separate approved business requirement exists.
- Privacy policy and Google Play Data safety answers must match actual runtime behavior and every shipped SDK.

### 21.8 Security gates

Release is blocked until:

- Mobile threat model reviewed.
- Bridge threat model reviewed.
- App Links and inbound sharing reviewed.
- Exported components tested.
- PendingIntent mutability audit complete.
- Backup extraction rules tested.
- Network security configuration tested.
- Keystore invalidation/recovery tested.
- Room migration tests pass.
- Dependency, SBOM, and secret scans pass.
- Provider-policy checklist is complete.
- No P0/P1 security or privacy defect remains.

---

## 22. Google Play and provider compliance requirements

### 22.1 Google Play requirements

1. Store metadata must not imply endorsement by OpenAI, Anthropic, or Google.
2. Use provider names only to describe compatibility; use provider marks or logos only with documented permission.
3. Publish an accessible privacy policy before testing with external users.
4. Complete Google Play Data safety based on actual app and SDK behavior, including optional cloud/email features.
5. If Demeter supports cloud account creation, provide:
   - In-app account deletion.
   - A public web deletion route.
   - Clear explanation of data deleted and any legally retained data.
6. Build and upload an Android App Bundle.
7. Target API 37 for this implementation, exceeding the current Google Play minimum.
8. Support 16 KB page sizes for all packaged 64-bit native libraries.
9. Use Play App Signing and protect the upload key.
10. Configure internal, closed, open, and production tracks with staged rollout and rollback.
11. Resolve blocking Android vitals, pre-launch report, accessibility, security, and policy findings before production.
12. Keep store screenshots and descriptions accurate to the shipped account-ingestion and reminder behavior.
13. Do not claim “real-time” consumer usage or “exact reminders” unless the implementation and provider access actually support those claims.
14. Declare only permissions used by a user-visible, documented feature.
15. Provide a valid permission explanation for notifications and, when present, local-network access.
16. Do not request broad photo/media permission for Photo Picker-based import.
17. Do not request exact-alarm permission in v1.
18. Do not use a foreground service for ordinary periodic quota polling.
19. Review every third-party SDK for Data safety, permissions, background behavior, and data collection.
20. Keep a release evidence package containing manifest diff, permission list, Data safety mapping, SDK inventory, target API, 16 KB report, and provider-integration authorization.

### 22.2 Android permission inventory

Expected production permissions, subject to manifest review:

| Permission/capability | Expected use | Request timing |
|---|---|---|
| `INTERNET` | Cloud, public bridge, and approved provider connectivity | Install-time normal permission; no runtime prompt |
| `POST_NOTIFICATIONS` | Local and optional remote reminder display on Android 13+ | After user enables reminders |
| `ACCESS_LOCAL_NETWORK` | Direct connection to a user-selected LAN bridge on Android 17+ | Only during explicit LAN bridge setup |
| `RECEIVE_BOOT_COMPLETED` | Reconcile future reminders after boot | No runtime prompt; disclose in technical/privacy docs |
| Biometric capability | Optional privacy-mode unlock | Only when the user enables biometric protection |
| Camera | Not expected in P0 if system-mediated code scanner is used | Add only after reviewed camera-based feature |
| Photo/media library | Not expected for Photo Picker | Must not be requested for normal screenshot import |
| Exact alarm | Prohibited in v1 | Must not be declared |
| Advertising ID | Not expected | Must not be declared without separate approved purpose |

Manifest merging in release CI must fail when an unapproved dangerous or special permission appears.

### 22.3 Provider compliance

Every provider adapter release checklist contains:

- Documented endpoint or written approval.
- Allowed authentication method.
- Allowed data scope.
- Account type and plan eligibility.
- Rate-limit behavior.
- Data retention expectations.
- Trademark and compatibility-language review.
- Kill-switch owner.
- Provider-change monitoring owner.
- Evidence that provider credentials are absent from Android and Demeter cloud.
- Synthetic contract tests and error-state copy.

Third-party service access must remain within service terms and documented authorization. A provider-policy or API change may disable one adapter without disabling local mode or deleting user history.

### 22.4 Store listing language

Approved direction:

> Demeter helps you organize user-entered or authorized AI usage information, track reset windows, and receive reminders.

Avoid:

- “Official ChatGPT and Claude tracker.”
- “Live token balance for every ChatGPT/Claude account.”
- “Connect any account automatically.”
- “Exact reset alarm.”
- “Guaranteed to maximize your subscription.”
- Any claim that suggests affiliation, endorsement, provider credential access, or automatic scraping.

### 22.5 Review notes package

Prepare `PLAY_REVIEW_NOTES.md` containing:

- Demo credentials for Demeter cloud only, never provider credentials.
- Sample-data path.
- Screenshot-import explanation.
- Notification permission timing and test path.
- Explanation that exact-alarm access is not used.
- LAN bridge permission path and public HTTPS alternative.
- Provider integration authorization matrix.
- Account-deletion test steps.
- Data safety mapping.
- Background-work explanation.
- Contact for policy questions.

---

## 23. Non-functional requirements

### 23.1 Performance

Reference measurements use a release-like build on the Pixel 10 Pro XL, with synthetic six-account data and no debugger attached.

| Metric | Target |
|---|---:|
| Cold startup to first meaningful cached dashboard, P95 | ≤ 1.5 s |
| Warm startup, P95 | ≤ 750 ms |
| Cached dashboard visible after composition begins, P95 | ≤ 500 ms |
| Local card-state update, P95 | ≤ 250 ms |
| Screenshot review available for a typical screenshot, P95 | ≤ 3 s |
| Explicit refresh UI response | Progress state within 100 ms |
| Scroll and common transition jank | No sustained visible jank; meet Android vitals and Macrobenchmark thresholds |
| Main-thread network/database/OCR work | 0 |
| ANR-causing synchronous broadcast or startup work | 0 |

Additional requirements:

- Run interaction and frame-pacing qualification with Smooth Display enabled and disabled; a 120 Hz panel must not hide work that misses 60 Hz or 120 Hz frame budgets.
- Treat Pixel 10 Pro XL measurements as the owned high-end reference, not proof of acceptable behavior on the minimum or lower-memory profile.
- Generate and ship Baseline Profiles for startup and primary navigation.
- Use immutable/stable Compose models where measured to reduce unnecessary recomposition.
- Avoid decoding full-resolution screenshots when a bounded representation is sufficient.
- Memory-test repeated imports, dashboard rotation/resizing, and navigation.
- Do not optimize through unreadable code before profiling evidence.

### 23.2 Reliability

- One provider or bridge failure cannot block other accounts.
- Every reminder schedule, cancel, and post path is idempotent.
- Snapshot writes are atomic.
- Network retries use bounded exponential backoff with jitter.
- Bridge and cloud calls have explicit timeouts and body limits.
- The app handles offline launch and shows cached values with stale labels.
- Process death during import, account add, refresh, or reminder reconciliation cannot create a partially committed snapshot or duplicate reminder.
- Room migrations are non-destructive and tested.
- App update and reboot repair derived alarm state.
- Notification permission or channel changes are detected on resume and before scheduling/posting.
- Clock abstraction makes time-dependent logic deterministic in tests.
- A corrupt local record is isolated where feasible and surfaced as repairable; the app must not silently erase all history.

### 23.3 Battery and network

- No continuous polling.
- No persistent foreground service.
- No unnecessary wake locks.
- Use inexact alarms.
- Background work is coalesced and constraint-aware.
- Snapshot payloads are compact.
- The app records aggregate worker/alarm duration and wake-up counts in development tests.
- Normal six-account daily background battery impact target on Pixel 10 Pro XL: less than 1% under the defined scenario.
- Because the reference phone has a typical 5200 mAh battery, release evidence also records estimated energy or charge where available, CPU time, wakeups, worker/alarm duration, alarm count, and network bytes; percentage alone is not sufficient.
- Data usage target for bridge/cloud refresh is documented after fixture measurement; source screenshots never upload.
- Battery Saver, Doze, App Standby, and restricted background modes are explicit test conditions.
- Demeter does not solicit unrestricted battery access as a default onboarding step.

### 23.4 Adaptive compatibility

- Support compact through extra-large adaptive window classes.
- Support portrait, landscape, split screen, freeform/resizable windows, and foldable posture changes.
- Do not lock orientation.
- Do not use raw screen-pixel breakpoints.
- Preserve state when configuration changes.
- Avoid letterboxing caused by unnecessary orientation or aspect-ratio restrictions.
- Test gesture and three-button navigation.
- Test display cutouts and system-bar inset changes.
- Pixel 10 Pro XL is the physical reference; emulator coverage remains mandatory for minimum API, a smaller compact phone near 400 dp usable width, low-memory behavior, and large-screen behavior.

### 23.5 Accessibility

- Meet Android accessibility guidance and internal WCAG-aligned contrast targets.
- TalkBack reading and traversal order is coherent.
- Switch Access and keyboard traversal reach every action.
- Minimum target size is 48 dp.
- Font scale 2.0 and large display size remain functional without clipping.
- Color is never the only status cue.
- Content descriptions do not duplicate visible text unnecessarily.
- Decorative imagery is excluded from the accessibility tree.
- Charts have summaries and a data alternative.
- Animations respect system settings.
- Timeouts are not used for essential reading or actions.
- Accessibility Scanner findings are triaged; blocking findings are zero at release.

### 23.6 Localization and internationalization

- English in v1.
- All user-facing strings externalized.
- Date, time, duration, percentage, currency, plural, and number formatting use locale-aware APIs.
- Reset times store absolute instants; local formatting follows current zone.
- OCR parser assumptions are explicit and user-correctable.
- Layout supports text expansion and right-to-left mirroring before localization launch.
- Do not concatenate translated fragments to form sentences.
- Provider labels that are contractual/product names are isolated from translatable descriptive text.

### 23.7 Security and privacy quality

- Release manifest contains only approved permissions and exported components.
- Provider credentials in app/cloud: zero.
- Raw screenshots outside transient private storage: zero.
- Secrets or PII in logs/analytics: zero.
- Cleartext production network traffic: zero.
- Invalid bridge signatures accepted: zero.
- Insecure mutable PendingIntents without documented reason: zero.
- Backup of non-transferable secrets: zero.
- Dependency vulnerabilities above the release threshold: zero unresolved without signed risk acceptance.

### 23.8 Observability

Required privacy-preserving events:

- `onboarding_completed`
- `account_add_started`
- `account_add_completed`
- `snapshot_import_result`
- `refresh_result`
- `reminder_rule_saved`
- `reminder_schedule_result`
- `reminder_receiver_result`
- `reminder_suppressed`
- `notification_opened`
- `notification_permission_result`
- `notification_channel_blocked`
- `email_destination_verified`
- `bridge_pair_result`
- `local_network_permission_result`
- `account_deleted`
- `cloud_account_deleted`
- `app_start_performance`
- `worker_result`

Do not log account nickname, email, provider credential, screenshot, raw recognized text, raw provider response, chat content, full bridge URL, or precise raw usage values.

### 23.9 Compatibility and build quality

- Release AAB installs and runs on API 29 through API 37 in the defined test matrix.
- All packaged 64-bit native libraries support 16 KB page sizes.
- 64-bit ARM production support is required; do not unnecessarily restrict emulator/test ABI support.
- No deprecated identity API is introduced when Credential Manager is available.
- No broad storage permission is required.
- No exact-alarm permission is present.
- The app builds from a clean checkout with documented commands.
- Static analysis, lint, unit tests, screenshot tests, and release checks are reproducible in CI.
- Dependency updates are automated but do not auto-merge without test evidence.

---

## 24. Analytics and product metrics

### 24.1 North-star metric

**Useful reset windows managed:** the number of usage windows for which Demeter presents a fresh or user-confirmed status and an actionable reset decision before the window ends.

### 24.2 Activation

Activated user:

- Adds at least two monitored accounts, or one account with two usage windows.
- Completes one successful refresh/import.
- Views the Today dashboard.

### 24.3 Engagement

- Weekly active users.
- Accounts monitored per active user.
- Refresh/import frequency.
- Account detail opens.
- Reminder rule adoption.
- History use.

### 24.4 Reminder quality

- Eligible windows.
- Scheduled reminders.
- Freshness suppressions.
- Quiet-hour suppressions.
- Duplicate prevention.
- Notification opens.
- Email delivery, bounce, and unsubscribe rates.

### 24.5 Trust guardrails

- Authorization failures.
- Invalid bridge signatures.
- OCR correction rate.
- Stale advisory rate.
- Privacy-mode adoption.
- Notification disable rate after first reminder.
- Security and privacy incidents.

---

## 25. Acceptance criteria

### 25.1 Account and adaptive layout

**AC-001** — Given zero accounts, Today shows one primary **Add account** action and no empty grid.

**AC-002** — Given one account at default font/display scale on the Pixel 10 Pro XL, its card uses the hero layout and is visually balanced.

**AC-003** — Given two accounts at default scale on the Pixel 10 Pro XL in portrait, both primary cards are visible without vertical scrolling.

**AC-004** — Given four accounts on compact width, the app selects a two-column layout when all minimum card constraints pass.

**AC-005** — Given six accounts, every account is discoverable and no primary metric is below the defined minimum size.

**AC-006** — Given font scale 2.0, the app preserves readable semantic typography and uses scrolling or list layout rather than clipping.

**AC-007** — Given long nicknames and reset strings, required status and reset content remains visible; full nicknames are available to TalkBack and detail view.

**AC-008** — Given a medium or expanded window, navigation and content adapt without stretching compact phone cards across the entire width.

**AC-009** — Given split-screen resize or rotation, account identity and scroll/navigation state remain stable.

**AC-010** — Given a separating fold posture, no critical action or status is hidden under the hinge.

**AC-011** — Given gesture and three-button navigation, all screens remain usable and correctly inset.

**AC-012** — Predictive Back previews and completes the expected destination without duplicate saves, refreshes, or deletes.

**AC-013** — The fourth active account for one provider is blocked with a clear explanation while the other provider remains addable until its own limit is reached.

**AC-014** — Given the Pixel 10 Pro XL in full-screen portrait, add account, refresh, open-account, and settings navigation each have a usable path that does not require an exclusive top-corner action.

**AC-015** — Given three reference-length accounts at default font/display scale on the Pixel 10 Pro XL, all three primary account states are visible without initial vertical scrolling.

**AC-016** — Given four reference-length accounts at default font/display scale on the Pixel 10 Pro XL, a two-by-two layout is selected and all primary metrics, reset state, and freshness labels are visible without initial scrolling when minimum constraints pass.

**AC-017** — Given Pixel 10 Pro XL landscape with compact available height, the app does not force a two-pane layout that clips cards, actions, or navigation.

**AC-018** — The release-candidate device record contains measured app-window bounds in px and dp, density, font scale, width/height classes, and system insets; no expected or hard-coded viewport is substituted.

**AC-019** — Changing the reported device model while holding the same window metrics and content constant does not change layout selection.

### 25.2 Data truthfulness and ingestion

**AC-020** — A missing limit renders “Limit not exposed,” not 0% or 100%.

**AC-021** — A screenshot-derived value is not saved until the user confirms it.

**AC-022** — A stale snapshot displays its observation time and stale state on card and detail view.

**AC-023** — An invalid bridge signature is rejected, logged locally as a security event, and never displayed as usage data.

**AC-024** — API organization usage is never labeled as ChatGPT or Claude consumer-plan allowance.

**AC-025** — Android Photo Picker import completes without requesting broad photo/media permission.

**AC-026** — A shared image with an unsupported MIME type, excessive size, or invalid URI fails safely without persistence or crash.

**AC-027** — Imported source images and temporary copies are removed after confirm/discard unless the user explicitly chooses a reviewed local-retention option.

**AC-028** — OCR review shows locale, time-zone assumption, and low-confidence fields.

**AC-029** — A user correction creates traceable normalized evidence and does not silently rewrite earlier snapshots.

**AC-030** — Null, unknown, exhausted, and zero-remaining states remain distinguishable in persistence and UI.

### 25.3 Android notification permission and channels

**AC-040** — No notification permission prompt appears on first launch.

**AC-041** — On Android 13+, `POST_NOTIFICATIONS` is requested only after the user enables a reminder and receives an in-context explanation.

**AC-042** — If notification permission is denied, the rule is saved but visibly blocked; dashboard, history, import, and email remain functional.

**AC-043** — If the Usage reminders channel is disabled in Android Settings, Demeter identifies the channel block and offers a valid settings deep link.

**AC-044** — A local test notification posts only when app permission and channel state allow it.

**AC-045** — User changes to channel sound, vibration, importance, or lock-screen visibility are not silently overwritten.

**AC-046** — Generic lock-screen mode contains no nickname, provider email, organization ID, usage value, or bridge host.

### 25.4 Reminder engine and timing

**AC-050** — Selecting 48h, 8h, and 1h creates exactly three logical schedule records when all triggers are in the future.

**AC-051** — The Android manifest contains neither `SCHEDULE_EXACT_ALARM` nor `USE_EXACT_ALARM`.

**AC-052** — Every local reminder uses the one-shot inexact scheduling path and the UI does not promise exact-to-the-minute delivery.

**AC-053** — If a refreshed value becomes exhausted, all pending reminders for that window/reset are canceled.

**AC-054** — If reset time moves, old logical/platform reminders are removed and new deterministic identifiers are created.

**AC-055** — A lead time whose logical trigger already passed is not delivered retroactively.

**AC-056** — In verified-only mode, a stale snapshot suppresses the reminder and creates a visible audit reason.

**AC-057** — In advisory mode, the notification contains an “as of” timestamp.

**AC-058** — Quiet-hour shifting never schedules a logical reminder at or after reset.

**AC-059** — Snooze is unavailable when one hour later would be at or after reset.

**AC-060** — Repeated reconciliation is idempotent and does not duplicate Room schedule records, platform alarms, or notifications.

**AC-061** — The alarm receiver re-evaluates exhaustion, threshold, reset, freshness, quiet hours, permission, and channel state before posting.

**AC-062** — The receiver performs bounded work and does not make provider network calls or OCR inside `onReceive()`.

**AC-063** — Reminder audit records logical trigger, requested alarm, observed receiver, notification post, and open timestamps when available.

**AC-064** — Reboot, app update, time change, and time-zone change cause one unique reconciliation path and restore eligible future reminders.

**AC-065** — After force-stop, the UI does not claim reminder health until the user relaunches and reconciliation completes.

**AC-066** — No persistent foreground service is present for polling or reminder timing.

### 25.5 WorkManager and refresh

**AC-070** — Periodic work uses unique names and cannot create duplicate refresh chains.

**AC-071** — Work retries are bounded and respect cancellation.

**AC-072** — Explicit pull-to-refresh starts immediately without waiting for periodic work.

**AC-073** — One failed account refresh does not cancel successful refreshes for other accounts.

**AC-074** — Offline launch shows cached data with stale/offline status.

**AC-075** — Battery Saver, Doze, or deferred WorkManager execution does not corrupt reminder intent or snapshot state.

### 25.6 Bridge and Android 17 local network

**AC-080** — Pairing to a public HTTPS bridge does not request local-network permission.

**AC-081** — On Android 17/API 37, pairing to a LAN/private bridge requests `ACCESS_LOCAL_NETWORK` only after an in-context explanation and user confirmation.

**AC-082** — Denying local-network permission blocks only that LAN bridge; local mode and public HTTPS connections continue.

**AC-083** — Demeter does not perform broad LAN discovery in v1.

**AC-084** — Cleartext LAN bridge URLs are rejected in production.

**AC-085** — A bridge origin or public-key fingerprint change requires explicit re-pairing.

**AC-086** — Expired, replayed, malformed, or reused QR pairing tokens fail closed.

**AC-087** — QR scanning has a manual-entry fallback and does not make camera permission mandatory.

### 25.7 Email and identity

**AC-090** — No email reminder can be enabled for an unverified destination.

**AC-091** — Sign in with Google is implemented through Credential Manager, not a newly introduced legacy Google Sign-In flow.

**AC-092** — A test email is rate-limited and visibly marked as a test.

**AC-093** — Every production email includes observation time, reset time, source, and unsubscribe controls.

**AC-094** — Deleting the Demeter cloud account disables pending email jobs and revokes access.

**AC-095** — An Android App Link opens the intended Demeter destination when installed and falls back to HTTPS when not installed.

### 25.8 Security and privacy

**AC-100** — Static analysis confirms no production Android model, API, resource, BuildConfig field, manifest metadata, or database column accepts a provider password, cookie, or administrative key.

**AC-101** — Demeter cloud schemas contain no provider credential columns.

**AC-102** — Release logs and analytics pass automated secret and PII redaction tests.

**AC-103** — Android backup/device-transfer rules exclude bridge private/device tokens, cloud refresh tokens, transient screenshots, and other non-transferable secrets.

**AC-104** — Release build cleartext traffic is disabled.

**AC-105** — All non-required Android components are non-exported.

**AC-106** — Every notification and action PendingIntent is explicit and immutable unless a documented test-covered exception exists.

**AC-107** — Malformed deep links, App Links, share intents, and QR payloads fail safely.

**AC-108** — A user can export and delete all local normalized data.

**AC-109** — A cloud user can delete the account inside the app and through the published web route.

**AC-110** — Privacy mode can prevent sensitive account values from appearing in app switcher previews.

**AC-111** — BiometricPrompt failure or unavailable biometrics never permanently lock the user out of deletion or account recovery.

### 25.9 Accessibility and visual quality

**AC-120** — TalkBack reads account name, provider, remaining value, reset, freshness, and primary action in a coherent order.

**AC-121** — Every status remains understandable in grayscale and supported color-correction modes.

**AC-122** — System animation removal eliminates nonessential spatial motion.

**AC-123** — Switch Access and keyboard traversal reach every actionable element with visible focus.

**AC-124** — All standard interactive targets meet 48-by-48 dp minimum.

**AC-125** — Charts provide a textual summary and navigable data alternative.

**AC-126** — The app passes light, dark, dynamic-color, and dynamic-color-disabled screenshot review.

**AC-127** — Adaptive icon displays correctly under common launcher masks and has a valid monochrome layer.

**AC-128** — No P0 screen depends on provider logos to communicate provider identity.

**AC-129** — On the Pixel 10 Pro XL, Comfort View/Night Light, supported color correction, and **Adjust brightness for sensitive eyes** do not make status distinctions depend on color alone or render required text unreadable.

### 25.10 Performance, compatibility, and release

**AC-130** — Release-like Pixel 10 Pro XL startup and dashboard performance meets Section 23 targets.

**AC-131** — No main-thread network, Room, file decode, or OCR operation occurs in tested P0 flows.

**AC-132** — API 29 install, launch, manual account, dashboard, and local history smoke tests pass.

**AC-133** — API 33 notification-permission path passes.

**AC-134** — API 37 local-network and target-behavior tests pass.

**AC-135** — Signed release AAB passes 16 KB page-size compatibility validation.

**AC-136** — Google Play pre-launch report contains no unresolved blocking crash, ANR, accessibility, security, or policy issue.

**AC-137** — Release manifest contains only the approved permission inventory.

**AC-138** — Clean-checkout release build, unit tests, lint, screenshot tests, and security checks pass in CI.

**AC-139** — Physical Pixel 10 Pro XL release-candidate test evidence is attached to the release record.

**AC-140** — No P0/P1 defect remains in security, privacy, data truthfulness, reminder deduplication, account deletion, or accessibility.

**AC-141** — Pixel 10 Pro XL scrolling, refresh, dashboard reflow, and predictive Back pass frame-pacing review with Smooth Display enabled and disabled.

**AC-142** — A current-API compact-phone emulator near 400 dp usable portrait width passes the 0–6 account visual, interaction, and font-scale matrix so the XL reference does not hide smaller-phone defects.

**AC-143** — Pixel 10 Pro XL battery qualification reports battery percentage plus wakeups, CPU time, worker/alarm duration, network bytes, and estimated energy/charge where available.

---

## 26. Test strategy

### 26.1 Test pyramid

| Layer | Purpose | Required tooling/examples |
|---|---|---|
| Pure unit tests | Domain policy, parsing, scoring, timing, invariants | JUnit, Kotlin test, property-based tests where useful |
| JVM integration tests | Repository, serialization, migration logic | Robolectric only where it adds value; Room test DB |
| Instrumented integration tests | Android framework boundaries | AndroidX Test, WorkManager Test, Alarm/notification fakes and device verification |
| Compose UI tests | Behavior and semantics | Compose testing APIs |
| Screenshot tests | Visual regression by account count/window class/theme/font scale | Approved screenshot framework |
| Contract tests | Bridge/cloud/provider fixture compatibility | JSON schema and signed fixture suites |
| Macrobenchmarks | Startup, scrolling, navigation | Macrobenchmark and Baseline Profile tooling |
| Physical-device tests | Real Android scheduling, permissions, notification channels, battery, Pixel UX | Google Pixel 10 Pro XL |
| Play validation | Device-farm compatibility and policy signals | Google Play pre-launch report and staged tracks |

### 26.2 Unit tests

Required suites:

- Account-count constraints.
- Null/unknown/zero handling.
- Capacity-at-risk scoring.
- Primary-window selection.
- Freshness TTL.
- Reminder eligibility.
- Verified/advisory/reset-only modes.
- Quiet hours across midnight.
- Deterministic logical IDs and request-code collision handling.
- Reset rollover.
- Snooze limits.
- Deduplication.
- Delivery-quality classification.
- Time-zone and DST conversion.
- Signature verification.
- Canonical payload hashing.
- URL classification: public HTTPS versus private/LAN.
- URL/QR parsing and rejection cases.
- OCR parser templates.
- Relative date parsing.
- Notification privacy content.
- Export redaction.
- Analytics redaction.

Use a fake `Clock` and explicit `ZoneId`; tests must never depend on wall-clock time.

### 26.3 Property and fuzz tests

Apply property-based or fuzz testing to:

- Random combinations of reset, lead time, quiet hours, and time zone.
- Request-code mapping from logical IDs.
- Malformed QR and App Link inputs.
- Oversized or adversarial JSON.
- Provider/bridge numeric ranges and decimal precision.
- OCR-like text permutations.
- Unicode nicknames, bidi controls, emoji, long strings, and confusable hostnames.
- CSV/JSON export escaping.
- URL redirect and origin-change logic.

Security-sensitive parsers must have corpus-based regression tests.

### 26.4 Room and DataStore tests

- Every schema migration from every released version.
- Snapshot plus window atomicity.
- Foreign-key and cascade behavior.
- Uniqueness of logical reminder IDs.
- Recovery from interrupted write.
- 30-day retention pruning.
- DataStore default and migration behavior.
- No secret-bearing field in schema.
- Backup include/exclude rule verification.
- Key invalidation/re-pair behavior where encrypted references exist.

### 26.5 Contract tests

- Bridge pairing start/complete.
- Signed snapshot schema.
- Replay, expiry, and nonce failure.
- OpenAI organization usage fixture mapping.
- Anthropic usage/cost fixture mapping.
- Cloud snapshot upload.
- Credential Manager backend identity exchange.
- Device registration and token rotation.
- Email job idempotency.
- Account deletion.
- FCM payload minimization, if remote notifications are enabled.
- Remote feature-flag kill switch.
- Provider adapter disabled-by-default behavior.

Contract fixtures must be synthetic and versioned.

### 26.6 Compose UI tests

Account counts:

```text
0, 1, 2, 3, 4, 5, 6
```

Required flows:

- Local onboarding.
- Optional Sign in with Google entry.
- Add ChatGPT account manually.
- Add Claude account through screenshot review.
- Edit, reorder, pause, archive, and delete.
- Reminder rule creation and blocked permission state.
- Notification settings recovery.
- Account detail and history.
- Bridge pairing with public origin.
- Bridge pairing with LAN permission path.
- Email verification state.
- Privacy mode and BiometricPrompt fallback.
- Export and deletion.
- Back and predictive Back.
- Process recreation at every wizard step.

Semantics assertions cover labels, roles, state descriptions, traversal order, and merged/unmerged nodes.

### 26.7 Screenshot and adaptive visual matrix

Generate approved snapshots for:

- Compact, medium, expanded, large, and extra-large windows.
- Portrait and landscape.
- Account counts 0–6.
- Hero, large, medium, compact, list, and two-pane layouts.
- Light and dark.
- Dynamic color on/off.
- Font scales 1.0, 1.3, 1.5, and 2.0.
- Default and large display size.
- Long English strings.
- RTL fixture.
- Healthy, urgent, exhausted, stale, error, unknown, and notification-blocked states.
- Gesture/three-button inset variants.
- Fold hinge/separating posture.

Visual diffs are review gates, not automatic proof of correctness; semantic and interaction tests remain required.

### 26.8 Android device/API matrix

| Tier | Device/environment | Purpose |
|---|---|---|
| Required physical | Pixel 10 Pro XL, latest stable Android 17/API 37 | Primary UX, notification, alarm, permission, battery, real-device validation |
| Required emulator | API 29 phone | Minimum supported OS |
| Required emulator | API 33 phone | `POST_NOTIFICATIONS` first-introduction behavior |
| Required emulator | API 35 16 KB environment | Page-size compatibility and Android 15 behavior |
| Required emulator | API 37 compact phone near 400 dp usable portrait width; Pixel 10 profile where available | Smaller-phone counter-coverage, target behavior, 0–6 account fit, and large text |
| Required emulator | API 37 resizable phone | Target-SDK behavior, local-network permission, dynamic window changes, and compact-height landscape |
| Required emulator | API 37 tablet | Expanded/large adaptive UI |
| Required emulator | API 37 foldable | Posture, hinge, resizability |
| Recommended | Lower-memory/low-end profile | Performance and process-death resilience |
| Play farm | Google Play pre-launch devices | Broader OEM/API coverage |

A single Pixel 10 Pro XL is sufficient for the owned physical-device lane, not for full compatibility coverage. The compact-phone API 37 emulator is a release requirement because the XL handset provides more physical and likely more logical room than many supported phones.

### 26.9 Time, alarm, and notification tests

Test every supported lead time under:

- Trigger in future.
- Trigger already passed.
- Reset moved earlier/later.
- Usage becomes exhausted.
- Remaining threshold crosses.
- Snapshot becomes stale.
- Quiet hours cross midnight.
- DST spring/fall transitions.
- Time zone changes.
- Manual device-clock change.
- Reboot.
- App update/package replacement.
- Process killed.
- App force-stopped and relaunched.
- Doze.
- Battery Saver.
- Restricted app battery mode.
- App Standby buckets.
- Notification permission allowed/denied/revoked.
- Channel enabled/disabled.
- Lock-screen detail levels.
- Multiple simultaneous accounts.
- Notification group behavior.
- Snooze before/after reset boundary.
- Device offline.
- WorkManager delayed or canceled.

Assertions cover policy decision, Room record, platform registration, receiver re-evaluation, notification content, and audit timing.

### 26.10 Background-work tests

- Unique periodic work.
- Unique one-time reconciliation.
- Network constraints.
- Backoff.
- Retry versus failure.
- Cancellation.
- Worker process death.
- Worker idempotency.
- App update.
- Work pruning.
- No foreground service.
- No expedited-work abuse.
- Background Task Inspector review in debug.
- Worker duration and battery instrumentation.

### 26.11 Permission tests

| Capability | Cases |
|---|---|
| Notifications | Not requested, allow, deny, deny then Settings allow, revoke, channel block |
| Local network | Not relevant/public bridge, allow LAN, deny LAN, revoke, upgrade to API 37 behavior |
| Photo Picker | Select, cancel, revoked URI, invalid URI, cloud-backed media latency |
| Camera/QR | System scanner available/unavailable; manual fallback; no mandatory camera permission |
| Biometrics | Strong/weak availability as policy allows, device credential fallback, lockout, key invalidation |
| App Links | Verified, unverified browser fallback, malformed/expired token |

No permission test may assume a prior clean state without explicit setup.

### 26.12 Failure tests

- Offline.
- Slow network.
- Captive portal.
- DNS failure.
- Bridge unavailable.
- Invalid TLS or hostname.
- Redirect across origin.
- DNS rebinding/private-address transition.
- Invalid signature.
- 401, 403, 404, 409, 429, and 5xx.
- Malformed/oversized provider payload.
- Unsupported screenshot.
- Partial OCR.
- Out-of-memory pressure during image processing.
- Storage full.
- Room migration failure simulation.
- Email bounce or complaint.
- Revoked Google credential.
- Cloud token expiry.
- FCM unavailable.
- Notification channel deleted/disabled.
- Keystore key invalidation.
- Corrupt cached row.
- Locale/time-zone ambiguity.

### 26.13 Security tests

- Repository, history, and release-artifact secret scan.
- Dependency and container vulnerability scan.
- SBOM generation.
- Exported-component audit.
- Intent spoofing.
- PendingIntent mutability/replay.
- Deep/App Link validation.
- Malicious QR.
- Share URI/path traversal.
- Image bomb and malformed bitmap.
- Backup extraction and restore.
- Network security configuration.
- Cleartext rejection.
- TLS/hostname failure.
- Bridge signature and canonicalization.
- Tenant isolation.
- Authorization object-scope tests.
- Email abuse/rate-limit tests.
- Privacy-mode recent-apps review.
- Screenshot memory/file lifecycle review.
- Release logging and crash-report redaction.
- R8/minification regression.
- Play Integrity degradation/failure behavior, if used.

### 26.14 Performance and battery tests

Macrobenchmark scenarios:

1. Cold launch to Today with six cached accounts.
2. Warm launch.
3. Scroll six dense cards and open detail.
4. Add account wizard.
5. Screenshot import to review.
6. History chart interaction.
7. Settings and notification-health view.

Battery and frame-pacing scenarios on Pixel 10 Pro XL:

- Six accounts, normal daily refresh schedule.
- Smooth Display enabled and disabled for dashboard scrolling, refresh, reflow, and predictive Back.
- Default and largest practical display size.
- Device idle overnight.
- Battery Saver.
- Poor network.
- Bridge repeatedly unavailable within bounded retry.
- Multiple reset reminders.
- No accounts and reminders disabled.

Record CPU time, wakeups, worker duration, alarm count, network bytes, battery delta, estimated charge/energy where available, thermal context, display mode, and elapsed test time. Investigate before accepting the <1% target; do not use the 5200 mAh battery to normalize away inefficient work.

### 26.15 16 KB page-size validation

- Inspect release AAB/APKs for native libraries.
- Run official alignment/compatibility checks.
- Install and execute in a 16 KB environment.
- Exercise every feature backed by a native dependency, especially ML/OCR and cryptography.
- Run startup, screenshot import, bridge signature, and release smoke tests.
- Block release on any incompatible transitive library.

### 26.16 Pixel 10 Pro XL physical release checklist

1. Update the phone to the intended stable Android/security/Play system build; do not qualify a public release only on a beta/QPR build.
2. Record model, storage variant, OS/build/patch metadata, Demeter commit/version/signing channel, and test date.
3. Export the measured viewport JSON in portrait and landscape: px/dp bounds, density, font scale, width/height classes, cutout, and insets.
4. Install a signed release candidate through Play internal testing or `adb`.
5. Clear app data and run first-launch/onboarding.
6. Exercise account counts 0–6 at default font/display scale and validate the Section 11.7 reference composition targets.
7. Repeat 0–6 account smoke tests at font scales 1.3, 1.5, and 2.0 and the largest practical display size.
8. Validate gesture navigation, three-button navigation, IME, one-handed mode when available, and predictive Back.
9. Test Photo Picker, inbound image/text share, paste, OCR confirmation, and temporary-file cleanup.
10. Test notification allow, deny, channel disable, quiet hours, snooze, lock-screen privacy, and Settings recovery.
11. Test a short synthetic reset and record logical trigger, requested alarm time, observed receiver time, notification post time, and delivery delay.
12. Reboot and verify reconciliation; repeat after package replacement/app update.
13. Change time zone and device clock, then verify display, audit, and reconciliation.
14. Test public and LAN bridge paths, including Android 17 local-network permission allow/deny/revoke.
15. Test offline, Battery Saver, Doze, restricted battery mode, poor network, and bridge-unavailable retries.
16. Run performance/frame-pacing scenarios with Smooth Display enabled and disabled.
17. Run the normal six-account battery scenario and record percentage, estimated energy/charge where available, CPU, wakeups, worker/alarm duration, network, and thermal context.
18. Review light, dark, dynamic color on/off, color correction, Comfort View/Night Light, and **Adjust brightness for sensitive eyes** for legibility and non-color status semantics.
19. Run TalkBack and Switch Access smoke tests plus Accessibility Scanner review.
20. Test privacy mode and app-switcher preview.
21. Export diagnostics, verify redaction, and delete all local/cloud data.
22. Attach pass/fail evidence against acceptance criteria, including the compact-phone emulator counter-test result.

### 26.17 Release fixtures

Maintain versioned, synthetic fixtures for:

- ChatGPT reset-only view.
- ChatGPT task-counter view.
- Claude session and weekly usage view.
- Claude model-specific weekly windows.
- OpenAI API token/cost buckets.
- Anthropic API token/cost buckets.
- Unknown, exhausted, stale, permission-blocked, delayed, and reset-changed states.
- Public and LAN bridge pairing.
- Google identity and email verification states.
- Long/RTL strings.
- 16 KB/native dependency smoke.

No real user screenshot, provider response, email, token, or production credential may be committed to the repository.

---

## 27. Rollout and launch gates

### Milestone A — Android product skeleton

Deliver:

- Kotlin/Compose project and CI.
- Android design tokens and adaptive app icon.
- Domain models.
- Room/DataStore foundations.
- Mock adapter and synthetic fixtures.
- One-through-six adaptive Today dashboard.
- Account detail and local history.
- Navigation, edge-to-edge, predictive Back.
- Accessibility baseline.
- Pixel 10 Pro XL developer-install path.

**Gate:** Clean-checkout build passes; 0–6 account visual/semantics review passes on Pixel 10 Pro XL and adaptive emulators; no provider integration exists outside mocks.

### Milestone B — Local consumer mode

Deliver:

- Manual entry.
- Explicit paste.
- Android Photo Picker.
- Inbound Sharesheet image/text.
- On-device ML Kit OCR.
- Editable confirmation flow.
- Freshness, confidence, reset-only, and unknown states.
- Temporary-image deletion and backup exclusions.

**Gate:** Supported synthetic/reference screenshot accuracy meets target after confirmation; no raw image or recognized text leaves device; import threat-model tests pass.

### Milestone C — Android reminder engine

Deliver:

- Pure reminder policy.
- Room schedule intent.
- One-shot inexact AlarmManager scheduler.
- WorkManager reconciliation.
- Notification permission rationale and request.
- Notification channels.
- All lead times and thresholds.
- Advisory/verified/reset-only modes.
- Quiet hours and snooze.
- Boot/time/time-zone/app-update repair.
- Reminder health and audit trail.
- Test notification.

**Gate:** Time-travel, permission, reboot, Doze, duplicate, and force-stop-recovery suites pass; manifest has no exact-alarm permission or reminder foreground service; Pixel 10 Pro XL observed-timing evidence is attached.

### Milestone D — Optional cloud, Google identity, and email

Deliver:

- Credential Manager Sign in with Google.
- Backend identity exchange.
- Verified email destinations.
- Snapshot upload.
- Email scheduling, unsubscribe, bounce/abuse controls.
- Android App Links.
- In-app and web account deletion.
- Optional FCM plumbing behind a disabled-by-default feature flag.

**Gate:** Privacy, tenant isolation, identity, email abuse, App Link, Data safety, and deletion tests pass. Local mode remains independent.

### Milestone E — User-owned bridge

Deliver:

- Open-source bridge.
- Public HTTPS pairing.
- Signed snapshots.
- Android QR/manual pairing.
- Android 17 LAN permission path.
- OpenAI organization adapter.
- Anthropic organization adapter.
- Deployment and rotation documentation.

**Gate:** Provider credentials are absent from app/cloud; TLS, signature, replay, URL, origin-change, and LAN permission tests pass; official endpoint and terms review complete.

### Milestone F — Android quality and Google Play readiness

Deliver:

- Material 3 Expressive visual polish.
- Dynamic color and dark theme.
- Adaptive tablet/foldable behavior.
- Baseline Profiles and Macrobenchmarks.
- 16 KB page-size validation.
- Accessibility closure.
- Data safety and privacy policy.
- Store listing and screenshots.
- Play review notes.
- Internal and closed testing.
- Android vitals and pre-launch triage.
- Signed release AAB.

**Gate:** No P0/P1 security, privacy, accessibility, data-truthfulness, reminder, account-deletion, 16 KB, or Google Play defect.

### 27.1 Test-track progression

| Track | Audience | Entry criteria | Exit criteria |
|---|---|---|---|
| Local debug | Developers | Milestone A build | Core flows and tests stable |
| Play internal | Team and named testers | Signed staging AAB, privacy-safe fixtures | Crash/ANR-free smoke; permission and update path pass |
| Closed alpha | Small trusted cohort | Milestones B–C, support channel, privacy policy | Reminder usefulness and delivery evidence; no severe trust issue |
| Closed beta | Broader target users | Milestones D–E as enabled, deletion and abuse controls | Reliability, OCR correction, notification opt-out, battery targets acceptable |
| Open testing, optional | Larger compatibility validation | Policy/review readiness | No unresolved scale or support blocker |
| Production staged | Public | Final gate and rollback readiness | Expand by evidence |

### 27.2 Production rollout

Recommended staged percentages:

```text
1% → 5% → 20% → 50% → 100%
```

Hold duration is evidence-based rather than calendar-only. At every stage review:

- Crash-free users and ANRs.
- Notification permission conversion.
- Reminder blocked/delayed/missed rates.
- Duplicate rate.
- OCR correction/failure.
- Email bounce/complaint/unsubscribe.
- Bridge pairing/signature/TLS failure.
- Battery and worker duration.
- Account deletion success.
- Support themes.
- Provider-policy signals.

### 27.3 Stop-ship and rollback triggers

Stop expansion or roll back when any of the following occurs:

- Provider credential, screenshot, email, or sensitive usage data exposure.
- Cross-tenant access.
- Invalid bridge signature accepted.
- Duplicate or materially incorrect reminder pattern above threshold.
- Destructive data migration or deletion failure.
- Significant crash/ANR regression.
- Material battery drain or runaway background work.
- Google Play policy warning with credible removal risk.
- Provider legal/policy objection to an enabled adapter.
- Broken notification/channel behavior after target/API update.
- 16 KB incompatibility in release artifact.
- Accessibility regression blocking essential use.

### 27.4 Rollback strategy

- Provider adapters, cloud sync, FCM, LAN bridge, and cloud email are remotely feature-gated.
- A server-side kill switch stops new email and remote-notification jobs.
- A local feature flag can disable a broken parser template or bridge path without erasing data.
- The app remains useful in local manual mode if cloud or bridge services are disabled.
- Provider policy changes disable the adapter without deleting local history.
- Staged rollout can be halted in Play Console.
- Database migrations are forward-safe; a rollback must not require installing an older schema-incompatible APK.
- Critical client correction uses a new version with accelerated staged rollout, not an undocumented server behavior change.

---

## 28. Prioritized scope

### P0 — Required for public Android v1

- Native Android phone app in Kotlin and Jetpack Compose.
- Minimum API 29; compile/target API 37.
- Pixel 10 Pro XL physical validation.
- Up to three accounts per provider.
- Adaptive one-through-six Today dashboard.
- Multiple usage windows per account.
- Manual entry, explicit paste, Android Photo Picker, and inbound Sharesheet.
- On-device OCR with mandatory confirmation.
- Local Room history for 30 days.
- One-shot inexact AlarmManager reminders at all requested lead times.
- WorkManager refresh and reminder reconciliation.
- Android 13+ notification permission flow and notification channels.
- Freshness, confidence, advisory, verified-only, and reset-only semantics.
- Quiet hours, thresholds, snooze, deduplication, and reminder audit.
- Boot, app-update, clock, and time-zone reconciliation.
- Notification privacy controls.
- Optional Credential Manager Sign in with Google.
- Verified email reminders and account deletion.
- User-owned bridge protocol and reference implementation.
- Public HTTPS bridge pairing.
- Android 17 LAN bridge permission path.
- OpenAI and Anthropic organization-usage bridge adapters.
- Privacy mode, export, local deletion, and cloud deletion.
- TalkBack, Switch Access, font/display scaling, color correction, and animation-setting support.
- Dynamic color, light/dark themes, adaptive app icon, and Material 3 polish.
- Baseline Profiles, Android vitals readiness, 16 KB compatibility, and Google Play release package.

### P0 launch exclusions

- Exact alarms.
- Persistent foreground service.
- Background clipboard monitoring.
- Broad photo-library permission.
- Provider password/session-cookie capture.
- Automatic consumer-plan scraping.
- LAN discovery.
- Camera permission as a mandatory pairing path.
- Provider logos without permission.
- Wear OS, Android TV, Automotive, or ChromeOS-specific experiences.
- iOS.

### P1 — After Android v1 quality bar

- Android home-screen widgets using Glance or the current recommended framework.
- A compact “next reset” widget and multi-account widget.
- Optional remote FCM reminder reconciliation for cloud/bridge-connected users.
- 90-day and annual history.
- Cross-device encrypted sync.
- Approved provider OAuth adapters.
- Claude Enterprise analytics adapter.
- Advanced budget forecasting.
- Better parser-template update mechanism.
- Tablet/foldable two-pane analytics experience.
- Quick Settings tile only if a clear user job exists.
- Wear OS glance companion only after phone reminder quality is proven.
- Local-only encrypted backup/export package with explicit restore flow.
- Managed configuration for enterprise deployments.

### P2 — Expansion

- Native iOS application derived from the platform-neutral domain and contract layers.
- Team-shared dashboards with explicit roles.
- Organization policy controls.
- Additional AI providers through the same adapter contract.
- Desktop/web dashboard.
- Advanced anomaly detection.
- User-owned notification relay.
- Provider-partner integrations requiring commercial agreements.
- Rich live-update Android surfaces only when platform policy and sustained user value justify them.

---

## 29. Repository and implementation handoff

### 29.1 Suggested repository structure

```text
/apps
  /android                 Native Kotlin/Compose application
/services
  /cloud-api               Optional identity, snapshot, email, and FCM service
  /bridge                  Self-hosted provider connector
/packages
  /contracts               JSON schemas and generated Kotlin/TypeScript types
  /fixtures                Synthetic provider, OCR, timing, and bridge fixtures
/docs
  /architecture
  /security
  /google-play
  /provider-compliance
  /runbooks
/infra                     Infrastructure as code
```

Suggested Android project structure:

```text
/apps/android
  /app
  /core
    /model
    /database
    /datastore
    /network
    /security
    /designsystem
    /notifications
    /testing
  /feature
    /onboarding
    /today
    /account
    /history
    /reminders
    /settings
    /import
    /bridge
  /data
    /usage
    /cloud
    /bridge
  /sync
  /benchmark
  /baselineprofile
```

### 29.2 Required documentation

- `README.md` — local development, architecture, and build commands.
- `SECURITY.md` — threat model, reporting, secret handling, and Android component policy.
- `PRIVACY.md` — data inventory, permissions, retention, backup, analytics, and deletion.
- `ANDROID_ARCHITECTURE.md` — module graph, UDF, persistence, WorkManager, AlarmManager, and notifications.
- `REMINDER_SEMANTICS.md` — eligibility, inexact timing, freshness, audit, and force-stop behavior.
- `PIXEL10_TEST_PLAN.md` — physical-device setup and evidence checklist.
- `ADAPTIVE_UI_SPEC.md` — window classes, account-count layouts, font/display scaling, and foldables.
- `BRIDGE_DEPLOYMENT.md` — public HTTPS and LAN bridge setup.
- `BRIDGE_SECURITY.md` — pairing, signatures, origin policy, LAN permission, and rotation.
- `PROVIDER_ADAPTER_CHECKLIST.md` — authorization and release gates.
- `PLAY_REVIEW_NOTES.md` — Google Play explanation and test path.
- `DATA_SAFETY_MAPPING.md` — Play Data safety answer evidence.
- `PERMISSIONS.md` — manifest permission inventory and request timing.
- `BACKUP_AND_RESTORE.md` — extraction rules and re-auth/re-pair behavior.
- `RUNBOOK_EMAIL.md` — bounce, abuse, and kill switch.
- `RUNBOOK_PROVIDER_CHANGE.md` — adapter disablement and recovery.
- `RUNBOOK_ANDROID_RELEASE.md` — signing, AAB, tracks, staged rollout, rollback.
- `RUNBOOK_INCIDENT.md` — privacy/security/reliability incident response.
- `SBOM.md` or generated equivalent — dependency inventory.

### 29.3 Build and verification commands

The repository must expose stable commands, for example:

```bash
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
./gradlew test
./gradlew connectedCheck
./gradlew lint
./gradlew :app:verifyReleaseResources
./gradlew :benchmark:connectedCheck
```

Add project-specific tasks for:

```text
schema migration tests
screenshot/golden tests
secret scan
dependency/SBOM scan
manifest permission audit
exported-component audit
16 KB page-size validation
provider-contract tests
bridge security tests
```

A coding agent must not mark a feature complete merely because a debug APK builds.

### 29.4 Agent execution contract for Claude Code or Codex

The coding agent must:

1. Treat this PRD as the source of product truth.
2. Build vertical slices in milestone order.
3. Start with mock and synthetic fixtures.
4. Use native Kotlin and Jetpack Compose; do not substitute a cross-platform framework without a recorded product decision.
5. Keep provider parsing behind adapter boundaries.
6. Keep Android framework types out of the pure domain layer.
7. Use Room as normalized local source of truth and DataStore only for appropriate settings.
8. Implement reminder policy as a pure, clock-injected, heavily tested module before platform scheduling.
9. Use inexact AlarmManager plus WorkManager reconciliation; do not add exact-alarm permission.
10. Request notification and local-network permissions only at the contextual feature boundary.
11. Use Photo Picker rather than broad media permission.
12. Use Credential Manager for Sign in with Google.
13. Use Android Keystore for device/application keys; never store provider administrative keys anywhere in the app.
14. Never invent an undocumented provider API.
15. Never implement password, cookie, session-token, accessibility-service, VPN, WebView, or private-endpoint capture.
16. Never upload raw screenshots or recognized OCR text.
17. Make every platform registration idempotent and repairable.
18. Add unit, integration, Compose UI, screenshot, and physical-device test instructions with each functional slice.
19. Maintain manifest, permission, exported-component, backup, and Data safety evidence.
20. Keep a decision log for deviations.
21. Stop production enablement of any provider adapter whose authorization basis is missing while continuing all non-blocked work.
22. Preserve local-only operation when cloud, email, FCM, bridge, or provider adapters are absent.
23. Update documentation and threat model with each material architecture change.
24. Produce a signed release-candidate AAB and Pixel 10 Pro XL validation checklist before declaring v1 complete.

### 29.5 Initial implementation sequence

#### Slice 1 — Domain and mock dashboard

- Models and IDs.
- Fixture generator.
- Capacity-at-risk and primary-window policy.
- Compose design system.
- 0–6 adaptive Today layouts.
- Account detail.
- TalkBack semantics.
- Screenshot tests.

#### Slice 2 — Local persistence and account management

- Room schema and migrations.
- DataStore settings.
- Repository layer.
- Add/edit/pause/delete.
- Local history.
- Export/delete.
- Backup rules.

#### Slice 3 — Reminder policy and Android platform scheduler

- Pure policy.
- Schedule records.
- AlarmManager abstraction.
- Receivers.
- WorkManager reconciliation.
- Notification channels and permission.
- Audit UI.
- Time-travel and device tests.

#### Slice 4 — Screenshot import

- Photo Picker and Sharesheet.
- Bounded image handling.
- ML Kit OCR.
- Parser templates.
- Review/confirmation.
- Temp-file cleanup.

#### Slice 5 — Cloud/email identity

- Credential Manager.
- Backend auth.
- Verified email.
- App Links.
- Deletion.

#### Slice 6 — Bridge

- Contracts.
- Public HTTPS pairing.
- Signature verification.
- LAN permission path.
- Provider organization adapters.
- Deployment docs.

#### Slice 7 — Release hardening

- Baseline Profiles.
- Macrobenchmark.
- 16 KB.
- Accessibility.
- Play evidence.
- Pixel 10 Pro XL release candidate.

### 29.6 Definition of done

A feature is done only when:

- Functional acceptance criteria pass.
- Error, empty, loading, offline, stale, permission-blocked, process-recreation, and accessibility states exist.
- Unit and relevant integration/UI/screenshot tests pass.
- Pixel 10 Pro XL manual steps are documented when a framework behavior cannot be fully automated.
- Analytics contain no restricted data.
- Security and privacy reviews are complete.
- Manifest and backup diffs are reviewed.
- User-facing copy is final.
- Design QA passes light, dark, dynamic color, font/display scale, and adaptive window modes.
- Battery/background implications are measured or bounded.
- Documentation and runbooks are updated.
- Release behavior does not depend on debug-only code, real credentials, or private provider endpoints.

---

## 30. Decision log

| Decision | Rationale |
|---|---|
| Native Android first | The user owns an Android Pixel 10 Pro XL and can physically validate the complete product |
| Kotlin and Jetpack Compose | Current recommended native Android UI stack with strong adaptive and accessibility support |
| Material 3 Expressive plus Demeter tokens | Creates a distinctive, platform-native quality bar without visually porting iOS |
| Minimum API 29 | Preserves broad Android compatibility while keeping modern platform and Jetpack capabilities practical |
| Compile/target API 37 | Aligns with Android 17 behavior, including current local-network protections, and exceeds Play's current minimum |
| Pixel 10 Pro XL as physical reference, not layout constant | Enables owned-device validation without hard-coding one resolution or excluding other Android devices |
| Compact-phone emulator required beside the Pro XL | Prevents the larger reference handset from hiding smaller-width, denser, or reachability defects |
| Energy-normalized battery evidence | The Pro XL’s 5200 mAh battery can make percentage-only measurements look better than the underlying background work |
| Maximum three accounts per provider in v1 | Matches requested scope and bounds the adaptive dashboard |
| “Usage allowance,” not universal “tokens” | Consumer limits can be dynamic and measured in several units |
| No consumer scraping | Provider terms, reliability, security, and Google Play risk |
| No provider keys in Android or Demeter cloud | Administrative credentials are high impact and do not belong in a client app |
| User-owned bridge for official API organizations | Enables automation while keeping credentials under user control |
| Manual/screenshot consumer mode | Buildable, explicit, privacy-preserving fallback until approved access exists |
| Android Photo Picker instead of media-library permission | Minimizes access and uses the system-mediated user-selection model |
| ML Kit OCR on device | Avoids uploading sensitive screenshots and supports an editable assistive flow |
| Local mode requires no Demeter account | Data minimization and immediate value |
| Credential Manager for optional Google identity | Current Android identity path; avoids introducing legacy sign-in APIs |
| Email requires verified destination | Prevents abuse and incorrect provider-email assumptions |
| Room is local source of truth | Supports history, transactions, migrations, and reminder auditability |
| DataStore is limited to settings | Prevents misuse as a relational/history database |
| Inexact AlarmManager plus WorkManager | Matches non-critical reminder semantics and Android power-management guidance |
| No exact-alarm permission in v1 | Precision is desirable but not core enough to justify special access or policy burden |
| No foreground-service polling | Avoids battery, trust, and policy cost for non-continuous work |
| Notification permission requested in context | Improves comprehension and follows Android runtime-permission behavior |
| User-modified notification channels are respected | Android users own channel importance, sound, vibration, and visibility |
| Android 17 local-network permission only for LAN bridge | Limits broad LAN access to an explicit user-selected feature |
| Public HTTPS bridge is preferred | Simpler reachability, permission, and security posture for mobile |
| No LAN discovery in v1 | Reduces permission scope, attack surface, and complexity |
| Freshness is always visible | Prevents stale data from appearing live |
| Logical reminder intent persists independently of platform alarms | Makes scheduling repairable after reboot, update, or framework loss |
| Accessibility may introduce scrolling | Legibility and autonomy take precedence over a rigid “everything must fit” rule |
| Dynamic color is optional | Supports Android personalization while preserving a stable Demeter identity and status semantics |
| 16 KB compatibility is a release gate | Required for current Google Play submissions targeting Android 15+ |
| iOS is P2 | Android validation is the immediate product path; platform-neutral contracts preserve future portability |

---

## 31. External dependencies and unresolved blockers

These are external dependencies or capability limits, not reasons to delay the rest of the Android build.

### 31.1 Provider-access blockers

1. A documented provider-approved API or OAuth scope is required for fully automatic individual ChatGPT and Claude consumer allowance monitoring.
2. Provider trademark permission may be required for logos or branded visual assets; v1 should use text compatibility labels.
3. Claude Enterprise and any future OpenAI workspace usage adapter require plan-specific credential and contract validation.
4. Provider products may change the wording and structure of consumer usage screens, requiring parser-template updates and explicit failure handling.

### 31.2 Android reminder limits

1. Exact conditional evaluation at the moment of delivery requires a fresh source; manual consumer mode can provide only advisory or user-refreshed verification.
2. Inexact alarms, WorkManager, Doze, App Standby, force-stop, and OEM battery policy prevent a universal exact-delivery guarantee.
3. A force-stopped app cannot guarantee alarms/work until the user launches it again.
4. User-disabled app notifications or channels cannot be bypassed.
5. Reminder quality must be evaluated on real devices and cannot be proven only through unit tests.

### 31.3 Bridge and network dependencies

1. A LAN-hosted bridge targeting Android 17 behavior requires user-granted local-network permission unless a system-mediated alternative applies.
2. Public HTTPS deployment requires a valid domain/certificate and secure bridge hosting.
3. Mobile reachability to home/NAS/workstation bridges depends on network topology, VPN, firewall, sleep state, and DNS.
4. Self-signed certificates are not accepted through a generic trust bypass in production; a reviewed private-PKI or public-certificate approach is required.
5. Provider organization APIs may have eligibility, administrative-key, rate-limit, or retention constraints.

### 31.4 Cloud/email dependencies

1. Transactional email requires a provider, domain verification, bounce/complaint processing, and abuse controls.
2. Sign in with Google requires Google Cloud/OAuth configuration and backend token validation.
3. Verified Android App Links require domain ownership and Digital Asset Links configuration.
4. Google Play account-deletion and Data safety requirements require public support/privacy infrastructure before external testing.
5. FCM is optional; core local reminders must not depend on it.

### 31.5 Validation dependencies

1. The user's Pixel 10 Pro XL is the only required owned physical device, but it cannot represent API 29, smaller compact phones, low-end memory, tablets, foldables, or diverse OEM power behavior.
2. A current-API compact-phone emulator near 400 dp usable width plus the broader emulator/device-farm matrix is required to close compatibility risk.
3. 16 KB validation depends on every transitive native dependency being compatible.
4. Google Play pre-launch and Android vitals evidence becomes available only after track uploads and user traffic.
5. Accessibility quality requires manual TalkBack/Switch Access review in addition to automated scanning.

The architecture keeps blocked capabilities feature-gated so Demeter can ship a truthful local and bridge-supported Android experience without waiting for provider consumer APIs or exact background execution.

---

## 32. Source and policy notes validated for this PRD

Validated on **July 12, 2026** against official Android, Google, OpenAI, and Anthropic materials. Revalidate platform policies, target-SDK rules, provider terms, and dependency versions before each production release.

### 32.1 Android platform findings reflected in this PRD

1. Android 17 is API level 37.
2. Apps targeting API 37 are subject to Android 17 behavior changes, including local-network protections.
3. Direct broad LAN access for a target-37 app can require the `ACCESS_LOCAL_NETWORK` runtime permission; Demeter therefore asks only during explicit LAN bridge setup.
4. Android 13+ uses the `POST_NOTIFICATIONS` runtime permission for ordinary app notifications.
5. Android recommends inexact alarms whenever possible; exact alarms are costly and subject to special access/policy constraints.
6. WorkManager provides durable best-effort work but is not an exact-timing mechanism.
7. Material 3 Expressive and Material 3 Adaptive support contemporary Android visual and adaptive UI implementation.
8. Android architecture guidance recommends Compose, repositories, ViewModels/state holders, coroutines/Flow, and unidirectional data flow.
9. Android Photo Picker provides scoped access to user-selected media without broad library access.
10. ML Kit Text Recognition can run OCR on Android images; Demeter still requires user confirmation.
11. Credential Manager is the current Android entry point for Sign in with Google.
12. Android Keystore supports non-exportable cryptographic key material and usage restrictions.
13. BiometricPrompt is the system authentication dialog for biometric/device-auth flows.
14. Google Play requires 16 KB page-size support for applicable new apps and updates targeting Android 15+.
15. Google Play currently requires new phone/tablet apps and updates to target at least Android 15/API 35; Demeter targets API 37.
16. The Pixel 10 Pro XL has a 6.8-inch 20:9 1344-by-2992 LTPO OLED display at 486 PPI, 1–120 Hz refresh, 16 GB RAM, a typical 5200 mAh battery, Tensor G5, and Titan M2. It measures 162.8 × 76.6 × 8.5 mm and weighs 232 g; it is appropriate as the owned high-end phone reference, not as the sole compatibility target.
17. Pixel 8 and later devices receive seven years of OS and security updates from initial availability, making the Pixel 10 Pro XL a durable validation device.
18. Android window-size classes are based on the app window, not physical screen diagonal, product name, or raw display resolution; compact width is below 600 dp.
19. Width and height classes are independent. A phone in landscape may have medium width but compact height, so multi-pane decisions must account for both.
20. Pixel display size and font size are user-adjustable. Pixel 10 Pro XL also exposes display comfort/accessibility options including Comfort View and **Adjust brightness for sensitive eyes**, which are relevant physical robustness checks but do not replace software contrast requirements.
21. Android 17 is available for Pixel 10 Pro XL; release qualification must record the exact stable build because monthly updates roll out over time.

### 32.2 Provider feasibility findings retained from v1.0

1. ChatGPT allowances may be model-, feature-, plan-, or window-specific and can be dynamic.
2. ChatGPT shows reset timing when that information is available.
3. OpenAI provides organization usage/cost APIs for API-platform data; those data must not be represented as consumer ChatGPT allowance.
4. Anthropic provides a Usage & Cost Admin API for eligible organizations and states that the Admin API is unavailable to individual accounts.
5. Consumer ChatGPT and Claude monitoring therefore requires manual/user-shared evidence until a documented provider-approved consumer API or OAuth scope exists.
6. Demeter must not collect provider passwords, session cookies, or administrative keys in the Android app.
7. Provider integrations remain subject to current terms, authentication requirements, rate limits, and product eligibility.

### 32.3 Official Android and Google references

- [Android 17 is here](https://developer.android.com/about/versions/17/blog-release)
- [Android 17 behavior changes for apps targeting API 37](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Task scheduling and persistent background work](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Compose Material 3 Adaptive releases](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive)
- [Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations)
- [Compose UI architecture and unidirectional data flow](https://developer.android.com/develop/ui/compose/architecture)
- [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)
- [ML Kit Text Recognition v2 for Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [Sign in with Google through Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Biometric authentication dialog](https://developer.android.com/identity/sign-in/biometric-auth)
- [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)
- [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Pixel 10 Pro and Pixel 10 Pro XL technical specifications](https://store.google.com/product/pixel_10_pro_specs)
- [Pixel phone hardware technical specifications](https://support.google.com/pixelphone/answer/7158570)
- [Pixel screen and display settings](https://support.google.com/pixelphone/answer/6111557)
- [Use window size classes](https://developer.android.com/develop/ui/views/layout/use-window-size-classes)
- [Get started with adaptive apps](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps)
- [Support different display sizes](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes)
- [Get Android 17 on supported Pixel devices](https://developer.android.com/about/versions/17/get)
- [Pixel software update policy](https://support.google.com/pixelphone/answer/4457705)
- [Google Play Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)

### 32.4 Official provider references

- [OpenAI Help Center — GPT-5.6 in ChatGPT](https://help.openai.com/en/articles/11909943-gpt-55-in-chatgpt)
- [OpenAI API Reference — Organization usage](https://platform.openai.com/docs/api-reference/usage)
- [OpenAI Terms of Use](https://openai.com/policies/terms-of-use/)
- [OpenAI Help Center — API key safety](https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety)
- [Claude Platform Docs — Usage and Cost API](https://platform.claude.com/docs/en/manage-claude/usage-cost-api)
- [Claude Help Center — Usage limit best practices](https://support.anthropic.com/en/articles/9797557-usage-limit-best-practices)
- [Anthropic Consumer Terms](https://www.anthropic.com/legal/consumer-terms)

### 32.5 Revalidation checklist before implementation freeze

- Confirm API 37 and Android 17 stable toolchain availability in the chosen Android Studio/AGP versions.
- Confirm latest stable Compose BOM, Material 3, Material 3 Adaptive, Room, WorkManager, Credential Manager, and ML Kit versions.
- Confirm Play target-SDK deadline, exact-alarm policy, Data safety, and account-deletion rules.
- Confirm `ACCESS_LOCAL_NETWORK` implementation guidance and compatibility behavior.
- Confirm the Pixel 10 Pro XL stable OS build, security patch, Play system update, navigation mode, display/font scale, Smooth Display state, and any exposed screen-resolution mode used for release qualification.
- Capture the actual portrait/landscape app-window bounds, density, font scale, width/height classes, cutout, and insets from the release candidate.
- Confirm the API 37 compact-phone counter-test remains in CI and release qualification.
- Confirm all native dependencies support 16 KB pages.
- Confirm current OpenAI and Anthropic provider API/terms position.
- Confirm any provider-approved OAuth scope before enabling its adapter.

---

# Recommendation

Build Demeter as a native, local-first Android application physically qualified on the user's Pixel 10 Pro XL, but selected and laid out entirely from runtime app-window constraints. Use Kotlin, Jetpack Compose, Material 3 Expressive, Material 3 Adaptive, Room, DataStore, Android Keystore, Photo Picker, on-device ML Kit OCR, one-shot inexact AlarmManager reminders, and WorkManager reconciliation.

Exploit the Pro XL's additional portrait room to improve information hierarchy and keep up to four reference account cards above the fold at default settings, while preserving bottom-reachable actions and requiring a smaller compact-phone emulator as a release counterweight. Do not treat the 6.8-inch panel, 1344 × 2992 resolution, 16 GB RAM, or 5200 mAh battery as application assumptions.

Do **not** make exact alarms, persistent foreground services, broad media permissions, provider credential capture, or unauthorized consumer scraping dependencies of v1. The strongest first release is a truthful, polished product that reliably handles user-supplied evidence, exposes reminder freshness and Android delivery health, and uses a user-owned bridge only for documented organization APIs.

# Top risks and mitigations

| Risk | Impact | Required mitigation |
|---|---|---|
| No official cross-account consumer usage API | Blocks continuous consumer automation | Ship manual/paste/screenshot mode; keep approved-adapter interface; pursue provider authorization |
| Android defers inexact alarms or background work | Reminder may arrive later than the logical lead time | No exact claim; inexact AlarmManager plus persisted intent, WorkManager repair, observed-timing audit, advisory wording |
| Force-stop or notification/channel disablement | Reminders cannot post | Visible reminder-health state, launch-time repair, settings recovery, no false “active” status |
| Provider credential exposure | Account compromise and charges | No keys/passwords/cookies in Android/cloud; user-owned bridge, least privilege, rotation, secret scanning |
| OCR or manual-entry error | Incorrect status or reset | On-device OCR, per-field confidence, mandatory confirmation, editable values, synthetic parser tests |
| Android 17 LAN permission or bridge reachability | LAN bridge fails or creates privacy concern | Ask only for explicit LAN pairing, prefer public HTTPS, no discovery, fail closed, clear alternative |
| Six-account density harms readability | Poor UX and accessibility | Constraint-based adaptive layouts, 48 dp targets, minimum type, list/scroll fallback |
| One Pixel 10 Pro XL hides smaller-phone and ecosystem defects | Width, density, OEM/API, low-memory, or large-screen issues reach users | Required ~400 dp API 37 compact-phone emulator, minimum-API/low-memory lanes, Play pre-launch, staged rollout |
| Pro XL size creates one-handed reach friction | Frequent actions become awkward or require grip shifts | Bottom navigation, lower-screen add/refresh paths, in-card actions, one-handed-mode test |
| 120 Hz display masks frame-pacing defects | UI seems smooth only on the reference phone | Qualify Smooth Display on/off, Macrobenchmark, frame metrics, lower-end emulator |
| 5200 mAh battery masks inefficient background work | Percentage target passes despite excessive wakeups or CPU | Record energy/charge, wakeups, CPU, worker/alarm duration, and network in addition to percent |
| Notification fatigue | Users disable channels/app | Thresholds, quiet hours, conservative defaults, deduplication, clear global pause |
| Google Play policy or target behavior changes | Release delay or removal risk | Current target SDK, permission inventory, Play evidence pack, kill switches, pre-release revalidation |
| Native dependency lacks 16 KB support | Play rejection or runtime failure | Dependency inventory, CI checks, 16 KB emulator/device validation, release gate |
| Cloud/email abuse or wrong destination | Privacy and deliverability risk | Verification, rate limits, unsubscribe, bounce/complaint handling, audit records |

# Next 3 actions

1. Create `/apps/android` and implement Milestone A only: domain models, synthetic fixtures, Room skeleton, design tokens, and the 0–6 account adaptive Today dashboard.
2. Implement the pure reminder policy with a fake clock and exhaustive time-travel tests before adding AlarmManager, WorkManager, notification permission, or channels.
3. Establish the Pixel 10 Pro XL release-test record and CI emulator matrix immediately: capture real viewport JSON on the phone, add the ~400 dp API 37 compact-phone counter-test, and retain minimum API, API 33 notification, API 37 local-network, tablet/foldable, low-memory, and 16 KB lanes.
