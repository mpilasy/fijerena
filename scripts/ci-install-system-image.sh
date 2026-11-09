#!/bin/bash
# Installs an emulator system image before android-emulator-runner starts (instrumented-tests.yml).
# The runner downloads the image once and fails the job when the download is bad: on 2026-11-08
# the phone job stopped with "Error on ZipFile unknown archive" and no test ran. Here a bad
# download is tried again; the runner then finds the image already installed and skips it.
# Usage: scripts/ci-install-system-image.sh <api level> <target> <arch>
# Example: scripts/ci-install-system-image.sh 34 google_apis x86_64
set -uo pipefail

if [ $# -ne 3 ]; then
  echo "Usage: $0 <api level> <target> <arch>" >&2
  exit 2
fi

API="$1"
TARGET="$2"
ARCH="$3"
PACKAGE="system-images;android-${API};${TARGET};${ARCH}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
IMAGE_DIR="$SDK/system-images/android-${API}/${TARGET}/${ARCH}"
ATTEMPTS=4

if [ -z "$SDK" ] || [ ! -x "$SDKMANAGER" ]; then
  echo "No sdkmanager at '$SDKMANAGER': set ANDROID_HOME" >&2
  exit 1
fi

yes | "$SDKMANAGER" --licenses > /dev/null

for attempt in $(seq 1 "$ATTEMPTS"); do
  echo "Installing $PACKAGE (attempt $attempt of $ATTEMPTS)"
  if "$SDKMANAGER" --install "$PACKAGE" --channel=0 > /dev/null && [ -f "$IMAGE_DIR/system.img" ]; then
    echo "Installed $PACKAGE"
    exit 0
  fi
  # A bad download can leave a part of the image behind: remove it, so the next try starts clean.
  rm -rf "$IMAGE_DIR" "$SDK/.temp"
  if [ "$attempt" -lt "$ATTEMPTS" ]; then
    sleep $((attempt * 15))
  fi
done

echo "Could not install $PACKAGE after $ATTEMPTS attempts" >&2
exit 1
