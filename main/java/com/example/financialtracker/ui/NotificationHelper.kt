// NotificationHelper.kt
package com.example.financialtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.financialtracker.ui.MainActivity

object NotificationHelper {

    private const val CHANNEL_ID = "upcoming_bills_channel"
    private const val CHANNEL_NAME = "Upcoming Bills"
    private const val CHANNEL_DESC = "Notifications for upcoming recurring bills and expenses"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        content: String,
        notificationId: Int,
        targetDateMillis: Long // <-- ADD THIS
    ) {
        // --- START: Create PendingIntent ---
        // Create an intent to launch MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add the date as an "extra" to the intent
            putExtra("TARGET_DATE_MILLIS", targetDateMillis)
            // Make the action unique to ensure the extra is updated
            action = "SHOW_DATE_${targetDateMillis}_${notificationId}"
        }

        // Wrap the intent in a PendingIntent
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId, // Use the unique notificationId as the request code
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // --- END: Create PendingIntent ---

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your app's icon
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // <-- ADD THIS

        try {
            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification
                notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            // This can happen if permission is revoked
            Log.e("NotificationHelper", "Failed to show notification due to SecurityException", e)
        }
    }
}