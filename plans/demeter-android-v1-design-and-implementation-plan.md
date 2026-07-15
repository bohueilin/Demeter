# Demeter Android v1 — Design & Implementation Plan

> **Source PRD:** `Demeter_PRD_Android_Pixel_10_Pro_XL_v2.1.md` (v2.1, July 12, 2026 — engineering-ready)
> **Purpose of this document:** Pre-implementation design plan, prepared for external design review. Feedback is requested on everything marked **[PROPOSAL]** and on the consolidated question list in §9. Nothing here has been implemented yet.
> **Status:** DRAFT — awaiting design feedback before implementation begins.
> **Revision 2 (2026-07-12):** The external design review (`demeter-android-v1-design-review-by-ChatGPT.md`) was received and **accepted** (verdict: conditionally approve after P0 changes). The authoritative record of all accepted changes is **`Demeter_PRD_v2.2_Design_Revision_Addendum.md`**, which takes precedence over both PRD v2.1 and this plan where they conflict. Statements in this plan that the review flagged as incorrect have been corrected in place below; the addendum carries the full delta (revised phase order, metrics/gates, adopted Q1–Q15 answers).

---

## 1. What Demeter is (context for the reviewer)

Demeter is a privacy-forward, local-first native Android app that gives one glanceable dashboard for usage allowances, reset windows, and utilization across up to three OpenAI/ChatGPT and three Anthropic/Claude accounts. It schedules **conditional, freshness-aware reminders** before allowance resets (lead times 48/24/12/8/4/2/1 h), optionally sends verified-destination email reminders, and never touches provider credentials from the phone. Reference validation device: Google Pixel 10 Pro XL on Android 17 (API 37); minimum API 29.

The defining product constraint: **there is no documented third-party API for consumer-plan allowances.** The PRD therefore fixes three trust tiers:

| Tier | Source | v1 |
|---|---|---|
| 1. Local consumer mode | Manual entry, paste, Photo Picker screenshot + on-device OCR, Sharesheet import | Required |
| 2. User-owned bridge | Official OpenAI/Anthropic **organization** usage APIs, called from user-controlled infrastructure; signed snapshots to the app | Required |
| 3. Provider-approved OAuth/API | Documented consumer usage scopes | Future; disabled stub only |

Everything else flows from this boundary plus Android's background-execution reality (Doze, App Standby, force-stop): reminders are **best-effort inexact**, and the product's honesty about that (freshness labels, confidence tiers, audit trail) *is* the brand — "calm control," truth over false precision.

### 1.1 Binding decisions feedback cannot change

These are settled in the PRD (§0.1, §30) and are out of scope for review — listed so the reviewer doesn't spend effort relitigating them:

- Native Android; Kotlin; Jetpack Compose; Material 3 Expressive + Demeter brand tokens; Material 3 Adaptive window-size classes; **no device-model-based layout, ever**.
- Min API 29, compile/target API 37; Pixel 10 Pro XL is the physical validation device, *never* a layout constant; a ~400 dp compact-phone emulator is a co-equal release gate.
- **Inexact** one-shot `AlarmManager` alarms + WorkManager reconciliation. No `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, no persistent foreground service, no exact-delivery claims.
- `POST_NOTIFICATIONS` requested contextually only after the user enables a reminder; `ACCESS_LOCAL_NETWORK` (Android 17) only when pairing a LAN-hosted bridge.
- Room = local source of truth for history/reminders; DataStore = settings only; Android Keystore for Demeter-generated keys; **no provider passwords, cookies, or admin keys anywhere in app or cloud** — zero-tolerance release gate.
- On-device ML Kit OCR with mandatory editable confirmation; source images discarded by default; Photo Picker, never broad media permission.
- Local mode requires no Demeter account; cloud (Sign in with Google via Credential Manager, verified email reminders) is opt-in; Play distribution via staged tracks.
- No consumer scraping, no hidden WebViews, no private endpoints; unapproved adapters stay disabled in production.
- Naming: "Demeter — AI Usage Monitor"; user-facing term is **usage allowance** (not "tokens"); provider identification by text label only (no logos without permission).

---

## 2. Durable architectural decisions

Decisions expected to hold through v1. Each is either a PRD restatement (cited) or marked **[PROPOSAL]** where the PRD leaves the choice open.

| # | Decision | Rationale / PRD ref |
|---|---|---|
| D1 | **Monorepo**: `/apps/android`, `/services/cloud-api`, `/services/bridge`, `/packages/contracts`, `/packages/fixtures`, `/docs`, `/infra` | Contracts + synthetic fixtures are shared release-gate artifacts; versioning them beside all three runtimes prevents schema drift (§29.1, §16.7) |
| D2 | Gradle modules per §18.2 from day one (`:app`, `:core:*`, `:feature:*`, `:data:*`, `:sync`, `:benchmark`, `:baselineprofile`); **`:core:model` is a pure-JVM Kotlin module** (no Android plugin) | The most load-bearing boundary is "reminder policy and domain types compile without Android"; enforcing it via module type beats convention (§18.2–18.3, §29.4 rule 6) |
| D3 | Layering: Presentation → Domain ← Data ← Platform-infrastructure, unidirectional; ViewModels expose immutable `StateFlow<UiState>`; adapters/parsers never touched by Composables | §18.3–18.4; makes the pure reminder policy and layout fit engine unit-testable with a fake `Clock` before platform code exists |
| D4 | **Room is the single source of truth** for accounts, connections, windows, snapshots, reminder rules, scheduled reminders, reminder events. AlarmManager/notification state is **derived and repairable** | Binding: logical reminder intent persists independently of platform alarms; enables idempotent repair after boot/update/time change (§14.4, §14.11, §15.2) |
| D5 | **[PROPOSAL]** Snapshots are append-only; `usage_windows` rows are owned by their snapshot (written atomically together); a separate `account_windows` identity table gives each logical window a stable `window_id` across snapshots. Manual corrections insert a new snapshot + correction event, never UPDATE | Honors invariants: append-only evidence, atomic snapshot+windows, null ≠ zero (nullable lossless decimal columns). Stable `window_id` is required by the deterministic reminder ID (§15.1–15.5) |
| D6 | **[PROPOSAL]** Platform request codes come from a persisted Room allocation table (`logical_id → request_code`), not a hash | Int-hashing `demeter:{...}` strings can collide; a persisted mapping is collision-free by construction (§15.1, §26.2) |
| D7 | **[PROPOSAL]** **Proto DataStore** for structured settings (reminder defaults, quiet hours, privacy mode, theme, remote-config cache); a minimal Preferences DataStore only for trivial flags (e.g. onboarding-done). Never credentials, keys, or history | Proto gives typed, migration-safe schemas for settings the PRD says must survive migrations (§15.3 leaves the choice open) |
| D8 | Background-work strategy: one-shot inexact `AlarmManager.setAndAllowWhileIdle()` for reminder triggers; all durable work (periodic refresh, reconciliation, pruning, upload) via unique WorkManager work. `ReminderAlarmReceiver` **re-evaluates eligibility from Room and posts the notification directly** (bounded work, no network/OCR); `BootAndTimeReceiver` only enqueues one unique reconciliation job | §14.4–14.6, §18.7–18.8. The alarm receiver must post directly — routing delivery through WorkManager would add unbounded latency the PRD's design forbids |
| D9 | **[PROPOSAL]** DI: Hilt (KSP), within the PRD's "compile-time DI" constraint. `Clock`, `ZoneIdProvider`, dispatchers, and all platform services (AlarmScheduler, NotificationPoster, PermissionReader) injected as interfaces | Injected clock/zone is a hard test requirement — §26.2 "never wall-clock" |
| D10 | **[PROPOSAL]** Navigation Compose, type-safe kotlinx-serialization routes, single activity; navigation arguments are IDs only (objects loaded from Room); predictive Back app-wide; App Links limited to email verification, account detail, settings recovery | §18.5; ID-only args keep saved state free of secrets and images |
| D11 | Adapter contract: `UsageProviderAdapter` (validate / fetchSnapshot / disconnect) is the **only** ingestion path. Seven adapters: Manual, PastedText, Screenshot, DemeterBridge, Mock, ApprovedOAuth (disabled stub behind a flag), **ClaudeEnterpriseAnalytics (interface + synthetic fixtures only; production gated on entitlement per §16.12)**. Adapters take no Android `Context` | §16.1–16.2, §7.1. The enterprise adapter's *interface and fixtures* are v1 scope even though production enablement is not |
| D12 | **[PROPOSAL]** Networking: OkHttp + Retrofit + kotlinx-serialization, with **two separately configured clients** — CloudClient (redirects allowed, pinned base URL) and BridgeClient (`followRedirects=false`, per-request origin pinning, bounded response bodies, private/public address-transition check). Cleartext disabled via `network_security_config` in release | §16.11 requires per-origin policies and DNS-rebinding defenses — impossible with one shared default client |
| D13 | Secrets: Android Keystore only for Demeter-generated keys (bridge device keypair, cloud token wrapping). **No schema field anywhere can hold a provider credential** — enforced by CI static checks (schema/field-name lint) and by the type system (no credential type exists) | §15.4, §21.3; making violations structurally impossible beats reviewing for them |
| D14 | **[PROPOSAL]** Cloud: small TypeScript service (Fastify + PostgreSQL + job queue), storing only identity, verified email destinations, device registrations, opt-in normalized snapshots, email rules/events. Email eligibility evaluated server-side with the **same policy semantics** as local reminders, from a shared spec + shared contract fixtures in `/packages/contracts` | §19, §20.3 (identical decision logic is mandated; TS-vs-Kotlin is open) |
| D15 | Local-first as a build fact: `:feature:*` and `:data:usage` have **no dependency** on `:data:cloud`; cloud/email/FCM sit behind a `CloudGateway` interface with a no-op local implementation; FCM disabled-by-default | "App works with cloud down" becomes a compile-time property (§19.8, §27.4) |
| D16 | **[PROPOSAL]** Testing tooling: Roborazzi for the screenshot matrix (window classes × font scales × themes); WorkManager TestDriver + injected clock for time-travel suites; Macrobenchmark + Baseline Profiles modules present from the skeleton | §26.7 and §26.9 are release gates; picking tooling now avoids churn |
| D17 | Build variants: debug / staging / release only in v1; the optional local-only/enterprise flavors deferred (dimension named but unused) | Extra flavors multiply the release-gate test matrix before earning their keep (§18.12) |

### 2.1 Component map

Text form (diagrammable later). Arrows read "depends on / calls."

**Domain core (pure JVM)**
1. **Domain Model** — `MonitoredAccount`, `Connection`, `UsageWindow`, `UsageSnapshot`, `ReminderRule`, `ScheduledReminder`, `ReminderEvent`, value types (Confidence, Freshness, ExhaustionState). No Android imports.
2. **ReminderPolicy** — pure functions: eligibility conjunction, freshness TTL, evidence-policy decision (current-data-only vs. use-last-known) plus the separate unknown-remaining reset-reminder opt-in, quiet-hours shifting, snooze validation, dedup-key derivation, delivery-quality classification. Inputs: rules + latest snapshot + injected `Clock`/`ZoneId` + capability state. Output: `ReminderDecision(Schedule|Cancel|Suppress|Blocked, reason)`.
3. **DashboardPolicy** — capacity-at-risk scoring, primary-window selection, card-state derivation over four orthogonal axes (usage / evidence / source / reminder-connection health) with a precedence contract per addendum Decision 2, urgency ordering. Pure.
4. **LayoutFitEngine** — layout-candidate generation per (account count × window class), hard-constraint checking (48 dp targets, 156 dp min card width, 22 sp min metric, 2-line nickname, never-hidden freshness/reset). Pure given measured inputs.

**Data layer**
5. **UsageRepository** — orchestrates adapters; atomic snapshot+windows persistence; per-account failure isolation; emits Flows of account/window state.
6. **Provider adapters** — the seven in D11.
7. **Room database** — tables per §15.2 plus the `account_windows` identity table (D5) and request-code allocation table (D6); FK cascades; migration tests from schema v1; 30-day pruning.
8. **SettingsStore** — Proto DataStore settings + remote-config cache (with the "remote values may only make reminder claims more conservative" merge rule enforced here).

**Reminder subsystem**
9. **ReminderReconciler** — the single reconciliation path: load rules+snapshots → run ReminderPolicy → persist decisions in one Room transaction → *then, post-commit* apply the diff to AlarmManager. Invoked by refresh completion, boot/package/time/zone events (via one unique WorkManager job), and app launch. Records the reconciliation reason.
10. **AndroidAlarmScheduler + ReminderAlarmReceiver + NotificationFactory** — inexact one-shot alarms; receiver re-evaluates full eligibility from Room before posting; three lock-screen privacy levels; five audit timestamps; snooze/dismiss action receiver; idempotent channel bootstrap that never overwrites user channel settings.

**Refresh & ingestion**
11. **RefreshCoordinator** — foreground refresh as app coroutines (never behind WorkManager), in-flight dedup per account, bounded parallelism; periodic WorkManager refresh for bridge/cloud sources only.
12. **ImportPipeline / OcrPipeline** — Photo Picker/Sharesheet intake → image validation (MIME/size/bounds-only decode) → private temp copy → ML Kit OCR → provider/screen classifier → **versioned declarative parse templates** → locale/zone-resolved dates → per-field confidence → editable review state; guaranteed cleanup on confirm/discard.

**Bridge**
13. **BridgeClient + PairingManager** — QR/manual pairing (5-min one-time token, challenge-response bound to a Keystore device key, fingerprint confirmation), origin classification (PUBLIC_HTTPS vs LOCAL_NETWORK), Android 17 LAN permission flow, signed-snapshot verification independent of TLS, health + revocation.

**Presentation**
14. **Feature UIs** — Today, Account detail, Add-account wizard, Import review, Bridge pairing/health, History + reminder audit, Reminder settings, Settings.
15. **DesignSystem** — M3 Expressive theme + Demeter tokens, dynamic-color mapping that preserves status semantics, growth-ring component, typography density tokens.

**Off-device**
16. **Cloud API** (`/services/cloud-api`) — identity exchange, email destinations + verification, snapshot upload (idempotency keys), reminder-rule sync, server-side email scheduler sharing the local eligibility spec, deletion (in-app + web route), kill switches. **Bridge reference implementation** (`/services/bridge`) — open-source, Docker, env-var credentials, official org-usage endpoints only, snapshot signing.

---

## 3. The five hardest technical design problems

### P1 — Reminder correctness under inexact alarms, Doze, reboot, and clock change

**Constraints:** no exact alarms ever; receivers do bounded work; recompute everything from persisted state after boot/update/time/zone change; idempotent across repeats; five audit timestamps and delivery-quality bands; never hold a DB transaction across framework calls.

**[PROPOSAL] Three-phase pipeline.**
- *Phase 1 (pure):* `ReminderPolicy.evaluate()` produces decisions from rules + latest snapshot + injected clock — exhaustively unit- and property-tested (DST, midnight-spanning quiet hours, moved resets) before any Android code.
- *Phase 2 (transactional):* ReminderReconciler diffs decisions against `scheduled_reminders` inside one Room transaction; logical-ID uniqueness (`demeter:{account_id}:{window_id}:{reset_epoch}:{lead_minutes}`) makes replays no-ops.
- *Phase 3 (platform, post-commit):* apply the diff to AlarmManager; failures mark rows `NEEDS_REPAIR`, fixed by the next reconciliation — platform state is always derived, never authoritative.

The alarm receiver re-runs eligibility against current Room state before posting, so scheduling staleness can only *suppress*, never wrongly fire. All entry points funnel into one unique WorkManager reconciliation job.

**Alternatives rejected:** exact alarms (prohibited); WorkManager as the trigger (not a timing mechanism; delays exceed even the 15-min "on time" band); hashed request codes (collision risk); alarms-as-source-of-truth (inverts the repairability requirement).

### P2 — Adaptive dashboard fit algorithm (measure-then-commit)

**Constraints:** layout from live window metrics only, never device model; hard rejects (48 dp targets, 156 dp min card width, 22 sp min primary metric, ≤2 nickname lines, never-clipped reset time, never-hidden freshness); Pixel-reference above-the-fold targets attempted first but the same code must pass a ~400 dp compact phone; at font scale 2.0, prefer a scrolling list over shrinking.

**[PROPOSAL — revised per addendum Decision 4] Two-pass candidate + verify engine.** The pure `LayoutFitEngine` generates an ordered candidate list per (account count, width class, height class) — candidate **ordering** stays a pure function. **Fit verification happens in Compose under real constraints** (real content, font scale, locale/RTL, insets, secondary-window shape) against safe bounds from `currentWindowAdaptiveInfo()` + insets; the first candidate violating nothing wins; the guaranteed terminal candidate is a single-column scrolling list, which cannot fail. **No manual memoization cache** — normal Compose invalidation and measurement are preferred, since any cache key omitting a layout-affecting input (content, locale, layout direction, display scale, insets, secondary-window shape) produces stale layouts. Pure candidate ordering is JVM-tested; text fit, font behavior, clipping, and semantics are tested in Compose UI/screenshot tests (the full fit matrix is not claimed to be JVM-verifiable). The measurement strategy (e.g., `SubcomposeLayout`) is a **prototype to be built and benchmarked before it becomes durable architecture** — Compose intrinsic calculations may be approximations.

**Alternatives rejected:** `LazyVerticalGrid(adaptive)`/`FlowRow` (can't express reject rules or preference ordering); per-device breakpoint tables (forbidden); runtime text auto-shrink (violates typography minimums).

### P3 — Screenshot OCR pipeline (truthful, leak-free, template-versioned)

**Constraints:** on-device ML Kit only; mandatory editable confirmation; per-field confidence and locale/zone assumptions displayed; parse failures visible, never force-matched; temp files/URI grants/buffers cleaned on confirm and discard; no image or recognized text ever leaves the device; telemetry limited to template ID + outcome category.

**[PROPOSAL] Staged pipeline where each stage owns its cleanup**, and parsing is **declarative versioned templates** (anchor-label + value-pattern rules per known ChatGPT/Claude screen), shipped as data so a kill flag can disable a broken template without an app release. Anything unmatched becomes an explicit "unparsed" region the user fills manually — the pipeline is *assistive autofill for a manual form*, which is the honest framing the PRD demands. Every saved field carries `parserVersion`, `templateId`, assumptions, and confidence. Fixture corpus in `/packages/fixtures` drives regression; the ≥95% extraction target applies to synthetic reference screens only.

**Confidence rule [RESOLVED — Revision 2, per addendum Decision 6 / Q13]:** user interaction does **not** map to confidence. Each saved field persists orthogonal properties: `extractionConfidence` (parser/OCR certainty per field), `confirmationState` (unreviewed / bulk-confirmed / individually confirmed / user-edited), `sourceAuthority` (manual / screenshot / signed bridge / approved connection), and `confirmedAt` + parser/template version. "Confirm and save" may confirm all visible fields as a group when the review scope is explicit, but it never upgrades extraction confidence; a user-edited value becomes **confirmed manual evidence**, not higher-confidence OCR.

### P4 — Bridge trust: pairing, signed snapshots, hostile-network defenses

**Constraints:** one-time 5-minute QR token; challenge-response bound to an app device key; human fingerprint confirmation; replay protection; TLS and payload signatures as *independent* controls; no cleartext, no trust-anyway button, no cross-origin redirects on pairing/snapshot endpoints; DNS-rebinding threat model; `ACCESS_LOCAL_NETWORK` only for explicit LAN origins, re-checked per attempt.

**[REVISED — Revision 2, per addendum Decision 8] for the unspecified crypto:** **public HTTPS is the supported default v1 bridge path; LAN is gated/beta.** **P-256 ECDSA is the mandatory baseline** signing algorithm for bridge identity and the app device key (Keystore-held — the documented Android Keystore pattern across API 29–37); **Ed25519 is optional**, enabled only after Keystore-backed availability is verified across the supported matrix. If multiple algorithms exist, **algorithm selection is bound into the signed pairing transcript and downgrade is prohibited.** LAN ships only behind a reviewed **per-bridge certificate pin delivered out-of-band in the QR pairing ceremony** (with rotation/recovery), or is deferred from public release — no generic trust bypass, no user-installed private CA. Snapshots signed over an RFC 8785 (JCS) canonical-JSON serialization; human-comparison fingerprint = SHA-256 of the bridge public key rendered as **16 grouped base32 characters (80 bits)** — long enough that forging a colliding key against a shoulder-surfed prefix is impractical, short enough to compare. Origin is classified once at pairing and pinned; any later resolution flip between public and private address space fails closed; origin/fingerprint change forces re-pairing. LAN flow: classify → in-context explainer → runtime permission request → on denial preserve the pairing draft and offer the public-HTTPS alternative.

**Alternatives rejected:** mTLS client certs (heavy for self-hosters; conflates the two independent controls); certificate pinning as trust root (PRD: optional, not a substitute for signatures — deferred entirely); mDNS/LAN discovery (prohibited).

### P5 — Snapshot/window identity and the "honest data" model

**Constraints:** null ≠ zero everywhere; multiple concurrent windows per account, never aggregated; unknown-limit and reset-only are first-class states; append-only corrections; UTC instants with local display; a moved reset time must invalidate reminders keyed to the old reset.

**[REVISED — Revision 2, per addendum Decision 7]** The D5 schema plus a **window identity resolver**: each ingested window matches a stable `account_windows` row by provider window ID when available; otherwise by a **versioned fingerprint** derived from (provider, account, windowType, unit, cadence/duration, provider/model scope, parser template) — **never display name alone**. Label aliases are stored separately from identity; **ambiguous matches prompt the user** ("update existing window or create new?"); resolver version and match reason are recorded in the audit trail; windows with different units, cadence, scope, or reset behavior are **never silently merged**. Unmatched windows create *new* identities rather than merging — the conservative failure mode, since silently merging unlike quotas is the product's cardinal sin. Reset epochs are part of the reminder logical ID, so a moved reset naturally mints new logical reminders and cancels orphans — invalidation falls out of the ID scheme. Display state is a sealed hierarchy (`Known(remaining, limit)` / `ResetOnly(resetAt)` / `UnknownLimit(used?)` / `Exhausted` / `Expired`) so "limit not exposed" vs "0%" is *unrepresentable* as a confusion. Values persist as lossless decimal columns.

---

## 4. UX design plan

### 4.1 Information architecture and navigation

Three top-level destinations (binding): **Today**, **History**, **Settings**. Navigation container adapts by *measured* window class: bottom `NavigationBar` at compact width (including Pixel 10 Pro XL portrait), `NavigationRail`/pane at larger classes; foldable postures keep critical actions out of the hinge area.

```
Root (single activity)
├─ onboarding ─ "Continue locally" (primary) ─► today (empty)
│      └─ "Sign in with Google" (optional secondary)
│      └─ sheet: how-connections-work (trust-tier explainer)
├─ today
│   ├─ addAccount/{step 1..7}   (Provider → Type → Method → Identity →
│   │     First snapshot | Bridge pairing → Reminder rules → Review)
│   ├─ account/{id} ─► reminderSettings, importReview, bridgeHealth
│   └─ pull-to-refresh; every card action also reachable non-gesturally
├─ history ─► reminderAudit/{eventId}
├─ settings ─► reminder defaults │ appearance │ privacy mode │
│              notification health │ email destinations │ bridge │
│              export │ delete-all │ cloud account/deletion │ diagnostics(gated)
├─ appLink: emailVerify │ account/{id} │ reminder-settings recovery
├─ shareTarget (image|text) ─► importReview (session-scoped)
└─ expanded/large widths: today renders list-detail two-pane
```

Screens (inventory, all specified in PRD §10): Onboarding, Empty Today, Today dashboard (1–6 adaptive cards), Account detail (fixed section order), 7-step Add Account wizard, Import review, Bridge pairing + health, History (charts never interpolate across gaps; every chart has a textual/TalkBack alternative), Reminder settings, Settings root, gated Device & viewport diagnostics.

Key first-run rule: **zero permission or consent prompts at onboarding.** The first system prompt a user ever sees is `POST_NOTIFICATIONS`, after they build a reminder and tap "Allow reminders."

**Reminder settings expose two orthogonal controls (Revision 2, per addendum Decision 1):** (1) **evidence policy** — *Current data only* vs. *Use last known data* (the PRD's Verified/Advisory pair); and (2) a separate **unknown-remaining opt-in** — "Remind me about the reset even when remaining usage is unknown" (with its disclosure copy). Reset-only is **not** a third confidence mode; it changes notification copy and eligibility only. Both controls are P0 policy inputs.

### 4.2 Design system

- **Tokens [PROPOSAL — three layers]:** (1) reference tokens (raw values, light/dark/high-contrast); (2) *semantic status* tokens — `status.healthy/useSoon/urgent/exhausted/stale/error/unknown` — pinned to the Demeter palette (Harvest gold, Sprout green, Soil, Linen, Attention amber, Urgent coral, Stale gray) and **never recolored by dynamic color**; (3) surface/brand tokens — the only layer Material You may recolor. Every status also carries icon + label + container-shape redundancy so color is confirmation, not the carrier (grayscale/color-correction survivable — this is an acceptance test, not a hope).
- **Typography:** PRD density tiers adopted verbatim (Hero 52–64 sp primary metric → Compact 24–28 sp; hard floor 22 sp at default scale; tabular numerals for countdowns; nickname ≤2 lines with full value in semantics). **[PROPOSAL]** one variable-weight display face for numerals falling back cleanly to Roboto Flex.
- **The fit algorithm shapes the components:** cards are designed as an ordered degradation — decorative ring detail sheds first; freshness label and reset time *never* shed. Scrolling beats shrinking. Card status is modeled as **four orthogonal axes** (Revision 2, per addendum Decision 2) — usage (healthy / use soon / urgent / exhausted / reset expected / unknown), evidence (current / aging / stale / error / syncing), source (manual / screenshot / bridge / approved connection — a stable label, never a status), and reminder/connection health (on / off / blocked; healthy / action required) — governed by a precedence contract and accessibility phrase order. Representative **cross-products** of these axes (e.g., exhausted + stale + manual + notifications blocked) × 4 density tiers form the screenshot-tested component matrix, replacing the earlier flat 12-state list.
- **Motion/haptics:** no continuous dashboard animation; refresh = subtle ring sweep + value crossfade; urgency reordering only after a refresh completes, with an explicit "reorder moment" animation so it reads as an event, not a glitch; reduced-animation setting swaps spatial transforms for fades; haptics only on pairing success, rule save, destructive confirm, manual refresh success. **[PROPOSAL]** single `DemeterMotion` token file so 60/120 Hz and animations-off tests target one surface.
- **Large-handset ergonomics (binding):** bottom navigation; Add-account reachable in the lower screen region; frequent card actions in the card body, not top-right overflow; destructive confirmations ≥48 dp and reachable one-handed.

### 4.3 The five riskiest UX decisions

**U1 — Glanceable truth vs. stale data.** Tier 1 data can't self-refresh, so most consumer cards will usually be somewhat stale; if freshness is fine print, users read a 42% ring as live truth and churn the first time it's wrong. **[PROPOSAL]** Freshness is a first-class chip beside the metric (Live / Updated recently / Stale / Manual), fit-protected; *confidence dims certainty, not visibility* — stale cards keep full-size numerals but switch the ring to an outlined treatment and prepend "as of \<time\>" to the semantic value; unknown limits use a time-only orbit, never a fabricated percentage; stale cards carry an in-body one-tap refresh affordance.

**U2 — Permission-ask choreography.** Contextual asks risk a dead-end feeling when denied. **[PROPOSAL]** Two-beat ask (in-context rationale sheet → system prompt), with the rationale beat doubling as the *inexact delivery* disclosure. The denied state is designed, not apologized for: "Saved — notifications off" + persistent "Fix in Settings" deep link + the "Notifications disabled" card state — exploiting the PRD's deliberate separation of rule eligibility from notification capability. LAN permission is a labeled step inside the pairing stepper, never bundled.

**U3 — Reminder trust: selling "inexact but honest."** Users' mental model of reminders is an alarm clock; the PRD forbids that promise. **[PROPOSAL]** Vocabulary discipline ("reminder," never "alarm"; lead times framed as planning windows); advisory notifications always carry "Based on your last update at \<time\>"; verified-only mode explained at selection with its cost stated plainly ("If your data is too old, Demeter stays silent instead of guessing"); **consumer accounts default to Advisory, bridge accounts to Verified-only [PROPOSAL]**; trust is auditable via the per-account delivery-health panel and plain-language audit reasons (delivered on time / delayed by Android / suppressed: data too old / blocked: notifications off); test notification for proof-of-life; battery troubleshooting appears only after evidence of missed reminders.

**U4 — Dynamic color vs. status integrity.** Dynamic color defaults ON (binding) but urgent/exhausted/stale must stay identifiable under any wallpaper. **[PROPOSAL]** the token split above (pin status hues; let Material You own surfaces), plus structural icon/label/shape redundancy, plus a screenshot matrix including adversarial seed colors and grayscale simulation.

**U5 — Screenshot-review rigor vs. abandonment.** The confirmation screen is non-negotiable, but it's also the funnel's most-repeated chore (≥80% add-completion target). **[PROPOSAL]** Two-density review: high-confidence parses render as a compact tappable checklist with one "Confirm and save"; low-confidence fields auto-expand inline with the source text shown; assumptions (date/locale/zone) as one editable summary line; repeat imports pre-map to existing windows so review becomes "confirm deltas"; template mismatch says "Couldn't read this screen" with manual-entry and reset-only paths (never a forced guess); a one-line footer — "Image deleted after saving" — lands the privacy promise at its most credible moment. Confirmation state is tracked separately from extraction confidence (see the revised P3 rule / addendum Decision 6).

### 4.4 Accessibility (structural, launch-gating)

- Each account card: the **card container is one semantic node** with ordered phrase (nickname, provider, remaining, reset, freshness, status) and a single "open details" action; **refresh is a separate 48 dp semantic child** placed after the card summary (Revision 2, per addendum Decision 3 — no merged node may silently remove an action from TalkBack or Switch Access); the growth ring is decoration with one textual equivalent.
- Every card-state axis value (usage, evidence, source, reminder/connection health) carries non-color cues; validated under grayscale and color correction across representative axis cross-products.
- Font scale 2.0 and largest display size are *designed* configurations (single-column list), not degradations; screenshot matrix covers 1.0/1.3/1.5/2.0.
- TalkBack traversal order matches visual priority; Switch Access and keyboard reach every actionable element; no critical function exclusively behind swipe/long-press/edge gestures; targets ≥48 dp.
- Charts ship with textual summaries and navigable data views; gaps announced as "no data."
- BiometricPrompt always has a device-credential fallback and **never gates deletion or recovery**.
- Per-slice accessibility acceptance in the definition of done: automated Scanner + Compose semantics assertions from the first slice; manual TalkBack/Switch Access passes on the physical-device checklist.

---

## 5. Phased implementation plan (tracer-bullet vertical slices)

Nine phases refining the PRD's own Slices 1–7 (§29.5) and Milestones A–F (§27). Each phase cuts through domain, data, UI, platform, and tests, and is independently demoable. Reminders (Phases 4–5) deliberately precede OCR (Phase 6): they're the riskiest subsystem and need only manual data to exercise end-to-end.

**Cross-cutting rules active from Phase 1 (definition of done, every phase):**
- All user-facing strings externalized; locale-aware date/time/percentage formatting; RTL- and text-expansion-safe layouts; provider names isolated from translatable text.
- Decision log updated for any deviation from the PRD; the subsystem's doc (SECURITY.md, PRIVACY.md, REMINDER_SEMANTICS.md, PERMISSIONS.md, …) updated in the same phase its subsystem lands — not in a Phase 9 catch-all.
- CI gates from day one: manifest permission audit (fails on any unapproved permission), secret scan, exported-component audit, schema credential-field lint.
- Error, empty, loading, offline, stale, permission-blocked, and process-recreation states exist for every shipped surface; accessibility assertions per slice.

### Phase 1 — Walking skeleton: repo, CI, design system, empty Today
*PRD: §29.1–29.3, §18.1–18.2, §12 tokens, §10.1–10.2. Milestone A (start).*

Monorepo + Gradle module skeleton (pure-JVM `:core:model`); single-activity Compose app, edge-to-edge, predictive Back; design-system module with brand tokens, density typography, adaptive icon (monochrome layer, mask-validated); onboarding (fixed copy, zero prompts) → empty Today with "Add account" + "Try with sample data"; CI running all §29.3 commands + the audit gates above; **emulator lanes stood up now**: API 29, API 33 (notification path), API 35 16 KB, API 37 compact ~400 dp, API 37 tablet/foldable; **basic gated viewport-diagnostics capture** (measured window bounds/density/classes as copyable JSON — the PRD wants the Pixel viewport record established immediately).

- [ ] Clean-checkout CI green on every §29.3 command
- [ ] Zero-account Today shows a single primary action; no permission prompt anywhere at first launch
- [ ] Manifest contains only `INTERNET`; no exact-alarm permissions (CI-enforced)
- [ ] Adaptive icon valid under all masks; edge-to-edge correct in gesture + 3-button nav
- [ ] i18n baseline: no hardcoded strings; pseudo-locale + RTL smoke passes

### Phase 2 — Domain model + adapter contract + adaptive 0–6 dashboard (mock data)
*PRD: §29.5 Slice 1, §6, §11, §10.4–10.5, §15.1 shapes. Milestone A (complete).*

Pure domain types (nullable lossless decimals; null ≠ zero); `UsageProviderAdapter` contract + `MockUsageAdapter` over versioned fixtures covering representative cross-products of the four card-state axes (usage / evidence / source / reminder-connection health, per addendum Decision 2), unknown-limit, reset-only, exhausted, long/RTL nicknames; **`ApprovedOAuthAdapter` disabled stub behind a feature flag + its disabled-by-default contract test; `ClaudeEnterpriseAnalytics` adapter interface + synthetic fixtures (production-disabled per the §16.12 gate)**; capacity-at-risk scoring + primary-window selection as pure functions; `LayoutFitEngine` with hard-reject constraints; Today dashboard + account detail on mock data; JVM tests for pure candidate ordering across the candidate matrix (fit verification lives in Compose UI/screenshot tests per addendum Decision 4); Roborazzi screenshots across window classes × font scales 1.0/1.3/1.5/2.0 × light/dark × 0–6 accounts.

- [ ] Above-the-fold reference targets attempted and met for 2–4 accounts on Pixel-class portrait; same code passes the ~400 dp compact lane
- [ ] Font scale 2.0 → scrolling list, nothing clipped; layout keyed to measured window metrics only (lint bans `Build.MODEL`/resolution reads in feature code)
- [ ] "Limit not exposed" vs 0% unrepresentable as a confusion; no aggregate percentage across unlike windows anywhere
- [ ] TalkBack: one coherent card-summary phrase per card container (open-details action) with refresh as a separate 48 dp semantic child; ring is one textual node
- [ ] Milestone A gate: 0–6 visual/semantics review on Pixel 10 Pro XL + adaptive emulators; no non-mock integration exists

### Phase 3 — Local persistence, account management, manual + paste ingestion, settings root, privacy mode
*PRD: §29.5 Slice 2, §15.2–15.5, §13.1, §10.3, §10.10, §13.10. Milestones A→B (start).*

Room schema v1 **including the `account_windows` identity table and the request-code allocation table** (D5/D6), FK cascades, migration harness, snapshot+windows atomicity, 30-day pruning; Proto DataStore settings (D7); repository layer with Manual + PastedText adapters; 7-step Add Account wizard (per-provider caps, duplicate-nickname handling, process-recreation-safe); manual values auto-expire at reset; History MVP (30-day, gap-honest, TalkBack data view); export (JSON/CSV, no secrets/screenshots) + delete-all-local; backup rules excluding secrets/transients; **Settings root assembly** (global reminder pause without deleting rules, appearance/dynamic-color toggle, battery/background explanation *without* coercing exemptions, compatibility disclaimer); **privacy mode**: [PROPOSAL] recents-safe redacted state as default with FLAG_SECURE as an opt-in (FLAG_SECURE blocks the user's own screenshots — ironic for a screenshot-driven app; see Q7), optional BiometricPrompt gating with device-credential fallback, and the no-lockout guarantee (biometric failure never blocks deletion/recovery).

- [ ] Fourth same-provider account blocked with explanation; other provider still addable
- [ ] Wizard survives predictive Back + process recreation without duplicate saves
- [ ] Migration, atomicity, FK/cascade, interrupted-write, pruning tests pass
- [ ] Export/delete-all works; backup extraction rules verified
- [ ] Privacy mode: app-switcher content hidden; biometric unlock + fallback; deletion never gated
- [ ] App is now genuinely useful in Tier 1 local mode

### Phase 4 — Reminder policy engine (pure) + time-travel suite
*PRD: §14 (policy half), §29.5 Slice 3a. Milestone C (start).*

`ReminderPolicy` as a pure module: eligibility conjunction (notification capability deliberately outside it); freshness TTL `min(6h, max(30m, 25% × window))` **(provisional — see Q8)**; the two-control model per addendum Decision 1 — evidence policy (Current data only / Use last known data) plus the separate unknown-remaining reset-reminder opt-in (behind explicit opt-in + disclosure; not a third confidence mode); quiet-hours shifting with the ≥15-min-before-reset floor; snooze (max two, rejected at/after reset); rollover semantics (never assume a new window is full); dedup on the deterministic logical ID. Reminder-rule UI with **both controls (evidence policy + unknown-remaining opt-in)**, lead times {48,24,12,8,4,2,1}h, thresholds, quiet hours, lock-screen privacy level; "next logical trigger" visible even though nothing fires yet. Fixture-driven time-travel debug control. Exhaustive fake-clock unit + property tests (DST both directions, midnight quiet hours, moved resets, request-code allocation).

- [ ] Three lead times → exactly three logical schedule records; repeat reconciliation is a no-op
- [ ] Exhaustion cancels; moved reset mints new IDs and orphans old; passed triggers never deliver
- [ ] Verified-only suppresses stale with an audit reason; advisory carries "as of"
- [ ] Quiet-hour shift never lands at/after reset; snooze unavailable when +1 h ≥ reset
- [ ] Every decision persisted with a user-inspectable reason

### Phase 5 — Android scheduling: alarms, notifications, permission flow, reconciliation
*PRD: §29.5 Slice 3b, §14.4–14.13, §18.7–18.8, §10.9 status panel. Milestone C (complete). Play-internal track opens after this phase.*

The D8/P1 pipeline in full: inexact `setAndAllowWhileIdle` scheduling post-commit; `ReminderAlarmReceiver` re-evaluating everything from Room and posting directly (bounded, no network/OCR); notification channels ("Usage reminders", "Service and account alerts"; "Reset expected" off by default) with idempotent bootstrap that never overwrites user channel edits; three lock-screen privacy levels with generic default; contextual `POST_NOTIFICATIONS` two-beat flow; denial → visibly blocked rule + "Fix in Settings"; test notification; `BootAndTimeReceiver` → one unique reconciliation job (boot/package/time/zone reasons recorded); reminder health panel + audit trail UI; **analytics/observability starts landing here [fix from critique]:** the reminder-pipeline subset of the 19 required §23.8 events, random Demeter-generated identifiers only, and the redaction test that proves the do-not-log list (no provider identifiers, no usage values, no emails) holds.

- [ ] No first-launch prompt; in-context request; denial keeps everything else working
- [ ] No exact-alarm permission (CI); no exact-delivery claim in any copy
- [ ] Receiver bounded-work audit; five audit timestamps captured per event
- [ ] Reboot/update/time/zone repair via the single reconciliation path; force-stop → honest "unknown health" state, repaired on next launch
- [ ] Doze/App Standby/Battery Saver test suite: late is recorded as late; nothing corrupts state; duplicate rate < 0.1% as a **measured** target (addendum Decision 18) — logical-ID uniqueness plus an explicit delivery state machine (`desired_state` / `applied_state` / `attempt_count` / `last_error` / `applied_at`) and notification tag/ID reuse so retries update rather than post a second notification; posting and audit-write are not one atomic transaction, so the rate is instrumented, never assumed
- [ ] Milestone C gate: time-travel, permission, reboot, Doze, duplicate, force-stop suites + Pixel observed-timing evidence attached

### Phase 6 — Screenshot & Sharesheet import with on-device OCR
*PRD: §29.5 Slice 4, §16.3–16.5, §10.6. Milestone B (complete). Closed alpha opens after this phase — see entry gate below.*

Photo Picker + Sharesheet (image/text) intake behind the adapter boundary; the staged pipeline of P3 (validation → bounded temp copy → ML Kit → classifier → versioned templates → locale/zone resolution → per-field confidence → two-density review screen → cleanup); parser telemetry limited to template ID/outcome/correction-count; **local per-template kill flag now, remote kill switch in Phase 7**; remaining §23.8 analytics events (import funnel, activation) + redaction tests, completing the instrumentation the closed-alpha exit evidence depends on.

**Closed-alpha entry gate (moved forward from release hardening — critique fix):** privacy policy published, support channel live, first-pass Data safety mapping drafted. These are §27.1 entry criteria, not Phase 9 polish.

- [ ] Nothing saved until confirmed; assumptions displayed; corrections append, never rewrite
- [ ] Fields persist orthogonal `extractionConfidence` / `confirmationState` / `sourceAuthority` / `confirmedAt` + parser version; interaction never upgrades extraction confidence; user edits become confirmed manual evidence (addendum Decision 6)
- [ ] No broad media permission (CI-proven); malformed/oversized shares fail safely; temp files + URI grants cleaned on both exits
- [ ] ≥95% extraction on synthetic reference screens after confirmation; review available P95 ≤ 3 s
- [ ] Milestone B gate: no image or recognized text leaves the device; import threat-model tests (image bomb, path traversal, malformed bitmap, OOM) pass

### Phase 7 — Optional cloud: identity, verified email, deletion, remote config
*PRD: §29.5 Slice 5, §19, §20, §8.6/8.8. Milestone D.*

Cloud API service (D14) with strict tenant scoping; Credential Manager Sign in with Google → token exchange → device registration; **snapshot upload (`POST /v1/accounts/{id}/snapshots`, idempotency keys) and reminder-rule sync — prerequisites for server-side email eligibility [critique fix]**; email destinations (verify by link or code; global or per-account), rate-limited labeled test email; server-side scheduler reusing the shared eligibility spec + contract fixtures; bounce/complaint/suppression handling; App Links (verification, deep-open) with HTTPS fallback; in-app + **public web** account deletion with immediate token revocation; FCM plumbing behind a disabled-by-default flag; cloud-down degradation (local features fully independent — compile-time enforced per D15); **remote-config/kill-switch infrastructure [critique fix]:** signed + versioned config cache in SettingsStore, conservativeness-only merge for freshness TTLs, remote gates for parser templates, adapters, cloud sync, FCM, LAN bridge, email — the §27.4 rollback strategy depends on these existing; **crash/ANR reporting decision + PII/secret review before enabling production collection [critique fix]**. [PROPOSAL] email-based cloud identity (`/v1/auth/email/*`) deferred out of v1 — Google-only at launch (see Q12).

- [ ] No email to an unverified destination (server-enforced); emails carry observation time, reset time, source, unsubscribe
- [ ] Credential Manager only (no legacy sign-in API present); cloud schemas contain no credential-capable columns (CI lint)
- [ ] Deletion works in-app and via the web route; jobs cancelled, tokens revoked
- [ ] Tenant-isolation + object-scope authorization tests; email job idempotency
- [ ] Kill-switch contract test: every remotely gated feature degrades to local mode cleanly
- [ ] Milestone D gate: privacy, identity, email-abuse, App Link, Data safety, deletion tests pass; local mode demonstrably independent

### Phase 8 — User-owned bridge + organization adapters
*PRD: §29.5 Slice 6, §16.6–16.11, §17. Milestone E. Closed beta opens after this phase.*

Open-source bridge reference implementation (Docker; env/secret-manager credentials; official org-usage endpoints only; snapshot signing; contracts in `/packages/contracts`); OpenAI + Anthropic organization adapters (token/request/cost/project-or-workspace/model/budget windows; user-defined budgets badged "User-defined"; **org usage never labeled as consumer allowance**); Android pairing per P4 as revised (QR via system scanner, manual URL+token+fingerprint fallback — camera never mandatory; challenge-response; 16-char fingerprint confirmation; **public HTTPS as the supported default; P-256 ECDSA baseline signing, algorithm bound into the pairing transcript, downgrade prohibited**); **LAN gated/beta** behind reviewed per-bridge pinning delivered in the QR pairing ceremony — origin classification + Android 17 LAN permission path (re-verify final `ACCESS_LOCAL_NETWORK` semantics at this phase's start — the PRD flags its own assumption); hardened BridgeClient (no redirects, strict URL parsing, rebinding defense, bounded bodies, no trust-anyway); bridge health + revocation UI; deployment + security + rotation docs.

- [ ] Public HTTPS pairing needs no LAN permission; LAN path asks only in context; denial preserves the draft and offers alternatives
- [ ] Invalid signature rejects the whole payload, logged as a security event, never rendered as usage
- [ ] Expired/replayed/malformed tokens fail closed; origin/fingerprint change forces re-pairing; cleartext rejected in production
- [ ] Static analysis: no credential-capable field anywhere in the app (the Milestone E headline gate)
- [ ] Per-adapter §16.12 evidence package (documented API, terms review) complete before production enablement

### Phase 9 — Release hardening & Play readiness
*PRD: §29.5 Slice 7, §18.13–18.16, §22, §23, §26.14–26.17, §27. Milestone F. Production staged rollout after this phase.*

Baseline Profiles + Macrobenchmarks (startup, dashboard, refresh scenarios; Smooth Display on *and* off); R8 hardening with regression tests; 16 KB page-size validation of the **signed release AAB** exercising ML Kit + crypto natives; accessibility closure (Scanner zero blocking findings + manual TalkBack/Switch Access passes); battery qualification with **energy-normalized evidence** (wakeups, CPU time, worker/alarm duration, network bytes — not percentage alone, because a 5,200 mAh battery hides inefficiency); finalized Data safety mapping from actual behavior; store listing in approved framing; Play review notes; polished diagnostics screen; remaining runbooks + SBOM; 22-step physical Pixel 10 Pro XL release checklist including the compact-phone counter-test; staged rollout 1→5→20→50→100% with evidence-based holds and the §27.3 stop-ship triggers wired into release process docs.

- [ ] All §23.1 performance targets on a release build; API 29 smoke, API 33 notification, API 37 behavior lanes green
- [ ] Signed AAB passes 16 KB validation; pre-launch report clean
- [ ] Zero P0/P1 across security, privacy, data truthfulness, reminder dedup, deletion, accessibility
- [ ] Physical release-candidate evidence attached; signed RC AAB produced

### Phase → milestone → track mapping

| Phase | PRD slice | Milestone | Demoable as | Track gate after |
|---|---|---|---|---|
| 1 Skeleton | 1a | A | Installable onboarding → empty Today; CI green | — |
| 2 Domain + dashboard | 1 | A ✓ | 0–6 fixture accounts, all card states, adaptive | — |
| 3 Persistence + privacy | 2 | B start | Real local accounts, history, export, privacy mode | — |
| 4 Reminder policy | 3a | C start | Time-travel demo of decisions + audit | — |
| 5 Android scheduling | 3 | C ✓ | Real notifications, permission flow, reboot repair | **Play internal** |
| 6 OCR import | 4 | B ✓ | Screenshot → review → confirmed snapshot | **Closed alpha** (privacy policy + support live) |
| 7 Cloud + email + remote config | 5 | D ✓ | Sign-in, verified email reminder, deletion, kill switches | — |
| 8 Bridge | 6 | E ✓ | Paired bridge, signed org snapshots | **Closed beta** |
| 9 Hardening | 7 | F ✓ | Signed RC AAB + evidence package | **Production staged** |

### Deliberately excluded from v1

- **Hard exclusions (release-gating if violated):** exact alarms; persistent foreground service; provider passwords/cookies/admin keys anywhere; hidden WebView/accessibility-service/VPN scraping; private endpoints; automatic consumer scraping; broad media permission; background clipboard monitoring; LAN discovery; camera as a mandatory path; provider logos; Wear/TV/Auto/ChromeOS; iOS.
- **P1 (after v1):** home-screen widgets; FCM-driven remote reconciliation (plumbing exists, disabled); 90-day/annual history; cross-device sync; approved provider OAuth adapters; **Claude Enterprise production enablement** (interface + fixtures ship in v1, Phase 2); budget forecasting; parser-template update mechanism beyond kill flags; two-pane tablet analytics; QS tile; Wear companion; managed config.
- **P2:** iOS from the platform-neutral layers; team dashboards; more providers via the same contract; desktop/web; anomaly detection.

---

## 6. Test strategy summary

The PRD's pyramid, condensed to what shapes the plan: pure-domain unit + property tests (policy, fit engine, parsers — fake clock, explicit zones, DST/rollover fuzzing); Room migration/atomicity tests from schema v1; contract tests against `/packages/contracts` fixtures for bridge + cloud (including the kill-switch and disabled-adapter tests); Compose semantics + screenshot matrix (window classes × font scales × themes × dynamic-color seeds × grayscale); six-layer reminder assertions (policy decision → Room record → platform registration → receiver re-evaluation → notification content → audit timing) under time-travel, reboot, Doze, and force-stop; import threat-model suite; 16 KB, Macrobenchmark, and energy-normalized battery lanes; the ~400 dp compact-phone emulator as a co-equal gate to the physical Pixel 10 Pro XL checklist.

---

## 7. Risk register (top 10, ranked)

| # | Risk | Sev / Likelihood | Mitigation | Retired by |
|---|---|---|---|---|
| R1 | **No consumer usage API exists** — the core loop depends on manual evidence; if import friction is too high, the north-star metric collapses | Critical / Certain (it's the state of the world) | First-class low-friction import; disabled OAuth stub ready; measure import frequency in alpha; pursue provider authorization in parallel | Accepted boundary; *viability* form answered at closed-alpha exit |
| R2 | **Best-effort delivery vs. alarm-clock expectations** | High / High | No exact claims anywhere; persisted logical intent + launch repair; observed-timing audit; visible health surface; honest force-stop state | Mechanism: Phase 5 gate; human acceptance: closed alpha |
| R3 | **Stale advisory reminders / verified-mode silence for Tier 1** — wrong notifications or no notifications both erode trust | High / High (structural) | Mandatory "as of" wording; visible suppression reasons; per-source-type defaults (Advisory for manual, Verified for bridge) | Policy: Phase 4–5; UX legibility: needs design review now (Q4) + alpha guardrail |
| R4 | **Reminder-engine correctness** (duplicates, reboot, DST, moved resets) — duplicate reminders are a stop-ship class | High / Medium | Pure policy before platform code; logical-ID uniqueness as dedup authority; one reconciliation path; six-layer test matrix | Phase 5 gate |
| R5 | **Credential/prohibited-integration compliance drift** (a "helpful" contributor adds cookie import) | Critical / Low-Med, quiet + cumulative | No credential-capable fields (CI lint); secret scans; §16.12 per-adapter evidence gate + kill switch; guardrails installed Phase 1, before the temptation | Continuous; hard gates Phases 8–9 |
| R6 | **OCR template fragility** — providers change screens at will; silent misparse worse than failure | Med-High / High over 6 months | Versioned fail-visible templates; mandatory confirmation; kill flags (local Phase 6, remote Phase 7); correction-count telemetry as early warning | Pipeline: Phase 6; residual risk permanent (P1 update mechanism is a known launch gap) |
| R7 | **Play compliance stack** (16 KB, web deletion route, Data safety, permission audit) — some items need external infra before external testing | High / Medium | 16 KB on signed artifact in CI; evidence package; **privacy policy + support + web deletion scheduled with Phases 6–7, not Phase 9** | Phase 9, with alpha-entry prerequisites at Phase 6 |
| R8 | **Reference-device masking** — the Pro XL (6.8", 120 Hz, 16 GB, 5,200 mAh) is a best case on every sensitive axis | High / Medium | ~400 dp compact emulator as co-equal gate; Smooth Display on/off; energy-normalized battery; window-metrics-only layout (lint-enforced) | Matrix from Phase 1; closure Phase 9 |
| R9 | **Bridge reachability + Android 17 LAN-permission assumption** (NAT/VPN/sleep; the PRD flags its own API assumption for revalidation) | Medium / High (friction), Medium (assumption) | Public HTTPS preferred; fail-closed with recovery; draft preserved on denial; re-verify `ACCESS_LOCAL_NETWORK` semantics at Phase 8 start | Phase 8 gate; real-world success only at beta |
| R10 | **Notification fatigue → channel disablement death spiral** | Med-High / Medium | Conservative defaults; strict dedup + group key; one-tap global pause preserving rules; disable-rate guardrail metric with authority to tune before production | Mechanism Phase 5; behavior at alpha/beta |

**External blockers (design-around, not delays):** no consumer API/OAuth (blocks Tier 3 only); provider trademark permission (v1 uses text labels); Claude Enterprise entitlement (interface ships, enablement gated); provider screens change at will (fail-visible design); Android power management (the advisory/verified architecture exists *because* of this); Android 17 LAN semantics + home-bridge reachability; no self-signed-cert bypass (see Q10); email/SiwG/App-Links/deletion-route infrastructure needed before external testing; single-device validation (emulator matrix compensates).

---

## 8. What changed after internal adversarial review

This plan was cross-checked by independent completeness and consistency reviews against the full PRD before being sent out. Material fixes applied: privacy mode + BiometricPrompt assigned to Phase 3 (was unowned P0 work); Claude Enterprise adapter interface + fixtures restored to v1 (Phase 2); ApprovedOAuth disabled stub + contract test added to Phase 2; analytics/observability + redaction tests scheduled (Phases 5–6) ahead of the alpha evidence that depends on them; remote-config/kill-switch infrastructure made a Phase 7 deliverable (the rollback strategy requires it); privacy policy/support/web-deletion pulled forward to the closed-alpha entry gate; cloud snapshot upload + rule sync added to Phase 7 (server-side email eligibility needs them); i18n and viewport-diagnostics pulled into Phases 1–2; Proto-vs-Preferences DataStore contradiction resolved (Proto); alarm-receiver responsibility corrected (posts directly; only boot/time receivers enqueue); pairing fingerprint lengthened from 40 to 80 bits; bulk-confirm confidence rule made explicit (caps at medium) — *superseded in Revision 2 by the orthogonal extractionConfidence/confirmationState/sourceAuthority model (addendum Decision 6)*.

---

## 9. Questions for the design reviewer

Scoped to genuinely open tensions where your answer changes the plan. Binding PRD decisions (§1.1) are excluded.

**Architecture**
1. **Snapshot-history vs. current-state modeling.** We propose append-only snapshots owning their window rows, plus a materialized latest-per-window projection for the dashboard read path (D5). Does this shape serve the audit/correction requirements without making dashboard reads expensive, or would you model a mutable current-window table + separate snapshot log?
2. **Persistence↔platform boundary for reminders.** We propose: persist decisions → commit → apply diff to AlarmManager → mark registered, with reconciliation as the repair loop (platform state as pure derived cache). Do you endorse this over a stricter outbox pattern? What failure windows between "committed" and "registered" worry you?
3. **Request-code derivation.** Persisted monotonic allocation table (collision-free by construction) vs. hash-with-collision-test: is the extra table justified given reconciliation already repairs state?

**UX**
4. **Making Tier 1 degradation legible — the product's central UX tension.** For manual accounts, Verified-only will almost never fire and Advisory may reflect days-old data. Should confidence-mode defaults differ by source type (our proposal: Advisory for manual, Verified for bridge)? How aggressive should "this may be stale — refresh to verify" be before honesty becomes noise? Is there a better third pattern (e.g., a reminder that *asks* the user to check rather than asserts a value)?
5. **Card design for unknown-limit windows.** What should the primary visual slot show for reset-only accounts so they don't read as broken next to percentage cards? (Our proposal: reset countdown as the primary metric, "Limit not exposed" as the secondary line.)
6. **Layout tie-breaks.** For 3 accounts: featured-hero+two vs. balanced list; for 6 compact: dense grid vs. list. We propose information-completeness first, then density. Where would you draw the tie-break — and should users get a manual density preference, or is that a settings smell?
7. **Privacy-mode recents protection.** FLAG_SECURE also blocks the user's *own* screenshots — ironic for a screenshot-driven import flow. We propose a recents-safe redacted state as default, FLAG_SECURE as opt-in. Agree, and should it be per-surface?
8. *(joined with Q13 below)* **One-tap review vs. per-field confirmation** — see Q13.

**Scheduling / reliability**
9. **Freshness TTL shape.** `min(6h, max(30m, 25% × window))` yields 75 min for a 5-hour session but caps weekly windows at 6 h — so a weekly-window *verified* reminder demands a snapshot < 6 h old, which manual users will essentially never satisfy. Is the cap right, or should TTL scale further for long windows at the cost of staler "verified" claims?
10. **Delivery-quality semantics.** The 15-min on-time band, 15-min quiet-hours floor, and two-snooze cap are provisional. Do these match user expectations for a planning-grade (not alarm-grade) reminder, and what alpha evidence would you want before tuning?

**Security / compliance**
11. **LAN bridge certificate story.** Production forbids trust bypasses and self-signed certs, but a home-LAN bridge rarely has a public certificate. Which reviewed path: per-bridge private CA provisioned at the pairing ceremony; require a public-cert reverse proxy; or de-emphasize LAN in v1 and lean on public HTTPS? This decides real Phase 8 scope.
12. **Optional hardening scope.** Room field-level encryption and cloud step-up auth are permitted-but-undecided. Given app-private storage + Keystore, does either clear the cost/benefit bar for v1, considering key-invalidation/recovery complexity?

**Scope / product**
13. **Bulk-confirm confidence rule.** We propose: one-tap "Confirm and save" caps untouched fields at *medium* confidence; only individually opened/affirmed fields can be *high*. Does this satisfy the spirit of "high confidence requires user confirmation of every field," or should high confidence require per-field-group affirmative interaction (more friction on the funnel's most repeated chore)?
14. **Cloud identity breadth.** Ship Google-only identity in v1 (simpler) — or does excluding Google-averse users from email reminders undermine a privacy-forward product's positioning enough to justify carrying email-based identity from the start?
15. **Anything load-bearing we missed?** Given the summary above, what would you change about the phase ordering, the module boundaries, or the risk ranking?

---

*End of plan. Feedback on any [PROPOSAL], the five hard problems (§3), the risky UX decisions (§4.3), and Q1–Q15 is what we need before implementation starts.*
