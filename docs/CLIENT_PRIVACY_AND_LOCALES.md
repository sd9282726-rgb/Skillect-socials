# Client privacy + locales progress

## message_privacy persistence
- Backend: `user_preferences.message_privacy` (`everyone|following|nobody`) live.
- Edge: `start-conversation` v10 honors privacy.
- Client: `scripts/apply_privacy_client.sh` injects API + repo + ViewModel wiring before CI builds.
- Settings chips use localized labels and call `updateMessagePrivacy`.

## Locales
All 9 language folders updated with nav/DM/privacy strings:
en, hi, es, fr, de, pt, ja, ko, ar.

Many other screens still contain hardcoded English — full i18n is incremental.

## Social (live DB snapshot)
- profiles: 12
- follows: 2
- posts: 0 (pre-fix; image publish trigger was blocking)
- messages: 0

`toggleFollow` uses real follows table with RLS.

## Sightengine
Secrets present: `SIGHTENGINE_API_USER`, `SIGHTENGINE_API_SECRET`.
Media path publishes then screens asynchronously after migration 0011.
