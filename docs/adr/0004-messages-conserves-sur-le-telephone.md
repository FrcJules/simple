# 0004 — Conserver les derniers messages sur le téléphone

Statut : accepté (2026-10-07) — **remplacé en partie par l'[ADR 0005](0005-sms-par-defaut-et-historique-illimite.md)** : plus de limite de 30 messages / 7 jours, et l'envoi de SMS passe par le rôle d'application SMS par défaut. Le reste (dictée, lecture, données sensibles écartées, effacement) reste valable.

## Contexte
Le launcher doit montrer à Jean-Pierre les messages reçus (photo de l'expéditeur, lecture à voix haute). Google Play interdit de lire la boîte SMS ([ADR 0002](0002-notifications-plutot-que-journal-d-appels.md)) : la seule source est la **notification**, qui disparaît dès qu'on la balaie. Sans mémoire, un message ignoré quelques minutes serait perdu.

## Décision
- Les messages vus dans les notifications des applications autorisées (Messages, WhatsApp) sont conservés **sur le téléphone seulement**, dans un fichier privé de l'application.
- Bornés : **30 messages** et **7 jours** au plus, le plus ancien est supprimé en premier.
- Exclus des sauvegardes cloud et des transferts d'appareil.
- Jamais envoyés ailleurs que vers l'écran maison appairé (comportement existant), qui les garde en mémoire seulement.
- Pas d'envoi de SMS par l'application : « Répondre » ouvre l'application Messages avec le texte dicté, que l'utilisateur envoie lui-même.

## Conséquences
+ Les messages restent lisibles et écoutables après avoir disparu de la barre de notifications.
− Du contenu de messages est stocké, même brièvement : à déclarer dans la politique de confidentialité (fait) et à ne jamais journaliser (`StoredMessage.toString` le masque).
− Le fichier n'est pas chiffré (protégé par le stockage privé d'Android). Chiffrement Keystore possible plus tard.
- Désinstaller l'application supprime tout.

## Dictée et lecture à voix haute
- **Lecture** : uniquement sur appui du bouton « Écouter », jamais automatique. Elle utilise le moteur de synthèse vocale du téléphone, choisi par le système : il peut, selon la voix installée, passer par le réseau (hors de notre contrôle).
- **Dictée** : l'application demande la reconnaissance vocale du système (`RecognizerIntent`, avec la préférence « hors ligne »). Sur la plupart des téléphones, c'est le service de Google, **qui peut envoyer l'audio à ses serveurs** : c'est une exception au principe « rien ne quitte le foyer », assumée parce que l'utilisateur la déclenche lui-même, mais **hors de notre contrôle**. L'application ne reçoit que le texte, qu'elle remet à l'application Messages.
- **Erreurs d'identité** : un nom dans une notification n'est pas une identité. La photo et le numéro d'un proche ne sont associés que si **un seul** contact porte ce nom, et « Répondre » n'est proposé que pour les SMS, jamais pour WhatsApp (la réponse changerait de canal).
- **Données sensibles** : les messages qui ressemblent à un code à usage unique ou à une alerte bancaire sont **écartés** avant tout stockage, lecture ou envoi à l'écran maison.
- **Effacement** : « Réglages famille » permet d'effacer les messages conservés et les photos des proches.

## Alternatives
Ne rien conserver (messages perdus) ; base Room chiffrée (plus lourd) ; devenir l'application SMS par défaut (rejeté, voir ADR 0002).
