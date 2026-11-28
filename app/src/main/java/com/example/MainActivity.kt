package com.example

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.SkillectApp

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PROMPT_COMPLETED, true).apply()
    }

    private fun requestNotificationAccessOnFirstLaunch() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(PROMPT_COMPLETED, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean(PROMPT_COMPLETED, true).apply()
            return
        }
        window.decorView.post {
            if (isFinishing || isDestroyed || prefs.getBoolean(PROMPT_COMPLETED, false)) return@post
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Android 10–12 have no POST_NOTIFICATIONS runtime permission.
                // Explain the difference and let the user open the system's app notification settings.
                AlertDialog.Builder(this)
                    .setTitle("Stay connected on Skillect")
                    .setMessage("Receive notifications for messages, replies, and important account updates. On this Android version, notification access is managed in system settings rather than a permission pop-up.")
                    .setPositiveButton("Open notification settings") { _, _ ->
                        prefs.edit().putBoolean(PROMPT_COMPLETED, true).apply()
                        val settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))
                        }
                        try { startActivity(settingsIntent) } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")))
                        }
                    }
                    .setNegativeButton("Not now") { _, _ ->
                        prefs.edit().putBoolean(PROMPT_COMPLETED, true).apply()
                    }
                    .setOnCancelListener { prefs.edit().putBoolean(PROMPT_COMPLETED, true).apply() }
                    .show()
            }
        }
    }

    private var deepLinkUriState by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var deepLinkTypeState by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var deepLinkEntityIdState by androidx.compose.runtime.mutableStateOf<String?>(null)

    private fun readNotificationDeepLink(intent: Intent?) {
        val incomingType = intent?.getStringExtra("deep_link_type") ?: intent?.getStringExtra("type")
        deepLinkTypeState = if (incomingType == "new_message") "message" else incomingType
        deepLinkEntityIdState = intent?.getStringExtra("deep_link_entity_id") ?: intent?.getStringExtra("entity_id")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
                onNotificationDeepLinkConsumed = { deepLinkTypeState = null; deepLinkEntityIdState = null },
                authDeepLinkUri = deepLinkUriState,
                onAuthDeepLinkConsumed = { deepLinkUriState = null },
            )
        }
        requestNotificationAccessOnFirstLaunch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUriState = intent.data?.toString()
        readNotificationDeepLink(intent)
    }

    private companion object {
        const val PREFS = "skillect_notification_permissions"
        const val PROMPT_COMPLETED = "notification_access_prompt_completed"
    }
}
