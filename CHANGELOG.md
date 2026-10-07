# Changelog
Format [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/), versionnage [SemVer](https://semver.org/lang/fr/).

## [Non publié]
### Corrigé
- Build du hub (`tsup.config.ts`) ; CI : permissions de `paths-filter`, `ci-ok` dépend de `changes`.
### Ajouté
- Apparence de l'accueil configurable par un fichier JSON validé (contraste imposé, texte jamais plus petit) ; import dans « Réglages famille ».
- Launcher : écran Messages (liste, détail, lecture à voix haute, dictée et réponse via l'application Messages), pastille « nouveaux » sur l'accueil, mémoire locale bornée (ADR 0004).
- Launcher : photos des proches choisies par la famille dans « Réglages famille ».
- Launcher : thème à fort contraste, vraies icônes, écran d'explication avant les permissions, écran « J'appelle Marie… » annulable, retour haptique, prénom configurable, orientation portrait.
- Launcher : contacts favoris en photos, appel direct avec confirmation, tuiles appareil photo / galerie / messages.
- Lecture des notifications (appels manqués, SMS, WhatsApp) → hub → écran maison ; événement `new_message`.
- Transport hub ↔ téléphone en HTTPS avec certificat épinglé (ADR 0003).
- Écran « Réglages famille » (adresse du hub, jeton, accès aux notifications).
- Squelette du monorepo : launcher Android, hub, écran, contrats, CI/CD.
