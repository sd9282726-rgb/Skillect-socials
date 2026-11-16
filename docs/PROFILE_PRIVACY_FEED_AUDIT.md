# Profile, privacy, feed design — implementation report

## Live backend (applied to chohswyxsckpwyzlbzav)

### 0014_private_profiles_follow_requests.sql
- Table `follow_requests` (pending/accepted/rejected) with RLS
- `private.is_profile_public_or_self` now allows **approved followers** to see private profiles/posts
- Profiles SELECT policy uses privacy rules (public OR self OR follower OR moderator)
- RPC `accept_follow_request` / `reject_follow_request`

### Prior (still in effect)
- 0011 media publish service_role
- 0012 message_privacy
- 0013 block cleans follows + blocks follow inserts

## Client (CI patches under docs/patches/)
- Compact feed header (remove 112dp hero banner)
- API + repository private follow → creates request when target is private
- Connect button shows Requested; status message on request sent
- Networks / Connected stats open live connection list dialog (respects private profile)
- Settings refreshes blocked accounts list on open

## Profile saving
- Updates go through `moderate-text` gateway then re-fetch profile
- Fields: display_name, website, bio, location, interests, avatar_url, cover_url
- Username changes still require careful uniqueness handling (kept from existing username on edit)

## Avatars
- Storage bucket `avatars` is public
- MediaUploadManager uploads avatar/cover; profile update persists URL via moderation gateway

## Not device-verified
- Full private-profile accept/reject UI surface for incoming requests (backend RPC ready)
- Avatar display consistency on every screen after upload
- Feed header visual on physical device light/dark
