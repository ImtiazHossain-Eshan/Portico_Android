package com.portico.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.clerk.api.Clerk
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Initializes Clerk and the Firebase guardrails before the first Activity. */
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

        if (BuildConfig.FIREBASE_CONFIGURED) startFirebaseGuardrails()
    }

    /*
     * App Check attests that traffic reaching Firestore and the bridge came from
     * a genuine build of this app. Without it the rules still isolate one user
     * from another, but anyone holding the API key -- which ships inside every
     * APK and is not a secret -- can talk to the backend directly.
     *
     * Play Integrity is the real attestation and only works for builds Play has
     * seen. Debug builds use the debug provider instead, which prints a token to
     * logcat that has to be registered in the Firebase Console once per machine.
     */
    private fun startFirebaseGuardrails() {
        runCatching {
            FirebaseApp.initializeApp(this)
            // appCheckFactory() is variant-specific: Play Integrity in release,
            // the debug provider in debug. See src/{debug,release}/AppCheckFactory.kt.
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory())
            // Crash reports carry no portfolio data: figures, addresses and
            // document names are the user's own and never leave the device
            // through this channel.
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }
    }
}
