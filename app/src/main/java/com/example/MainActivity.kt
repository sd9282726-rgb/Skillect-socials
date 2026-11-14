package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.SkillectApp

class MainActivity : ComponentActivity() {
    private var deepLinkUriState by mutableStateOf<String?>(null)
    private var deepLinkTypeState by mutableStateOf<String?>(null)
    private var deepLinkEntityIdState by mutableStateOf<String?>(null)

    private fun readNotificationDeepLink(intent: Intent?) {
        val incomingType = intent?.getStringExtra("deep_link_type")
            ?: intent?.getStringExtra("type")
        deepLinkTypeState = if (incomingType == "new_message") "message" else incomingType
        deepLinkEntityIdState = intent?.getStringExtra("deep_link_entity_id")
            ?: intent?.getStringExtra("entity_id")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SkillectApplication
        readNotificationDeepLink(intent)
        deepLinkUriState = intent?.data?.toString()

        setContent {
            SkillectApp(
                container = app.container,
                deepLinkType = deepLinkTypeState,
                deepLinkEntityId = deepLinkEntityIdState,
                onNotificationDeepLinkConsumed = {
                    deepLinkTypeState = null
                    deepLinkEntityIdState = null
                },
                authDeepLinkUri = deepLinkUriState,
                onAuthDeepLinkConsumed = { deepLinkUriState = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUriState = intent.data?.toString()
        readNotificationDeepLink(intent)
    }
}
