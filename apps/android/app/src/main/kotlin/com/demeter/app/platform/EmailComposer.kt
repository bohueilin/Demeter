package com.demeter.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a pre-filled message to whatever mail app the user has installed, via a `mailto:` intent.
 *
 * The mail app does the sending — Demeter never opens a socket. That is why email reminders need
 * no INTERNET permission and no access to the user's mail account or password. The trade-off is
 * honest and unavoidable: the user still taps Send. Silently sending mail would require either a
 * server or Gmail API credentials, both of which would break the app's no-network guarantee.
 */
object EmailComposer {

    /** SharedPreferences key shared with the ViewModel/Settings screen. */
    const val PREF_EMAIL = "reminder_email"

    fun addressOf(context: Context): String =
        context.getSharedPreferences("demeter", Context.MODE_PRIVATE)
            .getString(PREF_EMAIL, "")
            .orEmpty()
            .trim()

    /** ACTION_SENDTO + mailto: so only email apps can handle it (never a browser or chat app). */
    fun intentFor(to: String, subject: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

    fun compose(context: Context, to: String, subject: String, body: String) {
        val intent = intentFor(to, subject, body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Very small sanity check — enough to hide the action when the field is blank or clearly wrong. */
    fun looksValid(address: String): Boolean =
        address.isNotBlank() && address.contains("@") && address.substringAfter("@").contains(".")
}
