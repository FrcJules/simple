# Contribuer

Merci ! Avant de commencer, lisez [docs/vision.md](docs/vision.md) : chaque contribution doit respecter les deux principes (la techno s'adapte au senior ; la famille ne change pas ses habitudes).

1. Ouvrez une issue pour discuter des changements importants.
2. Branche `feat/…` ou `fix/…`, commits en [Conventional Commits](https://www.conventionalcommits.org/).
3. `npm run lint && npm run typecheck && npm test` doivent passer (et les tests Android si vous les touchez).
4. Toute modification d'UI : captures d'écran dans la PR et respect des règles d'accessibilité de [CLAUDE.md](CLAUDE.md).
5. En contribuant, vous acceptez la licence Apache-2.0 (DCO : signez avec `git commit -s`).

## Vitesse de la CI
La CI dure environ 6 à 10 minutes. Le job le plus long est `android-emulator` (démarrage de l'émulateur, compilation, puis les tests d'interface) ; il est coupé en **deux émulateurs en parallèle** (`matrix.shard`), chacun jouant la moitié des tests (`scripts/ci-emulator-tests.sh`, variables `SHARD` / `NUM_SHARDS`). Les captures d'écran de chaque moitié sont dans les artefacts `emulator-screenshots-and-reports-0` et `-1`. En local, `gradle :app:lintDebug :app:testDebugUnitTest :app:assembleDebug` (environ 1 min 30) évite la plupart des échecs avant de pousser ; seuls les tests d'interface exigent un émulateur (KVM) ou un téléphone de test.
