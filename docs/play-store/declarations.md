# Déclarations et questionnaires (Play Console → Contenu de l'application)

## Fiche
- **Catégorie** : Communication.
- **Coordonnées** : adresse e-mail publique obligatoire.
- **Publicité** : l'application ne contient pas de publicité.
- **Politique de confidentialité** : https://frcjules.github.io/simple/privacy-policy

## Public cible et contenu
- **Public cible** : 18 ans et plus (l'application ne s'adresse pas aux enfants).
- **Questionnaire de classification (IARC)** : catégorie « Communication ». Les utilisateurs échangent des messages (SMS/MMS, avec des contacts qu'ils connaissent) : répondre **Oui** à « les utilisateurs peuvent communiquer entre eux » ; pas de violence, de contenu sexuel, de jeu d'argent, ni de partage de position.
- **Application d'actualités / santé / finance / gouvernement** : non.
- **Accès à l'application** : aucune connexion requise ; donner les instructions de revue ci-dessous.

## Déclaration « SMS et journal d'appels » (obligatoire)
Play Console → *Contenu de l'application* → *Autorisations sensibles* → **SMS et journal d'appels**.
- **Fonction principale** : *Gestionnaire de SMS par défaut* (Default SMS handler).
- **Permissions déclarées** : `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`. Aucune permission du journal d'appels (`READ_CALL_LOG` n'est pas demandée).
- **Description à copier** :
  > Simple est une application de messages et un écran d'accueil pour personnes âgées qui lisent peu. Quand l'utilisateur la choisit comme application SMS par défaut (question d'Android, à l'initiative de l'utilisateur ou de sa famille), elle reçoit, affiche en très grand, lit à voix haute et envoie les SMS et MMS. READ_SMS sert à afficher l'historique existant, SEND_SMS à envoyer la réponse dictée et confirmée, RECEIVE_SMS/RECEIVE_MMS/RECEIVE_WAP_PUSH à recevoir les messages et les photos de la famille. Les messages restent sur le téléphone : l'application n'a pas d'accès à Internet et n'envoie rien au développeur ni à un tiers.
- **Vidéo de démonstration** (lien YouTube non répertorié ou Google Drive), 1 à 2 minutes, sur un vrai téléphone avec carte SIM :
  1. Installer Simple, toucher « Messages » : Simple explique « Pour lire et écrire vos messages ici, le téléphone va vous poser une ou deux questions », toucher « Continuer » ; la question d'Android (application SMS par défaut) apparaît, accepter.
  2. Recevoir un SMS d'un proche : il apparaît en grand dans « Messages » ; toucher « Écouter ».
  3. Toucher « Répondre », dicter, vérifier, « Envoyer » ; montrer le message reçu sur l'autre téléphone.
  4. Recevoir un MMS (photo) et l'ouvrir.

## Autres autorisations sensibles
| Autorisation | Justification |
|---|---|
| `READ_CONTACTS` | Afficher les proches en photos, les appeler et leur écrire, reconnaître l'expéditeur d'un message. Lecture seule |
| `CALL_PHONE` | Lancer l'appel après la confirmation de l'utilisateur (repli sur le composeur si refusée) |
| Accès aux notifications (`BIND_NOTIFICATION_LISTENER_SERVICE`) | **Facultatif** : afficher dans Simple les messages WhatsApp et les appels manqués, et répondre à WhatsApp par l'action « Répondre » de sa notification. Ne lit que Téléphone, Messages et WhatsApp ; rien ne quitte le téléphone. Divulgation : fiche, politique de confidentialité, écran « Réglages famille » |
| Application d'accueil (`HOME`) | Simple remplace l'écran d'accueil à la demande de l'utilisateur ; retour possible dans les réglages d'Android |

## Instructions pour l'équipe de revue Google
> Simple est un écran d'accueil et une application de messages simplifiés pour personnes âgées. Aucun compte ni mot de passe. Au premier lancement, un guide propose de choisir un code famille (par exemple 2468), de faire de Simple l'écran d'accueil et d'autoriser les contacts. En touchant « Messages », Simple propose de devenir l'application de messages (« Continuer », puis accepter la question d'Android). Ensuite : « Appeler » affiche les contacts favoris en photos (confirmation avant l'appel) ; « Messages » affiche les SMS, MMS et, si l'accès aux notifications est accordé, WhatsApp ; chaque message peut être écouté et recevoir une réponse dictée, confirmée avant l'envoi. Les réglages de la famille s'ouvrent en touchant 7 fois l'heure de l'accueil puis en saisissant le code famille.
