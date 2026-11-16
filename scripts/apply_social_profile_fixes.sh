#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
for patch in \
  docs/patches/api_follow_requests.patch \
  docs/patches/repository_private_follow.patch \
  docs/patches/viewmodel_connections.patch \
  docs/patches/profile_network_ui.patch \
  docs/patches/feed_header.patch \
  docs/patches/settings_blocked_refresh.patch
do
  if [ -f "$patch" ]; then
    if git apply --check "$patch" 2>/dev/null; then
      git apply "$patch" && echo "Applied $patch"
    elif git apply --check -R "$patch" 2>/dev/null; then
      echo "Already applied $patch"
    else
      echo "WARN: could not apply $patch (continuing)"
    fi
  fi
done
echo "apply_social_profile_fixes complete"
