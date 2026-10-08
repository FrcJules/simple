# Changelog
Format [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/), versionnage [SemVer](https://semver.org/lang/fr/).

## [Non publié]
### Corrigé
- Build du hub (`tsup.config.ts`) ; CI : permissions de `paths-filter`, `ci-ok` dépend de `changes`.
### Ajouté (SMS)
- Application SMS par défaut (facultative) : réception, envoi, import de l'historique du téléphone, conversations par personne, statut d'envoi, codes de sécurité masqués, liens `sms:`. MMS à venir ; rôle non proposé en release.

### Ajouté (appairage)
- Appairage par QR code : `npm run pair -w @presence/hub` affiche un QR code à **secret à usage unique** (2 minutes) et l'empreinte du hub ; le téléphone l'échange contre **son propre jeton d'appareil** (haché côté hub, révocable) après comparaison de l'empreinte par la famille. Adresses normalisées et strictes (identifiants, chemin, zéros en tête refusés).

### Ajouté (sécurité)
- « Réglages famille » protégés par un code famille (empreinte PBKDF2, blocage progressif) ; jeton d'appairage chiffré par le Keystore Android.

### Ajouté (WhatsApp)
- Répondre à un message WhatsApp par la dictée, via l'action « Répondre » de sa notification ; sinon bouton « Ouvrir WhatsApp ».

### Ajouté (MMS)
- Envoi d'une photo par MMS (prendre ou choisir, aperçu et confirmation, réduction sans métadonnées, statut, réessai).
- Réception des MMS (durcie après revue de sécurité : décodeur borné, adresse filtrée, annonces plafonnées, images vérifiées, effacement complet) : photos affichées dans la conversation, « en cours de réception », « pas arrivée » avec réessai, visionneuse plein écran, accusé de réception à l'opérateur. Décodeur issu d'`android-smsmms` (Apache-2.0).

### Modifié
- Messages : historique **illimité** sur le téléphone (base SQLite, migration automatique de l'ancien fichier), à la place de 30 messages / 7 jours.

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
