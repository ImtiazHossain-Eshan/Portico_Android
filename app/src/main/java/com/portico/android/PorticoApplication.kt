package com.portico.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.clerk.api.Clerk

/** Initializes Clerk before the first Activity is created. */
class PorticoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                PorticoMessagingService.CHANNEL_ID,
                "Portfolio updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Property, document, payment and portfolio reminders"
            }
        )
        BuildConfig.CLERK_PUBLISHABLE_KEY
            .takeIf { it.isNotBlank() }
            ?.let { Clerk.initialize(this, publishableKey = it) }
    }
}
