#!/usr/bin/env bash
# Only apply the safe feed-header compaction. Skip repository/API injections that
# previously broke release assemble. Backend private-profile migration 0014 remains live.
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
p = Path("app/src/main/java/com/example/ui/feed/FeedScreen.kt")
if not p.exists():
    print("FeedScreen missing")
    raise SystemExit(0)
t = p.read_text()
marker = "            // Photo-led editorial header: image + typography, without a generic slogan."
tabs = "            // Clean Underline Tabs"
if marker in t and tabs in t:
    i, j = t.find(marker), t.find(tabs)
    if 0 <= i < j:
        t = t[:i] + "            // Compact feed header\n            Spacer(modifier = Modifier.height(4.dp))\n\n" + t[j:]
        p.write_text(t)
        print("Feed header compacted")
    else:
        print("Feed header indices invalid")
else:
    print("Feed header already compact or markers missing")
print("apply_social_profile_fixes complete (safe mode)")
PY
