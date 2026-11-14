-- ============================================================================
-- SKILLECT — STEP 4: COUNTERS, PRIVACY HARDENING, DM/TOKEN/DELETION RPCs & STORAGE RLS
-- STAGED ONLY — DO NOT EXECUTE ON PRODUCTION WITHOUT EXPLICIT USER APPROVAL.
-- Idempotent and non-destructive. Safe for existing Supabase projects.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. SECURITY-INVOKER STORIES VIEW (Prevents view RLS bypass)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.active_stories
WITH (security_invoker = true) AS
SELECT * FROM public.stories
WHERE expires_at > now();

GRANT SELECT ON public.active_stories TO anon, authenticated;

-- ----------------------------------------------------------------------------
-- 2. PRIVILEGED & COUNTER COLUMN PROTECTION TRIGGERS
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.protect_profile_privileged_columns()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF current_setting('role', true) = 'service_role'
       OR current_setting('app.internal_trigger', true) = 'on'
       OR public.is_admin_or_mod() THEN
        RETURN NEW;
    END IF;

    IF NEW.role IS DISTINCT FROM OLD.role
       OR NEW.is_verified IS DISTINCT FROM OLD.is_verified
       OR NEW.account_status IS DISTINCT FROM OLD.account_status
       OR NEW.followers_count IS DISTINCT FROM OLD.followers_count
       OR NEW.following_count IS DISTINCT FROM OLD.following_count
       OR NEW.posts_count IS DISTINCT FROM OLD.posts_count THEN
        RAISE EXCEPTION 'Unauthorized modification of protected profile fields.';
    END IF;

    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_profile_privileged ON public.profiles;
CREATE TRIGGER trg_protect_profile_privileged
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.protect_profile_privileged_columns();

CREATE OR REPLACE FUNCTION public.protect_post_counters()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF current_setting('role', true) = 'service_role'
       OR current_setting('app.internal_trigger', true) = 'on'
       OR public.is_admin_or_mod() THEN
        RETURN NEW;
    END IF;

    IF NEW.likes_count IS DISTINCT FROM OLD.likes_count
       OR NEW.comments_count IS DISTINCT FROM OLD.comments_count
       OR NEW.reposts_count IS DISTINCT FROM OLD.reposts_count
       OR NEW.moderation_status IS DISTINCT FROM OLD.moderation_status THEN
        RAISE EXCEPTION 'Unauthorized modification of protected post counters or status.';
    END IF;

    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_post_counters ON public.posts;
CREATE TRIGGER trg_protect_post_counters
    BEFORE UPDATE ON public.posts
    FOR EACH ROW
    EXECUTE FUNCTION public.protect_post_counters();

-- ----------------------------------------------------------------------------
-- 3. DENORMALIZED COUNTER & NOTIFICATION TRIGGERS
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_post_like_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_author_id uuid;
BEGIN
    PERFORM set_config('app.internal_trigger', 'on', true);
    IF TG_OP = 'INSERT' THEN
        UPDATE public.posts SET likes_count = likes_count + 1 WHERE id = NEW.post_id RETURNING author_id INTO v_author_id;
        IF v_author_id IS NOT NULL AND v_author_id <> NEW.user_id THEN
            INSERT INTO public.notifications (recipient_id, actor_id, type, entity_id, title, body)
            VALUES (v_author_id, NEW.user_id, 'like', NEW.post_id, 'New like on your post', 'Someone liked your Skillect post.');
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.posts SET likes_count = GREATEST(0, likes_count - 1) WHERE id = OLD.post_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_post_like_change ON public.post_likes;
CREATE TRIGGER trg_post_like_change
    AFTER INSERT OR DELETE ON public.post_likes
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_post_like_change();

CREATE OR REPLACE FUNCTION public.handle_post_comment_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_post_author uuid;
BEGIN
    PERFORM set_config('app.internal_trigger', 'on', true);
    IF TG_OP = 'INSERT' THEN
        UPDATE public.posts SET comments_count = comments_count + 1 WHERE id = NEW.post_id RETURNING author_id INTO v_post_author;
        IF v_post_author IS NOT NULL AND v_post_author <> NEW.author_id THEN
            INSERT INTO public.notifications (recipient_id, actor_id, type, entity_id, title, body)
            VALUES (
                v_post_author,
                NEW.author_id,
                CASE WHEN NEW.parent_comment_id IS NOT NULL THEN 'reply' ELSE 'comment' END,
                NEW.post_id,
                CASE WHEN NEW.parent_comment_id IS NOT NULL THEN 'New reply in discussion' ELSE 'New comment on your post' END,
                left(NEW.content, 120)
            );
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.posts SET comments_count = GREATEST(0, comments_count - 1) WHERE id = OLD.post_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_post_comment_change ON public.post_comments;
CREATE TRIGGER trg_post_comment_change
    AFTER INSERT OR DELETE ON public.post_comments
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_post_comment_change();

CREATE OR REPLACE FUNCTION public.handle_post_insert_or_delete()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_target uuid;
    v_target_author uuid;
BEGIN
    PERFORM set_config('app.internal_trigger', 'on', true);
    IF TG_OP = 'INSERT' THEN
        UPDATE public.profiles SET posts_count = posts_count + 1 WHERE id = NEW.author_id;
        v_target := COALESCE(NEW.reposted_post_id, NEW.quoted_post_id);
        IF v_target IS NOT NULL THEN
            UPDATE public.posts SET reposts_count = reposts_count + 1 WHERE id = v_target RETURNING author_id INTO v_target_author;
            IF v_target_author IS NOT NULL AND v_target_author <> NEW.author_id THEN
                INSERT INTO public.notifications (recipient_id, actor_id, type, entity_id, title, body)
                VALUES (v_target_author, NEW.author_id, 'repost', v_target, 'Your post was shared', left(NEW.content, 120));
            END IF;
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.profiles SET posts_count = GREATEST(0, posts_count - 1) WHERE id = OLD.author_id;
        v_target := COALESCE(OLD.reposted_post_id, OLD.quoted_post_id);
        IF v_target IS NOT NULL THEN
            UPDATE public.posts SET reposts_count = GREATEST(0, reposts_count - 1) WHERE id = v_target;
        END IF;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_post_insert_or_delete ON public.posts;
CREATE TRIGGER trg_post_insert_or_delete
    AFTER INSERT OR DELETE ON public.posts
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_post_insert_or_delete();

CREATE OR REPLACE FUNCTION public.handle_follow_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    PERFORM set_config('app.internal_trigger', 'on', true);
    IF TG_OP = 'INSERT' THEN
        UPDATE public.profiles SET following_count = following_count + 1 WHERE id = NEW.follower_id;
        UPDATE public.profiles SET followers_count = followers_count + 1 WHERE id = NEW.following_id;
        INSERT INTO public.notifications (recipient_id, actor_id, type, entity_id, title, body)
        VALUES (NEW.following_id, NEW.follower_id, 'follow', NEW.follower_id, 'New follower', 'Started following your Skillect profile.');
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.profiles SET following_count = GREATEST(0, following_count - 1) WHERE id = OLD.follower_id;
        UPDATE public.profiles SET followers_count = GREATEST(0, followers_count - 1) WHERE id = OLD.following_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_follow_change ON public.follows;
CREATE TRIGGER trg_follow_change
    AFTER INSERT OR DELETE ON public.follows
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_follow_change();

CREATE OR REPLACE FUNCTION public.handle_community_member_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.communities SET members_count = members_count + 1 WHERE id = NEW.community_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.communities SET members_count = GREATEST(0, members_count - 1) WHERE id = OLD.community_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_community_member_change ON public.community_members;
CREATE TRIGGER trg_community_member_change
    AFTER INSERT OR DELETE ON public.community_members
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_community_member_change();

CREATE OR REPLACE FUNCTION public.handle_poll_vote_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.poll_options SET votes_count = votes_count + 1 WHERE id = NEW.option_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.poll_options SET votes_count = GREATEST(0, votes_count - 1) WHERE id = OLD.option_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_poll_vote_change ON public.poll_votes;
CREATE TRIGGER trg_poll_vote_change
    AFTER INSERT OR DELETE ON public.poll_votes
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_poll_vote_change();

CREATE OR REPLACE FUNCTION public.handle_new_message_updates()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE public.conversations SET updated_at = now() WHERE id = NEW.conversation_id;
    INSERT INTO public.notifications (recipient_id, actor_id, type, entity_id, title, body)
    SELECT cp.user_id, NEW.sender_id, 'message', NEW.conversation_id, 'New direct message', left(COALESCE(NULLIF(NEW.content, ''), 'Sent an attachment'), 120)
    FROM public.conversation_participants cp
    WHERE cp.conversation_id = NEW.conversation_id
      AND cp.user_id <> NEW.sender_id
      AND NOT public.is_blocked_between(cp.user_id, NEW.sender_id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_new_message_updates ON public.messages;
CREATE TRIGGER trg_new_message_updates
    AFTER INSERT ON public.messages
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_message_updates();

-- ----------------------------------------------------------------------------
-- 4. CONVERSATION PARTICIPANTS RLS FIX & PRIVACY TIGHTENING
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_conversation_member(conv_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.conversation_participants cp
        WHERE cp.conversation_id = conv_id AND cp.user_id = auth.uid()
    );
$$;

DROP POLICY IF EXISTS "conversation_participants_member_select" ON public.conversation_participants;
CREATE POLICY "conversation_participants_member_select" ON public.conversation_participants
    FOR SELECT TO authenticated
    USING (public.is_conversation_member(conversation_id));

CREATE OR REPLACE FUNCTION public.can_view_profile_section(target_uid uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles p
        WHERE p.id = target_uid
          AND (
              (auth.role() = 'anon' AND p.account_status = 'active' AND p.is_private = false)
              OR (
                  auth.role() = 'authenticated'
                  AND (
                      p.id = auth.uid()
                      OR (
                          p.account_status = 'active'
                          AND NOT public.is_blocked_between(auth.uid(), p.id)
                          AND (
                              p.is_private = false
                              OR EXISTS (
                                  SELECT 1 FROM public.follows f
                                  WHERE f.follower_id = auth.uid() AND f.following_id = p.id
                              )
                          )
                      )
                  )
              )
          )
    );
$$;

DROP POLICY IF EXISTS "projects_select_all" ON public.projects;
CREATE POLICY "projects_select_all" ON public.projects
    FOR SELECT USING (public.can_view_profile_section(owner_id));

DROP POLICY IF EXISTS "experiences_select_all" ON public.experiences;
CREATE POLICY "experiences_select_all" ON public.experiences
    FOR SELECT USING (public.can_view_profile_section(user_id));

DROP POLICY IF EXISTS "education_select_all" ON public.education;
CREATE POLICY "education_select_all" ON public.education
    FOR SELECT USING (public.can_view_profile_section(user_id));

DROP POLICY IF EXISTS "achievements_select_all" ON public.achievements;
CREATE POLICY "achievements_select_all" ON public.achievements
    FOR SELECT USING (public.can_view_profile_section(user_id));

DROP POLICY IF EXISTS "user_skills_select_all" ON public.user_skills;
CREATE POLICY "user_skills_select_all" ON public.user_skills
    FOR SELECT USING (public.can_view_profile_section(user_id));

-- ----------------------------------------------------------------------------
-- 5. SAFE RPCs FOR DM INITIATION, FCM DEVICE TOKENS & ACCOUNT DELETION
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_or_get_direct_conversation(p_peer_id uuid)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_me uuid := auth.uid();
    v_existing uuid;
    v_peer_allow_dms text;
    v_peer_status text;
BEGIN
    IF v_me IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;
    IF p_peer_id IS NULL OR p_peer_id = v_me THEN
        RAISE EXCEPTION 'Invalid conversation peer.';
    END IF;
    IF public.is_blocked_between(v_me, p_peer_id) THEN
        RAISE EXCEPTION 'Cannot message this user due to block settings.';
    END IF;

    SELECT allow_dms_from, account_status
    INTO v_peer_allow_dms, v_peer_status
    FROM public.profiles
    WHERE id = p_peer_id;

    IF v_peer_status IS NULL OR v_peer_status <> 'active' THEN
        RAISE EXCEPTION 'Recipient account is not active.';
    END IF;

    IF v_peer_allow_dms = 'nobody' THEN
        RAISE EXCEPTION 'This member does not accept direct messages.';
    ELSIF v_peer_allow_dms = 'following_only' THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.follows
            WHERE follower_id = p_peer_id AND following_id = v_me
        ) THEN
            RAISE EXCEPTION 'This member only accepts direct messages from accounts they follow.';
        END IF;
    END IF;

    SELECT cp1.conversation_id INTO v_existing
    FROM public.conversation_participants cp1
    JOIN public.conversation_participants cp2 ON cp1.conversation_id = cp2.conversation_id
    WHERE cp1.user_id = v_me AND cp2.user_id = p_peer_id
      AND (SELECT count(*) FROM public.conversation_participants cp3 WHERE cp3.conversation_id = cp1.conversation_id) = 2
    LIMIT 1;

    IF v_existing IS NOT NULL THEN
        RETURN v_existing;
    END IF;

    INSERT INTO public.conversations DEFAULT VALUES RETURNING id INTO v_existing;
    INSERT INTO public.conversation_participants (conversation_id, user_id)
    VALUES (v_existing, v_me), (v_existing, p_peer_id);

    RETURN v_existing;
END;
$$;

REVOKE ALL ON FUNCTION public.create_or_get_direct_conversation(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_or_get_direct_conversation(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.upsert_my_device_token(
    p_fcm_token text,
    p_platform text DEFAULT 'android'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_me uuid := auth.uid();
BEGIN
    IF v_me IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;
    IF p_fcm_token IS NULL OR length(trim(p_fcm_token)) = 0 THEN
        RETURN;
    END IF;

    INSERT INTO public.user_device_tokens (user_id, fcm_token, platform, updated_at)
    VALUES (v_me, trim(p_fcm_token), COALESCE(NULLIF(trim(p_platform), ''), 'android'), now())
    ON CONFLICT (fcm_token) DO UPDATE
    SET user_id = EXCLUDED.user_id,
        platform = EXCLUDED.platform,
        updated_at = now();
END;
$$;

REVOKE ALL ON FUNCTION public.upsert_my_device_token(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.upsert_my_device_token(text, text) TO authenticated;

CREATE OR REPLACE FUNCTION public.request_account_deletion()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_me uuid := auth.uid();
BEGIN
    IF v_me IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    PERFORM set_config('app.internal_trigger', 'on', true);
    UPDATE public.profiles
    SET account_status = 'pending_deletion',
        updated_at = now()
    WHERE id = v_me;

    DELETE FROM public.user_device_tokens WHERE user_id = v_me;
END;
$$;

REVOKE ALL ON FUNCTION public.request_account_deletion() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.request_account_deletion() TO authenticated;

-- ----------------------------------------------------------------------------
-- 6. STORAGE BUCKETS & ZERO-TRUST STORAGE RLS POLICIES
-- ----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('avatars', 'avatars', true),
    ('post-media', 'post-media', true),
    ('stories-media', 'stories-media', true),
    ('chat-media', 'chat-media', false)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "skillect_public_media_read" ON storage.objects;
CREATE POLICY "skillect_public_media_read" ON storage.objects
    FOR SELECT
    USING (bucket_id IN ('avatars', 'post-media', 'stories-media'));

DROP POLICY IF EXISTS "skillect_chat_media_read" ON storage.objects;
CREATE POLICY "skillect_chat_media_read" ON storage.objects
    FOR SELECT TO authenticated
    USING (
        bucket_id = 'chat-media'
        AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR EXISTS (
                SELECT 1
                FROM public.conversation_participants cp_me
                JOIN public.conversation_participants cp_owner
                  ON cp_me.conversation_id = cp_owner.conversation_id
                WHERE cp_me.user_id = auth.uid()
                  AND cp_owner.user_id::text = (storage.foldername(name))[1]
            )
        )
    );

DROP POLICY IF EXISTS "skillect_media_owner_insert" ON storage.objects;
CREATE POLICY "skillect_media_owner_insert" ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id IN ('avatars', 'post-media', 'stories-media', 'chat-media')
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "skillect_media_owner_delete" ON storage.objects;
CREATE POLICY "skillect_media_owner_delete" ON storage.objects
    FOR DELETE TO authenticated
    USING (
        bucket_id IN ('avatars', 'post-media', 'stories-media', 'chat-media')
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
