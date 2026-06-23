package com.niko.liberomail.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.niko.liberomail.InboxActivity
import com.niko.liberomail.R
import com.niko.liberomail.data.EmailMessage

object NotificationHelper {

    private const val CHANNEL_ID = "nuove_email"
    private const val CHANNEL_NAME = "Nuove email"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avvisi di nuovi messaggi nella casella Libero"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    fun notifyNewEmail(context: Context, message: EmailMessage) {
        ensureChannel(context)

        val intent = Intent(context, InboxActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context,
            message.uid.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mail)
            .setContentTitle(message.from)
            .setContentText(message.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.subject))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(message.uid.toInt(), notification)
        } catch (_: SecurityException) {
            // permesso notifiche non concesso: ignora silenziosamente
        }
    }
}
