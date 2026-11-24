#!/usr/bin/env bash
# Antigravity Mobile (Android Root Edition) — On-Device End-to-End Verification Script
# Supports BOTH USB ADB and Wireless ADB (IP:PORT) and KernelSU / Magisk / APatch root.
# Safe & non-destructive: read-only system probes + debug APK install + logcat checks.

set -euo pipefail

APP_PACKAGE="com.antigravity.mobile"
MAIN_ACTIVITY="${APP_PACKAGE}/.MainActivity"
ACCESSIBILITY_COMPONENT="${APP_PACKAGE}/com.antigravity.mobile.core.accessibility.AntigravityAccessibilityService"
ROOTFS_DIR="/data/local/ag_rootfs"
LOG_TAG="AntigravityMobile"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
APK_PATH="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"

ADB_TARGET="${ADB_TARGET:-}"
ADB_SERIAL="${ADB_SERIAL:-}"
LOGCAT_WAIT_SEC="${LOGCAT_WAIT_SEC:-12}"

usage() {
    cat <<EOF
Usage: $(basename "$0") [options]

Options:
  --target <IP:PORT>   Connect and verify over Wireless ADB (or set ADB_TARGET=IP:PORT)
  --serial <SERIAL>    Target a specific ADB device serial (or set ADB_SERIAL=<serial>)
  --apk <PATH>         Path to debug APK (default: app/build/outputs/apk/debug/app-debug.apk)
  --timeout <SECONDS>  Seconds to wait for runtime logcat signals (default: 12)
  -h, --help           Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --target)
            [[ $# -ge 2 ]] || { echo "ERROR: --target requires IP:PORT" >&2; exit 2; }
            ADB_TARGET="$2"
            shift 2
            ;;
        --serial)
            [[ $# -ge 2 ]] || { echo "ERROR: --serial requires a device serial" >&2; exit 2; }
            ADB_SERIAL="$2"
            shift 2
            ;;
        --apk)
            [[ $# -ge 2 ]] || { echo "ERROR: --apk requires a file path" >&2; exit 2; }
            APK_PATH="$2"
            shift 2
            ;;
        --timeout)
            [[ $# -ge 2 ]] || { echo "ERROR: --timeout requires a numeric value" >&2; exit 2; }
            LOGCAT_WAIT_SEC="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "ERROR: Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERROR: Required command '$1' not found in PATH." >&2
        exit 1
    fi
}

require_cmd adb

echo "======================================================================"
echo " Antigravity Mobile — On-Device Verification (USB & Wireless ADB)"
echo "======================================================================"

# ----------------------------------------------------------------------
# Step 1: Connect / Resolve Single ADB Device & Verify Root (KernelSU/Magisk)
# ----------------------------------------------------------------------
if [[ -n "${ADB_TARGET}" ]]; then
    echo "[1/4] Connecting to Wireless ADB target: ${ADB_TARGET} ..."
    adb connect "${ADB_TARGET}"
    SERIAL="${ADB_TARGET}"
elif [[ -n "${ADB_SERIAL}" ]]; then
    SERIAL="${ADB_SERIAL}"
else
    mapfile -t CONNECTED_DEVICES < <(
        adb devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1}'
    )
    DEVICE_COUNT="${#CONNECTED_DEVICES[@]}"
    if [[ "${DEVICE_COUNT}" -eq 0 ]]; then
        echo "ERROR: No online ADB devices found." >&2
        echo "       Connect via USB ADB or pass --target <IP:PORT> for Wireless ADB." >&2
        exit 1
    elif [[ "${DEVICE_COUNT}" -gt 1 ]]; then
        echo "ERROR: Multiple ADB devices detected (${DEVICE_COUNT}):" >&2
        printf '  - %s\n' "${CONNECTED_DEVICES[@]}" >&2
        echo "       Specify one device via --serial <ID> or --target <IP:PORT>." >&2
        exit 1
    fi
    SERIAL="${CONNECTED_DEVICES[0]}"
fi

# Confirm the resolved target is online in `adb devices`
if ! adb devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1}' | grep -Fxq "${SERIAL}"; then
    echo "ERROR: ADB device '${SERIAL}' is not in 'device' state." >&2
    adb devices
    exit 1
fi

DEVICE_MODEL="$(adb -s "${SERIAL}" shell getprop ro.product.model | tr -d '\r')"
ANDROID_RELEASE="$(adb -s "${SERIAL}" shell getprop ro.build.version.release | tr -d '\r')"
ANDROID_SDK="$(adb -s "${SERIAL}" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "[1/4] Connected device: ${SERIAL} (${DEVICE_MODEL}, Android ${ANDROID_RELEASE} / API ${ANDROID_SDK})"

echo "[1/4] Verifying root access via 'adb -s ${SERIAL} shell su -c id' ..."
ROOT_ID_OUT="$(adb -s "${SERIAL}" shell "su -c id" 2>&1 | tr -d '\r' || true)"
if [[ "${ROOT_ID_OUT}" != *"uid=0"* ]]; then
    echo "ERROR: Root check failed on ${SERIAL}. Output: ${ROOT_ID_OUT}" >&2
    echo "       Open KernelSU Manager / Magisk / APatch Manager and grant root to shell & Antigravity Mobile." >&2
    exit 1
fi
echo "[1/4] Root verified: ${ROOT_ID_OUT}"

# ----------------------------------------------------------------------
# Step 2: Install Debug APK
# ----------------------------------------------------------------------
if [[ ! -f "${APK_PATH}" ]]; then
    echo "ERROR: Debug APK not found at: ${APK_PATH}" >&2
    echo "       Build it first with: ./gradlew assembleDebug" >&2
    exit 1
fi

echo "[2/4] Installing debug APK (${APK_PATH}) ..."
adb -s "${SERIAL}" install -r "${APK_PATH}"
echo "[2/4] APK installed successfully."

# ----------------------------------------------------------------------
# Step 3: Check ARM64 Chroot Rootfs & `agy --version`
# ----------------------------------------------------------------------
echo "[3/4] Checking ARM64 glibc rootfs (${ROOTFS_DIR}) and chroot 'agy --version' ..."
ROOTFS_STATUS="$(adb -s "${SERIAL}" shell "su -c 'if [ -d ${ROOTFS_DIR} ] && ([ -x ${ROOTFS_DIR}/bin/sh ] || [ -x ${ROOTFS_DIR}/usr/bin/env ]); then echo READY; else echo MISSING; fi'" | tr -d '\r' || echo MISSING)"

AGY_STATUS="NOT_INSTALLED"
if [[ "${ROOTFS_STATUS}" == "READY" ]]; then
    AGY_VERSION_OUT="$(adb -s "${SERIAL}" shell "su -c 'chroot ${ROOTFS_DIR} /usr/bin/env PATH=/usr/local/bin:/usr/bin:/bin /usr/local/bin/agy --version 2>&1'" 2>&1 | tr -d '\r' || true)"
    if [[ -n "${AGY_VERSION_OUT}" ]] && [[ "${AGY_VERSION_OUT}" != *"No such file"* ]] && [[ "${AGY_VERSION_OUT}" != *"not found"* ]]; then
        AGY_STATUS="READY (${AGY_VERSION_OUT})"
        echo "[3/4] Rootfs READY; chroot agy --version -> ${AGY_VERSION_OUT}"
    else
        echo "[3/4] Rootfs exists at ${ROOTFS_DIR}, but 'agy' CLI is not installed yet."
        echo "      -> In Antigravity Mobile Onboarding Step 3, tap 'Rootfs Kur & Bağla' then 'agy Kur / Denetle'."
    fi
else
    echo "[3/4] Rootfs not yet initialized at ${ROOTFS_DIR}."
    echo "      -> Open Antigravity Mobile Onboarding Wizard:"
    echo "         1) Step 1: Verify KernelSU / Magisk root access."
    echo "         2) Step 2: Tap 'İzinleri Otomatik Yapılandır' & 'Servisleri Kaydet'."
    echo "         3) Step 3: Select APK Asset / Remote Download / Local .tar.gz -> 'Rootfs Kur & Bağla' -> 'agy Kur / Denetle'."
    echo "         4) Step 4: Sign in with your Google account via Chrome Custom Tabs or ~/.gemini import."
fi

# ----------------------------------------------------------------------
# Step 4: Launch MainActivity & Verify Logcat Diagnostics
# ----------------------------------------------------------------------
echo "[4/4] Clearing logcat snapshot and launching ${MAIN_ACTIVITY} ..."
adb -s "${SERIAL}" logcat -c || true
adb -s "${SERIAL}" shell am start -W -n "${MAIN_ACTIVITY}"

echo "[4/4] Waiting up to ${LOGCAT_WAIT_SEC}s for runtime signals in logcat ..."
MCP_FOUND="false"
ACCESSIBILITY_FOUND="false"
UI_OR_ACP_FOUND="false"
LOG_CAPTURE=""

for ((i = 1; i <= LOGCAT_WAIT_SEC; i++)); do
    LOG_CAPTURE="$(adb -s "${SERIAL}" logcat -d -s "${LOG_TAG}:I" "${LOG_TAG}:W" "${LOG_TAG}:E" 2>/dev/null | tr -d '\r' || true)"

    if grep -qE "Local Android System MCP server listening on 127\.0\.0\.1:[0-9]+" <<<"${LOG_CAPTURE}"; then
        MCP_FOUND="true"
    fi
    if grep -qE "AccessibilityService connected=true" <<<"${LOG_CAPTURE}"; then
        ACCESSIBILITY_FOUND="true"
    fi
    if grep -qE "(ui_get_tree sample captured|agy acp handshake active)" <<<"${LOG_CAPTURE}"; then
        UI_OR_ACP_FOUND="true"
    fi

    if [[ "${MCP_FOUND}" == "true" && "${ACCESSIBILITY_FOUND}" == "true" && "${UI_OR_ACP_FOUND}" == "true" ]]; then
        break
    fi
    sleep 1
done

ENABLED_A11Y="$(adb -s "${SERIAL}" shell "settings get secure enabled_accessibility_services" 2>/dev/null | tr -d '\r' || true)"

echo ""
echo "----------------------------------------------------------------------"
echo " Verification Summary (${SERIAL})"
echo "----------------------------------------------------------------------"
printf "  %-34s %s\n" "1. ADB + Root (su -c id):" "PASS (${ROOT_ID_OUT})"
printf "  %-34s %s\n" "2. Debug APK Install:" "PASS"
printf "  %-34s %s\n" "3. Rootfs / agy CLI:" "${ROOTFS_STATUS} / ${AGY_STATUS}"

if [[ "${MCP_FOUND}" == "true" ]]; then
    MCP_LINE="$(grep -m1 -E "Local Android System MCP server listening on 127\.0\.0\.1:[0-9]+" <<<"${LOG_CAPTURE}" || true)"
    printf "  %-34s %s\n" "4a. Local MCP Server (127.0.0.1):" "PASS (${MCP_LINE##*AntigravityMobile: })"
else
    printf "  %-34s %s\n" "4a. Local MCP Server (127.0.0.1):" "FAIL (not seen in logcat within ${LOGCAT_WAIT_SEC}s)"
fi

if [[ "${ACCESSIBILITY_FOUND}" == "true" ]]; then
    printf "  %-34s %s\n" "4b. AccessibilityService:" "PASS (connected=true)"
else
    printf "  %-34s %s\n" "4b. AccessibilityService:" "PENDING (enabled_accessibility_services=${ENABLED_A11Y})"
    echo "      -> Complete Step 2 ('Servisleri Kaydet') in Onboarding to enable ${ACCESSIBILITY_COMPONENT}."
fi

if [[ "${UI_OR_ACP_FOUND}" == "true" ]]; then
    SAMPLE_LINE="$(grep -m1 -E "(ui_get_tree sample captured|agy acp handshake active)" <<<"${LOG_CAPTURE}" || true)"
    printf "  %-34s %s\n" "4c. ui_get_tree / agy acp log:" "PASS (${SAMPLE_LINE##*AntigravityMobile: })"
else
    printf "  %-34s %s\n" "4c. ui_get_tree / agy acp log:" "PENDING (awaiting AccessibilityService or first ACP session)"
fi
echo "----------------------------------------------------------------------"

if [[ "${MCP_FOUND}" != "true" ]]; then
    echo "Recent ${LOG_TAG} logcat lines:"
    echo "${LOG_CAPTURE}"
    exit 1
fi

echo "Verification finished cleanly."
