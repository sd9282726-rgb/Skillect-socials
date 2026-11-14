package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface SkillectRoute {
    @Serializable data object Auth : SkillectRoute
    @Serializable data object Onboarding : SkillectRoute
    @Serializable data object Feed : SkillectRoute
    @Serializable data object Shorts : SkillectRoute
    @Serializable data object Explore : SkillectRoute
    @Serializable data class Composer(val communityId: String? = null) : SkillectRoute
    @Serializable data object Communities : SkillectRoute
    @Serializable data class CommunityDetail(val communityId: String) : SkillectRoute
    @Serializable data object Messages : SkillectRoute
    @Serializable data class ChatDetail(val conversationId: String, val peerName: String = "") : SkillectRoute
    @Serializable data object Notifications : SkillectRoute
    @Serializable data class Profile(val userId: String? = null) : SkillectRoute
    @Serializable data object Settings : SkillectRoute
    @Serializable data object ModerationAdmin : SkillectRoute
}
