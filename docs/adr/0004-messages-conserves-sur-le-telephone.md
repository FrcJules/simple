# 0004 — Conserver les derniers messages sur le téléphone

Statut : accepté (2026-10-07)

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

## Alternatives
Ne rien conserver (messages perdus) ; base Room chiffrée (plus lourd) ; devenir l'application SMS par défaut (rejeté, voir ADR 0002).
