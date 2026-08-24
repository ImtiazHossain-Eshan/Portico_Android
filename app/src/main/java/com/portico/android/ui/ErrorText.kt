package com.portico.android.ui

import android.content.Context
import com.portico.android.R
import com.portico.android.data.PorticoBackendException
import java.io.IOException

/**
 * Turns a thrown failure into something a member can act on.
 *
 * Internal strings leak easily. "No active Clerk session" is a correct sentence
 * for a stack trace and a useless one on screen: it names a library the member
 * has never heard of and does not say what to do. Anything shown to a person has
 * to say what happened and what fixes it.
 *
 * Messages the server wrote are passed through unchanged. Those are already
 * written for a member, and rewording them here would mean maintaining the same
 * sentence in two places and letting them drift.
 */
fun humanError(context: Context, error: Throwable): String {
    if (error is PorticoBackendException) {
        return when (error.code) {
            "missing_session_token", "invalid_session_token", "invalid_user_identity" ->
                context.getString(R.string.your_session_has_expired_sign_in_again)
            "insufficient_role", "role_exceeds_your_own", "target_outranks_you" ->
                error.message.ifBlank { context.getString(R.string.your_role_does_not_allow_that) }
            /*
             * The one code whose message is not written for a member. Its text
             * names the field that failed by its path on the wire, so
             * "property.currency is invalid" reached the screen of somebody who
             * had simply left the currency at its default. The path is worth
             * keeping in a log and worth hiding from a person, so the message is
             * replaced rather than passed through.
             */
            "invalid_request" ->
                context.getString(R.string.some_details_could_not_be_saved_check_and_retry)
            else -> error.message.ifBlank { context.getString(R.string.that_change_could_not_be_saved) }
        }
    }

    val raw = error.message.orEmpty()
    return when {
        // Thrown before a request is even attempted, so no status code exists.
        raw.contains("Clerk session", ignoreCase = true) ->
            context.getString(R.string.your_session_has_expired_sign_in_again)
        raw.contains("backend URL is missing", ignoreCase = true) ->
            context.getString(R.string.this_build_has_no_server_configured)
        error is IOException || raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("timeout", ignoreCase = true) ->
            context.getString(R.string.cannot_reach_portico_check_your_connection)
        raw.isNotBlank() -> raw
        else -> context.getString(R.string.that_change_could_not_be_saved)
    }
}
