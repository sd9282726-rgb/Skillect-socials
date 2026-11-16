# Skillect repair progress

Checkpoint SHA before repair work: `2608ccd13787c7ebdd376377f0fa047cfda04743`

## Live Supabase changes already applied (project chohswyxsckpwyzlbzav)

1. **0011** `private.require_approved_post_media` + `guard_post_moderation_visibility`
   - Root cause of `Image is safely held but the post could not be created`
   - Trigger required pending media + `moderation_hidden=true`, but edge function published with `moderation_hidden=false`
   - service_role may now publish pending media visibly for async screening

2. **0012** `user_preferences.message_privacy` column (`everyone` | `following` | `nobody`)

3. **start-conversation** edge function redeployed (version 10)
   - Honors `message_privacy`
   - No longer forces `request_required` when recipient has privacy=everyone

## Client changes in progress
- Persist DM privacy from Settings (was UI-only; `updatePrivacySettings` ignored allowDmsFrom)
- DayNight theme parent to reduce light flash
- API/repo methods for user_preferences

## Security note
Rotate any Supabase PAT shared in chat.
