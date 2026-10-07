#!/usr/bin/env bash
# Runs the instrumented tests on the running emulator, then pulls the screenshots even if tests failed.
set -uo pipefail
cd "$(dirname "$0")/../apps/launcher-android"

gradle :app:connectedDebugAndroidTest
status=$?

mkdir -p app/build/screenshots
adb pull /sdcard/Android/media/app.presence.launcher/screenshots/. app/build/screenshots/ || echo "no screenshots to pull"
exit $status
