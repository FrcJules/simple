# Installer l'écran maison (Raspberry Pi)

Pas à pas pour un Raspberry Pi 4 ou 5 relié à un écran. Tout reste dans la maison : aucun service en ligne n'est nécessaire. À faire une fois, par la personne de la famille qui installe.

## 1. Système

1. Avec *Raspberry Pi Imager*, installez **Raspberry Pi OS (64 bits) avec bureau**. Dans les réglages avancés : nom `presence`, Wi-Fi de la maison, SSH activé avec une clé, utilisateur de votre choix (par exemple `famille`), **ouverture de session automatique** sur le bureau.
2. Donnez au Pi une **adresse fixe** (réservation DHCP dans la box) : le QR code d'appairage contient cette adresse.
3. Mises à jour de sécurité automatiques :
   ```sh
   sudo apt update && sudo apt full-upgrade -y
   sudo apt install -y unattended-upgrades git curl openssl python3 ufw
   sudo dpkg-reconfigure -plow unattended-upgrades
   ```
4. Node 22 ou plus récent (voir <https://nodejs.org/> ou le dépôt NodeSource), puis `node --version` doit afficher `v22` au moins.

## 2. Pare-feu

Seul le port HTTPS des téléphones est ouvert, et seulement sur le réseau de la maison (adaptez `192.168.1.0/24`) :
```sh
sudo ufw default deny incoming
sudo ufw allow from 192.168.1.0/24 to any port 22 proto tcp
sudo ufw allow from 192.168.1.0/24 to any port 8788 proto tcp
sudo ufw enable
```
Le hub (8787) et l'écran (5173) n'écoutent que sur `127.0.0.1`.

## 3. Installer Simple

```sh
sudo useradd --system --create-home --home-dir /var/lib/presence --shell /usr/sbin/nologin presence
sudo git clone https://github.com/FrcJules/presence.git /opt/presence
sudo chown -R presence:presence /opt/presence
sudo install -d -m 0750 -o root -g presence /etc/presence
sudo install -m 0640 -o root -g presence /opt/presence/deploy/pi/hub.env.example /etc/presence/hub.env
```
Éditez `/etc/presence/hub.env` : mettez dans `PRESENCE_DISPLAY_TOKEN` **et** `VITE_DISPLAY_TOKEN` la même valeur, obtenue avec `openssl rand -hex 24`, et le prénom dans `VITE_SENIOR_NAME`.

Construisez puis démarrez (la mise à jour fait la même chose, voir plus bas) :
```sh
sudo cp /opt/presence/deploy/pi/presence-*.service /etc/systemd/system/
sudo /opt/presence/deploy/pi/update.sh
sudo systemctl enable --now presence-hub presence-display
```
Au premier démarrage le hub crée son certificat dans `/var/lib/presence/tls` et affiche son **empreinte** : `sudo journalctl -u presence-hub -n 20`.

## 4. Kiosque

Chromium plein écran, verrouillé sur l'écran maison (aucune autre page ne peut s'ouvrir) :
```sh
sudo install -d /etc/chromium/policies/managed
sudo cp /opt/presence/deploy/pi/chromium-policy.json /etc/chromium/policies/managed/presence.json
mkdir -p ~/.config/autostart
cat > ~/.config/autostart/presence-kiosk.desktop <<'DESK'
[Desktop Entry]
Type=Application
Name=Simple
Exec=/opt/presence/deploy/pi/kiosk.sh
DESK
```
Désactivez aussi la mise en veille de l'écran (*Préférences ▸ Raspberry Pi Configuration ▸ Affichage ▸ Screen Blanking : désactivé*), puis redémarrez : l'heure et la date s'affichent.

## 5. Appairer un téléphone

Sur l'écran maison, touchez **5 fois de suite l'heure** : un QR code s'affiche avec l'empreinte du hub. Scannez-le avec l'appareil photo du téléphone, entrez le code famille, **comparez l'empreinte** puis « Appairer » (voir `docs/real-phone-test.md`). Autre voie, depuis un terminal : `cd /opt/presence && sudo -u presence PRESENCE_DATA_DIR=/var/lib/presence PRESENCE_TLS_DIR=/var/lib/presence/tls npm run pair -w @simple/hub -- "Téléphone de Marie"`.

Liste et retrait des téléphones : mêmes variables, `npm run devices -w @simple/hub` et `npm run revoke -w @simple/hub -- <identifiant>`. Pour changer de certificat (carte SD perdue) : arrêtez le hub, `npm run rotate-cert -w @simple/hub`, redémarrez, appairez de nouveau.

## 5 bis. Rappels

```sh
cd /opt/presence && sudo -u presence PRESENCE_DATA_DIR=/var/lib/presence npm run reminders -w @simple/hub -- add "Prendre le médicament" --at 08:00
sudo -u presence PRESENCE_DATA_DIR=/var/lib/presence npm run reminders -w @simple/hub -- add "Rendez-vous médecin" --at 14:30 --date 2026-10-12
sudo -u presence PRESENCE_DATA_DIR=/var/lib/presence npm run reminders -w @simple/hub -- add "Marché" --at 09:00 --days mar,sam
sudo -u presence PRESENCE_DATA_DIR=/var/lib/presence npm run reminders -w @simple/hub -- list
```
Sans `--date` ni `--days`, le rappel revient tous les jours. L'écran le montre en plein écran à l'heure dite pendant 5 minutes (avec un bouton « OK »), puis en petit sous la date jusqu'à une heure après ou jusqu'à « OK ». Un titre ne peut pas contenir de dose (« 2 comprimés », « 500 mg ») : le rappel dit quoi, jamais combien. Les rappels de la même heure sont sur la même carte.

## 5 quinquies. Météo (facultative)

Désactivée par défaut ([ADR 0006](adr/0006-meteo-open-meteo.md)). Pour l'activer, ajoutez dans `/etc/presence/hub.env` les coordonnées de **votre ville** (pas de votre maison : elles sont arrondies à 0,1°, soit environ 11 km, avant d'être envoyées) :
```
PRESENCE_WEATHER_LAT=48.9
PRESENCE_WEATHER_LON=2.4
```
puis `sudo systemctl restart presence-hub`. Le Pi interroge Open-Meteo toutes les 30 minutes au plus ; l'écran affiche « Grand soleil, 18° (de 11° à 21°) ». Pour la désactiver, videz les deux lignes.

## 5 quater. Anniversaires

```sh
cd /opt/presence && sudo -u presence PRESENCE_DATA_DIR=/var/lib/presence npm run birthdays -w @simple/hub -- add "Marie" --day 03-14 --year 1950
sudo -u presence PRESENCE_DATA_DIR=/var/lib/presence npm run birthdays -w @simple/hub -- list
```
Le jour même, l'écran affiche « Aujourd'hui, Marie fête ses 76 ans » (si l'année est donnée et plausible ; sinon « Aujourd'hui, anniversaire de Marie ») ; dans les 7 jours qui précèdent, « Samedi, anniversaire de Marie » (ou « Demain »). Plusieurs personnes le même jour sont regroupées en une phrase. Le 29 février est fêté le 28 les autres années. `-- remove <identifiant>` supprime une entrée.

## 5 ter. Photos de famille

Le diaporama utilise **un dossier du Pi choisi par la famille**, jamais la photothèque d'un téléphone :
```sh
sudo install -d -m 0750 -o presence -g presence /var/lib/presence/photos
# depuis votre ordinateur : scp *.jpg famille@presence.local:/tmp/ puis sudo mv /tmp/*.jpg /var/lib/presence/photos/ ; sudo chown presence: /var/lib/presence/photos/*
```
Ajoutez dans `/etc/presence/hub.env` : `PRESENCE_PHOTOS_DIR=/var/lib/presence/photos` (et `VITE_SLIDE_SECONDS=30` avant la construction de l'écran pour changer la durée). Formats : JPEG, PNG, WebP, 12 Mo au plus, 300 photos au plus, pas de sous-dossier ni de lien. Une nouvelle photo apparaît au plus tard 10 minutes après son dépôt. Sans photo, l'écran garde son fond uni.

## 6. Mises à jour

- **Système** : automatiques (étape 1).
- **Simple** : `sudo /opt/presence/deploy/pi/update.sh` (dernière version) ou `sudo /opt/presence/deploy/pi/update.sh v0.2.0` (version précise, pour revenir en arrière aussi). Les téléphones appairés, le certificat et les réglages sont conservés.
- Vérification : `systemctl status presence-hub presence-display`, `journalctl -u presence-hub -f`.

## 7. Sauvegarde et sécurité

- Sauvegardez `/var/lib/presence` (appareils appairés, certificat) et `/etc/presence/hub.env` ailleurs que sur la carte SD. Le dossier contient des empreintes et le nom des téléphones : gardez la sauvegarde privée.
- Ne mettez **pas de proxy inverse** devant le hub et n'exposez jamais le port 8787 ou 5173 (voir `docs/privacy.md`).
- Le jeton d'affichage est dans les fichiers de `apps/display/dist` : c'est pourquoi Chromium est verrouillé et le dossier appartient à l'utilisateur `presence`.
