package com.example.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.config.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.skillectDataStore by preferencesDataStore(name = "skillect_prefs")

private object SessionTokenCipher {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "skillect_session_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ENC_PREFIX = "enc_v1:"
    private const val IV_SIZE = 12

    private fun getOrCreateKey(): SecretKey {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (error: Exception) {
            throw IllegalStateException("Android Keystore is unavailable; session tokens cannot be stored securely.", error)
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (error: Exception) {
            throw IllegalStateException("Session tokens could not be encrypted; refusing to store them as plain text.", error)
        }
    }

    fun decrypt(stored: String): String {
        if (stored.isBlank()) return ""
        if (!stored.startsWith(ENC_PREFIX)) return stored
        return try {
            val key = getOrCreateKey()
            val combined = Base64.decode(stored.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
            if (combined.size <= IV_SIZE) return ""
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

data class PersistedSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val email: String = "",
) {
    val isAuthenticated: Boolean get() = accessToken.isNotBlank() && userId.isNotBlank()
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val languageCode: String = "en",
    val pushLikes: Boolean = true,
    val pushComments: Boolean = true,
    val pushMessages: Boolean = true,
    val pushCommunities: Boolean = true,
)

class SessionStore(private val context: Context) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val PUSH_LIKES = booleanPreferencesKey("push_likes")
        val PUSH_COMMENTS = booleanPreferencesKey("push_comments")
        val PUSH_MESSAGES = booleanPreferencesKey("push_messages")
        val PUSH_COMMUNITIES = booleanPreferencesKey("push_communities")
    }

    val sessionFlow: Flow<PersistedSession> = context.skillectDataStore.data.map { prefs ->
        PersistedSession(
            accessToken = SessionTokenCipher.decrypt(prefs[Keys.ACCESS_TOKEN].orEmpty()),
            refreshToken = SessionTokenCipher.decrypt(prefs[Keys.REFRESH_TOKEN].orEmpty()),
            userId = prefs[Keys.USER_ID].orEmpty(),
            email = prefs[Keys.EMAIL].orEmpty(),
        )
    }

    val preferencesFlow: Flow<UserPreferences> = context.skillectDataStore.data.map { prefs ->
        val mode = when (prefs[Keys.THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
        UserPreferences(
            themeMode = mode,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            pushLikes = prefs[Keys.PUSH_LIKES] ?: true,
            pushComments = prefs[Keys.PUSH_COMMENTS] ?: true,
            pushMessages = prefs[Keys.PUSH_MESSAGES] ?: true,
            pushCommunities = prefs[Keys.PUSH_COMMUNITIES] ?: true,
        )
    }

    suspend fun currentSession(): PersistedSession = sessionFlow.first()

    suspend fun saveSession(session: PersistedSession) {
        context.skillectDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = SessionTokenCipher.encrypt(session.accessToken)
            prefs[Keys.REFRESH_TOKEN] = SessionTokenCipher.encrypt(session.refreshToken)
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.EMAIL] = session.email
        }
    }

    suspend fun clearSession() {
        context.skillectDataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.EMAIL)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.skillectDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguageCode(code: String) {
        context.skillectDataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun updateNotificationPrefs(
        likes: Boolean,
        comments: Boolean,
        messages: Boolean,
        communities: Boolean,
    ) {
        context.skillectDataStore.edit { prefs ->
            prefs[Keys.PUSH_LIKES] = likes
            prefs[Keys.PUSH_COMMENTS] = comments
            prefs[Keys.PUSH_MESSAGES] = messages
            prefs[Keys.PUSH_COMMUNITIES] = communities
        }
    }
}
