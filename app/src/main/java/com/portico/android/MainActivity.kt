package com.portico.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
import com.portico.android.ui.PaymentReturns
import com.portico.android.ui.PorticoApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleClerkCallback(intent)
        enableEdgeToEdge()
        setContent { PorticoApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleClerkCallback(intent)
    }

    /*
     * Two different things arrive as deep links: Clerk's OAuth callback and the
     * payment gateway's return hop. The payment one is claimed first, because
     * handing a portico://payment URI to Clerk would have it try to resolve a
     * sign-in that is not happening.
     */
    private fun handleClerkCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (PaymentReturns.accept(uri)) return
        lifecycleScope.launch { Clerk.auth.handle(uri) }
    }
}
