package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.config.AppConfig
import com.example.core.network.AuthTokenResponse
import com.example.core.network.AuthUserDto
import com.example.core.network.CreatePostRequest
import com.example.core.network.PasswordSignInRequest
import com.example.core.network.PersistedSession
import com.example.core.network.RecoverPasswordRequest
import com.example.core.network.SessionStore
import com.example.core.network.SessionTokenCipher
import com.example.core.network.SignUpRequest
import com.example.core.network.SupabaseAuthApi
import com.example.core.network.SupabaseClientFactory
import com.example.core.network.SupabasePostgrestApi
import com.example.core.network.SupabaseRealtimeClient
import com.example.core.network.UpdatePasswordRequest
import com.example.core.notifications.AnalyticsTracker
import com.example.data.repository.AuthenticationRequiredException
import com.example.data.repository.SkillectRepository
import com.example.ui.auth.AuthDeepLinkParser
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.Locale


private object TestSessionTokenCipher : SessionTokenCipher {
    private const val PREFIX = "test_enc_v1:"

    override fun encrypt(plainText: String): String =
        PREFIX + java.util.Base64.getEncoder().encodeToString(plainText.toByteArray(Charsets.UTF_8))

    override fun decrypt(stored: String): String =
        if (stored.startsWith(PREFIX)) {
            String(java.util.Base64.getDecoder().decode(stored.removePrefix(PREFIX)), Charsets.UTF_8)
        } else {
            stored
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SkillectUnitAndRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `age eligibility enforces 16 plus requirement globally`() {
        assertTrue(AppConfig.isEligibleAge16Plus("2000-01-15"))
        assertTrue(AppConfig.isMinor16To17("2009-12-01"))
        assertFalse(AppConfig.isEligibleAge16Plus("2015-06-01"))
        assertFalse(AppConfig.isEligibleAge16Plus("invalid-date"))
    }

    @Test
    fun `international formatting supports compact counts and global countries`() {
        assertEquals("950", AppConfig.formatCompactCount(950, Locale.US))
        assertEquals("12.5K", AppConfig.formatCompactCount(12500, Locale.US))
        assertEquals("1.2M", AppConfig.formatCompactCount(1200000, Locale.US))
        assertTrue(AppConfig.supportedCountries.any { it.isoCode == "JP" && it.dialCode == "+81" })
        assertTrue(AppConfig.supportedCountries.any { it.isoCode == "NG" && it.dialCode == "+234" })
    }

    @Test
    fun `app opens immediately in Guest Mode and allows browsing without sign-in`() {
        composeRule.waitForIdle()
        // Home feed and guest banner visible immediately without login gate
        composeRule.onNodeWithTag("nav_feed").assertIsDisplayed()
        composeRule.onNodeWithTag("guest_mode_banner").assertIsDisplayed()

        // Navigate to Explore as guest
        composeRule.onNodeWithTag("nav_explore").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("explore_search_input").assertIsDisplayed()

        // Navigate to Notifications as guest -> gated nicely
        composeRule.onNodeWithTag("nav_feed").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_notifications_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guest_notifications_gate").assertIsDisplayed()
    }

    @Test
    fun `guest profile tab displays create account and sign in options and preserves browsing`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("guest_profile_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("guest_create_account_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("guest_sign_in_button").performScrollTo().assertIsDisplayed()

        // Navigate back to Feed seamlessly
        composeRule.onNodeWithTag("nav_feed").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guest_mode_banner").assertIsDisplayed()
    }

    @Test
    fun `guest restricted actions prompt sign-in and support join 16 plus and forgot password navigation`() {
        composeRule.waitForIdle()

        // Tapping Create tab as guest prompts auth sheet with explanation
        composeRule.onNodeWithTag("nav_create").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guest_restriction_prompt").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_dob_input").performScrollTo().assertIsDisplayed()

        // Switch to Sign In and verify Forgot Password button navigates to Recover mode
        composeRule.onNodeWithText("Sign In").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("forgot_password_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Send Reset Email").performScrollTo().assertIsDisplayed()

        // Dismiss back to guest browsing
        composeRule.onNodeWithTag("explore_workspace_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guest_mode_banner").assertIsDisplayed()

        // Messages tab also gates guest access cleanly
        composeRule.onNodeWithTag("nav_messages").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guest_messages_gate").assertIsDisplayed()
    }

    @Test
    fun `recovery deep link parser handles valid token, expired link error, and foreign uris`() {
        val valid = AuthDeepLinkParser.parse("skillect://auth-callback#access_token=tok_recovery_123&type=recovery")
        assertNotNull(valid)
        assertEquals("tok_recovery_123", valid?.accessToken)
        assertEquals("recovery", valid?.type)
        assertNull(valid?.error)

        val expired = AuthDeepLinkParser.parse("skillect://auth-callback?error=access_denied&error_description=Email+link+is+invalid+or+has+expired")
        assertNotNull(expired)
        assertEquals("access_denied", expired?.error)
        assertEquals("Email link is invalid or has expired", expired?.errorDescription)

        assertNull(AuthDeepLinkParser.parse("https://example.com/other"))
    }

    @Test
    fun `session store persists and clears user session cleanly across restarts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SessionStore(context, TestSessionTokenCipher)
        store.clearSession()
        assertFalse(store.currentSession().isAuthenticated)

        store.saveSession(
            PersistedSession(
                accessToken = "jwt_access_123",
                refreshToken = "refresh_456",
                userId = "user-uuid-1",
                email = "creator@skillect.global",
            ),
        )
        val restored = store.currentSession()
        assertTrue(restored.isAuthenticated)
        assertEquals("user-uuid-1", restored.userId)
        assertEquals("creator@skillect.global", restored.email)

        store.clearSession()
        assertFalse(store.currentSession().isAuthenticated)
    }

    @Test
    fun `repository enforces guest read-only restrictions and maps auth and recovery errors`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SessionStore(context)
        store.clearSession()

        var failWithDuplicate = false
        var failWithNetwork = false
        var lastRecoverRedirect: String? = null
        var updatedPasswordHeader: String? = null

        val fakeAuthApi = object : SupabaseAuthApi {
            override suspend fun signUp(request: SignUpRequest): Response<AuthTokenResponse> {
                if (failWithDuplicate) {
                    return Response.error(
                        422,
                        """{"msg":"User already registered","error_code":"user_already_exists"}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                }
                return Response.success(
                    AuthTokenResponse(
                        accessToken = "direct_session_token",
                        refreshToken = "direct_refresh_token",
                        user = AuthUserDto(id = "u_new_1", email = request.email),
                    ),
                )
            }

            override suspend fun signInWithPassword(request: PasswordSignInRequest): Response<AuthTokenResponse> {
                if (failWithNetwork) throw IOException("Timed out")
                return if (request.password == "CorrectPass123") {
                    Response.success(
                        AuthTokenResponse(
                            accessToken = "signed_in_token",
                            refreshToken = "signed_in_refresh",
                            user = AuthUserDto(id = "u_1", email = request.email),
                        ),
                    )
                } else {
                    Response.error(
                        400,
                        """{"error_description":"Invalid login credentials"}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                }
            }

            override suspend fun refreshToken(request: com.example.core.network.RefreshTokenRequest) =
                Response.success(AuthTokenResponse(accessToken = "refreshed_tok", refreshToken = "new_ref"))

            override suspend fun recoverPassword(request: RecoverPasswordRequest): Response<okhttp3.ResponseBody> {
                lastRecoverRedirect = request.redirectTo
                return Response.success("{}".toResponseBody("application/json".toMediaType()))
            }

            override suspend fun signOut(): Response<okhttp3.ResponseBody> =
                Response.success("{}".toResponseBody("application/json".toMediaType()))

            override suspend fun getCurrentUser(): Response<AuthUserDto> =
                Response.success(AuthUserDto(id = "u_1", email = "creator@skillect.global"))

            override suspend fun updateUserPassword(
                bearerToken: String,
                request: UpdatePasswordRequest,
            ): Response<AuthUserDto> {
                updatedPasswordHeader = bearerToken
                return Response.success(AuthUserDto(id = "u_1", email = "creator@skillect.global"))
            }
        }

        val postgrestStub = Proxy.newProxyInstance(
            SupabasePostgrestApi::class.java.classLoader,
            arrayOf(SupabasePostgrestApi::class.java),
        ) { _, _, _ ->
            Response.success(emptyList<Any>())
        } as SupabasePostgrestApi

        val repo = SkillectRepository(
            sessionStore = store,
            authApi = fakeAuthApi,
            postgrestApi = postgrestStub,
            realtimeClient = SupabaseRealtimeClient(SupabaseClientFactory.createOkHttpClient(store), store),
            analyticsTracker = AnalyticsTracker(context),
            isConfiguredCheck = { true },
        )

        // 1. Guest mutations & private reads are rejected immediately with AuthenticationRequiredException
        assertTrue(repo.createPost(CreatePostRequest("", "text", "hello")).exceptionOrNull() is AuthenticationRequiredException, "guest createPost must require authentication")
        assertTrue(repo.toggleLike("post-1").exceptionOrNull() is AuthenticationRequiredException, "guest toggleLike must require authentication")
        assertTrue(repo.addComment("post-1", "nice").exceptionOrNull() is AuthenticationRequiredException, "guest addComment must require authentication")
        assertTrue(repo.toggleFollow("user-2").exceptionOrNull() is AuthenticationRequiredException, "guest toggleFollow must require authentication")
        assertTrue(repo.toggleSavePost("post-1", false).exceptionOrNull() is AuthenticationRequiredException, "guest toggleSavePost must require authentication")
        assertTrue(repo.fetchConversations().exceptionOrNull() is AuthenticationRequiredException, "guest fetchConversations must require authentication")
        assertTrue(repo.sendMessage("conv-1", "hi").exceptionOrNull() is AuthenticationRequiredException, "guest sendMessage must require authentication")
        assertTrue(repo.fetchNotifications().exceptionOrNull() is AuthenticationRequiredException, "guest fetchNotifications must require authentication")
        assertTrue(repo.joinCommunity("comm-1").exceptionOrNull() is AuthenticationRequiredException, "guest joinCommunity must require authentication")

        // 2. Sign up enters immediately when email confirmation is disabled
        val signUpOk = repo.signUp("new@skillect.global", "StrongPass123", "2002-04-10", "US")
        assertTrue(signUpOk.isSuccess)
        assertTrue(signUpOk.getOrThrow().isAuthenticated)
        assertTrue(store.currentSession().isAuthenticated)

        // 3. Sign out returns cleanly to guest mode
        repo.signOut()
        assertFalse(store.currentSession().isAuthenticated)

        // 4. Duplicate signup error mapped cleanly
        failWithDuplicate = true
        val dupErr = repo.signUp("dup@skillect.global", "StrongPass123", "2002-04-10", "US").exceptionOrNull()?.message.orEmpty()
        assertTrue(dupErr.contains("already exists", ignoreCase = true))

        // 5. Invalid credentials & network errors mapped cleanly
        val badCreds = repo.signIn("u@skillect.global", "WrongPass99").exceptionOrNull()?.message.orEmpty()
        assertTrue(badCreds.contains("Incorrect email or password", ignoreCase = true))

        failWithNetwork = true
        val netErr = repo.signIn("u@skillect.global", "CorrectPass123").exceptionOrNull()?.message.orEmpty()
        assertTrue(netErr.contains("Network connection error", ignoreCase = true))
        failWithNetwork = false

        // 6. Password recovery & reset flow
        assertTrue(repo.recoverPassword("u@skillect.global").isSuccess)
        assertEquals("skillect://auth-callback", lastRecoverRedirect)
        assertTrue(repo.completePasswordRecovery("", "NewPass1234").isFailure)
        assertTrue(repo.completePasswordRecovery("tok_rec_999", "NewPass1234").isSuccess)
        assertEquals("Bearer tok_rec_999", updatedPasswordHeader)

        // 7. Repository validates poll option count, post length, and safe URLs before hitting network
        store.saveSession(PersistedSession("tok_abc", "ref_abc", "u_1", "u@skillect.global"))
        val badPoll = repo.createPost(CreatePostRequest("u_1", "poll", "Pick one"), listOf("Only one"))
        assertTrue(badPoll.isFailure)
        assertTrue(badPoll.exceptionOrNull()?.message?.contains("2 options") == true)

        val badUrl = repo.createPost(CreatePostRequest("u_1", "link", "Check", linkUrl = "javascript:alert(1)"))
        assertTrue(badUrl.isFailure)
        assertTrue(badUrl.exceptionOrNull()?.message?.contains("http") == true)

        val tooLong = repo.createPost(CreatePostRequest("u_1", "text", "a".repeat(AppConfig.MAX_POST_LENGTH + 1)))
        assertTrue(tooLong.isFailure)
    }

    @Test
    fun `updateProfileFields strips privileged columns and OkHttp injects session Bearer token`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SessionStore(context)
        store.saveSession(PersistedSession("secret_jwt_token_999", "ref_1", "u_1", "u@skillect.global"))

        var capturedAuthHeader: String? = null
        val client = SupabaseClientFactory.createOkHttpClient(store)
            .newBuilder()
            .addInterceptor { chain ->
                capturedAuthHeader = chain.request().header("Authorization")
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://mock.supabase.local/")
            .client(client)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(SupabaseClientFactory.moshi))
            .build()
        val api = retrofit.create(SupabasePostgrestApi::class.java)
        val resp = api.getProfiles(limit = 1)
        assertTrue(resp.isSuccessful)
        assertEquals("Bearer secret_jwt_token_999", capturedAuthHeader)

        var patchedBody: Map<String, Any?>? = null
        val interceptingPostgrest = Proxy.newProxyInstance(
            SupabasePostgrestApi::class.java.classLoader,
            arrayOf(SupabasePostgrestApi::class.java),
        ) { _, method, args ->
            if (method.name == "updateProfile") {
                @Suppress("UNCHECKED_CAST")
                patchedBody = args?.get(1) as? Map<String, Any?>
                Response.success(
                    listOf(
                        com.example.core.network.ProfileDto(
                            id = "u_1",
                            username = "creator",
                            displayName = "Creator",
                        ),
                    ),
                )
            } else {
                Response.success(emptyList<Any>())
            }
        } as SupabasePostgrestApi

        val authStub = Proxy.newProxyInstance(
            SupabaseAuthApi::class.java.classLoader,
            arrayOf(SupabaseAuthApi::class.java),
        ) { _, _, _ -> Response.success(AuthUserDto(id = "u_1")) } as SupabaseAuthApi

        val repo = SkillectRepository(
            sessionStore = store,
            authApi = authStub,
            postgrestApi = interceptingPostgrest,
            realtimeClient = SupabaseRealtimeClient(client, store),
            analyticsTracker = AnalyticsTracker(context),
            isConfiguredCheck = { true },
        )

        val updateRes = repo.updateProfileFields(
            "u_1",
            mapOf(
                "display_name" to "Updated Creator",
                "role" to "admin",
                "is_verified" to true,
                "account_status" to "active",
                "followers_count" to 999999,
            ),
        )
        assertTrue(updateRes.isSuccess)
        assertNotNull(patchedBody)
        assertEquals("Updated Creator", patchedBody?.get("display_name"))
        assertFalse(patchedBody!!.containsKey("role"))
        assertFalse(patchedBody!!.containsKey("is_verified"))
        assertFalse(patchedBody!!.containsKey("account_status"))
        assertFalse(patchedBody!!.containsKey("followers_count"))
        store.clearSession()
    }

    @Test
    fun `guest can browse Shorts and Communities screens seamlessly`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_shorts_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skillect Shorts").assertIsDisplayed()
        composeRule.onNodeWithTag("shorts_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_communities_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Communities & Groups").assertIsDisplayed()
    }

    @Test
    fun `publishable sb_publishable key sends apikey without Bearer header when unauthenticated and injects Bearer on sign-in`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val store = SessionStore(context)
            store.clearSession()

            var capturedApiKey: String? = null
            var capturedAuthHeader: String? = "UNSET"

            val client = SupabaseClientFactory.createOkHttpClient(store) { "sb_publishable_test_key_123" }
                .newBuilder()
                .addInterceptor { chain ->
                    capturedApiKey = chain.request().header("apikey")
                    capturedAuthHeader = chain.request().header("Authorization")
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("[]".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()

            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl("https://mock.supabase.local/")
                .client(client)
                .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(SupabaseClientFactory.moshi))
                .build()
            val api = retrofit.create(SupabasePostgrestApi::class.java)

            // Guest request with sb_publishable_ key must omit Authorization header
            api.getProfiles(limit = 1)
            assertEquals("sb_publishable_test_key_123", capturedApiKey)
            assertNull(capturedAuthHeader)

            // Signed-in request injects user JWT into Authorization header
            store.saveSession(PersistedSession("user_jwt_live_999", "ref_live", "u_live", "user@skillect.global"))
            api.getProfiles(limit = 1)
            assertEquals("sb_publishable_test_key_123", capturedApiKey)
            assertEquals("Bearer user_jwt_live_999", capturedAuthHeader)

            store.clearSession()
        }

    @Test
    fun `moshi parses live production Supabase JSON payloads cleanly`() {
        val moshi = SupabaseClientFactory.moshi

        val profileJson = """
            {
              "id": "u_prod_1",
              "username": "maya.dev",
              "display_name": "Maya",
              "bio": "Systems & Compose",
              "avatar_url": null,
              "cover_url": null,
              "country": null,
              "date_of_birth": "2002-05-01",
              "is_private": false,
              "website": "https://maya.example",
              "location": "Berlin",
              "skills": ["Kotlin", "Compose"],
              "interests": ["Design"],
              "onboarded": false,
              "created_at": "2026-01-10T10:00:00Z"
            }
        """.trimIndent()
        val profile = moshi.adapter(com.example.core.network.ProfileDto::class.java).fromJson(profileJson)!!
        assertEquals("Global", profile.countryCode)
        assertEquals("Berlin", profile.regionLabel)
        assertEquals("2002-05-01", profile.birthDate)
        assertFalse(profile.onboarded)
        assertEquals(listOf("Kotlin", "Compose"), profile.skills)

        val communityJson = """
            {
              "id": "comm_1",
              "slug": "creative-coders",
              "name": "Creative Coders",
              "description": "Global builders",
              "category": "Tech",
              "cover_url": "https://cdn.example/cover.jpg",
              "is_private": false,
              "created_by": null,
              "member_count": 42,
              "topics": ["Android", "Web"]
            }
        """.trimIndent()
        val community = moshi.adapter(com.example.core.network.CommunityDto::class.java).fromJson(communityJson)!!
        assertEquals("", community.creatorId)
        assertEquals(42, community.membersCount)
        assertEquals("https://cdn.example/cover.jpg", community.bannerUrl)

        val notifJson = """
            {
              "id": "notif_1",
              "user_id": "u_prod_1",
              "actor_id": "u_prod_2",
              "type": "like",
              "entity_id": "post_9",
              "message": "liked your post",
              "read": false,
              "created_at": "2026-01-10T12:00:00Z"
            }
        """.trimIndent()
        val notif = moshi.adapter(com.example.core.network.NotificationDto::class.java).fromJson(notifJson)!!
        assertEquals("u_prod_1", notif.recipientId)
        assertEquals("liked your post", notif.body)
        assertFalse(notif.isRead)
    }
}
