#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/src/main/java/com/example/ui/SkillectApp.kt"
curl -fsSL "https://raw.githubusercontent.com/sd9282726-rgb/Skillect-socials/ec0e83aac38e4e3f210e55d07f2586c70aed8c90/app/src/main/java/com/example/ui/SkillectApp.kt" -o "$APP"
test -s "$APP"

python3 - "$APP" << 'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1])
text = p.read_text()
if "findStartDestination" not in text:
    text = text.replace(
        "import androidx.navigation.NavDestination.Companion.hasRoute\n",
        "import androidx.navigation.NavDestination.Companion.hasRoute\nimport androidx.navigation.NavGraph.Companion.findStartDestination\n",
    )
old = """        val onSelectNavItem: (TopNavItem) -> Unit = { item ->
            if (item.route is SkillectRoute.Composer && currentUserId.isBlank()) {
                authVm.openAuthPrompt(
                    mode = AuthMode.SIGN_UP,
                    reason = \"Create an account or sign in to publish posts, stories, and short videos.\",
                )
            } else {
                navController.navigate(item.route) {
                    launchSingleTop = true
                }
            }
        }"""
new = """        val onSelectNavItem: (TopNavItem) -> Unit = { item ->
            if (item.route is SkillectRoute.Composer && currentUserId.isBlank()) {
                authVm.openAuthPrompt(
                    mode = AuthMode.SIGN_UP,
                    reason = \"Create an account or sign in to publish posts, stories, and short videos.\",
                )
            } else {
                val alreadyOnTab = navController.currentBackStackEntry?.destination
                    ?.hasRoute(item.route::class) == true
                if (alreadyOnTab && item.route !is SkillectRoute.Composer) {
                    // Already on this tab — ignore re-tap to avoid navigation freezes.
                } else {
                    runCatching {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }.onFailure {
                        runCatching {
                            navController.navigate(item.route) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        }"""
if old not in text:
    raise SystemExit("onSelectNavItem block not found")
p.write_text(text.replace(old, new, 1))
print("Navigation freeze fix applied to", p)
PY

# Bump version to 1.9.0 / 19
sed -i 's/versionCode = [0-9]*/versionCode = 19/' "$ROOT/app/build.gradle.kts" || true
sed -i 's/versionName = "[^"]*"/versionName = "1.9.0"/' "$ROOT/app/build.gradle.kts" || true
grep -E 'versionCode|versionName' "$ROOT/app/build.gradle.kts" | head -5
wc -l "$APP"
