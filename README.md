# Simple

> Une couche d'accessibilité et de présence autour des outils que la famille utilise déjà,
> pour aider une personne âgée à rester connectée à ses proches et lutter contre la solitude.

[![CI](https://github.com/FrcJules/presence/actions/workflows/ci.yml/badge.svg)](https://github.com/FrcJules/presence/actions/workflows/ci.yml)
[![Licence](https://img.shields.io/badge/licence-Apache--2.0-blue.svg)](LICENSE)

## Pourquoi

Le projet est né d'un besoin réel : Jean-Pierre (prénom d'usage pour notre utilisateur type) vit seul,
lit peu, utilise peu la technologie et comprend beaucoup mieux les **images** que le texte.
Deux principes guident tout :

1. **La technologie s'adapte à Jean-Pierre**, pas l'inverse.
2. **La famille ne change aucune habitude** : WhatsApp, téléphone et SMS restent ses outils.

Simple ne crée pas une nouvelle messagerie : il **rend visibles les interactions qui existent déjà**.
Voir [docs/vision.md](docs/vision.md).

## Composants

| Composant | Dossier | Stack | État |
|---|---|---|---|
| Launcher Android ultra-simplifié | [`apps/launcher-android`](apps/launcher-android) | Kotlin, Jetpack Compose | squelette |
| Écran maison (Raspberry Pi) | [`apps/display`](apps/display) | TypeScript, React, Vite | squelette |
| Hub local (événements, intégrations) | [`apps/hub`](apps/hub) | Node.js, Fastify | squelette |
| Contrats partagés (événements) | [`packages/contracts`](packages/contracts) | TypeScript, Zod | squelette |

Architecture : [docs/architecture.md](docs/architecture.md) · Décisions : [docs/adr](docs/adr).

## Démarrer (développement)

```bash
npm install          # hub, display, contracts (workspaces)
npm run dev:hub      # http://localhost:8787
npm run dev:display  # http://localhost:5173
npm test             # tests de tous les workspaces
npm run lint
```

Launcher Android : ouvrir `apps/launcher-android` dans Android Studio (JDK 17).

## Vie privée

Les données de la famille (photos, appels, messages) sont **traitées localement** et ne quittent pas
le foyer par défaut. Voir [docs/privacy.md](docs/privacy.md).

## Contribuer

Lire [CONTRIBUTING.md](CONTRIBUTING.md) et [CLAUDE.md](CLAUDE.md) (conventions, utiles aussi aux humains).
Signalement de vulnérabilité : [SECURITY.md](SECURITY.md).

## Licence

[Apache-2.0](LICENSE)
