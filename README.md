# Simple

> Une couche d'accessibilité et de présence autour des outils que la famille utilise déjà,
> pour aider une personne âgée à rester connectée à ses proches et lutter contre la solitude.

[![CI](https://github.com/FrcJules/simple/actions/workflows/ci.yml/badge.svg)](https://github.com/FrcJules/simple/actions/workflows/ci.yml)
[![Licence](https://img.shields.io/badge/licence-Apache--2.0-blue.svg)](LICENSE)

## Pourquoi

Le projet est né d'un besoin réel : Jean-Pierre (prénom d'usage pour notre utilisateur type) vit seul,
lit peu, utilise peu la technologie et comprend beaucoup mieux les **images** que le texte.
Deux principes guident tout :

1. **La technologie s'adapte à Jean-Pierre**, pas l'inverse.
2. **La famille ne change aucune habitude** : WhatsApp, téléphone et SMS restent ses outils.

Simple ne crée pas une nouvelle messagerie : il **rend visibles les interactions qui existent déjà**.
Voir [docs/vision.md](docs/vision.md).

## L'application

Simple est une **application Android** ([`apps/launcher-android`](apps/launcher-android), Kotlin, Jetpack Compose) :

- un **écran d'accueil** ultra-simplifié : gros boutons, proches en photos, appel après confirmation, appareil photo et galerie du téléphone ;
- une **application de messages** (SMS et MMS) que la famille peut choisir comme application SMS par défaut, avec lecture à voix haute et réponse dictée ;
- les **messages WhatsApp** lus dans leurs notifications, avec réponse par la notification ;
- des **réglages famille** protégés par un code, et une apparence modifiable ([docs/customization.md](docs/customization.md)).

État : v0.2.0, AAB publiés sur la piste test interne de Google Play ; pas encore essayé sur un vrai téléphone ([état et plan](docs/roadmap-launcher.md)).

L'écran maison (Raspberry Pi) prévu au départ a été abandonné le 28 novembre 2026 ([vision](docs/vision.md#abandonné)).

Architecture : [docs/architecture.md](docs/architecture.md) · Tests : [docs/testing.md](docs/testing.md) · Décisions : [docs/adr](docs/adr).

## Démarrer (développement)

Ouvrir `apps/launcher-android` dans Android Studio (JDK 17), ou en ligne de commande :

```bash
cd apps/launcher-android
gradle :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Les tests d'interface sur émulateur tournent dans la CI ([docs/testing.md](docs/testing.md)).

## Vie privée

L'application n'a **aucun accès à Internet** : photos, appels, messages et contacts **restent sur le téléphone**.
Aucune télémétrie, aucun compte. Voir [docs/privacy.md](docs/privacy.md) et la
[politique de confidentialité](docs/privacy-policy.md).

## Contribuer

Lire [CONTRIBUTING.md](CONTRIBUTING.md) et [CLAUDE.md](CLAUDE.md) (conventions, utiles aussi aux humains).
Signalement de vulnérabilité : [SECURITY.md](SECURITY.md).

## Licence

[Apache-2.0](LICENSE)
