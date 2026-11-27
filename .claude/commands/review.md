---
description: Lance les relecteurs adaptés au diff de la branche courante (argument facultatif = base, origin/main par défaut)
argument-hint: [base]
---
1. Base = `$ARGUMENTS` ou `origin/main`. Liste les fichiers modifiés (`git diff --name-only <base>...HEAD`).
2. Choisis les relecteurs selon CLAUDE.md (section « Agents et revue ») : toujours `code-reviewer` ; `senior-ux-reviewer` + `ui-designer-reviewer` si `ui/`, `res/`, `apps/display/src/*.tsx` ou `*.css` changent ; `privacy-reviewer` si photos, appels, messages, contacts, réseau, stockage, permissions ou manifeste changent ; `security-reviewer` si `apps/hub`, l'appairage, `security/`, le réseau, le manifeste, les permissions, `package*.json`, `*.gradle*` ou `.github/` changent.
3. Pour l'UI, fournis des captures : artefact CI `emulator-screenshots-and-reports-*` du dernier run (`gh run download`) pour le launcher, captures Playwright pour l'écran maison si disponibles. Sinon, préviens les relecteurs que la revue se fait sur le code seul.
4. Lance-les en parallèle (un seul message), en leur donnant le chemin du worktree, la base et la liste des fichiers.
5. Fusionne leurs retours en une seule liste Bloquant / Important / Souhaitable, sans doublon, avec `fichier:ligne`. Ne corrige rien sans me le dire.
