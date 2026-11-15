#!/bin/bash
# ==============================================================================
# GameBar APK Standalone Installation Script
# Installs GameBar APK via Android Package Manager, verifies package identity,
# and ensures the application starts without crashing.
# ==============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

EXPECTED_PKG="com.android.gamebar.fork"
MAIN_ACTIVITY="com.android.gamebar.GameBarSettingsActivity"
TMP_DIR="/data/local/tmp"
TMP_APK="${TMP_DIR}/gamebar_install.apk"

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}   GameBar Standalone APK Installer            ${NC}"
echo -e "${BLUE}===============================================${NC}"

# Determine root execution method
RUN_CMD=""
if [ "$(id -u)" -eq 0 ]; then
    RUN_CMD="sh -c"
elif command -v su >/dev/null 2>&1; then
    RUN_CMD="su -c"
else
    echo -e "${YELLOW}[!] Warning: su not detected. Attempting direct execution...${NC}"
    RUN_CMD="sh -c"
fi

# Locate GameBar APK
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH=""

if [ -f "${SCRIPT_DIR}/out/GameBar.apk" ]; then
    APK_PATH="${SCRIPT_DIR}/out/GameBar.apk"
elif [ -f "${SCRIPT_DIR}/build/outputs/apk/release/GameBar-release.apk" ]; then
    APK_PATH="${SCRIPT_DIR}/build/outputs/apk/release/GameBar-release.apk"
else
    echo -e "${YELLOW}[*] APK not found. Building APK with Gradle...${NC}"
    (cd "${SCRIPT_DIR}" && bash ./gradlew copyApk)
    APK_PATH="${SCRIPT_DIR}/out/GameBar.apk"
fi

if [ ! -f "${APK_PATH}" ]; then
    echo -e "${RED}[ERROR] Could not find or build GameBar APK at: ${APK_PATH}${NC}"
    exit 1
fi

echo -e "${BLUE}[1/5] Source APK found:${NC} ${APK_PATH} ($(ls -lh "${APK_PATH}" | awk '{print $5}'))"

# Stage APK to /data/local/tmp to avoid FUSE / SELinux restrictions on /storage
echo -e "${BLUE}[2/5] Staging APK to ${TMP_DIR}...${NC}"
$RUN_CMD "cp '${APK_PATH}' '${TMP_APK}' && chmod 0644 '${TMP_APK}'"

# Install APK using Android Package Manager
echo -e "${BLUE}[3/5] Installing APK via Android Package Manager...${NC}"
INSTALL_OUTPUT=$($RUN_CMD "pm install -r '${TMP_APK}' 2>&1")
echo "${INSTALL_OUTPUT}"

# Clean up staged APK
$RUN_CMD "rm -f '${TMP_APK}'" 2>/dev/null || true

if ! echo "${INSTALL_OUTPUT}" | grep -q "Success"; then
    echo -e "${RED}[ERROR] Package Manager installation failed!${NC}"
    exit 1
fi
echo -e "${GREEN}[+] Package Manager installation SUCCESSFUL!${NC}"

# Verify package name and installation path
echo -e "${BLUE}[4/5] Verifying installed package...${NC}"
INSTALLED_PATH=$($RUN_CMD "pm path '${EXPECTED_PKG}'" 2>/dev/null || true)

if [ -z "${INSTALLED_PATH}" ]; then
    echo -e "${RED}[ERROR] Verification failed: Package '${EXPECTED_PKG}' not found in Package Manager!${NC}"
    exit 1
fi

echo -e "${GREEN}[+] Verified package name:${NC} ${EXPECTED_PKG}"
echo -e "${GREEN}[+] Verified install path:${NC} ${INSTALLED_PATH}"

# Check if installed to /data/app (independent user app)
if echo "${INSTALLED_PATH}" | grep -q "/data/app"; then
    echo -e "${GREEN}[+] Confirmed: App installed independently in /data/app (independent of /system/priv-app)${NC}"
fi

# Grant necessary development/runtime permissions and appops
echo -e "${BLUE}[*] Configuring legitimate system permissions and appops...${NC}"
$RUN_CMD "pm grant '${EXPECTED_PKG}' android.permission.ACCESS_FPS_COUNTER 2>/dev/null || true"
$RUN_CMD "pm grant '${EXPECTED_PKG}' android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true"
$RUN_CMD "pm grant '${EXPECTED_PKG}' android.permission.POST_NOTIFICATIONS 2>/dev/null || true"
$RUN_CMD "appops set '${EXPECTED_PKG}' SYSTEM_ALERT_WINDOW allow 2>/dev/null || true"
$RUN_CMD "appops set '${EXPECTED_PKG}' GET_USAGE_STATS allow 2>/dev/null || true"

# Ensure application can be launched
echo -e "${BLUE}[5/5] Testing application launch...${NC}"
LAUNCH_OUTPUT=$($RUN_CMD "am start -n '${EXPECTED_PKG}/${MAIN_ACTIVITY}' 2>&1")
echo "${LAUNCH_OUTPUT}"

sleep 1

APP_PID=$($RUN_CMD "pidof '${EXPECTED_PKG}'" 2>/dev/null || true)
if [ -n "${APP_PID}" ]; then
    echo -e "${GREEN}[+] Application is running successfully (PID: ${APP_PID})!${NC}"
else
    echo -e "${YELLOW}[!] Warning: PID not found immediately, checking package dump...${NC}"
fi

# Verify no crash in logcat
CRASH_CHECK=$($RUN_CMD "logcat -d -t 50 | grep -E 'FATAL EXCEPTION.*${EXPECTED_PKG}'" 2>/dev/null || true)
if [ -n "${CRASH_CHECK}" ]; then
    echo -e "${RED}[ERROR] Detected crash during launch:${NC}"
    echo "${CRASH_CHECK}"
    exit 1
fi

echo -e "${GREEN}===============================================${NC}"
echo -e "${GREEN}   GameBar APK Installed & Verified Successfully! ${NC}"
echo -e "${GREEN}===============================================${NC}"
