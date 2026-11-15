# Friendly user-facing copy (apply in SkillectRepository.kt)

Replace these exact strings:

- `Your message request is already pending.` → `You already sent a hello. Wait for them to accept.`
- `A message request is required before you can start this conversation.` → `Send a quick hello first. They will need to accept before you can chat.`
- `Messaging service returned an empty response.` → `Could not start the chat. Please try again.`
- `Conversation could not be created.` → `Could not open the chat. Please try again.`
- `Profile save could not be confirmed by Skillect safety screening.` → `Your profile could not be saved right now. Please try again.`
- `Profile update could not be confirmed by Skillect safety screening.` → `Your profile could not be updated right now. Please try again.`
- `Post could not be confirmed by Skillect safety screening.` → `Your post could not be published right now. Please try again.`
- `Comment could not be confirmed by Skillect safety screening.` → `Your comment could not be posted right now. Please try again.`
- `Message could not be confirmed by Skillect safety screening.` → `Your message could not be sent right now. Please try again.`

ProfileScreen display name:
```kotlin
text = profile?.displayName?.takeIf { it.isNotBlank() } ?: profile?.username?.takeIf { it.isNotBlank() } ?: if (uiState.isLoading) "Loading…" else "Member"
```
