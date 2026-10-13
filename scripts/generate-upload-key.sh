#!/usr/bin/env bash
# Generates the Play *upload* key (Play App Signing keeps the real app-signing key on Google's side).
# Run it on YOUR machine (needs a JDK). Never commit the .jks file or the passwords.
set -euo pipefail

OUT="${1:-$HOME/presence-upload-key.jks}"
ALIAS="${2:-presence-upload}"

[ -e "$OUT" ] && { echo "Refusing to overwrite existing $OUT" >&2; exit 1; }
command -v keytool >/dev/null || { echo "keytool not found: install a JDK 17" >&2; exit 1; }

read -r -s -p "Keystore password (min 12 chars): " PASS; echo
[ "${#PASS}" -ge 12 ] || { echo "Password too short" >&2; exit 1; }

keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Simple, O=Simple, C=FR"

cat <<MSG

Created: $OUT   (BACK IT UP somewhere safe: a password manager + an offline copy)

Add these 4 GitHub secrets (Settings > Secrets and variables > Actions), e.g. with the gh CLI:
  base64 -w0 "$OUT" | gh secret set ANDROID_KEYSTORE_BASE64
  gh secret set ANDROID_KEYSTORE_PASSWORD   # the password you just typed
  gh secret set ANDROID_KEY_PASSWORD        # same password
  echo -n "$ALIAS" | gh secret set ANDROID_KEY_ALIAS
MSG
