#!/bin/bash
set -e

echo "==============================================="
echo "  Building GameBar Module & Standalone APK     "
echo "==============================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

bash ./gradlew packageModule

echo ""
echo "==============================================="
echo "  Build Completed Successfully!                "
echo "==============================================="
echo "Flashable Module: out/GameBar-Module-v1.0.zip"
echo "Standalone APK:   out/GameBar.apk"
echo ""
ls -lh out/GameBar-Module-v1.0.zip out/GameBar.apk 2>/dev/null || true
echo ""
echo "To install APK:   bash install_apk.sh"
echo "==============================================="
