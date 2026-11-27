# Tests

| Où | Outil | Lancer | Où tourne |
|---|---|---|---|
| `packages/contracts`, `apps/hub`, `apps/display` | Vitest | `npm test` | local + CI |
| Launcher : logique, SQLite, SMS/MMS | JUnit + Robolectric | `gradle :app:testDebugUnitTest` | local + CI |
| Launcher : **captures d'écran** | Roborazzi (Robolectric, sans émulateur) | `gradle :app:recordRoborazziDebug --tests '*ScreenshotsTest*'` | local + CI |
| Launcher : parcours réels, rôles SMS/accueil | Espresso / Compose UI sur émulateur | `scripts/ci-emulator-tests.sh` | CI seulement (KVM) |

## Captures d'écran du launcher
`app/src/test/java/app/simple/launcher/ui/ScreenshotsTest.kt` dessine chaque écran du senior en trois variantes : `phone` (411×891 dp), `small` (320×568 dp) et `font200` (police système à 200 %), en français.

- Images : `apps/launcher-android/app/build/outputs/roborazzi/<écran>_<variante>.png`.
- En CI : artefact **`launcher-screenshots-jvm`** du job `android` (et `emulator-screenshots-and-reports-*` pour l'émulateur).
- Sans la tâche `record`, les écrans sont quand même composés : un écran qui plante fait échouer les tests.
- Ajouter un écran : une ligne `shoot("NN_nom") { MonEcran(...) }` avec des données factices (jamais de vraies données personnelles).
- Ces images servent aux relecteurs `senior-ux-reviewer` et `ui-designer-reviewer` (voir `/review`).

Pas encore d'images de référence versionnées : la comparaison automatique (`verifyRoborazziDebug`) viendra quand le rendu sera stable.
