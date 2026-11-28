# Architecture

Simple est **une seule application Android** (`apps/launcher-android`, Kotlin + Jetpack Compose), sans serveur, sans compte et **sans accès à Internet** (pas de permission `INTERNET`). Tout ce qu'elle reçoit, stocke et affiche reste sur le téléphone ([privacy.md](privacy.md)).

```
 Téléphone Android
 ┌──────────────────────────────────────────────────────────────┐
 │  Accueil (launcher HOME)        Réglages famille (code)      │
 │   cases, contacts en photo       apparence JSON, proches     │
 │            │                                                 │
 │  Messages ─┼─ SMS / MMS : rôle d'application SMS par défaut  │
 │            │   (réception, envoi, import, opérateur)          │
 │            └─ WhatsApp (et SMS sans le rôle) : lecture des    │
 │                notifications, réponse par la notification     │
 │                                                              │
 │  Historique local (SQLite + fichiers privés, hors sauvegarde)│
 │  Dictée / synthèse vocale : services du système              │
 └──────────────────────────────────────────────────────────────┘
```

## Modules (`app/src/main/java/app/simple/launcher/`)
- **Accueil et appels** (`MainActivity`, `Contacts*`, `FavoritesStore`, `HomeRole`) : launcher HOME, proches en photos, appel après confirmation.
- **Apparence** (`config/`) : thème et cases décrits par un JSON validé ([customization.md](customization.md)), cases d'applications du téléphone (`apps/`).
- **Messages** (`messages/`) : historique illimité en SQLite ([ADR 0005](adr/0005-sms-par-defaut-et-historique-illimite.md)), conversations par personne, textes sensibles masqués, lecture à voix haute, journal de diagnostic sans contenu.
- **SMS** (`sms/`) : rôle d'application SMS par défaut (réception, envoi, import de l'historique, statut d'envoi). Le « mode direct » ([ADR 0007](adr/0007-sms-mode-direct.md)) n'existe que dans les versions de test (`BuildConfig.SMS_DIRECT_MODE_OFFERED`, récepteur `SMS_RECEIVED` déclaré dans `src/debug/AndroidManifest.xml`).
- **MMS** (`mms/`) : réception (décodeur borné, images vérifiées, fichiers privés) et envoi d'une photo (réduite, sans métadonnées).
- **Notifications** (`notifications/`) : `NotificationListenerService` limité à une liste blanche (applications Messages et WhatsApp ; les notifications de l'application Téléphone ne sont plus lues) ; réponse WhatsApp par l'action « Répondre » de la notification ([ADR 0002](adr/0002-notifications-plutot-que-journal-d-appels.md)).
- **Sécurité** (`security/`) : code famille (PBKDF2, empreinte scellée par le Keystore) ; `LegacyHubCleanup` efface au premier démarrage les données d'appairage laissées par une version antérieure à la v0.2.0.

## Versions de l'application
| | Version Google Play (`release`) | Version de test (`debug`) |
|---|---|---|
| Rôle d'application SMS par défaut | proposé | proposé |
| Mode direct (permissions SMS sans le rôle) | **absent** | proposé si Android refuse le rôle |
| Minification | oui | non |

Décisions : [adr/](adr/).

## CI/CD
- `ci.yml` (filtré par chemins ; job `ci-ok` = seul check requis par la protection de `main`) :
  - `android` : `lintDebug`, `lintRelease` (la version Play diffère : pas de mode direct, minifiée), tests unitaires (`testDebugUnitTest`), APK debug, captures d'écran Roborazzi (artefact `launcher-screenshots-jvm`) ; vérification que le manifeste **release** fusionné ne contient **pas** la permission `INTERNET` (une dépendance qui la rajouterait fait échouer la CI) ; APK debug publié en artefact `launcher-debug-apk`.
  - `android-emulator` : tests d'interface sur 2 émulateurs en parallèle (API 34, `scripts/ci-emulator-tests.sh`), application forcée en français ; captures et rapports dans `emulator-screenshots-and-reports-*`.
- `codeql.yml` : analyse CodeQL hebdomadaire et sur PR.
- `release.yml` : tag `vX.Y.Z` → le job `android` attend l'**approbation de l'environnement `release`**, puis signe l'AAB (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`), l'envoie sur la piste **test interne** de Google Play **en brouillon** (si `PLAY_SERVICE_ACCOUNT_JSON` est présent) et le joint à une GitHub Release. `versionCode` = numéro du run. Le passage en test fermé ou en production se fait à la main dans la Play Console : voir [release.md](release.md).

## Tests
- **Unitaires** : JVM avec Robolectric (`gradle :app:testDebugUnitTest`).
- **Captures d'écran** : Roborazzi, sans émulateur ([testing.md](testing.md)).
- **Interface** : émulateur Android dans la CI seulement (job `android-emulator`).
- Aucun test sur un vrai téléphone n'est automatisé : voir [real-phone-test.md](real-phone-test.md).
