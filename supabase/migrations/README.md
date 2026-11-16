# Supabase migration inventory

Project: `chohswyxsckpwyzlbzav`

Live database inventory updated on 2026-11-16 after applying profile follow counts and tightening follow-request RPC permissions.

## Migration history

The live `supabase_migrations.schema_migrations` table contains **72 recorded application migrations**, from `20261106114348` through `20261116110209`.

| Version | Migration name |
|---|---|
| 20261106114348 | repair_conversation_rls_and_harden_functions |
| 20261106114602 | prevent_direct_messages_across_blocks |
| 20261106114807 | move_rls_helpers_to_private_schema |
| 20261106115014 | notification_events_and_device_token_registry |
| 20261106120724 | teen_safety_enforcement_and_media_gate |
| 20261106121823 | moderator_content_visibility_and_appeal_limits |
| 20261106122038 | moderator_comment_visibility |
| 20261106122517 | android_push_delivery_queue_and_trigger |
| 20261106122659 | android_push_delivery_queue_and_trigger |
| 20261106122745 | message_in_app_notifications |
| 20261106122759 | conversation_read_markers_for_unread_counts |
| 20261106122919 | moderation_action_audit_triggers |
| 20261106122935 | reports_target_user_reference |
| 20261106123048 | secure_moderation_evidence_snapshots |
| 20261106123429 | text_moderation_gateway_for_user_content |
| 20261106123546 | text_moderation_gateway_for_user_content |
| 20261106123552 | audit_text_moderation_reviews |
| 20261106123948 | moderator_rejected_media_review |
| 20261106124111 | audit_media_moderation_reviews |
| 20261106132138 | enable_realtime_publication_for_social_activity |
| 20261106132416 | index_foreign_keys_and_remove_duplicate_notification_index |
| 20261106132532 | optimize_rls_auth_uid_initplans |
| 20261106132658 | schedule_moderation_evidence_retention_cleanup |
| 20261106132914 | consolidate_overlapping_moderation_rls_policies |
| 20261106133010 | restrict_owner_moderator_updates_to_authenticated |
| 20261106141223 | mvp_video_manual_moderation |
| 20261106141629 | allow_private_video_uploads |
| 20261107041830 | instant_video_publish_with_async_moderation |
| 20261107044315 | allow_media_caption_moderation_cases |
| 20261107132124 | align_profiles_security_and_global_16_plus_age |
| 20261107132955 | require_verified_16_plus_profile_for_social_writes |
| 20261107133735 | respect_private_profile_visibility |
| 20261108132710 | harden_legacy_public_function_execution |
| 20261108132907 | keep_community_member_counts_in_sync |
| 20261108133046 | maintain_post_engagement_counters |
| 20261108133101 | add_private_user_preferences |
| 20261108133202 | move_age_gate_helper_to_private_schema |
| 20261108133422 | respect_user_notification_preferences |
| 20261108133759 | realtime_new_conversation_participants |
| 20261108134028 | notify_community_posts_after_media_approval |
| 20261108134108 | allow_service_role_to_read_user_preferences |
| 20261108134304 | close_private_community_policy_gaps |
| 20261109040100 | prevent_client_age_gate_bypass |
| 20261109040241 | moderate_community_creation |
| 20261109052915 | restrict_private_community_post_visibility |
| 20261109063155 | enforce_blocked_account_visibility |
| 20261109063220 | preserve_blocked_member_labels |
| 20261109063545 | private_community_media_and_message_requests |
| 20261109063552 | create_message_request_inbox |
| 20261109064035 | publish_message_requests_realtime |
| 20261109064317 | index_private_media_and_message_request_foreign_keys |
| 20261109064324 | allow_message_request_moderation_cases |
| 20261109064830 | preserve_private_media_member_access_after_visibility_change |
| 20261109064900 | fix_private_community_media_folder_authorization |
| 20261109065008 | qualify_private_media_storage_object_path |
| 20261109065526 | cascade_community_media_assets_on_delete |
| 20261109065537 | cascade_community_posts_on_delete |
| 20261109065547 | allow_moderator_private_media_review |
| 20261109065715 | fix_public_feed_and_project_rls_helpers |
| 20261111055556 | grant_conversation_participant_execute |
| 20261111055659 | use_private_conversation_rls_helper |
| 20261115060635 | conversation_reads_last_read_at_column_update |
| 20261115060739 | conversation_activity_timestamp |
| 20261115104741 | profile_avatar_storage |
| 20261115105052 | stories_private_media_storage |
| 20261115105128 | restrict_story_table_privileges |
| 20261115105423 | hold_stories_for_moderation |
| 20261115112013 | resolve_moderation_appeals |
| 20261115113302 | realtime_account_safety |
| 20261115122721 | realtime_media_and_safety_tables |
| 20261116084057 | profile_follow_counts |
| 20261116110209 | revoke_anon_reject_follow_request |

## Inventory findings

- The live database has 27 application tables in `public`.
- All 27 have Row Level Security enabled.
- All 27 have at least one RLS policy.
- None currently has FORCE ROW LEVEL SECURITY enabled. This is not automatically a defect for Supabase service-role operations; review table ownership and bypass roles before changing it.
- The live catalog now reports no public SECURITY DEFINER function executable by `anon`. Two SECURITY DEFINER RPCs remain executable by `authenticated`: `reject_follow_request` (recipient/moderator check in the function body) and `resolve_moderation_appeal` (moderator check in the function body). Their use is intentional for signed-in workflows but remains a security-review item.
- Two migration names occur twice, each with different version identifiers. This is valid migration history; do not rename or rewrite applied entries.
- `reject_follow_request(uuid)` had inherited `PUBLIC`/`anon` execution. Migration `20261116110209` revokes those grants while preserving `authenticated` and `service_role`; catalog privileges were rechecked after applying it.

## Important limitation

This file is an inventory of applied migration identifiers, **not the original migration SQL**. The Supabase migration listing endpoint exposes versions and names but did not provide the historical SQL bodies. The current live schema was inspected separately through PostgreSQL catalogs. Do not run this README as SQL or treat this inventory as a complete restorable database backup.

Before establishing a reproducible migration baseline, export the actual current schema (tables, types, constraints, indexes, functions, triggers, grants, policies, publications, and storage policies) using the Supabase CLI or Dashboard schema export. Keep production data and secrets out of Git.

## Android follow-up migration status

- `0006_profile_avatar_storage.sql` is applied. The live `avatars` bucket is public for profile-image display, limited to JPEGs up to 12 MiB, with owner-scoped uploads and deletes.
- `0007_conversation_participants_read_update.sql` was not run as a separate local migration because the live database already has the equivalent column-only `last_read_at` grant recorded as `20261115060635`.
- `0008_conversation_activity_timestamp.sql` is represented by the live `20261115060739` migration.
- `0009_story_media_storage.sql` is applied. It creates the missing `stories` table and a **private** `stories-media` bucket. Story reads respect public/private profiles, follows, expiry, and blocks. Media is delivered through short-lived signed URLs.
- `0011_hold_stories_for_moderation.sql` is applied. New stories default to `pending`; only their owner can view them until approved. This avoids exposing unreviewed media while Sightengine credentials are unavailable.
- `0010_restrict_story_table_privileges.sql` is applied. It removes broad default grants and leaves only SELECT for guests, and SELECT/INSERT/DELETE for authenticated users.

The old `0001`–`0005` files are an inspection/bootstrap sequence for a different schema baseline. Do not run them against the evolved live project. No user rows were deleted or rewritten by these follow-up migrations.

## Live Edge Function alignment (2026-11-16)

The following production functions were deployed from the matching source files on `fix/onboarding` and then read back from Supabase to verify the deployed source matched the GitHub file:

| Function | Production version | JWT verification | Source match |
|---|---:|---|---|
| `review-video-post` | 13 | Enabled | Exact |
| `respond-message-request` | 8 | Enabled | Exact |

- `review-video-post` now retains the private-community destination bucket when a moderator approves media; it no longer forces every approved item into the public `post-media` bucket.
- `respond-message-request` now applies the 16–17-year-old recipient rule before accepting a request: the teen recipient must already follow the sender.
- Both functions still require a valid user JWT at the platform gateway and independently verify the caller and authorization in their handlers.
- The other nine production functions were already reported as matching their branch source in the preceding audit. Re-run a full source comparison before any future production release.

## Remaining dashboard and verification items

- Security Advisor still reports leaked-password protection disabled. Supabase documents this as a Pro-plan-and-above Auth setting; if this project is on Free, the setting cannot be enabled without upgrading. On an eligible plan, enable it under Authentication → Sign In / Providers → Email. See https://supabase.com/docs/guides/auth/password-security.
- Two signed-in SECURITY DEFINER RPCs remain flagged: `reject_follow_request` and `resolve_moderation_appeal`. They retain authenticated access for their respective signed-in workflows and perform authorization checks in their function bodies. Do not revoke authenticated execution or convert them to SECURITY INVOKER without a reviewed replacement.
- Android lint, unit tests, Gradle compilation, and device tests have not been run for the current branch head. Do not treat source inspection or successful Edge Function deployment as Android build verification.
