package com.portico.android

import android.app.Application
import com.clerk.api.Clerk

/** Initializes Clerk before the first Activity is created. */
class PorticoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BuildConfig.CLERK_PUBLISHABLE_KEY
            .takeIf { it.isNotBlank() }
            ?.let { Clerk.initialize(this, publishableKey = it) }
    }
}
