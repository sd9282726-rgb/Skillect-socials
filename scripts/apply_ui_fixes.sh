#!/usr/bin/env bash
set -euo pipefail
echo "Applying surgical UI fixes..."

python3 <<'PY'
from pathlib import Path

# PrimaryButton + SkillChip softWrap
p = Path("app/src/main/java/com/example/core/designsystem/Components.kt")
t = p.read_text()
old = "        Text(text = text, style = MaterialTheme.typography.labelLarge)\n    }\n}\n\n@Composable\nfun SkillectOutlinedButton("
new = """        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SkillectOutlinedButton("""
if old in t:
    t = t.replace(old, new, 1)
    print("PrimaryButton softWrap fixed")
else:
    print("PrimaryButton already fixed or pattern missing")
needle = """                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,"""
repl = """                color = fg,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,"""
if needle in t and "softWrap = false" not in t[t.find("fun SkillChip"):t.find("fun SkillChip")+800]:
    t = t.replace(needle, repl, 1)
    print("SkillChip softWrap fixed")
p.write_text(t)

# Profile name
p = Path("app/src/main/java/com/example/ui/profile/ProfileScreen.kt")
t = p.read_text()
t2 = t.replace(
    'text = profile?.displayName ?: "Skillect Member"',
    'text = profile?.displayName?.takeIf { it.isNotBlank() } ?: profile?.username?.takeIf { it.isNotBlank() } ?: if (uiState.isLoading) "Loading…" else "Member"',
)
p.write_text(t2)
print("Profile name", "changed" if t2 != t else "unchanged")

for f in [
    "app/src/main/java/com/example/ui/communities/CommunitiesScreen.kt",
    "app/src/main/java/com/example/ui/explore/ExploreScreen.kt",
]:
    p = Path(f)
    if not p.exists():
        continue
    t = p.read_text()
    t2 = t.replace('?: "Skillect Member"', '?: "Member"')
    p.write_text(t2)
    print(f, "changed" if t2 != t else "unchanged")

# Friendly copy
p = Path("app/src/main/java/com/example/data/repository/SkillectRepository.kt")
t = p.read_text()
pairs = [
    ("Your message request is already pending.", "You already sent a hello. Wait for them to accept."),
    ("A message request is required before you can start this conversation.", "Send a quick hello first. They will need to accept before you can chat."),
    ("Messaging service returned an empty response.", "Could not start the chat. Please try again."),
    ("Conversation could not be created.", "Could not open the chat. Please try again."),
    ("Profile save could not be confirmed by Skillect safety screening.", "Your profile could not be saved right now. Please try again."),
    ("Profile update could not be confirmed by Skillect safety screening.", "Your profile could not be updated right now. Please try again."),
    ("Post could not be confirmed by Skillect safety screening.", "Your post could not be published right now. Please try again."),
    ("Comment could not be confirmed by Skillect safety screening.", "Your comment could not be posted right now. Please try again."),
    ("Message could not be confirmed by Skillect safety screening.", "Your message could not be sent right now. Please try again."),
]
n = 0
for a, b in pairs:
    if a in t:
        t = t.replace(a, b)
        n += 1
p.write_text(t)
print(f"Friendly copy replacements: {n}")
print("Surgical UI fixes complete")
PY
