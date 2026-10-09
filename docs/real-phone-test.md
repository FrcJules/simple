# Essai sur un vrai téléphone (SMS et MMS)

Ce que l'émulateur ne peut pas vérifier : les **vrais SMS et MMS d'un opérateur**, la double SIM, la dictée, le son. Cet essai est la dernière étape avant de proposer le rôle SMS dans une version publiée (voir `src/debug/AndroidManifest.xml`).

> ⚠️ **Pas sur le téléphone de Jean-Pierre, ni sur un téléphone dont vous avez besoin au quotidien.** La version de test devient l'application SMS du téléphone : tant que les MMS n'ont pas été éprouvés, un message pourrait être perdu. Utilisez **votre** téléphone, avec une SIM, et un second téléphone (ou un proche) pour envoyer les messages.

## 1. Installer la version de test
1. Sur GitHub, onglet *Actions* → dernier run vert de la branche `main` → artefact **`launcher-debug-apk`** → télécharger et décompresser.
   En ligne de commande : `gh run download <id-du-run> -n launcher-debug-apk`.
2. Copier `app-debug.apk` sur le téléphone et l'ouvrir (autoriser « Installer des applications inconnues » pour l'application utilisée). Ou, avec le débogage USB : `adb install -r app-debug.apk`.
3. Appuyer sur Accueil : Android propose de choisir l'application d'accueil → **Présence**, « Toujours ».

## 1 bis. Appairer avec l'écran maison (si vous avez le Raspberry Pi)
Sur le Pi : touchez 5 fois de suite l'heure sur l'écran maison (ou lancez `npm run pair -w @presence/hub` dans un terminal) : un QR code s'affiche. Le terminal affiche aussi l'**empreinte** du hub. Avec l'appareil photo du téléphone, scannez le QR code : il s'ouvre dans Présence → **Réglages famille** → code famille → une carte « Appairer avec cet écran maison ? » montre l'adresse et l'empreinte → **comparez l'empreinte avec celle du terminal** → **Appairer**. Le QR code ne marche qu'une fois et pendant 2 minutes. Vérifiez qu'un appel manqué apparaît ensuite sur l'écran maison.

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

> Premier lancement : tant que la famille n'a pas choisi de code, le geste caché (7 appuis sur l'heure) n'ouvre qu'un écran rassurant, jamais le choix du code. La famille ouvre « Réglages » depuis la liste des applications (si Présence est le lanceur : Réglages Android › Applications › Présence › Ouvrir) ou en scannant le QR code, puis choisit le code.

Installation du Pi : [docs/pi-install.md](pi-install.md).

## Ouvrir les réglages de la famille
- **Première installation** : tant qu'aucun code famille n'existe, un bouton « Pour la famille : choisir le code de Présence » apparaît en bas de l'accueil pendant **30 minutes** après le premier affichage. Il mène au choix du code (notez-le !). Il disparaît dès que le code existe. Passé ce délai, choisissez le code depuis l'icône « Réglages ».
- **Ensuite** : touchez 7 fois de suite l'heure sur l'accueil, ou ouvrez l'icône « Réglages » de Présence dans la liste des applications (si Présence est le lanceur : Réglages Android › Applications › Présence › Ouvrir). Les deux demandent le code.
- Dans les réglages : « Qui voulez-vous appeler ? » affiche deux onglets, **Favoris** (les personnes choisies ici) et **Général** (tous les contacts du téléphone), plus un bouton **Chercher** avec un gros clavier pour trouver une personne par les premières lettres de son prénom.
- « Écrire un message » : si Présence n'est pas encore l'application SMS par défaut, la question est reposée ; si elle n'est pas disponible dans cette version, un message simple s'affiche (jamais Google Messages).

## Messages en mode direct
Si Android ne propose pas de faire de Présence l'application SMS (cas fréquent d'une application installée à la main), touchez « Continuer » dans « Messages » : après la question du rôle, Présence demande les permissions **SMS** (autorisez-les) et **copie vos SMS existants** dans son historique ; les nouveaux sont copiés à chaque ouverture. « Écrire un message » envoie ensuite directement. Les SMS envoyés ainsi n'apparaissent pas dans l'autre application de messages. Si les permissions SMS sont grisées : voir l'aide « paramètres restreints » ci-dessous.

## Les messages ne marchent pas ?
Ouvrez « Réglages famille » : la section **Messages** dit en clair où ça bloque.
1. **« Application SMS du téléphone : une autre »** → touchez **Faire de Présence l'application SMS** et acceptez la question d'Android. Sans cela, Présence ne voit que les SMS annoncés par les notifications, et ne peut pas écrire elle-même.
2. **La question ne s'affiche pas, ou le bouton ne change rien** → si le texte « installée à la main » est présent, c'est le blocage d'Android 13+ pour les applications hors boutique : touchez **Ouvrir les infos de l'application**, puis ⋮ (en haut à droite) › **Autoriser les paramètres restreints**, puis revenez et réessayez. La même autorisation débloque « Lecture des notifications ».
3. **« Lecture des notifications : désactivée »** → touchez « Ouvrir l'accès aux notifications » plus bas, activez Présence (voir 2 si c'est grisé).
4. **Rien de tout cela** → notez le texte exact affiché dans cette section et le modèle du téléphone, et ouvrez une issue.
Une version « release » ne propose pas encore le rôle SMS (voir le commentaire de `src/debug/AndroidManifest.xml`) : utilisez l'APK debug de la CI pour ces essais.

## Écrire un message à quelqu'un
« Messages › Écrire un message » ouvre le même choix que pour appeler : **Favoris**, **Général** (tous les contacts du téléphone) et **Chercher** (clavier de lettres : le début du prénom suffit). « Reculer » et « Recommencer » restent à côté des lettres tapées.

