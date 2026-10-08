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

- **Contrats** (`packages/contracts`) : types d'événements (`missed_call`, `new_photo`, `birthday`, …) validés avec Zod. Versionnés (`schemaVersion`).
- **Hub** : reçoit les événements authentifiés par jeton d'appairage, les garde en mémoire (puis SQLite), les diffuse en SSE.
- **Display** : s'abonne au flux ; reste utile hors-ligne (heure, date, photos locales).
- **Réseau** : tout reste sur le réseau local ; aucun cloud par défaut ([privacy.md](privacy.md)).
- **TV** (plus tard) : hub → Zigbee2MQTT → contrôleur IR.

Décisions : [adr/](adr/).

## Appairage
`npm run pair -w @presence/hub` ou 5 touches rapides sur l'heure de l'écran maison (`POST /pairing/start` sur la boucle locale, jeton d'affichage, une fois toutes les 3 s, absent du port HTTPS du réseau) → secret à usage unique (2 min) + QR code du lien `presence://pair?u=<adresse>&s=<secret>&f=<empreinte>` → l'appareil photo du téléphone l'ouvre dans « Réglages famille » (derrière le code famille) → l'adresse et l'empreinte sont affichées à comparer avec celles du hub → « Appairer » : le téléphone échange le secret (`POST /pair`, connexion épinglée à l'empreinte du lien) contre **son propre jeton d'appareil**, chiffré ensuite par le Keystore. Le hub ne stocke que des empreintes SHA-256 (un fichier par appareil dans `data/devices/`, un par QR code dans `data/claims/`, droits 0600 : supprimer le fichier d'un QR code l'utilise, supprimer celui d'un appareil le révoque). Le jeton statique `PRESENCE_PAIRING_TOKEN` est facultatif. Le certificat du hub est épinglé ([ADR 0003](adr/0003-tls-epingle.md)).

## Transport
Écran → hub : HTTP loopback (même appareil). Téléphone → hub : HTTPS, certificat auto-signé épinglé par empreinte ([ADR 0003](adr/0003-tls-epingle.md)).

## CI/CD
- `ci.yml` : lint/typecheck/test/build web (Node 22) et lint/test/APK Android, filtré par chemins ; job `ci-ok` = check requis.
- `codeql.yml` : analyse statique hebdomadaire et sur PR.
- `release.yml` : tag `vX.Y.Z` → archive Pi + AAB signé → GitHub Release. Secrets requis : `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. La publication Play Store (fastlane / Play Developer API) viendra une fois le compte développeur créé.
