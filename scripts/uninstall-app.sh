#!/bin/bash
# Explicitly uninstall Fijerena from a target Android device.
# Usage: scripts/uninstall-app.sh [device-serial-or-ip]
#
# This script is the ONLY sanctioned way to uninstall the app, requiring
# explicit human confirmation to prevent accidental wipe of credentials,
# watch progress, and providers.
set -euo pipefail

PACKAGE_NAME="org.njarasoa.fijerena"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb command not found in PATH." >&2
    exit 1
fi

TARGET="${1:-}"

if [ -z "$TARGET" ]; then
    mapfile -t ATTACHED < <(adb devices | awk '$2=="device" {print $1}')
    if [ "${#ATTACHED[@]}" -eq 0 ]; then
        echo "No connected Android devices found via adb." >&2
        exit 1
    elif [ "${#ATTACHED[@]}" -eq 1 ]; then
        TARGET="${ATTACHED[0]}"
    else
        echo "Multiple devices connected. Please specify the target device serial/IP:"
        for dev in "${ATTACHED[@]}"; do
            MODEL=$(adb -s "$dev" shell getprop ro.product.model 2>/dev/null || echo "unknown")
            echo "  - $dev ($MODEL)"
        done
        echo ""
        read -rp "Enter target device: " TARGET
        if [ -z "$TARGET" ]; then
            echo "No target specified. Aborting." >&2
            exit 1
        fi
    fi
fi

# Verify device is responsive
if ! adb -s "$TARGET" get-state >/dev/null 2>&1; then
    echo "Error: Cannot connect to device '$TARGET'." >&2
    exit 1
fi

DEVICE_MODEL=$(adb -s "$TARGET" shell getprop ro.product.model 2>/dev/null || echo "Unknown Device")
IS_INSTALLED=$(adb -s "$TARGET" shell pm list packages "$PACKAGE_NAME" 2>/dev/null || true)

if [ -z "$IS_INSTALLED" ]; then
    echo "Package $PACKAGE_NAME is not installed on $TARGET ($DEVICE_MODEL)."
    exit 0
fi

echo "================================================================="
echo "⚠️  CRITICAL WARNING: UNINSTALLING FIJERENA"
echo "================================================================="
echo "Target Device: $TARGET ($DEVICE_MODEL)"
echo "Package:       $PACKAGE_NAME"
echo ""
echo "Uninstalling will PERMANENTLY ERASE all data on this device:"
echo "  - Configured media providers (Xtream, Jellyfin, SMB, etc.)"
echo "  - Watch history and resume state"
echo "  - Pinned favorites and user preferences"
echo "  - Downloaded EPG and local index databases"
echo "================================================================="
echo ""
read -rp "Type 'UNINSTALL' (all uppercase) to confirm: " CONFIRMATION

if [ "$CONFIRMATION" != "UNINSTALL" ]; then
    echo "Confirmation did not match 'UNINSTALL'. Aborted without making changes."
    exit 1
fi

echo "Uninstalling $PACKAGE_NAME from $TARGET..."
adb -s "$TARGET" uninstall "$PACKAGE_NAME"
echo "✅ Uninstalled successfully from $TARGET."
