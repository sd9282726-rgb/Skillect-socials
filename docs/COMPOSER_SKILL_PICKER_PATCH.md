# Composer skill picker patch

Replace the skill `OutlinedTextField` (comma-separated) in `ComposerScreen.kt` with multi-select chips.

1. Near other state vars, replace:
```kotlin
var skillInput by remember { mutableStateOf("") }
```
with:
```kotlin
val selectedSkills = remember { mutableStateListOf<String>() }
var customSkill by remember { mutableStateOf("") }
```

2. In publish handler, replace skill split with:
```kotlin
val skills = selectedSkills.toList()
```

3. Replace the skill OutlinedTextField UI with FlowRow of SkillChips from catalog
   (Kotlin, Android, UI Design, Python, Marketing, Writing, Video Editing, Photography, Music, Business, Product, Data Science, AI/ML) + optional custom skill field with Add (max 8 tags).

Also widen Publish control: `Modifier.widthIn(min = 110.dp)` on the Publish button.
