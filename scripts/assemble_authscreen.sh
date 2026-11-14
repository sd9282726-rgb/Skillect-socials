#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/java/com/example/ui/auth/AuthScreen.kt"
if [[ -f "$ROOT/docs/onboarding/AuthScreen.full.kt" ]]; then
  cp "$ROOT/docs/onboarding/AuthScreen.full.kt" "$OUT"
  echo "Installed AuthScreen.full.kt ($(wc -c < "$OUT") bytes)"
  exit 0
fi
echo "Missing docs/onboarding/AuthScreen.full.kt"
exit 1
