package com.portico.android

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds cannot use Play Integrity: attestation only succeeds for builds
 * Play has seen. The debug provider prints a token to logcat which has to be
 * registered once per machine under App Check -> Apps -> Manage debug tokens.
 */
fun appCheckFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
