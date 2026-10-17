# Publier Simple sur Google Play

Ce qui est **déjà prêt dans le dépôt** : fiche en français (`fastlane/metadata/android/fr-FR/`) et en anglais (`en-US/`, à copier dans « Traductions » de la fiche), icône et visuel de présentation, icône adaptative, `targetSdk 36`, workflow de release signé, politique de confidentialité ([docs/privacy-policy.md](../privacy-policy.md)), réponses aux formulaires ([data-safety.md](data-safety.md), [declarations.md](declarations.md)).

Ce qui **demande votre compte** (je n'ai aucun accès à la Play Console) : tout ce qui suit.

## 1. Compte et application (une fois)
1. Créer un compte développeur : https://play.google.com/console (25 USD, vérification d'identité).
2. *Créer une application* : nom « Simple : accueil senior », langue par défaut français, « Application », « Gratuite », cocher les déclarations.
3. Identifiant de l'application : `app.simple.launcher` (déjà dans le code ; il ne pourra plus changer après le premier envoi).

## 2. Clé de signature
1. Sur votre machine avec un JDK 17 : `./scripts/generate-upload-key.sh` (crée la clé d'**import** ; Google garde la vraie clé de signature avec *Play App Signing*).
2. Ajouter les 4 secrets GitHub indiqués par le script. **Sauvegardez le fichier `.jks` et le mot de passe** : sans eux, impossible de publier des mises à jour.

## 3. Premier envoi (manuel)
Google n'accepte pas qu'une application toute neuve reçoive son premier fichier par l'API.
1. `git tag v0.1.0 && git push origin v0.1.0` : le workflow *Release* produit le `.aab` signé (artefact de la release GitHub).
2. Play Console → *Tests* → *Test interne* → *Créer une release* → téléverser le `.aab`.

## 4. Vous ajouter comme testeur privé (test interne)
Le **test interne** convient pour vous : jusqu'à 100 testeurs, disponible en quelques minutes, sans revue Google.
1. Play Console → *Tests* → *Test interne* → onglet **Testeurs** → *Créer une liste d'e-mails* (ex. « Famille »).
2. Ajouter l'adresse Google avec laquelle vous êtes connecté sur le téléphone de test, enregistrer.
3. Copier le **lien d'accès** (« Copier le lien ») et l'ouvrir **sur le téléphone** connecté avec ce compte → *Devenir testeur* → installer depuis le Play Store.
4. Les futures versions arrivent en mise à jour normale.

## 5. Production : l'exigence des 12 testeurs
Pour un compte personnel créé après le 13 novembre 2023, Google exige avant la production un **test fermé avec au moins 12 testeurs inscrits pendant 14 jours consécutifs** (si le nombre repasse sous 12, le compteur repart à zéro). Règle confirmée par plusieurs sources début octobre 2026 ; vérifiez le chiffre exact dans la Play Console, qui peut évoluer. À prévoir :
- *Tests* → *Test fermé* → créer une release, ajouter 12 adresses e-mail (famille, amis), les inviter via le lien.
- Après 14 jours : *Tableau de bord* → *Demander l'accès à la production*.
- Un compte **organisation** n'a pas cette contrainte (il faut alors un numéro D-U-N-S).

## 6. Fiche et formulaires
- *Simple sur le Store* → *Fiche principale* : copier les textes de `fastlane/metadata/android/fr-FR/`, icône 512×512 et visuel 1024×500 du même dossier.
- **Captures d'écran téléphone (2 minimum) : à faire par vous** sur un vrai téléphone (accueil, liste de proches, « J'appelle… »). Format 16:9 ou 9:16, côté de 320 à 3840 px.
- *Politique de confidentialité* : il faut une **URL publique**. Le dépôt est privé : soit le passer en public puis activer GitHub Pages (ou utiliser l'URL `https://github.com/FrcJules/simple/blob/main/docs/privacy-policy.md`), soit héberger la page ailleurs.
- *Contenu de l'application* : suivre [declarations.md](declarations.md) et [data-safety.md](data-safety.md).

## 7. Mises à jour automatiques
Une fois le premier envoi fait, créer un compte de service Google Cloud lié à la Play Console (*Paramètres* → *Accès à l'API*), lui donner le droit de publier sur l'application, et enregistrer sa clé JSON dans le secret GitHub `PLAY_SERVICE_ACCOUNT_JSON`. Chaque tag `vX.Y.Z` enverra alors le `.aab` sur la piste **test interne** (brouillon). La promotion vers le test fermé ou la production reste manuelle, volontairement.

## Points d'attention propres à Simple
- **Niveau d'API** : depuis le 31 août 2026, les nouvelles applications doivent cibler Android 16 (API 36) : c'est fait. Source : [Google](https://support.google.com/googleplay/android-developer/answer/11926878).
- **Accès aux notifications** : règle propre à Google, à justifier dans la fiche et la politique de confidentialité (fait), voir [declarations.md](declarations.md).
- **Application d'accueil** : Simple se déclare « HOME » ; elle doit rester utilisable sans l'écran maison (c'est le cas).
