#!/bin/sh
# Mise à jour du Pi : récupère la dernière version publiée, reconstruit, redémarre. À lancer en root : sudo /opt/simple/deploy/pi/update.sh
# Rien n'est supprimé : /var/lib/simple (appareils appairés, certificat) et /etc/simple/hub.env restent tels quels.
set -eu
cd /opt/simple
sudo -u simple git fetch --tags --quiet
if [ "${1:-}" ]; then sudo -u simple git checkout --quiet "$1"; else sudo -u simple git pull --ff-only --quiet; fi
sudo -u simple npm ci --silent
set -a; . /etc/simple/hub.env; set +a   # VITE_DISPLAY_TOKEN, VITE_SENIOR_NAME... sont lus à la construction de l'écran
sudo -u simple --preserve-env=VITE_DISPLAY_TOKEN,VITE_SENIOR_NAME,VITE_HUB_URL npm run build --silent
systemctl restart simple-hub simple-display
systemctl is-active simple-hub simple-display
