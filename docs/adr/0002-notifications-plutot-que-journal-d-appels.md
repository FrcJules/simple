# 0002 — Lire les notifications plutôt que le journal d'appels / les SMS

Statut : accepté (2026-10-06) — décision prise ; le `NotificationListenerService` n'est pas encore implémenté.

## Contexte
Présence doit afficher « Marie vous a appelé » et rendre visibles les SMS/messages sans que la famille change d'outil. Les permissions `READ_CALL_LOG`, `READ_SMS`, `RECEIVE_SMS` sont, selon la [politique Google Play](https://support.google.com/googleplay/android-developer/answer/9047303), réservées à l'application SMS, Téléphone ou Assistant **par défaut**, hors exceptions rares. Un launcher n'est dans aucun de ces cas ; devenir l'application SMS/Téléphone par défaut serait disproportionné.

## Décision
Utiliser un `NotificationListenerService` (accès aux notifications, accordé manuellement par la famille lors de l'installation) pour :
- repérer les notifications d'appel manqué de l'application Téléphone ;
- afficher les SMS et messages (WhatsApp inclus) reçus, pour les montrer en grand et les lire à voix haute.

Garde-fous :
- On ne conserve que expéditeur + type + heure (+ texte du message uniquement en mémoire pour l'affichage/TTS) ; jamais d'envoi hors du réseau local ([privacy.md](../privacy.md)).
- Liste blanche de paquets surveillés (Téléphone, Messages, WhatsApp) ; toutes les autres notifications sont ignorées sans être lues.
- Écran d'explication en langage simple avant d'ouvrir les réglages d'accès aux notifications ; formulaire de déclaration Play rempli en conséquence.

## Conséquences
+ Aucune permission restreinte Play ; la famille continue à utiliser WhatsApp/SMS/téléphone.
− Dépend du format des notifications de chaque application (fragile, à tester par application et par constructeur).
− L'accès aux notifications est sensible : revue Play probable, justification claire dans la fiche.
− Les appels manqués donnent un nom ou un numéro tel qu'affiché par la notification ; la photo vient du contact Android correspondant.

## Alternatives
- `READ_CALL_LOG` / `READ_SMS` : refusé par la politique Play pour un launcher.
- Devenir application SMS/Téléphone par défaut : trop lourd, risque de casser des fonctions vitales (urgences).
- Hors Play (F-Droid / APK) : possible plus tard, mais objectif = Play Store.
