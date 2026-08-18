package com.portico.android.data

import android.content.Context
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.session.fetchToken
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.portico.android.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface FirebaseConnection {
    data object Connected : FirebaseConnection
    data class Unavailable(val reason: String) : FirebaseConnection
}

/**
 * Connects Clerk's authenticated Android session to Firebase Authentication.
 * The server verifies the Clerk JWT before minting a Firebase custom token;
 * no Clerk secret or Firebase service-account credential is shipped in the APK.
 */
object FirebaseBackend {
    private val json = Json { ignoreUnknownKeys = true }
    private val workspaceRepository by lazy { FirestoreWorkspaceRepository() }

    fun isConfigured(context: Context): Boolean =
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    suspend fun connect(context: Context): FirebaseConnection {
        if (!isConfigured(context)) {
            return FirebaseConnection.Unavailable("Firebase Android configuration is missing")
        }

        val bridgeUrl = BuildConfig.FIREBASE_TOKEN_BRIDGE_URL
        if (bridgeUrl.isBlank()) {
            return FirebaseConnection.Unavailable("Firebase token bridge URL is missing")
        }

        val session = Clerk.session
            ?: return FirebaseConnection.Unavailable("No active Clerk session")
        val clerkUserId = Clerk.user?.id
            ?: return FirebaseConnection.Unavailable("No active Clerk user")

        if (FirebaseAuth.getInstance().currentUser?.uid == clerkUserId) {
            return FirebaseConnection.Connected
        }

        val clerkJwt = when (val result = session.fetchToken()) {
            is ClerkResult.Success -> result.value.jwt
            is ClerkResult.Failure<*> -> null
        } ?: return FirebaseConnection.Unavailable("Clerk session token could not be issued")

        return runCatching {
            val firebaseToken = exchangeToken(bridgeUrl, clerkJwt)
            FirebaseAuth.getInstance().signInWithCustomToken(firebaseToken).await()
            FirebaseConnection.Connected
        }.getOrElse { error ->
            FirebaseConnection.Unavailable(error.message ?: "Firebase authentication failed")
        }
    }

    fun disconnect() {
        if (BuildConfig.FIREBASE_CONFIGURED) {
            runCatching { FirebaseAuth.getInstance().signOut() }
        }
    }

    internal suspend fun sessionToken(): String {
        val session = Clerk.session ?: error("No active Clerk session")
        return when (val result = session.fetchToken()) {
            is ClerkResult.Success -> result.value.jwt
            is ClerkResult.Failure<*> -> null
        } ?: error("Clerk session token could not be issued")
    }

    suspend fun loadWorkspace(userId: String): CloudWorkspace? {
        check(FirebaseAuth.getInstance().currentUser?.uid == userId) {
            "Firebase user does not match the active Portico account"
        }
        return workspaceRepository.load(userId)
    }

    suspend fun saveWorkspace(
        userId: String,
        previous: PorticoSnapshot?,
        current: PorticoSnapshot
    ): String {
        check(FirebaseAuth.getInstance().currentUser?.uid == userId) {
            "Firebase user does not match the active Portico account"
        }
        return workspaceRepository.save(userId, previous, current)
    }

    fun observeWorkspace(
        userId: String,
        onRevision: (revision: String, updatedAtEpochMillis: Long) -> Unit,
        onError: (Throwable) -> Unit = {}
    ): ListenerRegistration {
        check(FirebaseAuth.getInstance().currentUser?.uid == userId) {
            "Firebase user does not match the active Portico account"
        }
        return workspaceRepository.observeRevision(userId, onRevision, onError)
    }

    private suspend fun exchangeToken(url: String, clerkJwt: String): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $clerkJwt")
                setRequestProperty("Accept", "application/json")
            }

            try {
                connection.outputStream.use { it.write(ByteArray(0)) }
                val body = (if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (connection.responseCode !in 200..299) {
                    error("Firebase identity exchange was rejected")
                }

                json.parseToJsonElement(body)
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content
                    ?.takeIf(String::isNotBlank)
                    ?: error("Firebase identity exchange returned no token")
            } finally {
                connection.disconnect()
            }
        }
}

internal suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase task failed")
            )
        }
    }
}
