# Launcher Android : état et plan

*Mis à jour le 28 novembre 2026.*

Légende :
- **Vérifié** = essayé sur un vrai téléphone. **Rien ne l'est encore** (voir [real-phone-test.md](real-phone-test.md)).
- **Testé (émulateur)** = tests automatiques d'interface sur un émulateur Android, à chaque PR (CI).
- **Testé** = tests unitaires seulement (JVM / Robolectric).
- **Codé** = écrit, sans test automatique dédié.

## Versions publiées
- `v0.1.0` à `v0.1.5` (13 au 17 octobre 2026) : chaque tag produit un AAB signé, envoyé **en brouillon** sur la piste **test interne** de Google Play, et une GitHub Release.
- Ces versions sont des builds *release* : le rôle SMS et le mode SMS direct n'y étaient **pas** (réservés au build debug). Les messages y venaient des notifications.
- `v0.2.0` (28 novembre 2026) : Simple est proposé comme **application SMS par défaut** dans la version Google Play (SMS, MMS) ; le mode SMS direct reste réservé aux versions de test. **Plus d'écran maison ni d'appairage, plus aucun accès à Internet** (permission `INTERNET` retirée, vérifiée par la CI) ; les notifications de l'application Téléphone ne sont plus lues. AAB envoyé en brouillon sur la piste test interne.

## État actuel

| Fonction | État |
|---|---|
| Accueil : gros boutons, thème contrasté, icônes, pastille « nouveaux » | **Testé (émulateur)** |
| Accueil : heure et niveau de batterie ; l'horloge ne se réveille qu'une fois par minute et seulement écran visible | Testé |
| Rubriques de l'accueil modifiables par la famille, dont des tuiles d'applications (WhatsApp, vidéos…) | **Testé (émulateur)** |
| Apparence par JSON validé ([customization.md](customization.md)) | **Testé (émulateur)** |
| Appeler : proches choisis dans l'application (onglets Favoris / Général, recherche par lettres), confirmation, « J'appelle… » annulable, appel direct | **Testé (émulateur)** (appel simulé) |
| Photo d'un proche choisie par la famille (sélecteur Android, sans permission) | Testé |
| Sons, vibrations, « J'appelle Marie » dit à voix haute | Codé |
| État de l'écran conservé si Android arrête l'application | Codé |
| Messages : liste, détail, pastille, photo de l'expéditeur | **Testé (émulateur)** |
| Messages : codes à usage unique masqués jusqu'au toucher | **Testé (émulateur)** |
| Messages : lecture à voix haute | Codé ; son jamais entendu (émulateur) |
| Messages : répondre par la dictée | Chemins d'erreur **testés (émulateur)** ; **dictée réussie jamais essayée** (pas de reconnaissance vocale sur l'émulateur) |
| Appareil photo et galerie | Ouvrent les applications du système (ADR 0005) |
| Notifications SMS (quand Simple n'est pas l'application SMS) | Testé (décodeur) |
| Notifications WhatsApp | Testé (décodeur) ; pas de WhatsApp sur l'émulateur |
| Effacement des données d'appairage d'une version antérieure à la v0.2.0 | Testé |
| Réglages famille : accès caché (7 touches sur l'heure) + code famille, empreinte scellée par le Keystore | **Testé (émulateur)** |
| Réglages famille en sections ; guide « premiers pas » au premier lancement (dont le code famille) | **Testé (émulateur)** |
| Devenir l'écran d'accueil du téléphone (proposé au premier lancement et dans les réglages) | Codé |
| Diagnostic des messages dans les réglages (rôle SMS, accès aux notifications, aide « paramètres restreints ») | Codé |
| Anglais : toutes les chaînes traduites, langue choisie dans les réglages (Android 13+) | Codé ; l'émulateur teste les écrans en français |

## Ce qui manque (par priorité)
1. **Essai sur un vrai téléphone** ([#38](https://github.com/FrcJules/simple/issues/38), [guide](real-phone-test.md)) : vrais SMS et MMS, dictée, son, réponse WhatsApp ([#39](https://github.com/FrcJules/simple/issues/39)). Un premier essai a seulement montré qu'Android n'accorde pas le rôle SMS à une application installée à la main ([ADR 0007](adr/0007-sms-mode-direct.md)).
2. **Déclaration Play « SMS »** + vidéo ([#47](https://github.com/FrcJules/simple/issues/47)) : le rôle SMS est dans la version publiée depuis la v0.2.0 ([#48](https://github.com/FrcJules/simple/issues/48), fait).
3. **Google Play** : test fermé (12 testeurs, 14 jours, [#49](https://github.com/FrcJules/simple/issues/49)), puis production et mises à jour automatiques ([#60](https://github.com/FrcJules/simple/issues/60)).
4. **Grande police système ou petit écran** : toutes les actions encore atteignables (PR en cours).
5. Contrôle « Arrêter » pendant la lecture à voix haute.
6. Tests par captures d'écran (en cours).
7. Limite connue : répondre à WhatsApp sans sa notification est impossible (pas d'API).

## Contraintes Google Play (voir ADR 0002)
- `READ_SMS`, `SEND_SMS` et `RECEIVE_SMS` sont réservés à l'application SMS par défaut. Depuis la v0.2.0, la version publiée demande le **rôle** d'application SMS ; sans lui, les SMS affichés viennent des **notifications**. Le mode direct (permissions SMS sans le rôle, [ADR 0007](adr/0007-sms-mode-direct.md)) n'existe que dans les versions de test. WhatsApp passe toujours par les notifications, et la réponse par leur action « Répondre ».

## Décisions du 7 octobre 2026 (ADR 0005)
- Photos du téléphone : **jamais touchées** (caméra et galerie = applications du système).
- Historique des messages **illimité**, sur le téléphone seulement.
- Application **SMS par défaut** (réception, envoi, MMS) : codée ; dans la version Google Play depuis la v0.2.0 ; voir les risques dans l'ADR 0005.
- Apparence modifiable par **JSON** : livrée ([customization.md](customization.md)).

## SMS par défaut : avancement
| Étape | État |
|---|---|
| Historique illimité (SQLite) | **Testé** |
| Réception / envoi / import de l'historique, conversations | **Testé (émulateur)**, dont un test de bout en bout |
| Mode direct quand Android refuse le rôle : copie des SMS, envoi direct ([ADR 0007](adr/0007-sms-mode-direct.md)), versions de test seulement | Testé |
| MMS : réception (texte + photos, accusé, réessai) | **Testé (émulateur)** sur des MMS fabriqués selon la spécification ; **jamais essayé avec un vrai opérateur** |
| MMS : envoi d'une photo (prendre / choisir, aperçu, réduction, réessai) | **Testé (émulateur)** ; **jamais essayé avec un vrai opérateur** |
| Rôle SMS dans le manifeste de release | **Fait** (v0.2.0, [#48](https://github.com/FrcJules/simple/issues/48)) |
| Formulaire Play « SMS » + vidéo | À faire ([#47](https://github.com/FrcJules/simple/issues/47), [textes prêts](play-store/declarations.md)) |

## WhatsApp
| Fonction | État |
|---|---|
| Lire les messages (notifications) | Testé (décodeur) ; pas de WhatsApp sur l'émulateur |
| Répondre (dictée → action « Répondre » de la notification) | **Testé (émulateur)** avec des notifications simulées ; **à essayer avec WhatsApp sur un vrai téléphone** |
| Sans notification : ouvrir WhatsApp | **Testé (émulateur)** |
