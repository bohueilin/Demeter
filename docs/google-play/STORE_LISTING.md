# Demeter — Google Play Store Listing (v0.1)

Status: draft for review. All copy below follows PRD v2.1 §22 (Play compliance, approved/avoided
language) and the v2.2 addendum §8 (shipped demo scope). Copy describes only what v0.1 actually
does: manual/paste-entered usage data, local-only storage, best-effort inexact reminders.

---

## 1. App title (max 30 characters)

```
Demeter — AI Usage Monitor
```

26 characters. (ASCII-dash fallback if the em dash renders poorly in any locale:
`Demeter - AI Usage Monitor`, 26 characters.)

## 2. Short description (max 80 characters)

```
Track AI usage allowances you enter yourself. Local-only, no sign-in, no ads.
```

77 characters.

Alternative (71 characters), if "allowances" tests poorly:

```
Track your AI usage limits and reset times. 100% on-device. No sign-in.
```

## 3. Full description (max 4000 characters)

The block below is 2,833 characters — well under the 4,000 limit, leaving room for localized
expansion.

```
Keep track of the AI usage allowances you enter yourself — and get a heads-up before they reset.

If you use ChatGPT, Claude, or other AI assistants across several accounts, it is easy to lose track of which allowance resets when. Demeter gives you one calm dashboard for the usage information you record, plus optional reminders before a reset window closes.

WHAT DEMETER DOES
• One dashboard for the AI usage you record. For each account you add, see remaining usage (when you have entered it), when you last updated it, and a countdown to the next reset.
• Fast updates. Type a percentage, or paste the usage text shown by your provider and let Demeter suggest the matching fields — you always review before anything is saved.
• Best-effort reminders. Choose how long before a reset you want a nudge and how fresh your data must be for Demeter to mention remaining usage. Previews show exactly what each notification will say.
• Honest by design. Every card shows where a number came from and how old it is. If remaining usage is unknown, the card says so — Demeter never invents a value.
• Plain-language history. See what changed and what Demeter did (or deliberately did not do) about your reminders, and why.

WHAT DEMETER DOES NOT DO
• It does not connect to your ChatGPT, Claude, or any other provider account. No sign-in, no passwords, no API keys — ever.
• It does not scrape, poll, or read live usage from any provider. Every number in Demeter is something you typed or pasted.
• It does not promise exact reminder times. Reminders are scheduled as best-effort, inexact notifications; Android may delay or batch them, especially under battery saving. Demeter tells you this up front instead of pretending otherwise.

PRIVATE BY DEFAULT
• 100% on-device: this version makes no network connections at all — no servers, no cloud, no sync.
• No app account and no sign-in.
• No analytics, no crash reporting, no ads.
• All data lives in a local database on your phone. Delete everything from within the app at any time, or by uninstalling.
• Backups are disabled, so your entries never leave the device.
• Notifications are optional: Demeter only asks for notification permission if you turn reminders on.

WHO IT IS FOR
Anyone juggling usage limits across multiple AI accounts — power users with personal and work plans, developers watching plan windows, or anyone who wants a simple reset countdown without handing another app access to their accounts.

COMPATIBILITY AND AFFILIATION
Demeter is an independent app. It is not affiliated with, endorsed by, or connected to OpenAI, Anthropic, or Google. Provider names such as “ChatGPT” and “Claude” appear only to describe the kinds of usage information you may choose to record; Demeter has no access to those services.

Demeter records only what you give it. Nothing more.
```

Language checks (PRD §22.4): no "official", no "live/real-time balance", no "connect
automatically", no "exact alarm/reminder", no guarantee claims, explicit non-affiliation
statement, provider names used as text compatibility references only (no logos anywhere in
listing assets).

## 4. Category

**Primary suggestion: Tools.** Demeter is a single-purpose utility (record a value, get a
countdown and a reminder), not a task/workflow product. **Productivity is an acceptable
alternative** if we want adjacency to notes/reminder apps; pick one and keep it stable — category
changes reset some ranking signals. Tags (up to 5): Tools, Reminders, Utilities, Tracker,
Notifications.

## 5. Content rating questionnaire — expected answers (IARC)

Expected outcome: **Everyone / PEGI 3 / IARC 3+**.

| Questionnaire area | Expected answer | Why |
|---|---|---|
| App category (questionnaire type) | Utility / productivity / other app | Not a game, not social, not news |
| Violence, blood, fear content | No | None |
| Sexual content / nudity | No | None |
| Profanity or crude humor | No | All strings are neutral product copy |
| Drugs, alcohol, tobacco references | No | None |
| Gambling (real or simulated) | No | None |
| Users can interact or exchange content (chat, UGC sharing) | No | No user-to-user features of any kind |
| App shares user's current location with others | No | No location access at all |
| App allows purchase of digital goods | No | No IAP in v0.1 |
| Contains ads | No | No ads, no ad SDKs |
| Users can share their personal information | No | Nothing leaves the device |
| Miscellaneous (crypto, loot boxes, etc.) | No | None |

Note: if in-app purchases or any sharing feature are ever added, the questionnaire must be
resubmitted before release.

## 6. Screenshots to capture (6 screens)

Capture on the Pixel 10 Pro XL reference device, portrait, light theme (plus optional dark
variants), **using sample-data mode only — never real account nicknames** (PRD §21.3: synthetic
fixtures only in screenshots). Suggested caption overlays in quotes.

1. **Today dashboard (3 accounts)** — cards showing remaining %, freshness ("Updated 2h ago"),
   source, and reset countdown; Suggested-next banner visible. "All your AI allowances, one calm
   screen."
2. **Unknown-limit card state** — a card led by the reset countdown with "Remaining usage not
   available" secondary line. "Honest when it doesn't know."
3. **Update usage — paste-assist entry** — pasted provider text with suggested field matches
   awaiting user review. "Paste it. Review it. Done."
4. **Account detail** — "Update usage" primary action on top, independent usage windows,
   collapsed audit details. "Every number shows its source and age."
5. **Reminder editor with notification preview** — timing → evidence policy → unknown-remaining
   controls, live preview of the exact notification text, inexact-delivery helper line visible.
   "Reminders that say exactly what they know."
6. **History / audit trail** — plain-language events ("Reminder suppressed: data older than your
   freshness policy"). "See what Demeter did — and why."

Also needed for the listing (not part of the 6): 512×512 app icon, 1024×500 feature graphic
(no provider logos or marks), and the privacy-policy URL.
