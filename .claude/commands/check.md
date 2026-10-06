---
description: Lance toute la vérification qualité du monorepo
---
Exécute `npm run lint && npm run typecheck && npm test && npm run build` à la racine, puis, si `gradle` est disponible, les tâches Android de CLAUDE.md. Résume les échecs avec fichier:ligne et propose des corrections. Ne corrige rien sans le dire.
