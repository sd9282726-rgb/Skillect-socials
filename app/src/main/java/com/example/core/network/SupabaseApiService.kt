package com.example.core.network

import com.example.BuildConfig
import com.example.core.config.AppConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SupabaseAuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<AuthTokenResponse>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(@Body request: PasswordSignInRequest): Response<AuthTokenResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthTokenResponse>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(@Body request: RecoverPasswordRequest): Response<ResponseBody>

    @POST("auth/v1/logout")
    suspend fun signOut(): Response<ResponseBody>

    @GET("auth/v1/user")
    suspend fun getCurrentUser(): Response<AuthUserDto>

    @PUT("auth/v1/user")
    suspend fun updateUserPassword(
        @Header("Authorization") bearerToken: String,
        @Body request: UpdatePasswordRequest,
    ): Response<AuthUserDto>
}

interface SupabasePostgrestApi {
    @GET("rest/v1/profiles")
    suspend fun getProfiles(
        @Query("select") select: String = "*",
        @Query("id") idFilter: String? = null,
        @Query("username") usernameFilter: String? = null,
        @Query("or") orFilter: String? = null,
        @Query("order") order: String? = "created_at.desc",
        @Query("limit") limit: Int = 30,
    ): Response<List<ProfileDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/profiles")
    suspend fun upsertProfile(@Body profile: ProfileUpsertDto): Response<List<ProfileDto>>

    @POST("functions/v1/moderate-text")
    suspend fun moderateText(
        @Body request: Map<String, @JvmSuppressWildcards Any?>,
    ): Response<ModerationGatewayResponse>

    @Multipart
    @POST("functions/v1/moderate-media")
    suspend fun moderateMedia(
        @Part file: MultipartBody.Part,
        @Part("content") content: RequestBody,
        @Part("tags") tags: RequestBody,
        @Part("category") category: RequestBody,
        @Part("community_id") communityId: RequestBody? = null,
    ): Response<ModerationGatewayResponse>

    @POST("functions/v1/submit-video-post")
    suspend fun submitVideoPost(
        @Body request: Map<String, @JvmSuppressWildcards Any?>,
    ): Response<ModerationGatewayResponse>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idEq: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>,
    ): Response<List<ProfileDto>>

    @GET("rest/v1/posts")
    suspend fun getPosts(
        @Query("select") select: String = "*,author:profiles(*)",
        @Query("id") idFilter: String? = null,
        @Query("author_id") authorIdFilter: String? = null,
        @Query("community_id") communityIdFilter: String? = null,
        @Query("category") postTypeFilter: String? = null,
        @Query("or") orFilter: String? = null,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): Response<List<PostDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/posts")
    suspend fun createPost(@Body request: CreatePostRequest): Response<List<PostDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/posts")
    suspend fun updatePost(
        @Query("id") idEq: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>,
    ): Response<List<PostDto>>

    @DELETE("rest/v1/posts")
    suspend fun deletePost(@Query("id") idEq: String): Response<ResponseBody>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/poll_options")
    suspend fun createPollOptions(@Body options: List<Map<String, @JvmSuppressWildcards Any>>): Response<List<PollOptionDto>>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/poll_votes")
    suspend fun castPollVote(@Body vote: Map<String, String>): Response<ResponseBody>

    @GET("rest/v1/post_likes")
    suspend fun getPostLikes(
        @Query("user_id") userIdEq: String,
        @Query("post_id") postIdEq: String? = null,
        @Query("select") select: String = "post_id,user_id",
        @Query("order") order: String = "post_id.asc",
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
    ): Response<List<PostLikeRowDto>>

    @GET("rest/v1/poll_votes")
    suspend fun getPollVotes(
        @Query("user_id") userIdEq: String,
        @Query("select") select: String = "post_id,option_id,user_id",
        @Query("limit") limit: Int = 200,
    ): Response<List<PollVoteRowDto>>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/post_likes")
    suspend fun likePost(@Body body: Map<String, String>): Response<ResponseBody>

    @DELETE("rest/v1/post_likes")
    suspend fun unlikePost(
        @Query("post_id") postIdEq: String,
        @Query("user_id") userIdEq: String,
    ): Response<ResponseBody>

    @GET("rest/v1/comments")
    suspend fun getComments(
        @Query("post_id") postIdEq: String,
        @Query("id") idEq: String? = null,
        @Query("select") select: String = "*,author:profiles(*)",
        @Query("order") order: String = "created_at.asc",
    ): Response<List<CommentDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/comments")
    suspend fun createComment(@Body request: CreateCommentRequest): Response<List<CommentDto>>

    @GET("rest/v1/stories")
    suspend fun getActiveStories(
        @Query("select") select: String = "*,author:profiles(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 40,
    ): Response<List<StoryDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/stories")
    suspend fun createStory(@Body request: CreateStoryRequest): Response<List<StoryDto>>

    @DELETE("rest/v1/stories")
    suspend fun deleteStory(@Query("id") idEq: String): Response<ResponseBody>

    @Headers("Prefer: count=exact")
    @HEAD("rest/v1/posts")
    suspend fun countPostsByAuthor(
        @Query("select") select: String = "id",
        @Query("author_id") authorIdEq: String,
    ): Response<Void>

    @Headers("Prefer: count=exact")
    @HEAD("rest/v1/follows")
    suspend fun countFollowers(
        @Query("select") select: String = "id",
        @Query("following_id") followingIdEq: String,
    ): Response<Void>

    @Headers("Prefer: count=exact")
    @HEAD("rest/v1/follows")
    suspend fun countFollowing(
        @Query("select") select: String = "id",
        @Query("follower_id") followerIdEq: String,
    ): Response<Void>

    @GET("rest/v1/follows")
    suspend fun getFollows(
        @Query("follower_id") followerIdEq: String? = null,
        @Query("following_id") followingIdEq: String? = null,
        @Query("limit") limit: Int = 1000,
    ): Response<List<FollowRowDto>>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/follows")
    suspend fun followUser(@Body body: FollowRowDto): Response<ResponseBody>

    @DELETE("rest/v1/follows")
    suspend fun unfollowUser(
        @Query("follower_id") followerIdEq: String,
        @Query("following_id") followingIdEq: String,
    ): Response<ResponseBody>

    @GET("rest/v1/blocks")
    suspend fun getBlocks(
        @Query("blocker_id") blockerIdEq: String,
        @Query("select") select: String = "blocker_id,blocked_id,blocked:profiles!blocks_blocked_id_fkey(*)",
    ): Response<List<BlockRowDto>>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/blocks")
    suspend fun blockUser(@Body body: Map<String, String>): Response<ResponseBody>

    @DELETE("rest/v1/blocks")
    suspend fun unblockUser(
        @Query("blocker_id") blockerIdEq: String,
        @Query("blocked_id") blockedIdEq: String,
    ): Response<ResponseBody>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/mutes")
    suspend fun muteUser(@Body body: MuteRowDto): Response<ResponseBody>

    @GET("rest/v1/communities")
    suspend fun getCommunities(
        @Query("select") select: String = "*",
        @Query("category") categoryEq: String? = null,
        @Query("or") orFilter: String? = null,
        @Query("order") order: String = "member_count.desc",
    ): Response<List<CommunityDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/communities")
    suspend fun createCommunity(@Body request: CreateCommunityRequest): Response<List<CommunityDto>>

    @GET("rest/v1/community_members")
    suspend fun getCommunityMembers(
        @Query("community_id") communityIdEq: String? = null,
        @Query("user_id") userIdEq: String? = null,
        @Query("select") select: String = "*,profile:profiles(*)",
    ): Response<List<CommunityMemberDto>>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/community_members")
    suspend fun joinCommunity(@Body body: Map<String, String>): Response<ResponseBody>

    @DELETE("rest/v1/community_members")
    suspend fun leaveCommunity(
        @Query("community_id") communityIdEq: String,
        @Query("user_id") userIdEq: String,
    ): Response<ResponseBody>

    @GET("rest/v1/projects")
    suspend fun getProjects(
        @Query("select") select: String = "*,owner:profiles(*)",
        @Query("author_id") ownerIdEq: String? = null,
        @Query("order") order: String = "created_at.desc",
    ): Response<List<ProjectDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/projects")
    suspend fun createProject(@Body request: CreateProjectRequest): Response<List<ProjectDto>>

    @GET("rest/v1/skills")
    suspend fun getSkills(
        @Query("order") order: String = "name.asc",
        @Query("limit") limit: Int = 100,
    ): Response<List<SkillDto>>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/user_skills")
    suspend fun upsertUserSkills(@Body rows: List<Map<String, String>>): Response<ResponseBody>

    @GET("rest/v1/experiences")
    suspend fun getExperiences(
        @Query("user_id") userIdEq: String,
        @Query("order") order: String = "start_date.desc",
    ): Response<List<ExperienceDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/experiences")
    suspend fun createExperience(@Body request: CreateExperienceRequest): Response<List<ExperienceDto>>

    @GET("rest/v1/achievements")
    suspend fun getAchievements(
        @Query("user_id") userIdEq: String,
        @Query("order") order: String = "created_at.desc",
    ): Response<List<AchievementDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/achievements")
    suspend fun createAchievement(@Body request: CreateAchievementRequest): Response<List<AchievementDto>>

    @GET("rest/v1/conversations")
    suspend fun getConversations(
        @Query("select") select: String = "*,participants:conversation_participants(*,profile:profiles(*)),read_markers:conversation_reads(*)",
        @Query("order") order: String = "created_at.desc",
    ): Response<List<ConversationDto>>

    @GET("rest/v1/messages")
    suspend fun getMessages(
        @Query("conversation_id") conversationIdEq: String,
        @Query("id") idEq: String? = null,
        @Query("order") order: String = "created_at.asc",
        @Query("limit") limit: Int = 50,
    ): Response<List<MessageDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<List<MessageDto>>

    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Query("user_id") recipientIdEq: String,
        @Query("select") select: String = "*,actor:profiles!notifications_actor_id_fkey(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 40,
    ): Response<List<NotificationDto>>

    @PATCH("rest/v1/notifications")
    suspend fun markNotificationsRead(
        @Query("user_id") recipientIdEq: String,
        @Query("id") idEq: String? = null,
        @Body updates: Map<String, Boolean>,
    ): Response<ResponseBody>

    @GET("rest/v1/saved_collections")
    suspend fun getSavedCollections(@Query("owner_id") ownerIdEq: String): Response<List<SavedCollectionDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/saved_collections")
    suspend fun createSavedCollection(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<List<SavedCollectionDto>>

    @GET("rest/v1/saved_posts")
    suspend fun getSavedPosts(
        @Query("user_id") userIdEq: String,
        @Query("select") select: String = "user_id,post_id,post:posts(*,author:profiles(*))",
    ): Response<List<SavedPostDto>>

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/saved_posts")
    suspend fun savePost(@Body body: Map<String, String?>): Response<ResponseBody>

    @DELETE("rest/v1/saved_posts")
    suspend fun unsavePost(
        @Query("user_id") userIdEq: String,
        @Query("post_id") postIdEq: String,
    ): Response<ResponseBody>

    @GET("rest/v1/reports")
    suspend fun getReports(@Query("order") order: String = "created_at.desc"): Response<List<ReportDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/reports")
    suspend fun submitReport(@Body request: CreateReportRequest): Response<List<ReportDto>>

    @PATCH("rest/v1/reports")
    suspend fun updateReportStatus(
        @Query("id") idEq: String,
        @Body updates: Map<String, String>,
    ): Response<ResponseBody>

    @GET("rest/v1/moderation_audit_logs")
    suspend fun getAuditLogs(@Query("order") order: String = "created_at.desc"): Response<List<ModerationAuditLogDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/moderation_audit_logs")
    suspend fun createAuditLog(@Body request: CreateAuditLogRequest): Response<List<ModerationAuditLogDto>>

    @GET("rest/v1/moderation_appeals")
    suspend fun getModerationAppeals(@Query("order") order: String = "created_at.desc"): Response<List<ModerationAppealDto>>

    @GET("rest/v1/account_enforcements")
    suspend fun getMyEnforcement(
        @Query("user_id") userIdEq: String,
        @Query("select") select: String = "user_id,status,reason,issued_at,appeal_allowed",
        @Query("order") order: String = "issued_at.desc",
        @Query("limit") limit: Int = 1,
    ): Response<List<AccountEnforcementDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/moderation_appeals")
    suspend fun submitModerationAppeal(@Body request: CreateAppealRequest): Response<List<ModerationAppealDto>>

    @POST("rest/v1/rpc/resolve_moderation_appeal")
    suspend fun resolveModerationAppeal(@Body request: ResolveModerationAppealRequest): Response<ResponseBody>

    @PATCH("rest/v1/moderation_appeals")
    suspend fun updateModerationAppeal(
        @Query("id") idEq: String,
        @Body updates: Map<String, String>,
    ): Response<ResponseBody>

    @DELETE("rest/v1/account_enforcements")
    suspend fun restoreAccountAfterAppeal(
        @Query("user_id") userIdEq: String,
    ): Response<ResponseBody>

    @POST("functions/v1/start-conversation")
    suspend fun startConversation(@Body body: StartConversationRequest): Response<StartConversationResponse>

    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/device_tokens")
    suspend fun upsertDeviceToken(
        @Query("on_conflict") onConflict: String = "platform,token",
        @Body token: DeviceTokenUpsertDto,
    ): Response<ResponseBody>

    @DELETE("rest/v1/device_tokens")
    suspend fun deleteDeviceToken(
        @Query("user_id") userIdEq: String,
        @Query("platform") platformEq: String = "eq.android",
        @Query("token") tokenEq: String,
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/request_account_deletion")
    suspend fun requestAccountDeletionRpc(): Response<ResponseBody>

    @POST("functions/v1/delete-account")
    suspend fun requestAccountDeletion(): Response<ResponseBody>
}

interface SupabaseStorageApi {
    @POST("storage/v1/object/sign/{bucket}/{path}")
    suspend fun createSignedUrl(
        @Path("bucket") bucket: String,
        @Path("path", encoded = true) path: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadObject(
        @Path("bucket") bucket: String,
        @Path("path", encoded = true) path: String,
        @Header("Content-Type") contentType: String,
        @Header("x-upsert") upsert: String = "false",
        @Body body: RequestBody,
    ): Response<ResponseBody>
}

object SupabaseClientFactory {
    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val refreshLock = Any()

    fun createOkHttpClient(
        sessionStore: SessionStore,
        anonKeyProvider: () -> String = { AppConfig.supabaseAnonKey },
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("apikey")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val authInterceptor = Interceptor { chain ->
            val session = runBlocking { sessionStore.currentSession() }
            val original = chain.request()
            val anonKey = anonKeyProvider()
            val builder = original.newBuilder()
            if (anonKey.isNotBlank()) {
                builder.header("apikey", anonKey)
            }
            val fallbackJwt = anonKey.takeIf { !it.startsWith("sb_publishable_") }.orEmpty()
            val token = session.accessToken.ifBlank { fallbackJwt }
            if (original.header("Authorization") == null && token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }

        val refreshHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val tokenAuthenticator = Authenticator { _: Route?, response: okhttp3.Response ->
            if (!AppConfig.isSupabaseConfigured) return@Authenticator null
            if (response.request.url.encodedPath.contains("/auth/v1/token")) return@Authenticator null
            if (responseCount(response) >= 2) return@Authenticator null

            synchronized(refreshLock) {
                val current = runBlocking { sessionStore.currentSession() }
                if (!current.isAuthenticated || current.refreshToken.isBlank()) return@Authenticator null

                val requestAuth = response.request.header("Authorization")
                val latestBearer = "Bearer ${current.accessToken}"
                if (requestAuth != null && requestAuth != latestBearer) {
                    return@Authenticator response.request.newBuilder()
                        .header("Authorization", latestBearer)
                        .build()
                }

                val bodyJson = JSONObject().put("refresh_token", current.refreshToken).toString()
                val refreshReq = Request.Builder()
                    .url("${AppConfig.supabaseUrl}/auth/v1/token?grant_type=refresh_token")
                    .header("apikey", AppConfig.supabaseAnonKey)
                    .header("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                return@Authenticator try {
                    refreshHttpClient.newCall(refreshReq).execute().use { refreshResp ->
                        if (!refreshResp.isSuccessful) {
                            if (refreshResp.code == 400 || refreshResp.code == 401) {
                                runBlocking { sessionStore.clearSession() }
                            }
                            return@use null
                        }
                        val json = JSONObject(refreshResp.body?.string().orEmpty())
                        val newAccess = json.optString("access_token")
                        val newRefresh = json.optString("refresh_token").ifBlank { current.refreshToken }
                        if (newAccess.isBlank()) return@use null
                        runBlocking {
                            sessionStore.saveSession(
                                current.copy(accessToken = newAccess, refreshToken = newRefresh),
                            )
                        }
                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newAccess")
                            .build()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    fun createRetrofit(sessionStore: SessionStore): Retrofit {
        val baseUrl = if (AppConfig.isSupabaseConfigured) {
            "${AppConfig.supabaseUrl}/"
        } else {
            "https://unconfigured.supabase.invalid/"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createOkHttpClient(sessionStore))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
