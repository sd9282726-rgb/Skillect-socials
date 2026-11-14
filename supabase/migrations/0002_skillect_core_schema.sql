-- ============================================================================
-- SKILLECT — STEP 2: IDEMPOTENT, NON-DESTRUCTIVE CORE SCHEMA & RLS MIGRATION
-- Safe for existing Supabase projects: uses IF NOT EXISTS and ADD COLUMN IF NOT EXISTS.
-- Never drops tables or deletes existing user/post data.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ----------------------------------------------------------------------------
-- 1. PROFILES (Canonical user identity extending auth.users)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
    id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username text UNIQUE NOT NULL,
    display_name text NOT NULL,
    bio text DEFAULT '' NOT NULL,
    headline text DEFAULT '' NOT NULL,
    avatar_url text,
    cover_url text,
    country_code text DEFAULT 'US' NOT NULL,
    region_label text DEFAULT '' NOT NULL,
    timezone text DEFAULT 'UTC' NOT NULL,
    locale text DEFAULT 'en' NOT NULL,
    birth_date date,
    is_minor_safe_defaults boolean DEFAULT true NOT NULL,
    allow_dms_from text DEFAULT 'following_only' NOT NULL CHECK (allow_dms_from IN ('everyone', 'following_only', 'nobody')),
    is_private boolean DEFAULT false NOT NULL,
    is_verified boolean DEFAULT false NOT NULL,
    role text DEFAULT 'member' NOT NULL CHECK (role IN ('member', 'moderator', 'admin')),
    account_status text DEFAULT 'active' NOT NULL CHECK (account_status IN ('active', 'suspended', 'banned', 'pending_deletion')),
    portfolio_links jsonb DEFAULT '[]'::jsonb NOT NULL,
    interests text[] DEFAULT '{}'::text[] NOT NULL,
    followers_count integer DEFAULT 0 NOT NULL,
    following_count integer DEFAULT 0 NOT NULL,
    posts_count integer DEFAULT 0 NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT username_format CHECK (username ~ '^[a-zA-Z0-9_.]{3,30}$'),
    CONSTRAINT min_age_16 CHECK (birth_date IS NULL OR birth_date <= (CURRENT_DATE - INTERVAL '16 years'))
);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- ----------------------------------------------------------------------------
-- 2. SKILLS & PROFESSIONAL IDENTITY
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.skills (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text UNIQUE NOT NULL,
    category text DEFAULT 'General' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.user_skills (
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    skill_id uuid NOT NULL REFERENCES public.skills(id) ON DELETE CASCADE,
    proficiency text DEFAULT 'intermediate' CHECK (proficiency IN ('beginner', 'intermediate', 'advanced', 'expert')),
    created_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (user_id, skill_id)
);

CREATE TABLE IF NOT EXISTS public.experiences (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title text NOT NULL,
    organization text NOT NULL,
    location_label text DEFAULT '' NOT NULL,
    start_date date NOT NULL,
    end_date date,
    is_current boolean DEFAULT false NOT NULL,
    description text DEFAULT '' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.education (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    institution text NOT NULL,
    degree text NOT NULL,
    field_of_study text DEFAULT '' NOT NULL,
    start_year integer,
    end_year integer,
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.achievements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title text NOT NULL,
    issuer text DEFAULT '' NOT NULL,
    awarded_at date,
    description text DEFAULT '' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);

-- ----------------------------------------------------------------------------
-- 3. PROJECTS SHOWCASE
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.projects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title text NOT NULL,
    description text NOT NULL,
    category text DEFAULT 'Technology' NOT NULL,
    cover_url text,
    external_links jsonb DEFAULT '[]'::jsonb NOT NULL,
    skills_used text[] DEFAULT '{}'::text[] NOT NULL,
    collaborators_note text DEFAULT '' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL
);

-- ----------------------------------------------------------------------------
-- 4. COMMUNITIES & GROUPS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.communities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug text UNIQUE NOT NULL,
    name text NOT NULL,
    description text DEFAULT '' NOT NULL,
    category text NOT NULL,
    banner_url text,
    avatar_url text,
    is_private boolean DEFAULT false NOT NULL,
    creator_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    members_count integer DEFAULT 1 NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.community_members (
    community_id uuid NOT NULL REFERENCES public.communities(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role text DEFAULT 'member' NOT NULL CHECK (role IN ('owner', 'moderator', 'member')),
    joined_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (community_id, user_id)
);

-- ----------------------------------------------------------------------------
-- 5. POSTS, POLLS, LIKES, THREADED COMMENTS & SAVED COLLECTIONS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.posts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    post_type text DEFAULT 'text' NOT NULL CHECK (post_type IN (
        'text', 'image', 'video', 'carousel', 'link', 'poll',
        'project_update', 'professional_update', 'long_discussion', 'repost', 'quote'
    )),
    content text DEFAULT '' NOT NULL,
    media_urls text[] DEFAULT '{}'::text[] NOT NULL,
    media_types text[] DEFAULT '{}'::text[] NOT NULL,
    link_url text,
    link_title text,
    hashtags text[] DEFAULT '{}'::text[] NOT NULL,
    mentions text[] DEFAULT '{}'::text[] NOT NULL,
    skill_tags text[] DEFAULT '{}'::text[] NOT NULL,
    project_id uuid REFERENCES public.projects(id) ON DELETE SET NULL,
    community_id uuid REFERENCES public.communities(id) ON DELETE CASCADE,
    reposted_post_id uuid REFERENCES public.posts(id) ON DELETE SET NULL,
    quoted_post_id uuid REFERENCES public.posts(id) ON DELETE SET NULL,
    likes_count integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    reposts_count integer DEFAULT 0 NOT NULL,
    moderation_status text DEFAULT 'approved' NOT NULL CHECK (moderation_status IN ('approved', 'flagged', 'removed')),
    is_edited boolean DEFAULT false NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.poll_options (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    option_text text NOT NULL,
    votes_count integer DEFAULT 0 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS public.poll_votes (
    post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    option_id uuid NOT NULL REFERENCES public.poll_options(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.post_likes (
    post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.post_comments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    author_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    parent_comment_id uuid REFERENCES public.post_comments(id) ON DELETE CASCADE,
    content text NOT NULL,
    moderation_status text DEFAULT 'approved' NOT NULL CHECK (moderation_status IN ('approved', 'flagged', 'removed')),
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.saved_collections (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    name text NOT NULL,
    is_private boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.saved_posts (
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    collection_id uuid REFERENCES public.saved_collections(id) ON DELETE SET NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (user_id, post_id)
);

-- ----------------------------------------------------------------------------
-- 6. STORIES (24-Hour Server-Side Expiration)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.stories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    media_url text NOT NULL,
    media_type text DEFAULT 'image' NOT NULL CHECK (media_type IN ('image', 'video')),
    caption text DEFAULT '' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    expires_at timestamptz DEFAULT (now() + INTERVAL '24 hours') NOT NULL
);

CREATE TABLE IF NOT EXISTS public.story_views (
    story_id uuid NOT NULL REFERENCES public.stories(id) ON DELETE CASCADE,
    viewer_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    viewed_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (story_id, viewer_id)
);

CREATE OR REPLACE VIEW public.active_stories AS
SELECT * FROM public.stories
WHERE expires_at > now();

-- ----------------------------------------------------------------------------
-- 7. SOCIAL GRAPH, BLOCKS & MUTES
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.follows (
    follower_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    following_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT no_self_follow CHECK (follower_id <> following_id)
);

CREATE TABLE IF NOT EXISTS public.blocks (
    blocker_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    blocked_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT no_self_block CHECK (blocker_id <> blocked_id)
);

CREATE TABLE IF NOT EXISTS public.mutes (
    muter_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    muted_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (muter_id, muted_id)
);

-- ----------------------------------------------------------------------------
-- 8. REAL-TIME MESSAGING
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.conversation_participants (
    conversation_id uuid NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    last_read_at timestamptz,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content text DEFAULT '' NOT NULL,
    media_url text,
    delivery_status text DEFAULT 'sent' NOT NULL CHECK (delivery_status IN ('sent', 'delivered', 'read')),
    moderation_status text DEFAULT 'approved' NOT NULL CHECK (moderation_status IN ('approved', 'flagged', 'removed')),
    created_at timestamptz DEFAULT now() NOT NULL
);

-- ----------------------------------------------------------------------------
-- 9. NOTIFICATIONS & FIREBASE DEVICE TOKENS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    actor_id uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
    type text NOT NULL CHECK (type IN (
        'follow', 'like', 'comment', 'reply', 'mention',
        'repost', 'message', 'community', 'project', 'safety'
    )),
    entity_id uuid,
    title text NOT NULL,
    body text DEFAULT '' NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.user_device_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    fcm_token text UNIQUE NOT NULL,
    platform text DEFAULT 'android' NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL
);

-- ----------------------------------------------------------------------------
-- 10. SAFETY, MODERATION & AUDIT LOGS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.reports (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    target_type text NOT NULL CHECK (target_type IN ('post', 'comment', 'profile', 'message', 'community')),
    target_id uuid NOT NULL,
    reason text NOT NULL CHECK (reason IN (
        'csam_exploitation', 'grooming', 'harassment_bullying', 'threats_violence',
        'hate_content', 'explicit_sexual', 'scam_spam', 'impersonation', 'malicious_link', 'other'
    )),
    details text DEFAULT '' NOT NULL,
    status text DEFAULT 'pending' NOT NULL CHECK (status IN ('pending', 'reviewing', 'action_taken', 'dismissed')),
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.moderation_appeals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reason text NOT NULL,
    status text DEFAULT 'pending' NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewer_note text DEFAULT '' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.moderation_audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_admin_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    action text NOT NULL,
    target_type text NOT NULL,
    target_id uuid NOT NULL,
    notes text DEFAULT '' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);

-- ----------------------------------------------------------------------------
-- 11. ROW LEVEL SECURITY (ZERO-TRUST POLICIES)
-- ----------------------------------------------------------------------------
ALTER TABLE public.skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.experiences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.education ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.achievements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.communities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.poll_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.poll_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.post_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.post_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_collections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.story_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mutes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_device_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.moderation_appeals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.moderation_audit_logs ENABLE ROW LEVEL SECURITY;

-- Helper: server-side admin check
CREATE OR REPLACE FUNCTION public.is_admin_or_mod()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role IN ('admin', 'moderator') AND account_status = 'active'
    );
$$;

-- Helper: block check between current user and target
CREATE OR REPLACE FUNCTION public.is_blocked_between(a uuid, b uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.blocks
        WHERE (blocker_id = a AND blocked_id = b) OR (blocker_id = b AND blocked_id = a)
    );
$$;

-- Profiles policies
DROP POLICY IF EXISTS "profiles_select_visible" ON public.profiles;
CREATE POLICY "profiles_select_visible" ON public.profiles
    FOR SELECT USING (
        account_status = 'active'
        AND NOT public.is_blocked_between(auth.uid(), id)
    );

DROP POLICY IF EXISTS "profiles_insert_own" ON public.profiles;
CREATE POLICY "profiles_insert_own" ON public.profiles
    FOR INSERT WITH CHECK (auth.uid() = id AND role = 'member');

DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;
CREATE POLICY "profiles_update_own" ON public.profiles
    FOR UPDATE USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Posts policies
DROP POLICY IF EXISTS "posts_select_approved" ON public.posts;
CREATE POLICY "posts_select_approved" ON public.posts
    FOR SELECT USING (
        (moderation_status = 'approved' OR author_id = auth.uid() OR public.is_admin_or_mod())
        AND NOT public.is_blocked_between(auth.uid(), author_id)
    );

DROP POLICY IF EXISTS "posts_insert_own" ON public.posts;
CREATE POLICY "posts_insert_own" ON public.posts
    FOR INSERT WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "posts_update_own_or_mod" ON public.posts;
CREATE POLICY "posts_update_own_or_mod" ON public.posts
    FOR UPDATE USING (auth.uid() = author_id OR public.is_admin_or_mod());

DROP POLICY IF EXISTS "posts_delete_own_or_mod" ON public.posts;
CREATE POLICY "posts_delete_own_or_mod" ON public.posts
    FOR DELETE USING (auth.uid() = author_id OR public.is_admin_or_mod());

-- Stories policies (24-hour server-side expiry enforced in SELECT)
DROP POLICY IF EXISTS "stories_select_unexpired" ON public.stories;
CREATE POLICY "stories_select_unexpired" ON public.stories
    FOR SELECT USING (
        expires_at > now()
        AND NOT public.is_blocked_between(auth.uid(), author_id)
    );

DROP POLICY IF EXISTS "stories_insert_own" ON public.stories;
CREATE POLICY "stories_insert_own" ON public.stories
    FOR INSERT WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "stories_delete_own" ON public.stories;
CREATE POLICY "stories_delete_own" ON public.stories
    FOR DELETE USING (auth.uid() = author_id);

-- Saved collections & saved posts (strictly private to owner)
DROP POLICY IF EXISTS "saved_collections_owner_all" ON public.saved_collections;
CREATE POLICY "saved_collections_owner_all" ON public.saved_collections
    FOR ALL USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

DROP POLICY IF EXISTS "saved_posts_owner_all" ON public.saved_posts;
CREATE POLICY "saved_posts_owner_all" ON public.saved_posts
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- Messaging policies (only conversation members can access)
DROP POLICY IF EXISTS "conversations_member_select" ON public.conversations;
CREATE POLICY "conversations_member_select" ON public.conversations
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.conversation_participants cp
            WHERE cp.conversation_id = id AND cp.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "messages_member_select" ON public.messages;
CREATE POLICY "messages_member_select" ON public.messages
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.conversation_participants cp
            WHERE cp.conversation_id = conversation_id AND cp.user_id = auth.uid()
        )
        AND NOT public.is_blocked_between(auth.uid(), sender_id)
    );

DROP POLICY IF EXISTS "messages_member_insert" ON public.messages;
CREATE POLICY "messages_member_insert" ON public.messages
    FOR INSERT WITH CHECK (
        auth.uid() = sender_id
        AND EXISTS (
            SELECT 1 FROM public.conversation_participants cp
            WHERE cp.conversation_id = conversation_id AND cp.user_id = auth.uid()
        )
    );

-- Notifications & Device Tokens
DROP POLICY IF EXISTS "notifications_recipient_all" ON public.notifications;
CREATE POLICY "notifications_recipient_all" ON public.notifications
    FOR ALL USING (auth.uid() = recipient_id) WITH CHECK (auth.uid() = recipient_id);

DROP POLICY IF EXISTS "device_tokens_owner_all" ON public.user_device_tokens;
CREATE POLICY "device_tokens_owner_all" ON public.user_device_tokens
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- Reports & Moderation (server-side role gate)
DROP POLICY IF EXISTS "reports_insert_auth" ON public.reports;
CREATE POLICY "reports_insert_auth" ON public.reports
    FOR INSERT WITH CHECK (auth.uid() = reporter_id);

DROP POLICY IF EXISTS "reports_select_own_or_mod" ON public.reports;
CREATE POLICY "reports_select_own_or_mod" ON public.reports
    FOR SELECT USING (auth.uid() = reporter_id OR public.is_admin_or_mod());

DROP POLICY IF EXISTS "reports_update_mod" ON public.reports;
CREATE POLICY "reports_update_mod" ON public.reports
    FOR UPDATE USING (public.is_admin_or_mod());

DROP POLICY IF EXISTS "audit_logs_mod_only" ON public.moderation_audit_logs;
CREATE POLICY "audit_logs_mod_only" ON public.moderation_audit_logs
    FOR ALL USING (public.is_admin_or_mod()) WITH CHECK (public.is_admin_or_mod());

-- ----------------------------------------------------------------------------
-- 12. PERFORMANCE INDEXES
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON public.posts (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_author_id ON public.posts (author_id);
CREATE INDEX IF NOT EXISTS idx_posts_community_id ON public.posts (community_id);
CREATE INDEX IF NOT EXISTS idx_stories_expires_at ON public.stories (expires_at);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON public.notifications (recipient_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON public.messages (conversation_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_profiles_username_trgm ON public.profiles USING gin (username gin_trgm_ops);
