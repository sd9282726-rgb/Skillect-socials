#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATCH="$ROOT/scripts/apply_final_ui_2026-11-16.patch"
echo "Applying final UI fixes..."
if [[ -f "$PATCH" ]]; then
  if grep -q "A SPACE FOR WHAT YOU CREATE" "$ROOT/app/src/main/java/com/example/ui/feed/FeedScreen.kt" 2>/dev/null \
     || grep -q 'Text(text = text, style = MaterialTheme.typography.labelLarge)' "$ROOT/app/src/main/java/com/example/core/designsystem/Components.kt" 2>/dev/null; then
    patch -p1 -d "$ROOT" --forward --reject-file=- < "$PATCH" || true
    echo "final UI patch applied (or partial)"
  else
    echo "final UI sources already updated"
  fi
else
  echo "no final UI patch present"
fi
