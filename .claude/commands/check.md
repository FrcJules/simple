---
description: Lance toute la vérification qualité du monorepo
---
Exécute à la racine `npm run lint && npm run typecheck && npm test`, puis `~/.local/bin/build npm run build` (verrou de build de la machine, s'il existe ; sinon `npm run build`). Ensuite, si la chaîne Android est disponible (`. ~/tools/env.sh` sur la machine de dev), dans `apps/launcher-android` : `gradle :app:lintDebug :app:testDebugUnitTest :app:assembleDebug` (via `~/.local/bin/build` s'il existe). Les tests instrumentés (émulateur) ne tournent qu'en CI. Résume les échecs avec fichier:ligne et propose des corrections. Ne corrige rien sans le dire.
