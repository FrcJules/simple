#!/bin/sh
# Mise à jour du Pi : récupère la dernière version publiée, reconstruit, redémarre. À lancer en root : sudo /opt/presence/deploy/pi/update.sh
# Rien n'est supprimé : /var/lib/presence (appareils appairés, certificat) et /etc/presence/hub.env restent tels quels.
set -eu
cd /opt/presence
sudo -u presence git fetch --tags --quiet
if [ "${1:-}" ]; then sudo -u presence git checkout --quiet "$1"; else sudo -u presence git pull --ff-only --quiet; fi
sudo -u presence npm ci --silent
set -a; . /etc/presence/hub.env; set +a   # VITE_DISPLAY_TOKEN, VITE_SENIOR_NAME... sont lus à la construction de l'écran
sudo -u presence --preserve-env=VITE_DISPLAY_TOKEN,VITE_SENIOR_NAME,VITE_HUB_URL npm run build --silent
systemctl restart presence-hub presence-display
systemctl is-active presence-hub presence-display
