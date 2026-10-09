# 0007 — SMS en « mode direct » quand Android refuse le rôle d'application SMS

Statut : accepté pour les versions de test (2026-10-09) ; complète l'[ADR 0005](0005-sms-par-defaut-et-historique-illimite.md).

## Contexte
L'ADR 0005 fait de Présence l'application SMS par défaut. Premier essai sur un vrai téléphone : l'application installée à la main (hors boutique) n'obtient pas le rôle, Android ne pose même pas la question. Résultat : « pas de messages », « écrire » renvoyait vers une autre application. Un rôle refusé ne doit pas rendre la messagerie inutilisable.

## Décision
- Le **rôle SMS reste le mode complet** (réception directe, MMS, historique du téléphone). On le demande d'abord.
- S'il n'est pas accordé, **mode direct** avec les seules permissions SMS (`READ_SMS`, `SEND_SMS`), demandées à ce moment :
  - les SMS existants sont **copiés dans l'historique de Présence** (une copie, rien n'est déplacé ni effacé dans l'autre application), puis les nouveaux à chaque ouverture et à chaque notification de SMS ;
  - l'envoi se fait directement (`SmsManager`), suivi d'une confirmation d'envoi comme avant ;
  - les identifiants des messages copiés sont ceux du mode complet (`sms-<id>`) : passer plus tard au rôle ne crée aucun doublon.
- Toujours **debug seulement** pour l'instant (permissions dans `src/debug`), jusqu'au test sur téléphone réel.
- Les MMS (photos) restent réservés au mode complet.

## Conséquences
+ La messagerie marche même si Android refuse le rôle.
+ Aucune donnée ne sort du téléphone en plus (même copie locale qu'à l'ADR 0005).
− Un SMS envoyé en mode direct n'apparaît pas dans l'autre application de messages (seule l'application par défaut écrit dans la base du système).
− Les nouveaux SMS ne sont copiés qu'à l'ouverture de Présence ou à une notification : pas d'alerte propre à Présence tant qu'elle n'est pas l'application SMS.
− Play Store : la déclaration « SMS » exige d'être le gestionnaire par défaut ; le mode direct ne sert qu'aux versions de test.

## Alternatives
Ne rien faire sans le rôle (inutilisable) ; renvoyer vers l'application de messages d'origine (contraire au besoin : un seul endroit, très simple).
