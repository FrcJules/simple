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

L'application ne lit que les notifications des applications Téléphone, Messages et WhatsApp, et ignore toutes les autres.

## Conservation
- Sur le téléphone : seuls les réglages ci-dessus sont conservés. Les notifications ne sont pas enregistrées.
- Sur l'écran maison : les 200 derniers événements sont gardés **en mémoire** et disparaissent au redémarrage.

## Partage
Aucune donnée n'est vendue, partagée ou transmise à un tiers.

## Vos droits
Vous pouvez à tout moment retirer l'accès aux notifications ou aux contacts dans les réglages d'Android, ou désinstaller l'application, ce qui supprime toutes les données stockées sur le téléphone.

## Enfants
Présence ne s'adresse pas aux enfants.

## Contact
Questions : ouvrez une issue sur https://github.com/FrcJules/presence ou écrivez à jules.francois@epitech.eu.

Le code source est public : vous pouvez vérifier ces engagements.
