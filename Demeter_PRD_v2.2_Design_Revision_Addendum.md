# Demeter PRD v2.2 — Design Revision Addendum

> **Amends:** `Demeter_PRD_Android_Pixel_10_Pro_XL_v2.1.md` (PRD v2.1)
> **Basis:** External design review `demeter-android-v1-design-review-by-ChatGPT.md`, dated 2026-07-12 (verdict: *conditionally approve after P0 changes*)
> **Status:** ACCEPTED. All P0 and P1 items in the review have been accepted; one item is adapted (not rejected) for the hackathon context — see §6.
> **Precedence:** This addendum is the canonical record of the design revision. Where it conflicts with PRD v2.1 or with the implementation plan (`plans/demeter-android-v1-design-and-implementation-plan.md`), **this addendum takes precedence.** The 4,192-line PRD is deliberately not edited in place; the plan receives only surgical corrections pointing here.

---

## 1. Accepted P0 decisions

### Decision 1 (P0.1) — Two-control reminder model; reset-only is not a confidence mode

Reminder configuration uses **two orthogonal controls**, replacing any "three confidence modes" framing:

1. **Evidence policy:** *Current data only* (do not remind when the snapshot exceeds its freshness TTL) vs. *Use last known data* (remind with explicit "based on your last update at [time]" wording). These correspond to PRD v2.1's Verified/Advisory pair (PRD lines 647–650).
2. **Unknown-remaining behavior:** a separate opt-in — *"Remind me about the reset even when remaining usage is unknown."* This changes notification copy and eligibility only (PRD lines 1302–1308).

Reset-only is **not** a third peer confidence segment. *Rationale:* how old evidence may be and whether to remind when remaining capacity is unknown are two different user decisions; conflating them (as the plan draft did) produces settings users cannot predict.

### Decision 2 (P0.2) — Four orthogonal card-state axes with a precedence contract

The flat 12-state card list (PRD §11.6, lines 770–787) is replaced as a *model* by four concurrent axes:

| Axis | Values | Visual role |
|---|---|---|
| **Usage** | healthy, use soon, urgent, exhausted, reset expected, unknown | Primary metric and main status sentence |
| **Evidence** | current, aging, stale, error, syncing | Freshness line and container treatment |
| **Source** | manual, screenshot, bridge, approved connection | Stable source label — never a status |
| **Reminder / connection health** | reminders on / off / blocked; connection healthy / action required | Secondary action/status row |

A **precedence contract** (state-combination matrix plus accessibility phrase order) governs what leads when axes co-occur (e.g., exhausted + stale + manual + notifications blocked). Screenshot testing covers representative **cross-products**, not 12 isolated states. *Rationale:* the 12 states are not mutually exclusive; a single-state enum either hides concurrent trust failures or explodes combinatorially.

### Decision 3 (P0.3) — Card accessibility contract

Each account card's **container is one semantic node with a single "open details" action**. Refresh is a **separate 48 dp semantic child** placed after the card summary in traversal order (the review's preferred pattern). No merged node may silently remove an action from TalkBack or Switch Access. *Rationale:* the plan draft simultaneously promised an in-card refresh action and a single-action merged node — an unshippable contradiction.

### Decision 4 (P0.4) — Adaptive-layout measurement contract

- Candidate **ordering** stays a pure function, testable on the JVM.
- **Fit verification** happens in Compose under real constraints (real text, font scale, insets, locale/RTL, secondary-window shape) via Compose UI/screenshot tests — the full fit matrix is *not* claimed to be JVM-verifiable.
- **No manual memoization cache** whose key omits layout-affecting inputs (content, locale, layout direction, display scale, insets, secondary-window shape). Prefer normal Compose invalidation and measurement.
- The "SubcomposeLayout intrinsics" wording is downgraded to a **measurement prototype to be built and benchmarked** before it becomes durable architecture — Compose documents that custom-layout intrinsic calculations may be approximations.

*Rationale:* an incomplete cache key produces stale/wrong layouts after nickname edits, locale changes, or inset changes — exactly the class of bug the fit engine exists to prevent.

### Decision 5 (P0.5) — Validate the local consumer loop first; thin import earlier

The Tier 1 product-validity risk (will users repeatedly capture/enter evidence?) is retired **first**, not at closed-alpha exit:

- A **Phase 0 product-validation gate** precedes broad implementation (see §4 for the revised phase order and §5 for its gates).
- A **thin, fixture-backed screenshot-review happy path moves into Phase 2 / early Phase 3**; full ML Kit hardening remains later.
- **Hackathon adaptation:** the working demo build serves as the Phase 0 prototype vehicle (see §6).

*Rationale:* the plan itself ranked manual-ingestion viability Critical/Certain yet did not test it until Phase 6; Demeter can be technically excellent and still fail on this single question.

### Decision 6 (P0.6) — Orthogonal persisted confidence fields; interaction never upgrades extraction confidence

Every saved evidence field persists four **orthogonal** properties:

- `extractionConfidence` — parser/OCR confidence per field;
- `confirmationState` — unreviewed / bulk-confirmed / individually confirmed / user-edited;
- `sourceAuthority` — manual / screenshot / signed bridge / approved connection;
- `confirmedAt` plus parser/template version.

"Confirm and save" may confirm all visible fields as a group when the review scope is explicit, but it **never upgrades extraction confidence** — opening or tapping a field does not make OCR more accurate. A **user-edited value becomes confirmed manual evidence** (`sourceAuthority = manual`), not higher-confidence OCR. This supersedes the plan's "bulk confirm caps at medium / individually affirmed fields become high" rule. *Rationale:* mapping UI interaction to confidence corrupts the audit trail's meaning.

### Decision 7 (P0.7) — Versioned window-identity fingerprint

Logical-window matching prefers the provider window ID; otherwise it derives a **versioned fingerprint** from **provider, account, window type, unit, cadence/duration, provider/model scope, and parser template** — **never display name alone**. Additionally:

- Label **aliases are stored separately** from identity;
- **Ambiguous matches prompt the user** ("update existing window or create new?");
- Resolver version and **match reason are recorded in the audit trail**;
- Windows with **different units, cadence, scope, or reset behavior are never silently merged**.

*Rationale:* a normalized-display-name fallback splits one window after provider copy/locale changes and merges distinct windows with similar labels — corrupting reminder identity, the product's cardinal sin.

### Decision 8 (P0.8) — Bridge trust baseline

- **Public HTTPS is the supported default v1 bridge path.**
- **P-256 ECDSA is the mandatory baseline mobile signing algorithm** (it has a documented Android Keystore pattern across the API 29–37 matrix). **Ed25519 is optional**, enabled only after Keystore-backed availability is verified across the supported API/device matrix.
- If multiple algorithms exist, **algorithm selection is bound into the signed pairing transcript and downgrade is prohibited**.
- **LAN is gated/beta**: it ships only behind a reviewed **per-bridge certificate pin delivered out-of-band in the QR pairing ceremony**, with rotation/recovery — or it is deferred from public release. No generic trust bypass; no private-CA installation by ordinary users.
- Payload signature validation remains **independent of TLS** in all cases.

*Rationale:* the draft's Ed25519-first-with-negotiated-fallback design left the home-LAN certificate story unresolved and invited downgrade attacks.

---

## 2. Accepted P1 decisions

### Decision 9 — Onboarding sign-in de-emphasis

"Continue locally" remains dominant. Sign in with Google is presented low-emphasis, labeled **"Only needed for email reminders,"** and is otherwise **introduced at the moment the user enables email reminders** — not as an equal first-launch path. *Rationale:* equal weight at first launch weakens the local-first promise and raises a privacy question before value is shown.

### Decision 10 — Compressed branch-specific add-account flows

The seven logical wizard steps compress into progressive branch-specific flows (Consumer: provider/type → nickname/source → evidence review → reminders/review; Bridge: provider/type → bridge pairing → organization/window selection → reminders/review). **Progress is shown by meaningful outcome, not "Step 2 of 7."** Skipping reminders is allowed; the account completes first.

### Decision 11 — Freshness fully separated from source

- **Freshness values:** current, aging, stale, unknown.
- **Source values:** entered manually, screenshot, bridge, approved connection.
- **"Manual" is a source, never a freshness label** (resolving the PRD's own ambiguity at lines 544–553).
- **"Live" is reserved** for evidence that can actually auto-refresh and is within its source freshness policy.
- **No opacity-dimming for staleness** (it reads as disabled and risks contrast failures). Use explicit copy — e.g., "42% remaining · updated 2 days ago" — plus an outlined/neutral container treatment.

### Decision 12 — Two reminder intents with source-based defaults

Two notification intents exist:

1. **Allowance reminder** — based on sufficiently current known remaining capacity.
2. **Check-usage reminder** — "This window resets in 4 hours. Open Demeter to update what remains."

Defaults by source: **manual/screenshot accounts → Advisory evidence policy + check-usage intent; bridge accounts → current-data-only.** *Rationale:* one notification template cannot honestly cover all evidence quality; a stale manual value must not assert that capacity remains.

### Decision 13 — Keep the 6-hour verified-freshness cap

The six-hour cap on "current" claims stays. **Change the behavior, not the meaning:** manual weekly accounts default to advisory/check-usage reminders rather than diluting "Verified" to days-old evidence. Source-specific TTLs are tuned from evidence; the exact observation time is shown everywhere a decision depends on it.

### Decision 14 — Remote parser kill-switch before external OCR alpha

A **signed, privacy-safe, accountless remote configuration channel able to disable a broken parser template must exist before OCR reaches external alpha.** Until it does, Phase 6 (OCR) remains **internal-only**. A local flag alone cannot disable a newly broken template without an app update.

### Decision 15 — Import process-death behavior

- **Configuration change:** retain transient review state in memory/saved state (never raw image content).
- **Process death:** discard transient OCR/image state, clean temporary files on next launch, and return to a safe **"Import interrupted — select the image again"** state.
- **Never persist recognized text** merely to claim wizard restoration.

### Decision 16 — Cloud consent separation and email privacy

- "Use cloud for email reminders" is consented **separately** from any future "Sync my history."
- The app **previews exactly what leaves the device**.
- A **retention schedule is defined before external testing**.
- Email subject/privacy defaults to generic unless the user opts into account/value detail.
- **Changing an email destination, deleting the cloud account, and revoking devices are step-up-authentication actions.**

### Decision 17 — Slim initial module graph

Initial modules: `:app`, `:domain` (pure JVM), `:data`, `:platform`, `:designsystem`, `:feature:today`, `:feature:account`, `:testing` (benchmark/baseline-profile modules when performance work begins). **Split later only on evidence** (ownership, build time, dependency constraints). **One Proto DataStore** — no second Preferences DataStore for a single Boolean. The pure-JVM domain boundary is retained and enforced.

### Decision 18 — Duplicate rate is measured, not guaranteed "by construction"

Notification posting and Room audit writes are not one atomic transaction; a crash can land between them. Therefore:

- Define an explicit **delivery state machine**: `desired_state`, `applied_state`, `attempt_count`, `last_error`, `applied_at`.
- **Reuse the same notification tag/ID** so retries *update* an existing notification rather than posting a second one.
- The **< 0.1% duplicate rate is a measured launch target**, not a mathematical guarantee.

---

## 3. Screen-level UX decisions (review §5)

1. **Today dashboard — stable order:** card order stays stable by default; a small **"Suggested next" summary** replaces automatic card reordering (spatial memory matters in a multi-account tool). One account: extra space goes to source, observation time, and next action — not quota gamification. Three accounts: balanced composition unless one is pinned or uniquely actionable. Six accounts: grid only when every required field fits, else list; **no density preference in v1**.
2. **Unknown-limit cards:** the **reset countdown is the primary metric**, with **"Remaining usage not available"** as the secondary line. **No quantitative ring or orbit** that could imply a quantity. `Source` and `Updated` stay visible but visually secondary.
3. **Account detail:** **"Update usage" sits at the top** for manual/screenshot accounts — it is their main repeat action. Advanced connection/audit details collapse by default, with reminder suppression reasons one tap away. Each usage window renders as an independent object; windows are never implied to add together.
4. **Screenshot review:** one explicit review scope — **"Review N fields before saving."** Low-confidence/ambiguous fields auto-expand. Source crops appear only when coordinates are reliable (no fabricated boxes). Repeat imports visibly identify the proposed match ("Updates Weekly all-models") and allow changing it. "Image deleted after saving" stays adjacent to the primary action.
5. **Reminder settings order:** **timing → evidence policy → unknown-remaining behavior**, with **notification-text previews** for the current-data, advisory, and reset-only cases. Inexact Android delivery is explained in a short helper line, not as the permission rationale's headline. Local notification and email channel health are treated separately.
6. **History and audit:** the primary story is "what changed and what Demeter did," not a trend chart. Gaps are explicit, never interpolated. **Event codes translate to plain language**, with expandable technical detail retained for diagnostics.

---

## 4. Revised delivery sequence (review §7)

The phase order below supersedes the plan's Phase 1–9 numbering. It changes *when risks are learned*, not PRD scope.

| Phase | Content |
|---|---|
| **0 — Product/design validation** | Clickable prototype, copy deck, card-state axes, reminder previews, screenshot-review usability, accessibility tree design. *(Hackathon adaptation: the working demo build is the prototype vehicle — see §6.)* |
| **1 — Walking skeleton** | Local onboarding, sample data, design tokens, slim module boundaries, CI/security guards, viewport diagnostics |
| **2 — Local proof loop** | Domain model, persistence, manual entry, **fixture-backed screenshot review**, 0–6 dashboard, account detail, update flow |
| **3 — Reminder policy and UX** | Pure policy, settings, preview copy, audit reasons, time-travel tests |
| **4 — Android scheduling** | Permission, channels, inexact alarms, reconciliation, health, real-device evidence |
| **5 — OCR hardening** | ML Kit, parser templates, cleanup, hostile-image tests, **signed remote template disable before external alpha** |
| **6 — Closed alpha** | Local product only; validate repeat import, reminder usefulness, trust comprehension, notification fatigue |
| **7 — Cloud/email** | Explicit consent, minimum reminder payload, verified destination, privacy levels, retention/deletion, kill switches |
| **8 — Bridge** | Public HTTPS first; signed snapshots; organization adapters; LAN only when certificate and permission experience pass review |
| **9 — Closed beta and release hardening** | Full evidence matrix, accessibility closure, energy, 16 KB, Play readiness, staged rollout |

---

## 5. Metrics and launch gates (review §8)

**North star** stays *Useful reset windows managed*, redefined as a **deduplicated rate as well as a count**: the percentage of active usage windows that had current/user-confirmed evidence and either an eligible reminder or an intentional suppression decision recorded before reset. Both count and rate are reported, so growth in account/window records cannot inflate the metric.

| Metric | Definition | Initial gate |
|---|---|---:|
| Repeat evidence update | Activated Tier 1 users completing another update within 7 days | ≥ 50% in closed alpha |
| Import completion | Started screenshot imports reaching saved snapshot | ≥ 80% |
| Import review time | Picker return to confirmed snapshot, median / P95 | ≤ 60 s / ≤ 120 s |
| Freshness comprehension | Users correctly explain current vs stale vs unknown in task test | ≥ 80% |
| Reminder-prediction comprehension | Users correctly predict send/suppress/advisory in task test | ≥ 80% |
| Advisory regret | Advisory reminders marked wrong or not useful | < 10% |
| Notification disable after first reminder | Channel/app disabled within seven days | < 15% |
| Duplicate reminder rate | More than one visible notification for one logical reminder | < 0.1% (measured — see Decision 18) |

**Phase 0 comprehension gates:** ≥ 80% complete account add without facilitator rescue; median screenshot-review task under 60 s after picker return; ≥ 80% correctly explain current/stale/unknown; ≥ 80% correctly predict send/suppress/advisory; **no participant interprets unknown remaining capacity as zero or full.** These are alpha-calibration starting gates, not external guarantees.

---

## 6. Adopted answers to plan questions Q1–Q15

The review's answers (review §6) are adopted as decisions:

| Q | Adopted decision |
|---:|---|
| 1 | **Append-only snapshots + a materialized latest projection**, updated atomically. Keep source, parser version, correction lineage, and field confirmation metadata. Historical evidence is never mutated. |
| 2 | **Three-phase persistence/platform boundary approved**, made explicit as an outbox-style repair model: `desired_state`, `applied_state`, `attempt_count`, `last_error`, `applied_at`; reconcile on launch and system events. |
| 3 | **Persisted request-code allocation.** Allocate transactionally, enforce uniqueness, never reuse codes while any platform registration could remain; tombstone released mappings for a bounded period or until deletion/reconciliation proves cleanup. |
| 4 | **Defaults differ by source.** Manual/screenshot: Advisory with check-usage copy. Bridge/approved: current-data-only. Freshness on the card; detailed caveats in detail/audit. |
| 5 | **Reset countdown primary; "Remaining usage not available" secondary. No quantitative ring.** |
| 6 | **Information completeness first.** Three accounts balanced by default (feature only when pinned or uniquely urgent); six compact: grid only if all hard constraints pass, else list; no density preference in v1. |
| 7 | **Recents-safe redaction as default.** `FLAG_SECURE` is an explicit per-surface or global opt-in; never applied to the import picker/review without explaining the screenshot consequence. |
| 8 | Folded into Q13. |
| 9 | **Keep the six-hour verified cap initially.** Do not dilute "current"; use advisory/check-usage reminders. TTL is source-specific and remotely adjustable only toward more conservative. |
| 10 | **15 minutes as the initial delivery-quality band; two snoozes as the cap; quiet-hours floor provisional.** Measure delay distribution, post-decision-window deliveries, snooze use, disable rate, and usefulness feedback before tuning. |
| 11 | **Public HTTPS first.** If LAN ships: reviewed per-bridge pinning provisioned through pairing with rotation/recovery; otherwise LAN stays beta/gated. No private-CA installs, no trust-anyway. |
| 12 | **No Room field-level encryption in v1** absent a threat-model finding. App-private storage, backup exclusions, privacy mode, Keystore-protected tokens. **Step-up reauthentication only for high-impact cloud actions**, not routine viewing. |
| 13 | **UI interaction never maps to confidence.** Parser confidence and confirmation tracked separately (Decision 6). Bulk confirmation may confirm all clearly displayed fields; ambiguous fields expand individually; user edits become confirmed manual evidence. |
| 14 | **Google-only identity acceptable for v1 email**, introduced at email enablement rather than first launch. Auth boundary stays provider-agnostic; email-link identity added only if research shows meaningful exclusion. |
| 15 | **Add Phase 0, move a thin import loop earlier, move remote parser control before OCR alpha, slim the module graph, and validate the local consumer loop before cloud/bridge complexity dominates.** |

---

## 7. Rejected / adapted

**Nothing in the review was rejected outright.**

One item is **adapted** for the hackathon context:

- **P0.5's Phase 0 "clickable prototype tested with 5–8 target power users"** — the prototype vehicle is adapted: the **working demo build (2026-07-12) serves as the Phase 0 prototype**. The Phase 0 comprehension gates (§5) are unchanged in substance and are to be **run against the demo build afterward** rather than against a separate throwaway clickable prototype. The intent of P0.5 — retire the manual-evidence-loop viability risk before broad implementation hardening — is preserved.

---

## 8. Demo build (2026-07-12) — scope and deviations

The 3-hour demo build (`apps/android`, version 0.1.0-demo) implements the complete **Tier 1 local consumer loop** — the exact loop Decision 5 / Phase 0 says must be validated first: record or paste evidence → understand the card (usage / freshness / source / reminder-health axes) → configure a truthful reminder (two-control model, live delivery preview) → see what was delivered or suppressed and why (plain-language audit trail). Real inexact AlarmManager scheduling, delivery-time re-evaluation, contextual POST_NOTIFICATIONS two-beat ask, boot/update/time-change reconciliation, and notification tag-reuse are all functional. Domain policies (ReminderPolicy, DashboardPolicy, FreshnessPolicy) are pure JVM and unit-tested.

**In scope:** onboarding (zero prompts, local-dominant), accounts (max 3/provider), manual + paste-assist evidence entry (assistive, never force-matched), append-only Room history, adaptive Today dashboard with stable order + Suggested-next banner, unknown-limit cards led by reset countdown, account detail with Update-usage primary action, reminder editor ordered timing → evidence policy → unknown-remaining opt-in, allowance vs check-usage intents, audit trail, sample data mode.

**Out of scope (per revised phases 5–8):** screenshot OCR / Sharesheet, cloud/email/identity, bridge, widgets, release hardening.

**Temporary deviations (to be paid down before Phase 1 of the real build):** 2-module Gradle graph (`:app` + pure-JVM `:domain`) instead of the slim 8-module graph; manual DI instead of Hilt; SharedPreferences instead of Proto DataStore; compile/target SDK 35 instead of 37; strings hardcoded (i18n baseline deferred); adaptive-grid heuristic instead of the candidate/measurement fit engine; single quiet-hours preset; no snooze; request codes via Room autoincrement (per D6, kept).

### 8.1 Release-hardening update (2026-07-14)

The demo build was hardened to a signed release candidate (v1.0.0, versionCode 1):

**Security guardrails (all verified on the release build):**
- `INTERNET` and `ACCESS_NETWORK_STATE` stripped via `tools:node="remove"` — merged manifest verifiably contains only `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED` (aapt2-checked). Data cannot leave the device over the network, structurally.
- `allowBackup=false` **plus** `dataExtractionRules` (API 31+) and `fullBackupContent` (API < 31) excluding all domains — cloud backup, D2D transfer, and OEM migration all fail closed.
- Notification lock-screen privacy: `VISIBILITY_PRIVATE` + generic public version ("Demeter reminder / Unlock to view details"), channel default `lockscreenVisibility = PRIVATE`.
- Privacy mode (Settings toggle): `FLAG_SECURE` — screenshots/recording of the app blocked, recents redacted. Verified: screencap with mode on returns a black frame.
- Export (JSON via share sheet; user picks destination) and Delete-all (confirmed dialog; verified to also cancel all platform alarms — `alarm_cancelled` records observed in dumpsys).
- MainActivity validates the notification `accountId` extra against a UUID regex and handles `onNewIntent` (singleTask); the alarm receiver wraps delivery in a crash-guard that records a `delivery_error` audit event.
- R8 + resource shrinking on (2.4 MB APK, from 58 MB debug); signed with a locally generated upload keystore (`apps/android/keystore/upload.jks`, credentials in gitignored `keystore.properties` — **back both up; replace before real publication if desired**).

**Independent security audit** (read-only agent, full-source): posture confirmed strong — no logging of any kind, parameterized Room queries only, no WebView/clipboard/reflection/dynamic code, immutable explicit PendingIntents, non-exported receivers. All four real findings (notification visibility, backup rules, release minification, intent-extra validation) fixed as above; two informational suggestions (receiver crash-guard — done; CI merged-manifest permission check — carried as a Phase-1 item).

**Release-build verification walkthrough:** onboarding → add account → paste-assist ("Weekly limit: 62 % used, resets in 2 days 4 hours" → 38% remaining, weekly, reset prefilled) → save → reminder rule → two-beat permission → 2 inexact alarms registered → Settings (privacy toggle, export sheet, test notification) → delete-all → empty state. Light + dark listing screenshots captured.

**Play-side items only the account owner can do:** host the privacy policy at a public URL, create the Play Console app, upload `app-release.aab`, complete Data safety from `docs/google-play/DATA_SAFETY_MAPPING.md`, content rating, and (for personal accounts created after Nov 2023) run the required 12-tester / 14-day closed test before production.

### 8.2 Screenshot → on-device OCR capture (2026-07-14, v1.1.0)

Shipped the semi-automatic capture path — the shippable answer to "read my real usage automatically," given no provider exposes a consumer-subscription usage API and Play policy forbids accessibility/notification scraping (both verified by independent research on 2026-07-14).

**What shipped (v1.1.0, versionCode 2):**
- **Photo Picker import** (primary, verified on-device): "Import screenshot" in the Record/Update-usage editor launches Android's Photo Picker (no storage permission — Play-blessed), runs OCR on the chosen image, and prefills the same editable, never-force-matched fields the paste-assist uses. Verified end-to-end on emulator: OCR read a usage screenshot → "38% remaining · resets in 1d 6h · weekly" auto-filled.
- **Share-sheet target** (ACTION_SEND image/*): a screenshot shared from any app lands on a new Import screen (account picker + recognized-text preview + Save). Warm-start path works; cold-start nav is best-effort.
- **On-device ring gauges**: the Today cards now lead with a status-colored capacity ring (red urgent / green healthy), unknown limits draw only the track and lead with the reset countdown.

**Privacy/security (verified):**
- OCR uses the **bundled** ML Kit Latin recognizer (`com.google.mlkit:text-recognition`) — model ships inside the APK; logcat confirms it loads `libmlkit_google_ocr_pipeline.so` from `base.apk` and selects the local model, **no network, no Play Services download**.
- Merged manifest after adding ML Kit still contains **only** `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED` (aapt2-verified) — ML Kit injected no INTERNET. The image is read once for text and never copied, stored, or transmitted.
- ML Kit bundles Google's `datatransport` telemetry pipeline; because the app has **no INTERNET permission**, it is structurally unable to send anything — the permission removal defends against the SDK's own telemetry.

**Not shipped / limits (documented, not hidden):** still no silent/live auto-read (impossible + Play-noncompliant); accessibility/notification scraping deliberately not built; MediaProjection auto-capture deliberately skipped (per-session consent gives no tap savings on Android 14+, and it's off-label for the `mediaProjection` FGS type). APK grows ~a few MB from the bundled OCR model.

### 8.3 Multi-window OCR + real-screenshot decode fix (2026-07-14, v1.2.0)

Follow-up from live user testing on a real phone (a Claude "Usage" screenshot).

**Bug fixed — "the selected image could not be read":** real device screenshots failed OCR because Android hands ML Kit a HARDWARE bitmap it cannot read. `OcrReader` now decodes to a SOFTWARE ARGB_8888 bitmap (`ImageDecoder` with `ALLOCATOR_SOFTWARE`), downscales huge images, and falls back to a stream decoder; a failed read now shows a helpful message instead of silently doing nothing.

**Multi-window extraction:** the Claude Usage screen shows several windows at once (Current session / All models / Fable only), and OCR returns the "% used" column separately from the labels. `OcrReader.readLines()` now returns positioned lines, and a new `UsageScreenParser` reconstructs rows — pairing each label with the "% used" on its row and the reset line beneath it, ignoring the status bar and section headers. It handles relative resets ("in 58 min") and weekday/time resets ("Tue 10:59 AM"), and treats "% used" as the complement of remaining. A multi-window screenshot routes to a new `MultiImportScreen` (checklist preview → "Save N windows"); re-import updates existing windows by label instead of duplicating. Verified by `UsageScreenParserTest` (4 cases replaying the user's exact screen): Current session 84% used / 16% left / reset set; All models 24% used / 76% left / weekday reset; Fable only 0% used / 100% left / no reset.

**Provider picker:** replaced chips with two large tiles (ChatGPT/OpenAI, Claude/Anthropic) using original generic icons + accent colors — deliberately NOT the providers' trademarked logos (Play/affiliation risk). Verified on-device.

> **[Correction 2026-08-11]** The generic-icon statement above no longer describes the shipped
> UI: since the provider hand-off work (v1.5.1), `res/drawable-nodpi/ic_provider_*.png` are the
> providers' actual marks, shown solely to identify which service an account belongs to
> (nominative use), with the non-affiliation disclaimer in-app and in the README. The
> `ProviderCard` KDoc has been updated to match. **Action carried forward:** re-run the
> Play-listing trademark/affiliation risk assessment this section originally named before any
> public release, or restore generic marks.

**Note:** `MultiImportScreen` UI verified by build + unit test only; the shared emulator was taken over by a concurrent session mid-test, so the multi-window flow was not re-driven on-device by the assistant.
