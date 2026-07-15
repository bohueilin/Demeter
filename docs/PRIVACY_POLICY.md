# Privacy Policy — Demeter (AI Usage Monitor)

**Effective date:** July 14, 2026
**Applies to:** Demeter — AI Usage Monitor, Android app version 0.1 (package `com.demeter.app`)

Demeter is a local-only Android app that helps you keep track of the AI usage allowances you enter for your ChatGPT/OpenAI and Claude/Anthropic accounts, and reminds you before those allowances reset. This policy explains, in plain language, what data the app handles and what it never does.

The short version: **everything you put into Demeter stays on your device. The app makes no network connections of any kind.**

## 1. What data the app stores

Demeter stores only the information you type or paste into it yourself:

- **Usage evidence you enter.** Usage percentages, remaining amounts, reset times, and text you paste from a provider's usage page. Demeter never connects to any provider to fetch this — you supply it manually.
- **Account nicknames.** Labels you choose to tell your monitored accounts apart (for example, "Work ChatGPT"). These are just names you pick; they do not need to be — and should not be — real email addresses or credentials.
- **Reminder rules and settings.** Your thresholds, quiet hours, snooze choices, and notification preferences.
- **Audit events.** A local log the app keeps so it can show you why a reminder fired, was suppressed, or was rescheduled.

All of this lives in a local database on your device. It is never transmitted anywhere.

## 2. What the app does NOT collect or do

- **No network transmission.** The app does not request the Internet permission and makes no network calls at all. Nothing you enter ever leaves your device through the app.
- **No accounts or sign-in.** There is nothing to register for and no login.
- **No credentials.** Demeter never asks for, accepts, or stores passwords, API keys, session tokens, or any other provider credentials. This is a hard product rule.
- **No analytics, no crash reporting, no ads.** There are no tracking SDKs, advertising SDKs, or telemetry of any kind.
- **No selling or sharing of data.** We do not sell, share, rent, or disclose your data to anyone. We could not even if we wanted to: the app has no way to send it to us or to any third party.
- **No cloud backup.** Android's automatic backup is disabled for this app (`allowBackup=false`, plus data extraction rules), so your data is not copied into cloud backups.

## 3. Notifications

If you enable reminders, Demeter shows local notifications before an allowance reset. Depending on the detail level you choose, a notification may include an account nickname and the usage figures you entered (for example, a remaining percentage and reset time). You can choose a generic detail level that shows none of this. Notifications use Android's private lock-screen visibility by default, and you can further control what appears on the lock screen through Android's notification settings. Notification content is generated on your device and is never logged or sent anywhere.

## 4. Permissions and why the app asks for them

Demeter requests only two permissions:

- **Notifications (`POST_NOTIFICATIONS`).** Requested only when you turn on reminders, so the app can show them. If you decline, the app still works — you just won't get reminder notifications.
- **Run at boot (`RECEIVE_BOOT_COMPLETED`).** Lets the app reschedule your reminders after the device restarts, since Android clears scheduled alarms on reboot.

The app does not request the Internet permission, location, contacts, camera, microphone, storage, or any other permission.

## 5. Data retention and deletion

- Your data stays on your device for as long as you keep it. There is no server copy — none exists.
- You can delete an individual monitored account (with its usage history, reminders, and audit events) inside the app.
- You can delete **all** app data at any time with the delete-all option in Settings.
- Uninstalling the app removes everything the app stored. Because backups are disabled, no copy survives in cloud backups.

## 6. Children

Demeter is not directed at children under 13, and we do not knowingly collect information from children. (In practice, the app collects no information from anyone — see section 2.)

## 7. Changes to this policy

If a future version of Demeter changes how data is handled — for example, if an optional cloud feature were ever added — this policy will be updated before that version ships, the effective date above will change, and any new data handling will require your explicit opt-in inside the app.

## 8. Contact

Questions about this policy or the app's privacy practices:

**Email:** bohueilin@gmail.com

## 9. Independence

Demeter is an independent app. It is not affiliated with, endorsed by, or sponsored by OpenAI, Anthropic, or Google. Product names are used only to describe compatibility.
