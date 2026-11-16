#!/usr/bin/env bash
# Temporarily no-op: previous Kotlin injections broke :app:assemble on CI.
# Backend privacy (message_privacy column + start-conversation) remains live.
set -euo pipefail
echo "privacy client inject skipped (assemble-safe mode)"
exit 0
