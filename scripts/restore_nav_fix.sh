#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/src/main/java/com/example/ui/SkillectApp.kt"

# --- Restore SkillectApp from known-good commit + nav fix ---
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
print("Navigation freeze fix applied")
PY

# --- Adaptive icon: fill canvas (no empty side margins for Indus) ---
mkdir -p "$ROOT/app/src/main/res/drawable"
cat > "$ROOT/app/src/main/res/drawable/ic_launcher_foreground.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:drawable="@drawable/skillect_icon_fg"
        android:gravity="fill" />
</layer-list>
XML

cat > "$ROOT/app/src/main/res/drawable/ic_launcher_background.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0E0B1A" />
</shape>
XML

# Tighten skillect_icon_fg.png (zoom center) so logo dominates
FG="$ROOT/app/src/main/res/drawable/skillect_icon_fg.png"
if [ -f "$FG" ]; then
  python3 - "$FG" << 'PY' || true
import sys
from pathlib import Path
try:
    from PIL import Image
except ImportError:
    import subprocess, sys as s
    subprocess.check_call([s.executable, "-m", "pip", "install", "pillow", "-q"])
    from PIL import Image

path = Path(sys.argv[1])
src = Image.open(path).convert("RGBA")
w, h = src.size
m = int(w * 0.12)
crop = src.crop((m, m, w - m, h - m)).resize((w, h), Image.Resampling.LANCZOS)
bg = (14, 11, 26, 255)
out = Image.new("RGBA", (w, h), bg)
out.paste(crop, (0, 0), crop)
px = out.load()
for y in range(h):
    for x in range(w):
        r, g, b, a = px[x, y]
        if a < 20 or (r + g + b) < 25:
            px[x, y] = bg
        else:
            alpha = a / 255.0
            px[x, y] = (
                int(r * alpha + bg[0] * (1 - alpha)),
                int(g * alpha + bg[1] * (1 - alpha)),
                int(b * alpha + bg[2] * (1 - alpha)),
                255,
            )
out.save(path, "PNG", optimize=True)
print("Tightened icon foreground", path)

root = path.parents[2]
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for density, sz in sizes.items():
    d = root / f"mipmap-{density}"
    d.mkdir(parents=True, exist_ok=True)
    im = out.resize((sz, sz), Image.Resampling.LANCZOS)
    im.save(d / "ic_launcher.png", "PNG")
    im.save(d / "ic_launcher_round.png", "PNG")
print("Updated mipmap launcher icons")
PY
fi

# Version 2.0.0 / 25 — higher than Indus installs
sed -i 's/versionCode = [0-9]*/versionCode = 25/' "$ROOT/app/build.gradle.kts" || true
sed -i 's/versionName = "[^"]*"/versionName = "2.0.0"/' "$ROOT/app/build.gradle.kts" || true
grep -E 'versionCode|versionName' "$ROOT/app/build.gradle.kts" | head -5
wc -l "$APP"
