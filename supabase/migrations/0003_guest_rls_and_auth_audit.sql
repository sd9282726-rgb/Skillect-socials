-- ============================================================================
-- SKILLECT — STEP 3: GUEST READ-ONLY RLS & AUTHENTICATED ACCESS POLICIES
-- STAGED ONLY — DO NOT EXECUTE ON PRODUCTION WITHOUT EXPLICIT USER APPROVAL.
-- Idempotent and non-destructive. Safe for existing Supabase projects.
-- ============================================================================

-- 1. Explicitly prevent anonymous/guest roles from mutating ANY table in public
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM anon;

-- 2. Explicitly prevent anonymous/guest roles from reading private/sensitive tables
REVOKE SELECT ON
    public.conversations,
    public.conversation_participants,
    public.messages,
    public.notifications,
    public.user_device_tokens,
    public.saved_collections,
    public.saved_posts,
    public.blocks,
    public.mutes,
    public.reports,
    public.moderation_appeals,
    public.moderation_audit_logs,
    public.poll_votes
FROM anon;

-- Grant read-only SELECT only on public-facing tables/views
GRANT SELECT ON
    public.profiles,
    public.posts,
    public.poll_options,
    public.post_likes,
    public.post_comments,
    public.stories,
    public.active_stories,
    public.communities,
    public.community_members,
    public.projects,
    public.skills,
    public.user_skills,
    public.experiences,
    public.education,
    public.achievements,
    public.follows
TO anon;

-- 3. Profiles: Guests can only view active, non-private profiles; authenticated users see active non-blocked profiles (plus their own)
DROP POLICY IF EXISTS "profiles_select_visible" ON public.profiles;
CREATE POLICY "profiles_select_visible" ON public.profiles
    FOR SELECT USING (
        (
            auth.role() = 'anon'
            AND account_status = 'active'
            AND is_private = false
        )
        OR (
            auth.role() = 'authenticated'
            AND (
                id = auth.uid()
                OR (
                    account_status = 'active'
                    AND NOT public.is_blocked_between(auth.uid(), id)
                )
            )
        )
    );

-- 4. Posts: Guests can only read approved posts from non-private authors & non-private communities
DROP POLICY IF EXISTS "posts_select_approved" ON public.posts;
CREATE POLICY "posts_select_approved" ON public.posts
    FOR SELECT USING (
        (
            auth.role() = 'anon'
            AND moderation_status = 'approved'
            AND EXISTS (
                SELECT 1 FROM public.profiles p
                WHERE p.id = author_id AND p.account_status = 'active' AND p.is_private = false
            )
            AND (
                community_id IS NULL
                OR EXISTS (
                    SELECT 1 FROM public.communities c
                    WHERE c.id = community_id AND c.is_private = false
                )
            )
        )
        OR (
            auth.role() = 'authenticated'
            AND (moderation_status = 'approved' OR author_id = auth.uid() OR public.is_admin_or_mod())
            AND NOT public.is_blocked_between(auth.uid(), author_id)
            AND (
                community_id IS NULL
                OR EXISTS (
                    SELECT 1 FROM public.communities c
                    WHERE c.id = community_id
                      AND (
                          c.is_private = false
                          OR EXISTS (
                              SELECT 1 FROM public.community_members cm
                              WHERE cm.community_id = c.id AND cm.user_id = auth.uid()
                          )
                      )
                )
            )
        )
    );

-- 5. Stories: Guests only read unexpired stories of public accounts
DROP POLICY IF EXISTS "stories_select_unexpired" ON public.stories;
CREATE POLICY "stories_select_unexpired" ON public.stories
    FOR SELECT USING (
        expires_at > now()
        AND (
            (
                auth.role() = 'anon'
                AND EXISTS (
                    SELECT 1 FROM public.profiles p
                    WHERE p.id = author_id AND p.account_status = 'active' AND p.is_private = false
                )
            )
            OR (
                auth.role() = 'authenticated'
                AND NOT public.is_blocked_between(auth.uid(), author_id)
            )
        )
    );

DROP POLICY IF EXISTS "story_views_owner_all" ON public.story_views;
CREATE POLICY "story_views_owner_all" ON public.story_views
    FOR ALL TO authenticated
    USING (auth.uid() = viewer_id)
    WITH CHECK (auth.uid() = viewer_id);

-- 6. Likes, Poll Options, Poll Votes, Comments
DROP POLICY IF EXISTS "post_likes_select_public" ON public.post_likes;
CREATE POLICY "post_likes_select_public" ON public.post_likes
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "post_likes_insert_auth" ON public.post_likes;
CREATE POLICY "post_likes_insert_auth" ON public.post_likes
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "post_likes_delete_auth" ON public.post_likes;
CREATE POLICY "post_likes_delete_auth" ON public.post_likes
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "poll_options_select_all" ON public.poll_options;
CREATE POLICY "poll_options_select_all" ON public.poll_options
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "poll_options_insert_author" ON public.poll_options;
CREATE POLICY "poll_options_insert_author" ON public.poll_options
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.posts p
            WHERE p.id = post_id AND p.author_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "poll_votes_select_auth" ON public.poll_votes;
CREATE POLICY "poll_votes_select_auth" ON public.poll_votes
    FOR SELECT TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "poll_votes_insert_auth" ON public.poll_votes;
CREATE POLICY "poll_votes_insert_auth" ON public.poll_votes
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "comments_select_approved" ON public.post_comments;
CREATE POLICY "comments_select_approved" ON public.post_comments
    FOR SELECT USING (
        (moderation_status = 'approved' OR author_id = auth.uid() OR public.is_admin_or_mod())
        AND (auth.role() = 'anon' OR NOT public.is_blocked_between(auth.uid(), author_id))
    );

DROP POLICY IF EXISTS "comments_insert_auth" ON public.post_comments;
CREATE POLICY "comments_insert_auth" ON public.post_comments
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "comments_delete_own_or_mod" ON public.post_comments;
CREATE POLICY "comments_delete_own_or_mod" ON public.post_comments
    FOR DELETE TO authenticated
    USING (auth.uid() = author_id OR public.is_admin_or_mod());

-- 7. Communities & Community Members
DROP POLICY IF EXISTS "communities_select_public_or_member" ON public.communities;
CREATE POLICY "communities_select_public_or_member" ON public.communities
    FOR SELECT USING (
        is_private = false
        OR (
            auth.role() = 'authenticated'
            AND (
                creator_id = auth.uid()
                OR EXISTS (
                    SELECT 1 FROM public.community_members cm
                    WHERE cm.community_id = id AND cm.user_id = auth.uid()
                )
            )
        )
    );

DROP POLICY IF EXISTS "communities_insert_auth" ON public.communities;
CREATE POLICY "communities_insert_auth" ON public.communities
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = creator_id);

DROP POLICY IF EXISTS "community_members_select_visible" ON public.community_members;
CREATE POLICY "community_members_select_visible" ON public.community_members
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = community_id AND c.is_private = false
        )
        OR (auth.role() = 'authenticated' AND auth.uid() = user_id)
    );

DROP POLICY IF EXISTS "community_members_insert_self" ON public.community_members;
CREATE POLICY "community_members_insert_self" ON public.community_members
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "community_members_delete_self" ON public.community_members;
CREATE POLICY "community_members_delete_self" ON public.community_members
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id);

-- 8. Skills, User Skills, Experiences, Education, Achievements, Projects
DROP POLICY IF EXISTS "skills_select_all" ON public.skills;
CREATE POLICY "skills_select_all" ON public.skills FOR SELECT USING (true);

DROP POLICY IF EXISTS "user_skills_select_all" ON public.user_skills;
CREATE POLICY "user_skills_select_all" ON public.user_skills FOR SELECT USING (true);

DROP POLICY IF EXISTS "user_skills_manage_own" ON public.user_skills;
CREATE POLICY "user_skills_manage_own" ON public.user_skills
    FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "experiences_select_all" ON public.experiences;
CREATE POLICY "experiences_select_all" ON public.experiences FOR SELECT USING (true);

DROP POLICY IF EXISTS "experiences_manage_own" ON public.experiences;
CREATE POLICY "experiences_manage_own" ON public.experiences
    FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "education_select_all" ON public.education;
CREATE POLICY "education_select_all" ON public.education FOR SELECT USING (true);

DROP POLICY IF EXISTS "education_manage_own" ON public.education;
CREATE POLICY "education_manage_own" ON public.education
    FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "achievements_select_all" ON public.achievements;
CREATE POLICY "achievements_select_all" ON public.achievements FOR SELECT USING (true);

DROP POLICY IF EXISTS "achievements_manage_own" ON public.achievements;
CREATE POLICY "achievements_manage_own" ON public.achievements
    FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "projects_select_all" ON public.projects;
CREATE POLICY "projects_select_all" ON public.projects FOR SELECT USING (true);

DROP POLICY IF EXISTS "projects_manage_own" ON public.projects;
CREATE POLICY "projects_manage_own" ON public.projects
    FOR ALL TO authenticated USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

-- 9. Social Graph, Blocks, Mutes, Conversation Participants, Appeals
DROP POLICY IF EXISTS "follows_select_all" ON public.follows;
CREATE POLICY "follows_select_all" ON public.follows FOR SELECT USING (true);

DROP POLICY IF EXISTS "follows_manage_own" ON public.follows;
CREATE POLICY "follows_manage_own" ON public.follows
    FOR ALL TO authenticated USING (auth.uid() = follower_id) WITH CHECK (auth.uid() = follower_id);

DROP POLICY IF EXISTS "blocks_manage_own" ON public.blocks;
CREATE POLICY "blocks_manage_own" ON public.blocks
    FOR ALL TO authenticated USING (auth.uid() = blocker_id) WITH CHECK (auth.uid() = blocker_id);

DROP POLICY IF EXISTS "mutes_manage_own" ON public.mutes;
CREATE POLICY "mutes_manage_own" ON public.mutes
    FOR ALL TO authenticated USING (auth.uid() = muter_id) WITH CHECK (auth.uid() = muter_id);

DROP POLICY IF EXISTS "conversation_participants_member_select" ON public.conversation_participants;
CREATE POLICY "conversation_participants_member_select" ON public.conversation_participants
    FOR SELECT TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "moderation_appeals_owner_all" ON public.moderation_appeals;
CREATE POLICY "moderation_appeals_owner_all" ON public.moderation_appeals
    FOR ALL TO authenticated
    USING (auth.uid() = user_id OR public.is_admin_or_mod())
    WITH CHECK (auth.uid() = user_id);
