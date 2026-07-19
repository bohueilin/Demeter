package com.demeter.app.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.demeter.app.MainActivity
import com.demeter.app.R
import com.demeter.domain.model.MonitoredAccount
import com.demeter.domain.model.UsageWindow
import com.demeter.domain.reminder.DeliveryDecision
import com.demeter.domain.reminder.ReminderIntent
import java.time.Instant

object NotificationHelper {

    const val CHANNEL_REMINDERS = "usage_reminders"
    const val CHANNEL_SERVICE = "service_alerts"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Usage reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders before an allowance resets"
                // Default: details hidden on the lock screen. Users own this setting
                // after creation; Android never lets us overwrite their choice.
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Service and account alerts",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Connection and app status" },
        )
    }

    fun canPost(context: Context): Boolean {
        val permitted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Reminders never claim exactness and always disclose the observation time of the
     * evidence they are based on. Allowance reminders assert remaining capacity;
     * check-usage reminders only point at the reset and ask for an update.
     */
    fun postReminder(
        context: Context,
        logicalId: String,
        account: MonitoredAccount,
        window: UsageWindow,
        deliver: DeliveryDecision.Deliver,
        resetAt: Instant,
    ) {
        val resetPhrase = TimeFormat.untilPhrase(resetAt)
        val asOfPhrase = TimeFormat.agoPhrase(deliver.asOf)
        val (title, body) = when (deliver.intent) {
            ReminderIntent.ALLOWANCE ->
                "${account.nickname}: ${deliver.remainingPercent}% left · resets $resetPhrase" to
                    "${window.label} (${account.provider.displayLabel}). Based on your update $asOfPhrase. Use it before it resets."

            ReminderIntent.CHECK_USAGE ->
                if (deliver.remainingPercent != null) {
                    "${account.nickname}: ${window.label} resets $resetPhrase" to
                        "You had ${deliver.remainingPercent}% left as of $asOfPhrase. Open Demeter to update what remains."
                } else {
                    "${account.nickname}: ${window.label} resets $resetPhrase" to
                        "Remaining usage is not available. Check your ${account.provider.displayLabel} usage and update Demeter."
                }
        }
        post(context, logicalId, title, body, account.id)
    }

    fun postTest(context: Context) {
        post(
            context,
            "demeter:test",
            "Test reminder from Demeter",
            "This is what a reminder looks like. Delivery timing is best-effort — Android may delay it slightly.",
            accountId = null,
        )
    }

    private fun post(context: Context, tag: String, title: String, body: String, accountId: String?) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (accountId != null) putExtra("accountId", accountId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Lock-screen privacy: the full content (nickname, percentages) is private;
        // a locked device shows only a generic line. Users can relax this per-channel
        // in Android settings, which we respect and never override.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Demeter reminder")
            .setContentText("Unlock to view details.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)

        // Optional "Email" action — hands this reminder to the user's mail app, pre-filled.
        // Tapping a notification action is user interaction, so opening the composer is allowed;
        // a background alarm could never do it on its own, and Demeter never sends mail itself.
        val address = EmailComposer.addressOf(context)
        if (EmailComposer.looksValid(address)) {
            val mailIntent = EmailComposer.intentFor(
                to = address,
                subject = "Demeter: $title",
                body = "$body\n\nSent from Demeter. Figures are your own recorded evidence, not provider-authoritative numbers.",
            )
            val mailPending = PendingIntent.getActivity(
                context,
                "$tag:mail".hashCode(),
                mailIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.ic_notification, "Email", mailPending)
        }

        // Stable tag + id: a retry after a crash updates the same visible notification
        // instead of creating a duplicate.
        NotificationManagerCompat.from(context).notify(tag, NOTIFICATION_ID, builder.build())
    }
}
