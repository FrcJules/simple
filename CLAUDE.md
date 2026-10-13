# CLAUDE.md — Simple

Monorepo open source (Apache-2.0) d'un assistant numérique pour personne âgée. Lire `docs/vision.md` avant toute décision produit.

## Principes non négociables
1. **Le senior s'adapte à rien.** UI très visuelle (photos, icônes, gros boutons), peu de texte, aucune action destructive ou ambiguë, aucun jargon.
2. **La famille ne change pas ses habitudes.** Aucune fonctionnalité ne doit exiger que la famille installe une app ou change d'outil. On *observe* (appels, notifications) et on *affiche*, on ne remplace pas.
3. **Vie privée locale d'abord.** Photos/appels/messages restent dans le foyer. Pas de télémétrie, pas de cloud par défaut. Toute exception passe par un ADR.
4. **Langue** : UI et docs utilisateur en français (i18n prévue, chaînes jamais en dur dans la logique) ; code, identifiants et commits en anglais.

## Structure
- `apps/launcher-android` — Kotlin + Jetpack Compose (launcher HOME, contacts visuels, dictée/TTS).
- `apps/display` — UI plein écran Raspberry Pi (React + Vite), servie en kiosque Chromium.
- `apps/hub` — service Node/Fastify local : reçoit les événements du téléphone, les diffuse à l'écran (SSE).
- `packages/contracts` — schémas Zod des événements, source de vérité partagée hub/display.
- `docs/` — vision, architecture, vie privée, ADR (`docs/adr/NNNN-titre.md`).

## Commandes
- `npm install` · `npm run lint` · `npm run typecheck` · `npm test` · `npm run build` (workspaces racine)
- `npm run dev:hub` / `npm run dev:display`
- Android : `cd apps/launcher-android && gradle :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`

## Conventions
- Commits : Conventional Commits (`feat(display): …`, `fix(launcher): …`). Scopes : `launcher`, `display`, `hub`, `contracts`, `docs`, `ci`.
- Branches : `feat/…`, `fix/…`; PR obligatoire vers `main`, CI verte requise.
- Tout changement de format d'événement : modifier `packages/contracts` d'abord, tests inclus.
- TypeScript strict, pas de `any`. Kotlin : pas de `!!`, état UI via `StateFlow`.
- Accessibilité : cibles tactiles ≥ 64 dp, texte ≥ 24 sp, contraste WCAG AAA si possible, jamais d'info portée par la couleur seule.
- Pas de secret dans le dépôt ; `.env.example` documente les variables.
- Ne pas ajouter de dépendance sans justification (taille, licence compatible Apache-2.0, maintenance).

## Personnalisation
L'apparence de l'accueil (thème, tuiles) vient d'un JSON validé : `docs/customization.md`, `apps/launcher-android/app/src/main/java/app/simple/launcher/config/`. Toute nouvelle couleur ou action passe par ce format ; une couleur en dur dans l'interface est un défaut.

## Agents (`.claude/agents`)
`android-dev`, `display-dev`, `senior-ux-reviewer`, `privacy-reviewer`, `ci-release`. Les utiliser en revue : tout changement d'UI passe par `senior-ux-reviewer`, tout accès à des données personnelles par `privacy-reviewer`.
Commandes : `/check` (qualité complète), `/adr <titre>` (nouvel ADR).
