# Essai sur un vrai téléphone (SMS et MMS)

Ce que l'émulateur ne peut pas vérifier : les **vrais SMS et MMS d'un opérateur**, la double SIM, la dictée, le son. Cet essai est la dernière étape avant de proposer le rôle SMS dans une version publiée (voir `src/debug/AndroidManifest.xml`).

> ⚠️ **Pas sur le téléphone de Jean-Pierre, ni sur un téléphone dont vous avez besoin au quotidien.** La version de test devient l'application SMS du téléphone : tant que les MMS n'ont pas été éprouvés, un message pourrait être perdu. Utilisez **votre** téléphone, avec une SIM, et un second téléphone (ou un proche) pour envoyer les messages.

## 1. Installer la version de test
1. Sur GitHub, onglet *Actions* → dernier run vert de la branche `main` → artefact **`launcher-debug-apk`** → télécharger et décompresser.
   En ligne de commande : `gh run download <id-du-run> -n launcher-debug-apk`.
2. Copier `app-debug.apk` sur le téléphone et l'ouvrir (autoriser « Installer des applications inconnues » pour l'application utilisée). Ou, avec le débogage USB : `adb install -r app-debug.apk`.
3. Appuyer sur Accueil : Android propose de choisir l'application d'accueil → **Présence**, « Toujours ».

## 1 bis. Appairer avec l'écran maison (si vous avez le Raspberry Pi)
Sur le Pi : `npm run pair -w @presence/hub` affiche un QR code. Avec l'appareil photo du téléphone, scannez-le : il s'ouvre dans Présence → **Réglages famille** → code famille → les champs sont remplis → **Enregistrer**. Vérifiez qu'un appel manqué apparaît ensuite sur l'écran maison.

## 2. Devenir l'application SMS
Sur l'accueil, toucher **Messages**. L'explication s'affiche ; **Continuer**, puis « Oui » à la question d'Android. Vos anciens SMS sont copiés dans Présence (ils restent aussi dans Android).

## 3. Les essais (cochez, notez ce qui ne va pas)
Avec un **autre téléphone** (ou un proche) :
- [ ] Il envoie un **SMS** : il apparaît dans Présence, avec la notification, le nom de la personne si elle est dans vos contacts.
- [ ] Vous **répondez** en dictant : le message part, passe à « Envoyé » puis « Reçu ».
- [ ] Il envoie une **photo par MMS** (application Messages, *pas* WhatsApp) : la bulle « Photo en cours de réception… » apparaît, puis la photo.
- [ ] Il envoie une photo **avec du texte**.
- [ ] Il envoie **plusieurs photos** d'un coup.
- [ ] Vous touchez la photo : elle s'agrandit, **Retour** revient.
- [ ] Mettez le téléphone en **mode avion**, faites envoyer une photo, remettez le réseau : la photo doit arriver ou apparaître « Cette photo n'est pas arrivée » avec **Recevoir à nouveau**.
- [ ] Vous **envoyez une photo** : *Envoyer une photo* → prendre / choisir → aperçu → *Envoyer* : elle arrive sur l'autre téléphone.
- [ ] La même photo envoyée **n'apparaît pas deux fois** chez vous (l'opérateur ne renvoie pas la notification).
- [ ] **WhatsApp** : un proche vous écrit sur WhatsApp (notification **non balayée**) : le message apparaît dans Présence ; vous **répondez** en dictant ; il reçoit la réponse sur WhatsApp. Balayez ensuite la notification : le bouton « Répondre » est remplacé par « Ouvrir WhatsApp ».
- [ ] Un **code de sécurité** reçu par SMS (banque, site) est masqué (« Message de sécurité ») et n'est pas lu à voix haute.
- [ ] Vous recevez un message d'un **numéro inconnu** : il s'affiche par son numéro, rien n'apparaît sur l'écran maison.

## 4. Revenir en arrière
Réglages Android → Applications → Applications par défaut → **Application SMS** → choisissez l'ancienne. Rien n'est perdu côté Android.

## 5. Ce qu'il faut me renvoyer
- Pour chaque case : OK ou ce qui s'est passé (capture d'écran si possible).
- Votre **opérateur** et le modèle du téléphone.
- Si un MMS n'arrive pas : l'heure exacte, et si l'application d'origine le recevait.

### Option : me laisser regarder les journaux en direct
Je ne peux pas accéder à votre téléphone par défaut. Si vous le souhaitez, vous pouvez activer *Options pour les développeurs → Débogage sans fil* et me donner l'adresse et le port affichés, **uniquement sur votre réseau Wi-Fi**, le temps de l'essai ; ADB donne un contrôle complet du téléphone : désactivez-le après. Ce n'est pas nécessaire : les journaux de l'application ne contiennent volontairement ni messages, ni numéros.
