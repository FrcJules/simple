---
description: Lance toute la vérification qualité (dans la CI, pas en local)
---
Ne compile rien sur cette machine (cela la fait planter). Pousse la branche courante, ouvre ou mets à jour sa PR, puis suis la CI : `gh pr checks <n> --watch`. Lis **tous** les checks (y compris CodeQL), pas seulement `ci-ok`. En cas d'échec, lis le journal (`gh run view <id> --log-failed`) et résume les erreurs avec fichier:ligne, puis propose des corrections. Pour l'UI, télécharge l'artefact `launcher-screenshots-jvm` (`gh run download <id> -n launcher-screenshots-jvm`) et regarde les captures. Ne corrige rien sans le dire.
