#!/usr/bin/env bash
# Runs the instrumented tests on the running emulator, then pulls the screenshots.
# We call `am instrument` ourselves because `gradle connectedAndroidTest` uninstalls the app afterwards,
# which deletes the screenshots stored in its media directory.
set -uo pipefail
cd "$(dirname "$0")/../apps/launcher-android"

# Sharding (CI runs 2 emulators in parallel): SHARD is this emulator's index, NUM_SHARDS how many there are. Defaults = everything, here.
SHARD="${SHARD:-0}"
NUM_SHARDS="${NUM_SHARDS:-1}"
shard_args=()
if [ "$NUM_SHARDS" -gt 1 ]; then shard_args=(-e numShards "$NUM_SHARDS" -e shardIndex "$SHARD"); fi

gradle :app:installDebug :app:installDebugAndroidTest || exit 1

# The tests read the French screens; the emulator itself is in English (the app has an English translation too).
adb shell cmd locale set-app-locales app.simple.launcher --locales fr || echo "could not set the app language"

RUNNER=app.simple.launcher.test/androidx.test.runner.AndroidJUnitRunner
SMS_TEST=app.simple.launcher.SmsEndToEndTest

# 1. Every test except the SMS one, with the app as an ordinary app (becoming the SMS app also grants it the contacts
#    permission, which would change what the other screens do).
out1=$(adb shell am instrument -w -e notClass "$SMS_TEST" "${shard_args[@]}" "$RUNNER" 2>&1)
echo "$out1"

# 2. The SMS test, as the default SMS app (shard 0 only: it is the same four tests whatever the number of emulators).
out2="OK (0 tests)"
if [ "$SHARD" = "0" ]; then
  # The role is granted *before* the tests start: granting it to a running app kills its
  #    process (the test runner too). This also proves the debug manifest meets the role's requirements.
  adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.simple.launcher || echo "could not grant the SMS role"
  out2=$(adb shell am instrument -w -e class "$SMS_TEST" "$RUNNER" 2>&1)
  echo "$out2"
  adb shell cmd role remove-role-holder --user 0 android.app.role.SMS app.simple.launcher || true
fi

mkdir -p app/build/screenshots
adb pull /sdcard/Android/media/app.simple.launcher/screenshots/. app/build/screenshots/ || echo "no screenshots to pull"

# `am instrument` exits 0 even when tests fail: read the verdicts from its output.
for out in "$out1" "$out2"; do
  echo "$out" | grep -qE '^OK \([0-9]+ tests?\)' || { echo "Instrumented tests FAILED" >&2; exit 1; }
done
