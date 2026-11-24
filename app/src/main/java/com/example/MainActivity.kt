package com.example

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.SkillectApp

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The user's choice is retained by Android; Settings remains available for changes. */ }

    private fun requestNotificationPermissionOnFirstLaunch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return

        val preferences = getSharedPreferences("skillect_notification_permissions", MODE_PRIVATE)
        if (preferences.getBoolean("first_prompt_attempted", false)) return

        // Wait until the first screen has been attached before opening the system permission dialog.
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@post

            // Avoid showing the startup prompt repeatedly after a denial or Activity recreation.
            preferences.edit().putBoolean("first_prompt_attempted", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
        requestNotificationPermissionOnFirstLaunch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUriState = intent.data?.toString()
        readNotificationDeepLink(intent)
    }
}
