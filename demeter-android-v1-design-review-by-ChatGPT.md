# Demeter Android v1 — Comprehensive Design Review

**Reviewed:** `Demeter_PRD_Android_Pixel_10_Pro_XL_v2.1.md` and `demeter-android-v1-design-and-implementation-plan.md`  
**Review type:** Product, UX, interaction, accessibility, architecture, trust, privacy, and delivery-plan review  
**Decision:** **Conditionally approve the plan after the P0 changes below. Do not begin broad implementation against the current draft unchanged.**

## 1. Executive summary

The Fable 5 plan is highly aligned with the PRD. It correctly preserves the product's strongest differentiators: truthful data states, local-first value, explicit source/freshness semantics, no provider credentials, repairable reminder intent, Android-native permission timing, adaptive layout, and rigorous release evidence.

The main issue is not missing engineering detail. It is that several product and interaction concepts are still conflated, while the delivery plan retires the most important product risk too late.

The highest-priority corrections are:

1. **Separate reset-only eligibility from reminder confidence.** The PRD defines only Verified and Advisory as confidence modes; reset-only is an explicit opt-in for unknown remaining capacity. The plan incorrectly presents three confidence modes.
2. **Replace the “12 card states” model with orthogonal state dimensions.** Usage, freshness/source, connection, and reminder health can coexist. A card can be exhausted, stale, manual, and notification-blocked simultaneously.
3. **Resolve the card accessibility contradiction.** The plan proposes an in-card refresh action but also makes each card one merged semantic node with only one open-details action.
4. **Rework the adaptive-layout measurement design.** The memoization key omits content, locale, layout direction, display scale, insets, and secondary-window shape. The claim that the full fit matrix is JVM-testable also overstates what can be validated without Compose measurement.
5. **Retire the Tier 1 product-validity risk earlier.** The plan itself ranks manual-ingestion viability as critical/certain, yet the realistic screenshot loop does not reach users until Phase 6.
6. **Separate OCR parser confidence from user confirmation.** User interaction should not be used as a proxy for source or extraction certainty.
7. **Harden logical window identity.** Falling back to normalized display name can split one provider window after a rename or merge distinct windows with similar labels.
8. **Close the LAN certificate and mobile-key algorithm decisions before Phase 8.** Public HTTPS should be the supported default; LAN should remain beta or gated until a reviewed per-bridge trust design exists.

My recommended product strategy is to validate the complete local loop first: **import or enter evidence → understand the card → configure a truthful reminder → understand what was delivered or suppressed.** Cloud email and bridge automation should remain architectural tracks, but should not obscure whether the local consumer product earns repeat use.

## 2. What the plan gets right

- The reminder system is modeled as persisted product intent with Android registrations as repairable derived state.
- The receiver re-evaluates eligibility and performs bounded local work rather than using WorkManager as a timing mechanism.
- The plan preserves the core privacy invariant: provider credentials do not exist in the Android or cloud domain models.
- Local mode is structurally independent of cloud availability.
- The compact-phone emulator is treated as a co-equal gate to the Pixel 10 Pro XL.
- The plan explicitly addresses 60/120 Hz behavior, energy-normalized battery evidence, large text, TalkBack, Switch Access, dynamic color, and window classes.
- OCR is framed as assistive transcription with mandatory review, not provider-authoritative automation.
- The bridge independently verifies TLS and signed payloads.
- Append-only evidence and correction events match Demeter's truth-and-auditability thesis.
- Release gates, synthetic fixtures, secret scanning, manifest audits, and rollback mechanisms are unusually mature for a pre-implementation plan.

These foundations should be preserved.

## 3. P0 changes required before implementation

### P0.1 — Correct the reminder-mode model

**Evidence:** The PRD's reminder settings list two confidence modes—Verified only and Advisory (PRD lines 647–650). Reset-only is separately defined as an explicit opt-in when remaining capacity is unknown (PRD lines 1302–1308). The plan calls Verified, Advisory, and Reset-only “all three confidence modes” (plan lines 175 and 256).

**Why it matters:** This conflates two different decisions:

- How old the evidence may be: Verified vs. Advisory.
- Whether a reminder may be sent when remaining capacity is unknown: allow reset-timing-only vs. do not allow.

**Recommendation:** Use two controls:

1. **Evidence policy:** Current data only / Use last known data.
2. **Unknown-remaining behavior:** “Remind me about the reset even when remaining usage is unknown.”

Reset-only should change notification copy and eligibility, not appear as a peer confidence segment.

### P0.2 — Model card status as concurrent dimensions

**Evidence:** The plan treats “12 card states” as a pure derivation and a screenshot matrix (plan lines 70, 181, and 233). The PRD list includes usage conditions, data conditions, source types, and reminder/connection conditions together (PRD lines 770–787).

**Why it matters:** These are not mutually exclusive. Examples:

- Exhausted + stale + manual + notifications disabled.
- Urgent + bridge syncing.
- Unknown allowance + bridge action required.

A single-state enum will either hide important trust information or create an exploding combined-state enum.

**Recommendation:** Define four axes with a precedence contract:

| Axis | Values | Visual role |
|---|---|---|
| Usage | Healthy, use soon, urgent, exhausted, reset expected, unknown | Primary metric and main status sentence |
| Evidence | Current, aging, stale, error, syncing | Freshness line and container treatment |
| Source | Manual, screenshot, bridge, approved connection | Stable source label, not a status |
| Reminder/connection | On, off, blocked; healthy, action required | Secondary action/status row |

Create a state-combination matrix and accessibility phrase order. Screenshot representative cross-products, not only 12 isolated states.

### P0.3 — Resolve the accessibility/action conflict on cards

**Evidence:** The plan gives stale cards an in-body one-tap refresh action (plan line 187) while defining each account card as one merged semantic node with only a single open-details action (plan lines 197–205). The PRD asks for one clear open-details touch action but also requires frequent card actions to remain reachable.

**Recommendation:** Choose one of these patterns:

- Preferred: card container is one open-details action; refresh is a separate 48 dp semantic child after the card summary.
- Alternative: one merged card node with explicit custom accessibility actions for “Open details” and “Refresh,” plus a visible refresh control for sighted users.

Do not use a merged node that silently removes an action from TalkBack or Switch Access.

### P0.4 — Fix the adaptive-layout measurement contract

**Evidence:** The PRD says layout depends on content, display scale, insets, number of visible windows, orientation, and fold posture (PRD lines 700–714). The plan memoizes only window size/classes, font scale, count, and pinned account (plan line 118).

**Risks:** Stale or wrong layout after nickname edits, locale/RTL change, inset changes, secondary-window changes, large display setting, or content variation.

**Recommendation:**

- Keep candidate ordering as a pure function.
- Perform actual fit verification in Compose under real constraints.
- Avoid a manual memoization cache unless its key includes every layout-affecting input; prefer normal Compose invalidation and measurement first.
- Test pure candidate ordering on the JVM, but test text fit, font behavior, clipping, and semantics in Compose UI/screenshot tests.
- Replace “SubcomposeLayout intrinsics” with a concrete measurement prototype and benchmark before making it durable architecture. Official Compose guidance notes that custom-layout intrinsic calculations can be approximations and may not be correct for every layout.

### P0.5 — Add Phase 0 product-loop validation and move a thin import path earlier

**Evidence:** The plan ranks the lack of consumer automation and manual-ingestion viability as Critical/Certain, but says the viability question is answered only at closed-alpha exit after Phase 6 (plan lines 326–355).

**Why it matters:** Demeter can be technically excellent and still fail if users will not repeatedly capture or share evidence.

**Recommendation:** Before broad implementation:

- Build a clickable prototype for onboarding, screenshot review, Today cards, stale states, reminder setup, and reminder audit.
- Test with 5–8 target power users.
- Move a thin, fixture-backed screenshot-review happy path into Phase 2 or early Phase 3. Full ML Kit hardening can remain later.
- Do not wait until the reminder platform is complete to learn whether the core evidence loop is acceptable.

**Phase 0 gates:**

- At least 80% complete account add without facilitator rescue.
- Median screenshot-review task under 60 seconds after the picker returns.
- At least 80% correctly explain whether a displayed value is current, stale, or unknown.
- At least 80% correctly predict whether a sample reminder will send, suppress, or use advisory wording.
- No participant interprets unknown remaining capacity as zero or full.

### P0.6 — Separate parser confidence, confirmation, and source authority

**Evidence:** The plan caps untouched bulk-confirmed fields at medium and raises individually affirmed fields to high (plan lines 128 and 195). The PRD says high confidence requires confirmation of every saved field, but defines confidence from source type and parsing certainty (PRD lines 309 and 1713–1718).

**Problem:** Opening a field does not make OCR more accurate, and editing it does not make the provider source more authoritative.

**Recommendation:** Persist orthogonal fields:

- `extractionConfidence`: parser/OCR confidence per field.
- `confirmationState`: unreviewed, bulk-confirmed, individually confirmed, user-edited.
- `sourceAuthority`: manual, screenshot, signed bridge, approved connection.
- `confirmedAt` and parser/template version.

“Confirm and save” can confirm every visible field as a group if the review design makes the scope explicit. It should not automatically upgrade extraction confidence. A user-edited value becomes confirmed manual evidence, not higher-confidence OCR.

### P0.7 — Strengthen window identity resolution

**Evidence:** The proposed fallback identity is `(provider, windowType, providerWindowId ?: normalizedDisplayName)` (plan line 142).

**Risk:** Provider copy, locale, capitalization, model naming, or screen-template changes can create a duplicate logical window; similar labels can merge distinct windows.

**Recommendation:**

- Prefer provider window ID when available.
- Otherwise derive a versioned fingerprint from provider, account, window type, unit, cadence/duration, provider/model scope, and parser template—not display name alone.
- Store label aliases separately from identity.
- If matching is ambiguous, ask the user whether this updates an existing window or creates a new one.
- Record resolver version and match reason in the audit trail.
- Never silently merge two candidate windows with different units, cadence, scope, or reset behavior.

### P0.8 — Close bridge trust choices before implementation

**Evidence:** The plan proposes Ed25519 with negotiated P-256 fallback and rejects certificate pinning, while acknowledging that a home LAN rarely has a publicly trusted certificate (plan lines 130–136 and 395–397).

**Recommendations:**

- Make **public HTTPS the supported/default v1 bridge path**.
- Use one mandatory mobile signing algorithm for the minimum supported API path; P-256 ECDSA has a documented Android Keystore pattern. Treat Ed25519 as optional only after confirming Keystore-backed availability across the API 29–37 matrix.
- If multiple algorithms remain, bind algorithm selection into the signed pairing transcript and prohibit downgrade.
- For LAN beta, use either a reviewed per-bridge certificate pin delivered out-of-band in the QR pairing ceremony, with rotation/recovery, or defer LAN from public release. Do not ship a generic trust bypass or require ordinary users to install a private CA.
- Keep payload signature validation independent of TLS in all cases.

Android's current API 37 guidance confirms that direct LAN traffic is blocked by default for target-37 apps unless a relevant privacy-preserving picker applies or `ACCESS_LOCAL_NETWORK` is granted. The plan's contextual permission approach is therefore correct.

## 4. P1 recommendations before closed alpha

### 4.1 Reduce onboarding identity pressure

Keep **Continue locally** dominant. Present Sign in with Google as a lower-emphasis path labeled “Only needed for email reminders,” or introduce it when the user enables email. Equal visual weight at first launch weakens the local-first promise and creates an unnecessary privacy question before value is demonstrated.

### 4.2 Compress the seven-step wizard into branch-specific flows

The PRD's seven logical steps do not require seven visible screens. Use progressive branching:

- Consumer: Provider/type → nickname/source → evidence review → reminders/review.
- Bridge: Provider/type → bridge pairing → organization/window selection → reminders/review.

Show progress by meaningful outcome, not a generic “Step 2 of 7.” Permit skip-reminders and complete the account first.

### 4.3 Separate freshness from source in the UI

The PRD itself lists “Manual” as both a freshness label and a source, creating ambiguity (PRD lines 544–553). Resolve this in design:

- Freshness: Current, aging, stale, unknown.
- Source: Entered manually, screenshot, bridge, approved connection.

Reserve “Live” for evidence that can actually refresh automatically and is within the source freshness policy. Avoid lowering opacity to indicate stale; it can look disabled and may reduce contrast. Use explicit “42% remaining · updated 2 days ago” copy plus an outlined or neutral treatment.

### 4.4 Introduce two reminder intents

For manual/screenshot sources, a stale value should not produce an assertion that implies capacity remains. Use:

1. **Allowance reminder:** based on sufficiently current known remaining capacity.
2. **Check-usage reminder:** “This window resets in 4 hours. Open Demeter to update what remains.”

Default manual and screenshot accounts to Advisory/Check-usage behavior; default bridge accounts to Current-data-only. This is clearer than trying to make one notification template cover all evidence quality.

### 4.5 Keep the freshness cap strict; change the behavior, not the meaning

Do not extend “Verified” to days-old weekly evidence merely so manual reminders fire. Keep the six-hour cap as a reasonable starting point for a claim that is explicitly current. Let manual weekly accounts default to advisory/check-usage reminders. Tune source-specific TTLs from evidence, and show the exact observation time everywhere a decision depends on it.

### 4.6 Make parser kill switches available before parser alpha

The plan puts remote parser gates in Phase 7, after OCR reaches closed alpha in Phase 6. A local flag cannot disable a newly broken template without an app update. Provide a signed, privacy-safe, accountless configuration channel before OCR alpha, or accept that Phase 6 remains internal-only until Phase 7's remote disable mechanism exists.

### 4.7 Specify process-death behavior during import

The PRD asks for process-recreation resilience but prohibits unnecessary persistence of images and unconfirmed OCR. Define a deliberate behavior:

- Configuration change: retain transient review state in memory/saved state without raw image content.
- Process death: discard transient OCR/image state, clean temporary files on next launch, and return the user to a safe “Import interrupted—select the image again” state.

Do not persist recognized text merely to claim wizard restoration.

### 4.8 Add explicit cloud consent, retention, and email privacy design

Cloud email requires some current snapshot and rule data, but should not silently become full history sync.

- Separate “Use cloud for email reminders” from future “Sync my history.”
- Preview exactly what leaves the device.
- Define retention before external testing.
- Default email subject/privacy to generic unless the user opts into account/value detail.
- Treat changing an email destination, deleting the cloud account, and revoking devices as step-up-sensitive actions.

The PRD requires cloud and email to be separately consented and requires a published retention schedule (PRD lines 2593–2605); the plan needs corresponding screens and data contracts.

### 4.9 Slim the initial module graph

The pure-JVM domain boundary is worth enforcing. Creating the complete `:core:*`, `:feature:*`, `:data:*`, sync, benchmark, and baseline-profile module graph on day one is not.

Start with approximately:

- `:app`
- `:domain` (pure JVM)
- `:data`
- `:platform`
- `:designsystem`
- `:feature:today`
- `:feature:account`
- `:testing`
- benchmark/baseline modules when performance work begins

Split only when ownership, build-time, or dependency constraints justify it. Use one Proto DataStore rather than adding a second Preferences DataStore for one Boolean.

### 4.10 Do not claim duplicate prevention “by construction”

Logical IDs and stable notification tags materially reduce duplicates, but notification posting and Room audit writes are not one atomic transaction. A crash can occur between posting and recording. Define the delivery state machine and use the same notification tag/ID so retries update rather than create a second visible notification. Measure the launch target; do not treat it as mathematically guaranteed.

## 5. Screen-level UX recommendations

### Onboarding

- Lead with the local value proposition and one sentence explaining that Demeter does not connect to private consumer accounts automatically.
- Keep “How connections work” close to the source-selection step, where it becomes relevant.
- Let users explore sample data before sign-in or permissions.

### Today dashboard

- Keep order stable by default. Prefer a small “Suggested next” summary over automatic card reordering; spatial memory is valuable in a multi-account tool.
- For one account, do not let the hero treatment turn into quota gamification. Use the extra space for source, observation time, and next action.
- For three accounts, default to a balanced composition unless one is explicitly pinned or uniquely actionable.
- For six accounts, choose the grid only when every required field fits; otherwise choose a list. Do not offer a density preference in v1.
- For unknown limits, make the reset countdown the primary metric and say “Remaining usage not available.” Avoid an orbit or ring that could imply quantity.
- Keep `Source` and `Updated` visible but visually secondary to the metric and reset.

### Account detail

- Put “Update usage” near the top for manual/screenshot accounts; this is their main repeat action.
- Collapse advanced connection and audit details by default, but keep reminder suppression reasons one tap away.
- Show each usage window as an independent object; never imply the windows add together.

### Screenshot review

- Use one clear review scope: “Review 4 fields before saving.”
- Expand low-confidence or ambiguous fields automatically.
- Show the source crop only when coordinates are reliable; otherwise show the full image without fabricated boxes.
- On repeat import, visibly identify proposed matches: “Updates Weekly all-models.” Let the user change the match.
- Keep “Image deleted after saving” near the primary action.

### Reminder settings

- First choose reminder timing, then evidence policy, then unknown-remaining behavior.
- Explain inexact Android delivery in a short helper line, not in the permission rationale's main value statement.
- Preview the exact notification text for current, advisory, and reset-only cases.
- Treat local notification and email channel health separately.

### History and audit

- Make the primary history story “what changed and what Demeter did,” not a decorative trend chart.
- Use explicit gaps; do not interpolate.
- Translate event codes into plain language while retaining expandable technical details for diagnostics.

## 6. Direct answers to the 15 reviewer questions

| Q | Recommendation |
|---:|---|
| 1 | **Approve append-only snapshots plus a materialized latest projection.** Update both atomically. Keep source, parser version, correction lineage, and field confirmation metadata. Do not mutate historical evidence. |
| 2 | **Approve the three-phase persistence/platform boundary.** Make `desired_state`, `applied_state`, `attempt_count`, `last_error`, and `applied_at` explicit. This is an outbox-style repair model even if it is not named an outbox. Reconcile on launch and system events. |
| 3 | **Use persisted request-code allocation.** Allocate transactionally, enforce uniqueness, and do not reuse codes while any platform registration could remain. Tombstone released mappings for a bounded period or until account deletion/reconciliation proves cleanup. |
| 4 | **Defaults should differ by source.** Manual/screenshot: Advisory with “check usage” copy. Bridge/approved source: Current-data-only. Put freshness on the card; reserve detailed caveats for detail/audit. |
| 5 | **Use reset countdown as primary; “Remaining usage not available” as secondary.** Do not render a quantitative ring. |
| 6 | **Information completeness first.** Three accounts: balanced by default, feature only when pinned or uniquely urgent. Six compact: grid only if all hard constraints pass, otherwise list. No density preference in v1. |
| 7 | **Agree with recents-safe redaction as default.** Make `FLAG_SECURE` an explicit per-surface or global opt-in; never apply it to the import picker/review without explaining the screenshot consequence. |
| 8 | Addressed in Q13. |
| 9 | **Keep the six-hour verified cap initially.** Do not dilute “current” to accommodate manual behavior; use advisory/check-usage reminders instead. Make TTL source-specific and remotely more conservative only. |
| 10 | **Use 15 minutes as an initial delivery-quality band and two snoozes as a cap.** Treat the quiet-hours floor as provisional. Measure delay distribution, reminders delivered after the user's useful decision window, snooze use, disable rate, and “was this useful?” feedback before tuning. |
| 11 | **Public HTTPS first.** If LAN ships, use reviewed per-bridge pinning provisioned through pairing, with rotation/recovery; otherwise keep LAN beta/gated. Do not require users to install a private CA and do not ship trust-anyway. |
| 12 | **Do not add Room field-level encryption in v1** unless a threat-model finding requires it. Use app-private storage, backup exclusions, privacy mode, and Keystore-protected tokens. Use step-up reauthentication only for high-impact cloud actions, not routine viewing. |
| 13 | **Do not map UI interaction directly to confidence.** Track parser confidence and confirmation separately. Bulk confirmation can confirm all clearly displayed fields; individually expand ambiguous fields. User edits become confirmed manual evidence. |
| 14 | **Google-only identity is acceptable for v1 email**, provided it appears at email enablement rather than as an equal first-launch path. Keep the auth boundary provider-agnostic and add email-link identity only if research shows meaningful exclusion. |
| 15 | **Add Phase 0, move a thin import loop earlier, move remote parser control before OCR alpha, slim the module graph, and validate the local consumer loop before cloud/bridge complexity dominates.** |

## 7. Recommended delivery sequence

1. **Phase 0 — Product/design validation:** clickable prototype, copy deck, card-state axes, reminder previews, screenshot-review usability, accessibility tree design.
2. **Phase 1 — Walking skeleton:** local onboarding, sample data, design tokens, slim module boundaries, CI/security guards, viewport diagnostics.
3. **Phase 2 — Local proof loop:** domain model, persistence, manual entry, fixture-backed screenshot review, 0–6 dashboard, account detail, update flow.
4. **Phase 3 — Reminder policy and UX:** pure policy, settings, preview copy, audit reasons, time-travel tests.
5. **Phase 4 — Android scheduling:** permission, channels, inexact alarms, reconciliation, health, real-device evidence.
6. **Phase 5 — OCR hardening:** ML Kit, parser templates, cleanup, hostile-image tests, signed remote template disable before external alpha.
7. **Phase 6 — Closed alpha:** local product only; validate repeat import, reminder usefulness, trust comprehension, and notification fatigue.
8. **Phase 7 — Cloud/email:** explicit consent, minimum reminder payload, verified destination, privacy levels, retention/deletion, kill switches.
9. **Phase 8 — Bridge:** public HTTPS first; signed snapshots; organization adapters; LAN only when certificate and permission experience pass review.
10. **Phase 9 — Closed beta and release hardening:** full evidence matrix, accessibility closure, energy, 16 KB, Play readiness, staged rollout.

This sequence does not discard PRD scope; it changes when the most important risks are learned.

## 8. Metrics and launch gates to add

### North star

Keep **Useful reset windows managed**, but define it as a deduplicated rate as well as a count:

> Percentage of active usage windows that had current/user-confirmed evidence and either an eligible reminder or an intentional suppression decision recorded before reset.

Report count and rate; otherwise growth in account/window records can inflate the metric.

### Product-validity gates

| Metric | Definition | Initial gate |
|---|---|---:|
| Repeat evidence update | Activated Tier 1 users completing another update within 7 days | ≥ 50% in closed alpha |
| Import completion | Started screenshot imports reaching saved snapshot | ≥ 80% |
| Import review time | Picker return to confirmed snapshot, median / P95 | ≤ 60 s / ≤ 120 s |
| Freshness comprehension | Users correctly explain current vs stale vs unknown in task test | ≥ 80% |
| Reminder-prediction comprehension | Users correctly predict send/suppress/advisory in task test | ≥ 80% |
| Advisory regret | Advisory reminders marked wrong or not useful | < 10% |
| Notification disable after first reminder | Channel/app disabled within seven days | < 15% |
| Duplicate reminder rate | More than one visible notification for one logical reminder | < 0.1% |

These are starting gates for alpha calibration, not external guarantees.

## 9. External technical validation notes

- Android 17/API 37 direct LAN access requires `ACCESS_LOCAL_NETWORK` for target-37 apps unless an applicable system-mediated path avoids it. The plan's contextual request and denial isolation are correct: [Android local network permission](https://developer.android.com/privacy-and-security/local-network-permission).
- Compose documentation warns that intrinsic calculations for custom layouts may use approximations and may not be correct for every layout. Prototype the measurement strategy rather than making the current “SubcomposeLayout intrinsics” wording a durable decision: [Intrinsic measurements in Compose layouts](https://developer.android.com/develop/ui/compose/layouts/intrinsic-measurements).
- Photo Picker grants are transient by default. Demeter should avoid persisting them for the short review flow; if it explicitly takes a persistable grant, it must explicitly release that persisted grant: [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker) and [ContentResolver releasePersistableUriPermission](https://developer.android.com/reference/kotlin/android/content/ContentResolver#releasePersistableUriPermission(android.net.Uri,kotlin.Int)).
- Android documents a P-256 ECDSA signing example for Android Keystore. Use that as the conservative minimum-path baseline unless Ed25519 Keystore support is proven across Demeter's API/device matrix: [KeyGenParameterSpec P-256 example](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec).

## 10. Final recommendation

**Approve Fable 5 to proceed only after a short design revision that resolves the eight P0 items and adds a Phase 0 product-validation gate.** Do not rewrite the architecture; simplify and clarify it. The plan's core engineering judgment is strong. The largest remaining risk is that a technically rigorous system could make users perform too much work while still leaving them uncertain about what is current, what is inferred, and what a reminder actually promises.

### Top risks and mitigations

| Risk | Mitigation |
|---|---|
| Manual evidence loop does not earn repeat use | Validate the full loop before Phase 6; move thin import earlier; gate on repeat update behavior |
| Users mistake stale/manual values for live truth | Orthogonal freshness/source labels; check-usage reminder; explicit observation time |
| Card state model hides concurrent trust failures | Four-axis state model and precedence matrix |
| Adaptive layout caches the wrong composition | Complete invalidation inputs; Compose measurement tests; avoid incomplete memoization |
| OCR confidence becomes a proxy for user taps | Separate extraction confidence, confirmation, and source authority |
| Window matching corrupts reminder identity | Versioned fingerprint, aliases, match audit, user resolution for ambiguity |
| LAN bridge is secure on paper but unusable at home | Public HTTPS default; reviewed per-bridge pinning or gated LAN beta |

### Next 3 actions

1. Revise the plan's reminder-mode model, card-state model, accessibility semantics, and layout-fit contract.
2. Add Phase 0 and run a 5–8 user prototype test of screenshot review, stale/unknown cards, reminder setup, and audit comprehension.
3. Produce an updated plan with the recommended phase order, explicit alpha metrics, parser kill-switch timing, and bridge certificate/key decisions before implementation begins.
