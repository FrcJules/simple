# Publier une version

1. Mettre à jour `CHANGELOG.md` et `distribution/whatsnew/`, fusionner dans `main`.
2. `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. Le workflow **Release** démarre : l'archive Pi est construite tout de suite ; le job `android` (signature + envoi sur Google Play) **attend une validation** dans l'onglet *Actions* → *Review deployments* → environnement `release` → *Approve and deploy*.
4. L'AAB arrive en **brouillon** sur la piste *test interne* de la Play Console ; le déploiement est un clic manuel.

## Pourquoi cette validation
Le dépôt est public et le workflow utilise des actions et des plugins tiers. Pour limiter ce qu'un tag ou un composant compromis pourrait faire avec la clé de signature et le compte Play :
- le workflow n'a **aucune permission par défaut** ; seul le job `publish` peut écrire (création de la release GitHub) ;
- chaque secret n'est donné qu'à **l'étape** qui l'utilise ; le keystore est effacé après la construction ;
- toutes les actions sont **épinglées par SHA de commit** (Dependabot les met à jour, avec le numéro de version en commentaire) ;
- l'environnement `release` n'accepte que les tags `v*.*.*` et exige l'approbation d'un mainteneur.

## À faire une fois (mainteneur)
Pour que les secrets ne soient lisibles **que** par les jobs approuvés, les déplacer du dépôt vers l'environnement : *Settings* → *Environments* → `release` → *Add environment secret* pour `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, puis supprimer les versions de *Settings* → *Secrets and variables* → *Actions*. Les valeurs ne peuvent pas être relues : il faut les ressaisir (clé dans `~/simple-upload-key.jks`, encodée avec `base64 -w0`).
