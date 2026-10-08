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
`npm run pair -w @presence/hub` → QR code du lien `presence://pair?u=<adresse>&t=<jeton>&f=<empreinte>` → l'appareil photo du téléphone l'ouvre dans « Réglages famille » (derrière le code famille) → champs préremplis → « Enregistrer ». Le jeton est ensuite chiffré par le Keystore ; le certificat du hub est épinglé par son empreinte ([ADR 0003](adr/0003-tls-epingle.md)).

## Transport
Écran → hub : HTTP loopback (même appareil). Téléphone → hub : HTTPS, certificat auto-signé épinglé par empreinte ([ADR 0003](adr/0003-tls-epingle.md)).

## CI/CD
- `ci.yml` : lint/typecheck/test/build web (Node 22) et lint/test/APK Android, filtré par chemins ; job `ci-ok` = check requis.
- `codeql.yml` : analyse statique hebdomadaire et sur PR.
- `release.yml` : tag `vX.Y.Z` → archive Pi + AAB signé → GitHub Release. Secrets requis : `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. La publication Play Store (fastlane / Play Developer API) viendra une fois le compte développeur créé.
