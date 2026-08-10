package com.portico.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
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

    private fun handleClerkCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            lifecycleScope.launch { Clerk.auth.handle(uri) }
        }
    }
}
