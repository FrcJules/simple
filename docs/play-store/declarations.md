# Déclarations et questionnaires

## Fiche
- **Catégorie** : Style de vie (ou Communication).
- **Coordonnées** : une adresse e-mail publique obligatoire.
- **Publicité** : l'application ne contient pas de publicité.

## Public cible et contenu
- **Public cible** : 18 ans et plus (l'application ne s'adresse pas aux enfants).
- **Questionnaire de classification (IARC)** : aucune violence, contenu sexuel, jeu d'argent, ni interaction entre utilisateurs, ni partage de position → classification attendue « 3+ ».
- **Application d'actualités / santé / finance / gouvernement** : non.

## Autorisations sensibles
| Autorisation | Justification pour la fiche / la revue |
|---|---|
| `READ_CONTACTS` | Afficher en photos les contacts marqués d'une étoile, pour les appeler. Lecture seule, aucune donnée envoyée |
| `CALL_PHONE` | Lancer l'appel après la confirmation explicite de l'utilisateur (repli sur le composeur si refusée) |
| Accès aux notifications (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Fonction **facultative** : afficher sur l'écran maison de la famille les appels manqués, SMS et WhatsApp. Ne lit que Téléphone, Messages et WhatsApp, envoie uniquement au réseau local de l'utilisateur. Divulgation dans la fiche, la politique de confidentialité et l'écran « Réglages famille » |
| Historique des messages (illimité, local), dictée et lecture vocale | Les messages vus dans les notifications sont gardés **localement** pour être affichés et lus à voix haute sur demande ; la dictée sert à répondre. Détails et limites : [ADR 0004](../adr/0004-messages-conserves-sur-le-telephone.md) |
| `INTERNET` | Uniquement pour joindre l'écran maison sur le Wi-Fi du foyer (HTTPS épinglé) |

**Jamais utilisé** : `READ_CALL_LOG` (journal d'appels). **SMS** : les permissions `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` ne sont déclarées que dans les versions de **test** (manifeste `src/debug`) tant que les MMS ne sont pas gérés ; la version publiée n'en déclare aucune. Quand elles seront déclarées, voir la section « Application SMS par défaut » ci-dessous.

## Instructions pour l'équipe de revue Google
> Présence est un écran d'accueil simplifié. Sans matériel supplémentaire : installer, choisir Présence comme application d'accueil, toucher « Appeler » (accepter les contacts), voir les contacts étoilés en photos, confirmer un appel. La fonction d'écran maison (accès aux notifications, « Réglages famille ») est facultative et nécessite un Raspberry Pi ; elle n'est pas nécessaire pour utiliser l'application. Aucun compte ni mot de passe n'est requis.

## Application SMS par défaut (ADR 0005)
Présence peut devenir l'application SMS par défaut (rôle `ROLE_SMS`). Google Play impose alors :
- le **formulaire de déclaration des permissions « SMS et journal d'appels »** : usage déclaré = *application SMS par défaut* ; fonctions essentielles : recevoir, envoyer et afficher des SMS pour des seniors, avec dictée et lecture vocale ;
- une **justification** pour `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` (uniquement tant que l'application est celle par défaut ; arrêt de l'usage dès que le rôle est perdu) ;
- une **vidéo de démonstration** de la demande du rôle et de l'envoi/réception d'un SMS ;
- les **MMS** doivent fonctionner : un MMS non géré serait perdu. **Ne pas soumettre la version de production avant les MMS** (en release, le manifeste ne déclare ni le rôle ni les permissions SMS, et `SMS_ROLE_OFFERED` est `false`).
**MMS** : `RECEIVE_MMS` et `RECEIVE_WAP_PUSH` servent à recevoir les photos de la famille ; le téléchargement et l'accusé de réception passent par les API du système (`SmsManager`). Joindre à la vidéo de démonstration la réception d'un MMS.
Revue plus longue que d'habitude, refus possible : prévoir du temps avant de promettre une date.
