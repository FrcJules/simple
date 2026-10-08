# Launcher Android : état et plan

*Mis à jour le 7 octobre 2026. « Vérifié » = essayé sur un émulateur Android 13 ; « testé » = tests automatiques seulement.*

## État actuel

| Fonction | État |
|---|---|
| Accueil à 4 gros boutons, thème contrasté, icônes, pastille « N nouveaux » | **Vérifié** |
| Appeler : proches (contacts étoilés) en photos, confirmation, « J'appelle… », appel direct | **Vérifié** (appel simulé) |
| Photo d'un proche choisie par la famille (sélecteur Android, sans permission) | **Vérifié** : visible sur l'appel et les messages |
| Photos de contacts prises dans Android | Codé, non vérifié avec une vraie photo de contact |
| Messages : liste, détail, pastille, photo de l'expéditeur | **Vérifié** (SMS simulés) |
| Messages : codes à usage unique écartés | **Vérifié** |
| Messages : lecture à voix haute | **Déclenchée** sans erreur ; le son n'a pas pu être entendu depuis l'émulateur |
| Messages : répondre (dictée puis application Messages) | Chemin d'**erreur** vérifié (dictée indisponible). **Dictée réussie non vérifiée** : pas de reconnaissance vocale sur l'émulateur |
| Appareil photo et galerie | Ouvrent les applications du système, non vérifié |
| Notifications SMS / appels manqués → écran maison, TLS épinglé | **Vérifié** de bout en bout |
| Notifications WhatsApp | **Testé** (décodeur) seulement : pas de WhatsApp sur l'émulateur |
| Réglages famille (prénom, hub, appairage, effacement) | **Vérifié** ; protégés par un code famille |

## Ce qui manque (par priorité)
1. **Vérifier sur un vrai téléphone** : la dictée, la réponse à WhatsApp, les MMS ([guide](real-phone-test.md)).
2. Contrôles de lecture (« Arrêter »), état conservé à la rotation ou à la mort du processus.
3. QR code d'appairage (le code famille est fait).
4. Retour sonore / vocal (« J'appelle Marie »), double appui, grandes polices à 200 %.
5. Appareil photo et galerie intégrés (pas d'application externe).
6. Réponse WhatsApp hors notification (impossible sans leur API).
7. Clé du Keystore pour le code famille (limite connue).

## Contraintes Google Play (voir ADR 0002)
- Pas de lecture de la boîte SMS (`READ_SMS`) ni d'envoi direct (`SEND_SMS`), réservés à l'application SMS par défaut. Les messages affichés viennent donc des **notifications**, et l'envoi passe par l'application Messages (texte pré-rempli) ou, plus tard, par l'action « répondre » de la notification.

## Décisions du 7 octobre 2026 (ADR 0005)
- Photos du téléphone : **jamais touchées** (caméra et galerie = applications du système).
- Historique des messages **illimité**, sur le téléphone seulement (remplace 30 messages / 7 jours).
- Application **SMS par défaut** (réception, envoi, MMS) : décidée, **pas implémentée** ; voir les risques dans l'ADR 0005.
- Apparence modifiable par **JSON** : livrée ([customization.md](customization.md)).

## SMS par défaut : avancement
| Étape | État |
|---|---|
| Historique illimité (SQLite) | **Livré** |
| Réception / envoi / import de l'historique, conversations | **Codé, testé** (unitaires + test de bout en bout sur émulateur) ; non vérifié sur un vrai téléphone |
| MMS : réception (texte + photos, accusé, réessai) | **Codé, testé** (décodeur sur MMS fabriqués selon la spécification, flux avec faux opérateur, test émulateur) ; **non vérifié avec un vrai opérateur** |
| MMS : envoi d'une photo (prendre / choisir, aperçu, réduction, réessai) | **Codé, testé** (PDU relu par le décodeur, vrais bitmaps sur émulateur) ; **non vérifié avec un vrai opérateur** |
| Déplacer le rôle SMS dans le manifeste de release | Après essai réel des MMS sur un téléphone |
| Formulaire Play « SMS » + vidéo | À faire après les MMS |

## WhatsApp
| Fonction | État |
|---|---|
| Lire les messages (notifications) | Vérifié par tests du décodeur ; pas de WhatsApp sur l'émulateur |
| Répondre (dictée → action « Répondre » de la notification) | **Codé, testé** (vraies notifications et `RemoteInput` simulés) ; **à vérifier avec WhatsApp sur un vrai téléphone** |
| Sans notification : ouvrir WhatsApp | Codé, testé |

## Plan
- **Phase 1 : photos des proches.** Choisir une photo pour un proche depuis « Réglages famille » (stockée sur le téléphone, sans permission supplémentaire).
- **Phase 2 : messages.** Historique local illimité (base SQLite), écran Messages, détail avec lecture à voix haute, pastille « nouveaux » sur l'accueil.
- **Phase 3 : répondre.** Dictée vocale puis envoi par l'application Messages ; WhatsApp via l'action de réponse de la notification.
- **Phase 4 : fiabilité.** Faux WhatsApp pour les tests d'émulateur, sons.
