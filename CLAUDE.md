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

## Pièges connus
- Les tests instrumentés / UI ne tournent qu'en CI (job `android-emulator`, captures dans l'artefact `emulator-screenshots-and-reports-*`) : une machine sans KVM ne peut pas les lancer.
- Jamais de test Robolectric qui lance `SetupActivity` : il bloque.
- Après un changement de format stocké (SQLite, DataStore, JSON), chercher les anciennes hypothèses dans `androidTest`.
- Deux PR qui touchent `apps/display/src/App.tsx` entrent en conflit : fusionner `main` dans la seconde.
- L'émulateur de CI force la langue de l'app en `fr` (`cmd locale set-app-locales`) : les tests cherchent les textes français.
- Release : `versionCode` = `run_number` GitHub ; le workflow envoie l'AAB en test interne Play à l'état brouillon (déploiement = clic manuel).

## Agents (`.claude/agents`) et revue
- Dév : `android-dev` (launcher), `display-dev` (display, hub, contracts), `ci-release` (`.github/`, versions).
- Revue (lecture seule) : `code-reviewer` (bugs, tests, règles), `senior-ux-reviewer` (accessibilité, compréhension par le senior), `ui-designer-reviewer` (cohérence visuelle, états), `privacy-reviewer` (données personnelles, minimisation), `security-reviewer` (surface d'attaque : hub, appairage, TLS, composants Android, dépendances, CI).
- Avant chaque PR : `code-reviewer` ; + `senior-ux-reviewer` et `ui-designer-reviewer` si l'UI change (leur fournir les captures d'écran) ; + `privacy-reviewer` si photos, appels, messages, contacts, réseau ou stockage sont touchés ; + `security-reviewer` si le hub, l'appairage, le réseau, le manifeste, les permissions, le stockage, les dépendances ou `.github/` changent. Corriger, re-pousser, CI verte sur le dernier commit avant fusion.
- Donner aux relecteurs le chemin d'un `git worktree` de la branche ; ne pas changer de branche dans le dépôt principal pendant qu'ils lisent.

Commandes : `/check` (qualité complète), `/review` (relectures adaptées au diff), `/adr <titre>` (nouvel ADR).
