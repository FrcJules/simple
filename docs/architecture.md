# Architecture

```
 Téléphone Android (launcher)                 Raspberry Pi
 ┌──────────────────────────┐   LAN + jeton   ┌───────────────────────────┐
 │ détecte appels/notifs    │ ─────────────►  │ hub (Fastify)             │
 │ UI senior, dictée, TTS   │  POST /events   │  valide (contracts/Zod)   │
 └──────────────────────────┘                 │  GET /events/stream (SSE) │
                                              │            │              │
                                              │  display (React, kiosque) │
                                              └───────────────────────────┘
```

- **Contrats** (`packages/contracts`) : types d'événements envoyés par le téléphone (`missed_call`, `new_message`, `new_photo`, `birthday`) validés avec Zod ; l'écran reçoit en plus `device_paired`, émis par le hub seul (un téléphone ne peut pas l'envoyer). Versionnés (`schemaVersion`).
- **Hub** : reçoit les événements authentifiés par jeton d'appareil (ou le jeton statique facultatif), garde les 200 derniers **en mémoire** (perdus au redémarrage) et les diffuse en SSE. Il oublie un événement dès que l'écran ne peut plus l'afficher (`DISPLAY_RETENTION_MS` dans `packages/contracts` : 24 h pour un appel manqué, 4 h pour le texte d'un message). Un écran qui redémarre (navigateur rechargé) ou se reconnecte (coupure réseau) reçoit d'abord ce qu'il a manqué, 50 au plus (identifiants SSE `<démarrage>-<numéro>`, renvoyés par le navigateur dans `Last-Event-ID`). Un redémarrage du **hub** (ou du Pi) efface tout : ce qui arrive pendant ce temps est perdu, le téléphone ne renvoie pas. Il sert aussi à l'écran les rappels et anniversaires (fichiers JSON dans `data/`), les photos d'un dossier du Pi (`SIMPLE_PHOTOS_DIR`) et la météo Open-Meteo, désactivée par défaut ([ADR 0006](adr/0006-meteo-open-meteo.md)). Pas de base de données.
- **Display** : s'abonne au flux ; reste utile sans internet (heure, date, photos du Pi). Installation sur le Pi : [pi-install.md](pi-install.md).
- **Réseau** : tout reste sur le réseau local ; aucun cloud par défaut ([privacy.md](privacy.md)).
- **TV** (plus tard) : hub → Zigbee2MQTT → contrôleur IR.

Décisions : [adr/](adr/).

## Appairage
`npm run pair -w @simple/hub` ou 5 touches rapides sur l'heure de l'écran maison (`POST /pairing/start` sur la boucle locale, jeton d'affichage, une fois toutes les 3 s, absent du port HTTPS du réseau) → secret à usage unique (2 min) + QR code du lien `simple://pair?u=<adresse>&s=<secret>&f=<empreinte>` → l'appareil photo du téléphone l'ouvre dans « Réglages famille » (derrière le code famille) → l'adresse et l'empreinte sont affichées à comparer avec celles du hub → « Appairer » : le téléphone échange le secret (`POST /pair`, connexion épinglée à l'empreinte du lien) contre **son propre jeton d'appareil**, chiffré ensuite par le Keystore. Le hub ne stocke que des empreintes SHA-256 (un fichier par appareil dans `data/devices/`, un par QR code dans `data/claims/`, droits 0600 : supprimer le fichier d'un QR code l'utilise, supprimer celui d'un appareil le révoque). Le jeton statique `SIMPLE_PAIRING_TOKEN` est facultatif. Le certificat du hub est épinglé ([ADR 0003](adr/0003-tls-epingle.md)).

## Transport
Écran → hub : HTTP loopback (même appareil). Téléphone → hub : HTTPS, certificat auto-signé épinglé par empreinte ([ADR 0003](adr/0003-tls-epingle.md)). Le port réseau du hub (`phone-server.ts`) n'expose que `POST /pair` et `POST /events`, sert une requête par connexion et ferme les connexions lentes ou trop nombreuses (poignée TLS 5 s, en-têtes 10 s, requête 15 s, inactivité 10 s, durée de vie 20 s, 64 connexions dont 8 par adresse, 20 nouvelles par adresse toutes les 10 s). Risque résiduel accepté : un appareil du réseau local qui prend beaucoup d'adresses peut occuper ces places tant qu'il insiste (comme il pourrait brouiller le Wi-Fi) ; il n'obtient aucune donnée, et l'écran, servi en boucle locale, reste affiché.

## CI/CD
- `ci.yml` : lint/typecheck/test/build/`npm audit` web (Node 22), lint/tests unitaires/APK debug Android, tests d'interface sur émulateur (`android-emulator`), filtré par chemins ; job `ci-ok` = check requis.
- `codeql.yml` : analyse statique hebdomadaire et sur PR.
- `release.yml` : tag `vX.Y.Z` → archive Pi + AAB signé → GitHub Release, et envoi de l'AAB sur la piste **test interne** de Google Play, **en brouillon** (si le secret `PLAY_SERVICE_ACCOUNT_JSON` est présent). Le passage en test fermé ou en production se fait à la main dans la Play Console. Secrets de signature : `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Le job de signature attend une validation manuelle (environnement `release`) : voir [release.md](release.md).

## Tests
- **Web** : Vitest pour `apps/hub`, `apps/display` et `packages/contracts` (`npm test`).
- **Launcher, unitaires** : tests JVM avec Robolectric (`gradle :app:testDebugUnitTest`).
- **Launcher, interface** : tests sur émulateur Android dans la CI (job `android-emulator`, 2 émulateurs en parallèle) ; les captures d'écran et rapports sont publiés en artefact.
- **Tests par captures d'écran** (Roborazzi pour le launcher, Playwright pour l'écran) : en cours.
- Aucun test sur un vrai téléphone n'est automatisé : voir [real-phone-test.md](real-phone-test.md).
