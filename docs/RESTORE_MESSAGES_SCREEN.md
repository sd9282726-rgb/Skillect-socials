# CRITICAL: Restore MessagesScreen.kt

The file on `fix/onboarding` was accidentally reduced to a placeholder during an automated push.

## Fix (choose one)

1. **GitHub web/mobile**: Replace contents of
   `app/src/main/java/com/example/ui/messages/MessagesScreen.kt`
   with the full source from commit `4e108c2` **and** apply:

   - Remove `import java.time.Instant`
   - Add `import com.example.core.util.TimeCompat`
   - Replace Instant.parse unread logic with:

```kotlin
val hasUnreadMessages = TimeCompat.isAfter(
    conv.updatedAt,
    myReadMarker?.lastReadAt ?: conv.createdAt,
)
```

2. **Local**:
```bash
git checkout 4e108c2 -- app/src/main/java/com/example/ui/messages/MessagesScreen.kt
# then apply TimeCompat changes above
git commit -am "fix: restore MessagesScreen API24 TimeCompat"
git push origin fix/onboarding
```

## Already fixed on branch
- TimeCompat.kt
- MessagesNotificationsViewModel (TimeCompat.nowUtcIso)
- build.gradle.kts (coreLibraryDesugaring)

## CI failure root cause (resolved in VM)
`java.time.Instant.now()` requires API 26; minSdk is 24.
