# Changelog
Format [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/), versionnage [SemVer](https://semver.org/lang/fr/).

## [Non publié]
### Corrigé
- Build du hub (`tsup.config.ts`) ; CI : permissions de `paths-filter`, `ci-ok` dépend de `changes`.
### Ajouté
- Launcher : contacts favoris en photos, appel direct avec confirmation, tuiles appareil photo / galerie / messages.
- Lecture des notifications (appels manqués, SMS, WhatsApp) → hub → écran maison ; événement `new_message`.
- Écran « Réglages famille » (adresse du hub, jeton, accès aux notifications).
- Squelette du monorepo : launcher Android, hub, écran, contrats, CI/CD.
