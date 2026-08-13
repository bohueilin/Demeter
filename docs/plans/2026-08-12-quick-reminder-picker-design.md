# Quick reminder picker — design (2026-08-12)

**Goal:** arm the common reminder lead times (3d / 2d / 1d before reset) for an account's
weekly window in one tap from the Today dashboard, multi-select, without opening the
reminder editor.

**Entry.** A 48dp bell `IconButton` in each Today card's header, after Edit — a separate
semantic child per the card accessibility contract (Decision 3), labeled
"Reminders for {nickname}". Tint: healthy green when the headline window has an enabled
rule, `onSurfaceVariant` otherwise; error red is never used here (it stays reserved for
the blocked state on the meta row).

**Sheet.** The bell opens a Material 3 `ModalBottomSheet`:

- Title "Reminders · {window.label}" (heading semantics) with the account nickname beneath.
- Three multi-select `FilterChip`s — "3d before", "2d before", "1d before" — reflecting the
  current rule's `leadMinutes`. All three values already exist in
  `LEAD_TIME_CHOICES_MINUTES`; the domain model is untouched.
- Helper line: "Planning reminders, not alarms — Android may deliver a little late."
- If the rule has other leads (e.g. 4h), a summary line "Also active: 4h before reset" —
  the sheet never hides configuration it is editing around.
- "All options" `TextButton` → the existing `reminder/{accountId}/{windowId}` editor.

**Apply-on-tap.** Each chip toggle saves immediately via the existing `saveRule` path
(reconciler included). Existing rule: only 3d/2d/1d membership changes; other leads and
all policy settings are preserved. No rule: create with the editor's defaults
(`LAST_KNOWN` evidence policy, remind-when-unknown off, 10% threshold, no quiet hours).
`enabled = leadMinutes.isNotEmpty()` — deselecting everything turns reminders off, same
contract as the editor.

**Permission.** First arm with notifications ungranted (SDK 33+) launches the system
POST_NOTIFICATIONS prompt from the sheet. Declining keeps the rule saved; the sheet and
the card meta row show the existing "blocked" treatment, with a "Fix in Settings" action
in the sheet.

**Scope.** Today cards only; Compare remains a reading surface; the full editor is
unchanged. Rejected alternative: chips directly on the card (permanent clutter on the
calm dashboard) and detail-screen-only placement (one tap slower; kept as a possible
follow-up).
