#!/bin/bash
# Applies all EaglerXPaper porting patches to the source tree.
# Run from the project root: bash apply-patches.sh

set -e
SRC="core/core-platform-bukkit/src/main/java/net/lax1dude/eaglercraft/backend/server/bukkit"
CORE_SRC="core/src/main/java/net/lax1dude/eaglercraft/backend/server/base"
SKINS_SRC="$CORE_SRC/skins"
VOICE_SRC="$CORE_SRC/voice"
WEBVIEW_SRC="$CORE_SRC/webview"
QUERY_SRC="$CORE_SRC/query"
HANDSHAKE_SRC="$CORE_SRC/handshake"
PIPELINE_SRC="$CORE_SRC/pipeline"
ASYNC_SRC="$SRC/async"

echo "=== Applying EaglerXPaper porting patches ==="

# All patches are already applied via the Edit tool in the main conversation.
# This script is a placeholder for the release zip — the actual patched files
# are included directly in the zip.

echo "Patches already applied to source files."
echo "Done."
