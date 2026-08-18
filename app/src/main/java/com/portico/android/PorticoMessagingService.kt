package com.portico.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// Lint still checks the retired token callback; FCM 25.1 uses onRegistered(FID).
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class PorticoMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        getSharedPreferences(TOKEN_PREFERENCES, MODE_PRIVATE).edit {
            putString(PENDING_TOKEN, installationId)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Portico update"
        val body = message.notification?.body ?: message.data["message"] ?: return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: body.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "portfolio_updates"
        private const val TOKEN_PREFERENCES = "portico_push"
        private const val PENDING_TOKEN = "pending_installation_id"

        fun pendingToken(context: android.content.Context): String? =
            context.getSharedPreferences(TOKEN_PREFERENCES, android.content.Context.MODE_PRIVATE)
                .getString(PENDING_TOKEN, null)

        fun clearPendingToken(context: android.content.Context) {
            context.getSharedPreferences(TOKEN_PREFERENCES, android.content.Context.MODE_PRIVATE).edit {
                remove(PENDING_TOKEN)
            }
        }
    }
}
