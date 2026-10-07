# 0005 — Application SMS par défaut, historique illimité, photos du téléphone intouchées

Statut : accepté (2026-10-07), **pas encore implémenté**. Remplace, pour les points ci-dessous, certaines décisions de l'[ADR 0004](0004-messages-conserves-sur-le-telephone.md).

## Décisions du propriétaire du projet
1. **Photos** : Présence ne lit ni ne gère la photothèque du téléphone (« trop personnel »). Les tuiles « Photo » et « Mes photos » ouvrent l'appareil photo et la galerie du téléphone, sans développement propre. Aucune permission d'accès aux photos. (Les photos de **proches** choisies par la famille dans « Réglages famille » via le sélecteur système restent possibles : elles n'ouvrent pas la photothèque.)
2. **Historique** : les messages sont conservés **indéfiniment, sur le téléphone seulement**. Cela remplace la limite « 30 messages / 7 jours » de l'ADR 0004.
3. **Envoi** : l'application devient l'**application SMS par défaut** (rôle `ROLE_SMS`) pour recevoir et envoyer des SMS. Cela remplace « pas d'envoi de SMS » (ADR 0004) et le rejet du rôle SMS (ADR 0002 / 0004). Option qui avait été déconseillée pendant la discussion, choisie en connaissance de cause.
4. **Interface modifiable** : thème et tuiles décrits par un fichier JSON validé ([customization.md](../customization.md)) — **livré**.
5. **WhatsApp** : toujours lu via les notifications ; la réponse passerait par l'action « Répondre » de la notification.

## Conséquences à traiter avant d'implémenter 2 et 3
- Un historique illimité exige un stockage fiable (base SQLite/Room, voire chiffrement Keystore) à la place du fichier actuel, une purge manuelle (« Réglages famille »), et la mise à jour de [privacy.md](../privacy.md), de la politique de confidentialité et de la section « Sécurité des données ».
- Les permissions `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` ne sont admises par Google Play que pour l'application par défaut, avec le **formulaire de déclaration des permissions SMS / Journal d'appels** (revue plus longue, refus possible).
- Le rôle impose de gérer toute la messagerie : réception, envoi, **MMS** (photos reçues par SMS), notifications de nouveaux messages, écriture dans la base système. Un MMS non géré est **perdu** : ne pas définir l'application par défaut sur le téléphone de Jean-Pierre tant que les MMS ne sont pas pris en charge ou que ce risque n'est pas accepté explicitement.
- L'application doit continuer à fonctionner (dégradation gracieuse) si le rôle est retiré.

## Alternative écartée par le propriétaire
Pré-remplir le message dans l'application Messages du téléphone (comportement actuel, ADR 0004), sans responsabilité sur le rôle SMS.
