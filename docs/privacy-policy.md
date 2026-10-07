# Politique de confidentialité — Présence

*Dernière mise à jour : 7 octobre 2026*

Présence est une application libre et gratuite. Elle n'a **ni compte, ni publicité, ni outil de mesure d'audience**. Le développeur ne reçoit **aucune donnée** vous concernant.

## Ce que l'application utilise, et pourquoi

| Donnée | Pourquoi | Où elle va |
|---|---|---|
| Contacts marqués d'une étoile (nom, photo, numéro) | Afficher les proches en photos et les appeler | Reste sur le téléphone |
| Autorisation d'appeler | Lancer l'appel après votre confirmation | Reste sur le téléphone |
| Notifications d'appel manqué, de SMS et de WhatsApp (expéditeur, heure, texte) — **facultatif**, uniquement si la famille active l'accès aux notifications | Afficher « Marie vous a appelé » ou le dernier message sur l'écran maison de la famille | Envoyé **uniquement** à l'écran maison que vous avez vous-même installé (Raspberry Pi) sur votre réseau Wi-Fi, chiffré (HTTPS). Jamais vers Internet ni vers le développeur |
| Prénom, adresse de l'écran maison, code d'appairage | Réglages de la famille | Reste sur le téléphone, exclu des sauvegardes |
| Photos choisies par la famille pour les proches (depuis le sélecteur de photos d'Android, sans autorisation supplémentaire) | Reconnaître la personne | Copiées dans l'application, restent sur le téléphone |

L'application ne lit que les notifications des applications Téléphone, Messages et WhatsApp, et ignore toutes les autres.

## Conservation
- Sur le téléphone : les réglages ci-dessus, et **l'historique des messages** (SMS et WhatsApp), **conservé sans limite de durée** tant que vous ne l'effacez pas, pour pouvoir les afficher et les lire à voix haute dans l'application. Ils restent dans le stockage privé de l'application, exclus des sauvegardes, et disparaissent à la désinstallation. Les appels manqués ne sont pas enregistrés.
- Photos choisies pour les proches : copiées dans le stockage privé de l'application, jamais dans vos contacts, et supprimées à la désinstallation.
- Sur l'écran maison : les 200 derniers événements sont gardés **en mémoire** et disparaissent au redémarrage.

## Dictée et lecture à voix haute
Ces deux fonctions sont **facultatives** et ne se déclenchent que lorsque vous touchez le bouton. Elles utilisent les services de reconnaissance et de synthèse vocales **de votre téléphone** (généralement ceux de Google). L'application demande un traitement hors ligne, mais votre téléphone peut envoyer l'audio à ces services selon ses réglages : consultez la politique de confidentialité de votre fournisseur. L'application ne reçoit que le texte dicté.

Les messages qui ressemblent à un code à usage unique ou à une alerte bancaire sont ignorés et ne sont ni conservés, ni lus, ni transmis.

## Partage
Aucune donnée n'est vendue, partagée ou transmise à un tiers.

## Vos droits
Vous pouvez effacer les messages conservés et les photos des proches dans « Réglages famille ». Vous pouvez à tout moment retirer l'accès aux notifications ou aux contacts dans les réglages d'Android, ou désinstaller l'application, ce qui supprime toutes les données stockées sur le téléphone.

## Enfants
Présence ne s'adresse pas aux enfants.

## Contact
Questions : ouvrez une issue sur https://github.com/FrcJules/presence ou écrivez à jules.francois@epitech.eu.

Le code source est public : vous pouvez vérifier ces engagements.

## Si Présence devient votre application de messages (SMS)
C'est un choix facultatif, fait dans l'application. Présence reçoit alors vos SMS : ils sont enregistrés dans la boîte de réception d'Android (comme avec toute application de messages) et copiés dans l'historique de Présence, **sur votre téléphone uniquement**, sans limite de durée tant que vous ne l'effacez pas (« Réglages famille »). Les messages existants sont copiés à l'activation. Les codes de sécurité sont masqués et jamais lus à voix haute. Aucun message n'est envoyé à Internet ni au développeur ; seuls les messages ordinaires peuvent être affichés sur votre écran maison, sur votre réseau Wi-Fi, de façon chiffrée.
