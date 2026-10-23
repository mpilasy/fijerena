#!/bin/bash
# Prints the Fijerena applicationId on a device: the TV app's on an Android TV (leanback),
# the phone app's elsewhere (public release plan, D2). FIJERENA_PKG overrides it, e.g. to reach
# the phone app installed before the split, which used the TV app's id.
# Usage: scripts/app-package.sh <serial>
set -euo pipefail

SERIAL="${1:?usage: $0 <serial>}"
if [ -n "${FIJERENA_PKG:-}" ]; then
    echo "$FIJERENA_PKG"
elif adb -s "$SERIAL" shell pm has-feature android.software.leanback 2>/dev/null | grep -q true; then
    echo org.njarasoa.fijerena
else
    echo org.njarasoa.fijerena.mobile
fi
