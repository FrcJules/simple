#!/bin/sh
# Lancé à l'ouverture de session du Pi (voir docs/pi-install.md) : Chromium plein écran sur l'écran maison, jamais sur autre chose.
set -eu
until curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; do sleep 2; done
exec chromium-browser --kiosk --incognito --noerrdialogs --disable-infobars --no-first-run \
  --disable-session-crashed-bubble --check-for-update-interval=31536000 \
  --autoplay-policy=no-user-gesture-required http://127.0.0.1:5173
