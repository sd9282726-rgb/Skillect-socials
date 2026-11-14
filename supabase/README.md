# Supabase backend source

Project: `chohswyxsckpwyzlbzav`

The `functions/` directory mirrors the active Edge Function entrypoints retrieved from the connected Supabase project on 2026-11-14. The `config.toml` records the current JWT verification settings.

**Important:** these files are source snapshots only. Updating this repository does not deploy or change the live Supabase project. Review and explicitly approve any deployment before running `supabase functions deploy`.

The live database schema and migration history have not yet been exported into this repository. Do not treat this folder as a complete database baseline.

The versioned `moderate-text` source includes an un-deployed pre-release patch: profile updates now pass through text screening, profile fields are persisted consistently, age eligibility is rechecked, and custom profile images must already be approved in `media_assets`. The live function has **not** been updated by this repository change. Deploy only after the profile-media upload path and required secrets are reviewed.

Server-side secrets remain in Supabase, never in GitHub:
- `FIREBASE_SERVICE_ACCOUNT_JSON` for Android push delivery.
- `SIGHTENGINE_API_USER` and `SIGHTENGINE_API_SECRET` for text and media screening.
- Supabase runtime values such as `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` are supplied to Edge Functions by Supabase.

Never commit service-account JSON, service-role keys, or provider secrets.
