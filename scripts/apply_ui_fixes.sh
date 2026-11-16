#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATCH="$ROOT/scripts/apply_final_ui_2026-11-16.patch"
if [[ ! -f "$PATCH" ]]; then
  printf '%s' 'H4sICNml+moAA2FwcGx5X2ZpbmFsX3VpXzIwMjYtMTEtMTYucGF0Y2gAxVvrctu2Ev7vp0D0o6XGMnWx' | base64 -d | gzip -d > "$PATCH" 2>/dev/null || true
fi
echo "Applying final UI fixes..."
if [[ -f "$PATCH" ]] && { grep -q "A SPACE FOR WHAT YOU CREATE" "$ROOT/app/src/main/java/com/example/ui/feed/FeedScreen.kt" 2>/dev/null || grep -q 'Text(text = text, style = MaterialTheme.typography.labelLarge)' "$ROOT/app/src/main/java/com/example/core/designsystem/Components.kt" 2>/dev/null; }; then
  patch -p1 -d "$ROOT" --forward --reject-file=- < "$PATCH" || true
  echo "final UI patch applied (or partial)"
else
  echo "final UI sources already updated"
fi
