package com.example.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    val user: AuthUserDto? = null,
)

@JsonClass(generateAdapter = true)
data class AuthUserDto(
    val id: String,
    val email: String? = null,
    @Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null,
    @Json(name = "user_metadata") val userMetadata: Map<String, Any?>? = null,
)

@JsonClass(generateAdapter = true)
data class SignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class PasswordSignInRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RecoverPasswordRequest(
    val email: String,
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val email: String,
    val token: String,
    val type: String = "recovery",
)

@JsonClass(generateAdapter = true)
data class UpdatePasswordRequest(
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class PortfolioLinkDto(
    val label: String,
    val url: String,
)

@JsonClass(generateAdapter = true)
data class ProfileDto(
    val id: String,
    val username: String = "",
    @Json(name = "display_name") val displayName: String = "",
    val bio: String = "",
    val headline: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "cover_url") val coverUrl: String? = null,
    val country: String? = null,
    @Json(name = "country_code") val rawCountryCode: String? = null,
    val location: String = "",
    @Json(name = "region_label") val rawRegionLabel: String? = null,
    val timezone: String = "UTC",
    val locale: String = "en",
    val website: String = "",
    @Json(name = "date_of_birth") val dateOfBirth: String? = null,
    @Json(name = "birth_date") val rawBirthDate: String? = null,
    @Json(name = "is_private") val isPrivate: Boolean = false,
    @Json(name = "is_verified") val isVerified: Boolean = false,
    val onboarded: Boolean = true,
    val role: String = "member",
    @Json(name = "account_status") val accountStatus: String = "active",
    @Json(name = "portfolio_links") val portfolioLinks: List<PortfolioLinkDto> = emptyList(),
    val skills: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    @Json(name = "followers_count") val followersCount: Int = 0,
    @Json(name = "following_count") val followingCount: Int = 0,
    @Json(name = "posts_count") val postsCount: Int = 0,
    @Json(name = "created_at") val createdAt: String = "",
) {
    val countryCode: String
        get() = rawCountryCode?.takeIf { it.isNotBlank() }
            ?: country?.takeIf { it.isNotBlank() }
            ?: "Global"

    val regionLabel: String
        get() = rawRegionLabel?.takeIf { it.isNotBlank() }
            ?: location

    val birthDate: String?
        get() = rawBirthDate ?: dateOfBirth

    val isMinorSafeDefaults: Boolean
        get() = birthDate?.let { com.example.core.config.AppConfig.isMinor16To17(it) } == true

    val allowDmsFrom: String
        get() = if (isMinorSafeDefaults) "following_only" else "everyone"
}

@JsonClass(generateAdapter = true)
data class ModerationGatewayResponse(
    val status: String = "",
    @Json(name = "profile_saved") val profileSaved: Boolean? = null,
    val id: String? = null,
    @Json(name = "post_id") val postId: String? = null,
    @Json(name = "comment_id") val commentId: String? = null,
    @Json(name = "message_id") val messageId: String? = null,
    @Json(name = "conversation_id") val conversationId: String? = null,
    @Json(name = "sender_id") val senderId: String? = null,
    val content: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProfileUpsertDto(
    val id: String,
    val username: String,
    @Json(name = "display_name") val displayName: String,
    val bio: String = "",
    @Transient val headline: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "country") val countryCode: String = "US",
    @Json(name = "location") val regionLabel: String = "",
    @Transient val timezone: String = "UTC",
    @Transient val locale: String = "en",
    val website: String = "",
    @Json(name = "date_of_birth") val birthDate: String? = null,
    @Transient val isMinorSafeDefaults: Boolean = true,
    @Transient val allowDmsFrom: String = "following_only",
    @Json(name = "is_private") val isPrivate: Boolean = false,
    val onboarded: Boolean = true,
    val skills: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    @Transient val portfolioLinks: List<PortfolioLinkDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PollOptionDto(
    val id: String = "",
    @Json(name = "post_id") val postId: String = "",
    @Json(name = "option_text") val optionText: String,
    @Json(name = "votes_count") val votesCount: Int = 0,
    @Json(name = "sort_order") val sortOrder: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PostDto(
    val id: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "post_type") val postType: String = "text",
    val content: String = "",
    @Json(name = "image_url") val imageUrl: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    @Json(name = "media_urls") val mediaUrls: List<String> = emptyList(),
    @Json(name = "media_types") val mediaTypes: List<String> = emptyList(),
    @Json(name = "link_url") val linkUrl: String? = null,
    @Json(name = "link_title") val linkTitle: String? = null,
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    @Json(name = "skill_tags") val skillTags: List<String> = emptyList(),
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "community_id") val communityId: String? = null,
    @Json(name = "reposted_post_id") val repostedPostId: String? = null,
    @Json(name = "quoted_post_id") val quotedPostId: String? = null,
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "comments_count") val commentsCount: Int = 0,
    @Json(name = "reposts_count") val repostsCount: Int = 0,
    @Json(name = "moderation_status") val moderationStatus: String = "approved",
    @Json(name = "is_edited") val isEdited: Boolean = false,
    @Json(name = "created_at") val createdAt: String = "",
    val author: ProfileDto? = null,
    @Json(name = "poll_options") val pollOptions: List<PollOptionDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CreatePostRequest(
    @Json(name = "author_id") val authorId: String,
    @Transient val postType: String = "text",
    val content: String,
    @Transient val mediaUrls: List<String> = emptyList(),
    @Transient val mediaTypes: List<String> = emptyList(),
    @Transient val linkUrl: String? = null,
    @Transient val linkTitle: String? = null,
    @Transient val hashtags: List<String> = emptyList(),
    @Transient val mentions: List<String> = emptyList(),
    @Transient val skillTags: List<String> = emptyList(),
    @Transient val projectId: String? = null,
    @Json(name = "community_id") val communityId: String? = null,
    @Transient val repostedPostId: String? = null,
    @Transient val quotedPostId: String? = null,
    @Json(name = "image_url") val imageUrl: String? = mediaUrls.firstOrNull(),
    val category: String? = postType,
    val tags: List<String> = (skillTags + hashtags).distinct(),
)

@JsonClass(generateAdapter = true)
data class CommentDto(
    val id: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "parent_id") val parentCommentId: String? = null,
    val content: String,
    @Json(name = "moderation_status") val moderationStatus: String = "approved",
    @Json(name = "created_at") val createdAt: String = "",
    val author: ProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class CreateCommentRequest(
    @Json(name = "post_id") val postId: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "parent_id") val parentCommentId: String? = null,
    val content: String,
)

@JsonClass(generateAdapter = true)
data class StoryDto(
    val id: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "media_url") val mediaUrl: String,
    @Json(name = "media_type") val mediaType: String = "image",
    val caption: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "expires_at") val expiresAt: String = "",
    val author: ProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class CreateStoryRequest(
    @Json(name = "author_id") val authorId: String,
    @Json(name = "media_url") val mediaUrl: String,
    @Json(name = "media_type") val mediaType: String = "image",
    val caption: String = "",
)

@JsonClass(generateAdapter = true)
data class CommunityDto(
    val id: String,
    val slug: String = "",
    val name: String,
    val description: String = "",
    val category: String = "General",
    @Json(name = "cover_url") val bannerUrl: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_private") val isPrivate: Boolean = false,
    @Json(name = "created_by") val rawCreatorId: String? = null,
    @Json(name = "member_count") val membersCount: Int = 1,
    val topics: List<String> = emptyList(),
    @Json(name = "created_at") val createdAt: String = "",
) {
    val creatorId: String get() = rawCreatorId.orEmpty()
}

@JsonClass(generateAdapter = true)
data class CreateCommunityRequest(
    val slug: String,
    val name: String,
    val description: String,
    val category: String,
    @Json(name = "is_private") val isPrivate: Boolean = false,
    @Json(name = "created_by") val creatorId: String,
)

@JsonClass(generateAdapter = true)
data class CommunityMemberDto(
    @Json(name = "community_id") val communityId: String,
    @Json(name = "user_id") val userId: String,
    val role: String = "member",
    val status: String = "active",
    @Json(name = "created_at") val joinedAt: String = "",
    val profile: ProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class SkillDto(
    val id: String,
    val name: String,
    val category: String = "General",
)

@JsonClass(generateAdapter = true)
data class ProjectDto(
    val id: String,
    @Json(name = "author_id") val rawAuthorId: String? = null,
    @Json(name = "owner_id") val rawOwnerId: String? = null,
    val title: String,
    val description: String = "",
    val category: String = "Technology",
    val status: String = "active",
    @Json(name = "image_url") val rawImageUrl: String? = null,
    @Json(name = "cover_url") val rawCoverUrl: String? = null,
    @Json(name = "external_links") val externalLinks: List<PortfolioLinkDto> = emptyList(),
    @Json(name = "skills_used") val skillsUsed: List<String> = emptyList(),
    @Json(name = "collaborators_note") val collaboratorsNote: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    val owner: ProfileDto? = null,
) {
    val ownerId: String get() = rawOwnerId ?: rawAuthorId.orEmpty()
    val coverUrl: String? get() = rawCoverUrl ?: rawImageUrl
}

@JsonClass(generateAdapter = true)
data class CreateProjectRequest(
    @Json(name = "author_id") val ownerId: String,
    val title: String,
    val description: String,
    @Transient val category: String = "Technology",
    @Json(name = "image_url") val coverUrl: String? = null,
    @Transient val skillsUsed: List<String> = emptyList(),
    @Transient val collaboratorsNote: String = "",
    @Transient val externalLinks: List<PortfolioLinkDto> = emptyList(),
    val status: String = "active",
)

@JsonClass(generateAdapter = true)
data class ExperienceDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val title: String,
    val organization: String,
    @Json(name = "location_label") val locationLabel: String = "",
    @Json(name = "start_date") val startDate: String,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "is_current") val isCurrent: Boolean = false,
    val description: String = "",
)

@JsonClass(generateAdapter = true)
data class CreateExperienceRequest(
    @Json(name = "user_id") val userId: String,
    val title: String,
    val organization: String,
    @Json(name = "location_label") val locationLabel: String = "",
    @Json(name = "start_date") val startDate: String,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "is_current") val isCurrent: Boolean = false,
    val description: String = "",
)

@JsonClass(generateAdapter = true)
data class AchievementDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val title: String,
    val issuer: String = "",
    @Json(name = "awarded_at") val awardedAt: String? = null,
    val description: String = "",
)

@JsonClass(generateAdapter = true)
data class ConversationDto(
    val id: String,
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "updated_at") val updatedAt: String = "",
    val participants: List<ConversationParticipantDto> = emptyList(),
    @Json(name = "read_markers") val readMarkers: List<ConversationReadDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ConversationReadDto(
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "last_read_at") val lastReadAt: String? = null,
    @Json(name = "last_delivered_at") val lastDeliveredAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ConversationParticipantDto(
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "last_read_at") val lastReadAt: String? = null,
    val profile: ProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class MessageDto(
    val id: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "sender_id") val senderId: String,
    val content: String = "",
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "delivery_status") val deliveryStatus: String = "sent",
    @Json(name = "moderation_status") val moderationStatus: String = "approved",
    @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "sender_id") val senderId: String,
    val content: String,
    @Json(name = "media_url") val mediaUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: String,
    @Json(name = "user_id") val recipientId: String = "",
    @Json(name = "actor_id") val actorId: String? = null,
    val type: String = "activity",
    @Json(name = "entity_id") val entityId: String? = null,
    val title: String = "",
    @Json(name = "message") val body: String = "",
    @Json(name = "read") val isRead: Boolean = false,
    @Json(name = "created_at") val createdAt: String = "",
    val actor: ProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class MessageRequestDto(
    val id: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "recipient_id") val recipientId: String,
    @Json(name = "initial_message") val initialMessage: String = "",
    val status: String = "pending",
    @Json(name = "created_at") val createdAt: String = "",
    val sender: ProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class SavedCollectionDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    @Json(name = "is_private") val isPrivate: Boolean = true,
    @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class SavedPostDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "collection_id") val collectionId: String? = null,
    val post: PostDto? = null,
)

@JsonClass(generateAdapter = true)
data class ReportDto(
    val id: String,
    @Json(name = "reporter_id") val reporterId: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val reason: String,
    val details: String = "",
    val status: String = "pending",
    @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class CreateReportRequest(
    @Json(name = "reporter_id") val reporterId: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val reason: String,
    val details: String = "",
    @Json(name = "target_user_id") val targetUserId: String? = null,
)

@JsonClass(generateAdapter = true)
data class FollowRowDto(
    @Json(name = "follower_id") val followerId: String,
    @Json(name = "following_id") val followingId: String,
)

@JsonClass(generateAdapter = true)
data class BlockRowDto(
    @Json(name = "blocker_id") val blockerId: String,
    @Json(name = "blocked_id") val blockedId: String,
    val blocked: ProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class MuteRowDto(
    @Json(name = "muter_id") val muterId: String,
    @Json(name = "muted_id") val mutedId: String,
)

@JsonClass(generateAdapter = true)
data class DeviceTokenUpsertDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "token") val fcmToken: String,
    val platform: String = "android",
)

@JsonClass(generateAdapter = true)
data class ModerationAuditLogDto(
    val id: String,
    @Json(name = "actor_admin_id") val actorAdminId: String,
    val action: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val notes: String = "",
    @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class CreateAuditLogRequest(
    @Json(name = "actor_admin_id") val actorAdminId: String,
    val action: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val notes: String = "",
)

@JsonClass(generateAdapter = true)
data class PostLikeRowDto(
    @Json(name = "post_id") val postId: String,
    @Json(name = "user_id") val userId: String,
)

@JsonClass(generateAdapter = true)
data class PollVoteRowDto(
    @Json(name = "post_id") val postId: String,
    @Json(name = "option_id") val optionId: String,
    @Json(name = "user_id") val userId: String,
)

@JsonClass(generateAdapter = true)
data class CreateAchievementRequest(
    @Json(name = "user_id") val userId: String,
    val title: String,
    val issuer: String = "",
    @Json(name = "awarded_at") val awardedAt: String? = null,
    val description: String = "",
)

@JsonClass(generateAdapter = true)
data class ModerationMediaDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    val status: String,
    @Json(name = "media_type") val mediaType: String,
    @Json(name = "mime_type") val mimeType: String = "",
    @Json(name = "post_content") val postContent: String = "",
    @Json(name = "post_id") val postId: String? = null,
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "decision_reason") val decisionReason: String = "",
    @Json(name = "preview_url") val previewUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class ModerationAppealDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "enforcement_user_id") val enforcementUserId: String = userId,
    @Json(name = "explanation") val reason: String = "",
    val status: String = "pending",
    @Json(name = "moderator_note") val reviewerNote: String? = null,
    @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class CreateAppealRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "enforcement_user_id") val enforcementUserId: String,
    @Json(name = "explanation") val reason: String,
)

@JsonClass(generateAdapter = true)
data class ResolveModerationAppealRequest(
    @Json(name = "p_appeal_id") val appealId: String,
    @Json(name = "p_accept") val accept: Boolean,
    @Json(name = "p_moderator_note") val moderatorNote: String = "",
)

@JsonClass(generateAdapter = true)
data class AccountEnforcementDto(
    @Json(name = "user_id") val userId: String,
    val status: String,
    val reason: String,
    @Json(name = "issued_at") val issuedAt: String = "",
    @Json(name = "appeal_allowed") val appealAllowed: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class StartConversationRequest(
    @Json(name = "recipient_id") val recipientId: String,
    @Json(name = "peer_id") val peerId: String = recipientId,
)

@JsonClass(generateAdapter = true)
data class StartConversationResponse(
    val status: String = "",
    @Json(name = "conversation_id") val conversationId: String? = null,
    @Json(name = "request_id") val requestId: String? = null,
    @Json(name = "recipient_id") val recipientId: String? = null,
    val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class CreateDirectConversationParams(
    @Json(name = "peer_id") val peerId: String,
)

@JsonClass(generateAdapter = true)
data class DeviceTokenRpcParams(
    @Json(name = "p_fcm_token") val fcmToken: String,
    @Json(name = "p_platform") val platform: String = "android",
)
