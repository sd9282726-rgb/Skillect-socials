package com.example.core.notifications

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

class AnalyticsTracker(private val context: Context) {
    val isFirebaseInitialized: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    fun setUserId(supabaseUserId: String?) {
        if (!isFirebaseInitialized) return
        try {
            FirebaseAnalytics.getInstance(context).setUserId(supabaseUserId)
        } catch (_: Exception) {
        }
        try {
            FirebaseCrashlytics.getInstance().setUserId(supabaseUserId.orEmpty())
        } catch (_: Exception) {
        }
    }

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        if (!isFirebaseInitialized) return
        try {
            val bundle = Bundle().apply {
                params.forEach { (k, v) -> putString(k, v) }
            }
            FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
        } catch (_: Exception) {
        }
    }

    fun recordNonFatal(throwable: Throwable, contextMessage: String? = null) {
        if (!isFirebaseInitialized) return
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (!contextMessage.isNullOrBlank()) {
                crashlytics.log(contextMessage)
            }
            crashlytics.recordException(throwable)
        } catch (_: Exception) {
        }
    }

    fun startTrace(name: String): Trace? {
        if (!isFirebaseInitialized) return null
        return try {
            FirebasePerformance.getInstance().newTrace(name).apply { start() }
        } catch (_: Exception) {
            null
        }
    }

    fun stopTrace(trace: Trace?) {
        if (trace == null) return
        try {
            trace.stop()
        } catch (_: Exception) {
        }
    }

    fun fetchFcmToken(onToken: (String) -> Unit) {
        if (!isFirebaseInitialized) return
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) onToken(token)
            }
        } catch (_: Exception) {
        }
    }
}
