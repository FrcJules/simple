# Launcher Android : état et plan

*Mis à jour le 7 octobre 2026. « Vérifié » = essayé sur un émulateur Android 13 ; « testé » = tests automatiques seulement.*

## État actuel

| Fonction | État |
|---|---|
| Accueil à 4 gros boutons, thème contrasté, icônes | **Vérifié** |
| Appeler : proches (contacts étoilés) en photos, confirmation, « J'appelle… », appel direct | **Vérifié** (appel simulé) |
| Photos de contacts prises dans Android | Codé, **non vérifié** avec une vraie photo |
| Explication avant les permissions, écran de refus | **Vérifié** |
| Appareil photo et galerie | Ouvrent les applications du système, **non vérifié** |
| Messages | Ouvre l'application Messages du système. **Pas de lecture dans le launcher** |
| Notifications (SMS, appels manqués) → écran maison, TLS épinglé | **Vérifié** de bout en bout |
| Notifications WhatsApp | **Testé** (décodeur) seulement : pas de WhatsApp sur l'émulateur |
| Réglages famille (prénom, hub, appairage) | **Vérifié** ; pas protégé par un code |

## Ce qui manque (par priorité)
1. **Messages dans le launcher** : voir les messages reçus avec la photo de l'expéditeur, les écouter (voix), répondre en dictant.
2. **Photos des proches choisies par la famille** quand le contact n'en a pas.
3. Protéger « Réglages famille » (code famille), QR code d'appairage.
4. Retour sonore / vocal (« J'appelle Marie »), double appui, grandes polices à 200 %.
5. Appareil photo et galerie intégrés (pas d'application externe).
6. Répondre à WhatsApp sans quitter le launcher.
7. Jeton chiffré (Keystore), filtre des codes à usage unique.

## Contraintes Google Play (voir ADR 0002)
- Pas de lecture de la boîte SMS (`READ_SMS`) ni d'envoi direct (`SEND_SMS`), réservés à l'application SMS par défaut. Les messages affichés viennent donc des **notifications**, et l'envoi passe par l'application Messages (texte pré-rempli) ou, plus tard, par l'action « répondre » de la notification.

## Plan
- **Phase 1 : photos des proches.** Choisir une photo pour un proche depuis « Réglages famille » (stockée sur le téléphone, sans permission supplémentaire).
- **Phase 2 : messages.** Mémoire locale bornée (30 messages, 7 jours), écran Messages, détail avec lecture à voix haute, pastille « nouveaux » sur l'accueil.
- **Phase 3 : répondre.** Dictée vocale puis envoi par l'application Messages ; WhatsApp via l'action de réponse de la notification.
- **Phase 4 : fiabilité.** Faux WhatsApp pour les tests d'émulateur, sons, code famille.
