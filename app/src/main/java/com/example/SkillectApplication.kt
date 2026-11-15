package com.example

import android.app.Application
import com.example.core.media.MediaUploadManager
import com.example.core.network.SessionStore
import com.example.core.network.SupabaseAuthApi
import com.example.core.network.SupabaseClientFactory
import com.example.core.network.SupabasePostgrestApi
import com.example.core.network.SupabaseRealtimeClient
import com.example.core.network.SupabaseStorageApi
import com.example.core.notifications.AnalyticsTracker
import com.example.core.notifications.NotificationChannels
import com.example.data.repository.SkillectRepository

class AppContainer(application: Application) {
    val sessionStore = SessionStore(application)
    private val okHttpClient = SupabaseClientFactory.createOkHttpClient(sessionStore)
    private val retrofit = SupabaseClientFactory.createRetrofit(sessionStore)

    val authApi: SupabaseAuthApi = retrofit.create(SupabaseAuthApi::class.java)
    val postgrestApi: SupabasePostgrestApi = retrofit.create(SupabasePostgrestApi::class.java)
    val storageApi: SupabaseStorageApi = retrofit.create(SupabaseStorageApi::class.java)

    val realtimeClient = SupabaseRealtimeClient(okHttpClient, sessionStore)
    val mediaUploadManager = MediaUploadManager(application, storageApi, postgrestApi)
    val analyticsTracker = AnalyticsTracker(application)

    val repository = SkillectRepository(
        sessionStore = sessionStore,
        authApi = authApi,
        postgrestApi = postgrestApi,
        storageApi = storageApi,
        realtimeClient = realtimeClient,
        analyticsTracker = analyticsTracker,
    )
}

class SkillectApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        container = AppContainer(this)
    }
}
