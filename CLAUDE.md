# CLAUDE.md — Simple

Application Android open source (Apache-2.0) pour personne âgée : écran d'accueil et application de messages (SMS/MMS, WhatsApp) très simples. Aucun accès à Internet. Lire `docs/vision.md` avant toute décision produit.

## Principes non négociables
1. **Le senior s'adapte à rien.** UI très visuelle (photos, icônes, gros boutons), peu de texte, aucune action destructive ou ambiguë, aucun jargon.
2. **La famille ne change pas ses habitudes.** Aucune fonctionnalité ne doit exiger que la famille installe une app ou change d'outil. On *observe* (appels, notifications) et on *affiche*, on ne remplace pas.
3. **Vie privée locale d'abord.** Photos/appels/messages restent sur le téléphone : l'application n'a **pas de permission Internet** (vérifié en CI). Pas de télémétrie, pas de cloud. Toute exception passe par un ADR.
4. **Langue** : UI et docs utilisateur en français (i18n prévue, chaînes jamais en dur dans la logique) ; code, identifiants et commits en anglais.

## Structure
- `apps/launcher-android` — Kotlin + Jetpack Compose : launcher HOME, contacts visuels, application SMS par défaut (SMS/MMS), WhatsApp par les notifications, dictée/TTS.
- `fastlane/metadata/android/` — fiche Google Play (textes, captures) ; `distribution/whatsnew/` — notes de version Play.
- `docs/` — vision, architecture, vie privée, Play Store, ADR (`docs/adr/NNNN-titre.md`).
- L'écran maison Raspberry Pi (hub, display) a été abandonné le 2026-11-28 : son code reste dans l'historique git.

## Commandes
- Android : `cd apps/launcher-android && gradle :app:lintDebug :app:lintRelease :app:testDebugUnitTest :app:assembleDebug`
- Captures d'écran (JVM, Roborazzi) : ajouter `-Proborazzi.test.record=true` ; images dans `app/build/outputs/roborazzi/`, variante `store` = format Play.
- Sur la machine de dev partagée, **ne pas compiler localement** (cela fait planter la VM) : pousser la branche, ouvrir la PR, et lire la CI (`gh pr checks`, `gh run download` pour les captures).

## Conventions
- Commits : Conventional Commits (`feat(launcher): …`, `fix(launcher): …`). Scopes : `launcher`, `docs`, `ci`.
- Branches : `feat/…`, `fix/…`; PR obligatoire vers `main`, CI verte requise.
- Kotlin : pas de `!!`, état UI via `StateFlow`.
- Accessibilité : cibles tactiles ≥ 64 dp, texte ≥ 24 sp, contraste WCAG AAA si possible, jamais d'info portée par la couleur seule.
- Pas de secret dans le dépôt ; `.env.example` documente les variables.
- Ne pas ajouter de dépendance sans justification (taille, licence compatible Apache-2.0, maintenance).

## Personnalisation
L'apparence de l'accueil (thème, tuiles) vient d'un JSON validé : `docs/customization.md`, `apps/launcher-android/app/src/main/java/app/simple/launcher/config/`. Toute nouvelle couleur ou action passe par ce format ; une couleur en dur dans l'interface est un défaut.

## Pièges connus
- Les tests instrumentés / UI ne tournent qu'en CI (job `android-emulator`, captures dans l'artefact `emulator-screenshots-and-reports-*`) : une machine sans KVM ne peut pas les lancer.
- Jamais de test Robolectric qui lance `SetupActivity` : il bloque.
- Après un changement de format stocké (SQLite, DataStore, JSON), chercher les anciennes hypothèses dans `androidTest`.
- Permissions SMS : utilisées **seulement avec le rôle d'application SMS par défaut** dans la version Play (politique Google) ; le « mode direct » est réservé aux versions de test (`SMS_DIRECT_MODE_OFFERED`, `SmsAccess.directModeAllowed`).
- L'émulateur de CI force la langue de l'app en `fr` (`cmd locale set-app-locales`) : les tests cherchent les textes français.
- Release : `versionCode` = `run_number` GitHub ; le workflow publie l'AAB sur la piste test interne de Play après validation de l'environnement `release` ; test fermé et production restent des promotions manuelles dans la Console.

## Agents (`.claude/agents`) et revue
- Dév : `android-dev` (launcher), `ci-release` (`.github/`, versions, publication Play).
- Revue (lecture seule) : `code-reviewer` (bugs, tests, règles), `senior-ux-reviewer` (accessibilité, compréhension par le senior), `ui-designer-reviewer` (cohérence visuelle, états), `privacy-reviewer` (données personnelles, minimisation), `security-reviewer` (surface d'attaque : composants Android, intents, SMS/MMS, dépendances, CI et publication).
- Avant chaque PR : `code-reviewer` ; + `senior-ux-reviewer` et `ui-designer-reviewer` si l'UI change (leur fournir les captures d'écran) ; + `privacy-reviewer` si photos, appels, messages, contacts, réseau ou stockage sont touchés ; + `security-reviewer` si les SMS/MMS, la sécurité, le manifeste, les permissions, le stockage, les dépendances ou `.github/` changent. Corriger, re-pousser, CI verte sur le dernier commit avant fusion.
- Donner aux relecteurs le chemin d'un `git worktree` de la branche ; ne pas changer de branche dans le dépôt principal pendant qu'ils lisent.

Commandes : `/check` (qualité complète), `/review` (relectures adaptées au diff), `/adr <titre>` (nouvel ADR).
