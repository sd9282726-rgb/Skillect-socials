#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/example/ui/feed/FeedScreen.kt")
if p.exists():
    t = p.read_text()
    marker = "            // Photo-led editorial header: image + typography, without a generic slogan."
    tabs = "            // Clean Underline Tabs"
    if marker in t and tabs in t:
        i, j = t.find(marker), t.find(tabs)
        if 0 <= i < j:
            t = t[:i] + "            // Compact feed header\n            Spacer(modifier = Modifier.height(4.dp))\n\n" + t[j:]
            p.write_text(t)
            print("Feed header compacted")
    else:
        print("Feed header already compact or markers missing")

api = Path("app/src/main/java/com/example/core/network/SupabaseApiService.kt")
t = api.read_text()
if "follow_requests" not in t:
    anchor = '    @GET("rest/v1/follows")'
    insert = (
        '    @GET("rest/v1/follow_requests")\n'
        '    suspend fun getFollowRequests(\n'
        '        @Query("requester_id") requesterIdEq: String? = null,\n'
        '        @Query("target_id") targetIdEq: String? = null,\n'
        '        @Query("status") statusEq: String? = null,\n'
        '        @Query("select") select: String = "*",\n'
        '        @Query("limit") limit: Int = 50,\n'
        '    ): Response<List<Map<String, @JvmSuppressWildcards Any?>>>\n\n'
        '    @Headers("Prefer: resolution=merge-duplicates,return=representation")\n'
        '    @POST("rest/v1/follow_requests")\n'
        '    suspend fun createFollowRequest(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<List<Map<String, @JvmSuppressWildcards Any?>>>\n\n'
        '    @POST("rest/v1/rpc/accept_follow_request")\n'
        '    suspend fun acceptFollowRequest(@Body body: Map<String, String>): Response<ResponseBody>\n\n'
        '    @POST("rest/v1/rpc/reject_follow_request")\n'
        '    suspend fun rejectFollowRequest(@Body body: Map<String, String>): Response<ResponseBody>\n\n'
    )
    if anchor in t:
        api.write_text(t.replace(anchor, insert + anchor, 1))
        print("API follow_requests added")
    else:
        print("API anchor missing")
else:
    print("API follow_requests present")

rp = Path("app/src/main/java/com/example/data/repository/SkillectRepository.kt")
rt = rp.read_text()
old = "data class FollowToggleResult(val wasFollowing: Boolean, val isFollowing: Boolean)"
new = "data class FollowToggleResult(val wasFollowing: Boolean, val isFollowing: Boolean, val requestPending: Boolean = false)"
if old in rt:
    rt = rt.replace(old, new, 1)
    print("FollowToggleResult patched")

if "createFollowRequest" not in rt and "suspend fun toggleFollow" in rt:
    old_tf = '''        val wasFollowing = !current.body().isNullOrEmpty()
        val response = if (wasFollowing) {
            postgrestApi.unfollowUser("eq.$uid", "eq.$targetUserId")
        } else {
            postgrestApi.followUser(FollowRowDto(uid, targetUserId))
        }
        if (response.isSuccessful) Result.success(FollowToggleResult(wasFollowing, !wasFollowing))
        else Result.failure(IllegalStateException(response.parseError()))
    }'''
    new_tf = '''        val wasFollowing = !current.body().isNullOrEmpty()
        if (wasFollowing) {
            val response = postgrestApi.unfollowUser("eq.$uid", "eq.$targetUserId")
            return@guardConfigured if (response.isSuccessful) Result.success(FollowToggleResult(true, false))
            else Result.failure(IllegalStateException(response.parseError()))
        }
        val targetProfile = postgrestApi.getProfiles(idFilter = "eq.$targetUserId", limit = 1)
        val isPrivate = targetProfile.body()?.firstOrNull()?.isPrivate == true
        if (isPrivate) {
            val req = postgrestApi.createFollowRequest(
                mapOf("requester_id" to uid, "target_id" to targetUserId, "status" to "pending"),
            )
            return@guardConfigured if (req.isSuccessful) Result.success(FollowToggleResult(false, false, requestPending = true))
            else Result.failure(IllegalStateException(req.parseError()))
        }
        val response = postgrestApi.followUser(FollowRowDto(uid, targetUserId))
        if (response.isSuccessful) Result.success(FollowToggleResult(false, true))
        else Result.failure(IllegalStateException(response.parseError()))
    }

    suspend fun fetchConnectionProfiles(userId: String, mode: String): Result<List<ProfileDto>> = guardConfigured {
        val follows = if (mode == "followers") {
            postgrestApi.getFollows(followingIdEq = "eq.$userId", limit = 100)
        } else {
            postgrestApi.getFollows(followerIdEq = "eq.$userId", limit = 100)
        }
        if (!follows.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(follows.parseError()))
        val ids = follows.body().orEmpty().map {
            if (mode == "followers") it.followerId else it.followingId
        }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return@guardConfigured Result.success(emptyList())
        val inList = "(" + ids.joinToString(",") + ")"
        val resp = postgrestApi.getProfiles(idFilter = "in.$inList", limit = ids.size.coerceAtMost(100))
        if (!resp.isSuccessful) return@guardConfigured Result.failure(IllegalStateException(resp.parseError()))
        Result.success(resp.body().orEmpty())
    }'''
    if old_tf in rt:
        rt = rt.replace(old_tf, new_tf, 1)
        print("toggleFollow private path injected")
    else:
        print("toggleFollow pattern not matched")
rp.write_text(rt)

print("apply_social_profile_fixes complete")
PY
