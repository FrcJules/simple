# Vie privée

- Les photos, appels, messages et contacts sont traités **localement** (téléphone et Raspberry Pi).
- Aucune télémétrie, aucun traceur, aucun envoi vers un service tiers par défaut.
- Permissions Android demandées au strict nécessaire, expliquées en langage simple. Permissions actuelles : `READ_CONTACTS` (favoris uniquement, lecture seule, pour afficher les photos) et `CALL_PHONE` (appel après confirmation explicite ; repli sur le composeur si refusée). Journal d’appels : jamais lu. SMS : lus par les notifications, ou reçus directement si la famille fait de Présence l’application de messages (section « SMS » plus bas, versions de test uniquement pour l’instant) ; lecture des notifications, liste blanche d’applications (ADR 0002).
- Le hub n'accepte que les requêtes authentifiées par un jeton d'appairage.
- Le senior et sa famille peuvent supprimer toutes les données en effaçant l'application / le dossier de données du Pi.
- Toute fonction envoyant des données hors du foyer doit être optionnelle, désactivée par défaut et justifiée par un ADR.

## Statut d'implémentation
Lecture des notifications (ADR 0002) : **livrée en v0.2, à valider sur appareils réels**.
- Liste blanche (Téléphone, Messages, WhatsApp) vérifiée **avant** toute lecture du contenu ; seuls expéditeur, type, heure et texte (≤ 500 caractères) partent.
- Le client n'envoie qu'à une adresse privée dont la résolution réelle est vérifiée, sans suivre de redirection. Le hub exige un jeton d'appairage (écriture) et un jeton d'affichage distinct (lecture) ; CORS limité à l'origine de l'écran.
- Le jeton est exclu des sauvegardes et jamais réaffiché ni journalisé.

**Messages conservés sur le téléphone** ([ADR 0005](adr/0005-sms-par-defaut-et-historique-illimite.md)) : tout l'historique, **sans limite de durée ni de nombre**, dans une base privée exclue des sauvegardes ; effaçable depuis « Réglages famille » et supprimé à la désinstallation. Photos de proches choisies par la famille : stockage privé de l'application.

**Transport** : hub ↔ téléphone en HTTPS ; le certificat auto-signé du hub est généré une fois, son empreinte SHA-256 est affichée au démarrage du hub et saisie dans « Réglages famille » : le téléphone refuse tout autre certificat (voir [ADR 0003](adr/0003-tls-epingle.md)). Le HTTP en clair est désactivé sur le téléphone ; l'écran du Pi parle au hub en loopback.

**Limites connues (à traiter avant publication sur le Play Store)**
- Le jeton est dans des préférences privées non chiffrées (Keystore à prévoir).
- « Réglages famille » est accessible depuis la liste des applications : prévoir un code famille.
- Le filtre des codes à usage unique est volontairement large (faux positifs possibles) ; une option « ne pas afficher le texte » reste à faire.
- La dictée vocale passe par le service du système et peut envoyer l'audio chez son éditeur (ADR 0004).
- La saisie manuelle de l'empreinte est pénible : prévoir un QR code d'appairage affiché par le hub.

## SMS (si Présence devient l'application de messages)
Choix **facultatif** de la famille ([ADR 0005](adr/0005-sms-par-defaut-et-historique-illimite.md)). Présence reçoit alors vos SMS à la place de l'application d'origine (permissions `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, accordées par Android uniquement tant que Présence est l'application par défaut).
- Les SMS sont enregistrés dans la boîte de réception **du système** (comme toute application de messages) et copiés dans l'historique privé de Présence, **sur le téléphone seulement**, sans limite de durée, effaçable depuis « Réglages famille ».
- À l'activation, les SMS déjà présents sont copiés dans l'historique (marqués lus).
- Les codes à usage unique et alertes bancaires sont conservés mais **masqués** dans l'application, jamais lus à voix haute ni envoyés à l'écran maison.
- Seuls le nom de l'expéditeur et le texte (500 caractères au plus) des messages **ordinaires** sont envoyés à l'écran maison appairé, chiffrés, sur le réseau local.
- Aucune donnée ne quitte le téléphone ni le foyer. Si le rôle est retiré, l'historique reste, mais Présence ne reçoit plus de SMS.
- **MMS** : la réception est codée et testée mais **pas encore essayée avec un vrai opérateur** ; l'envoi d'une photo est limité à une photo, un destinataire, sans texte ni vidéo. Ne pas activer le rôle sur le téléphone utilisé au quotidien tant que ce n'est pas indiqué ici.

## MMS (photos reçues par SMS)
Si Présence est l'application de messages, les photos reçues par MMS sont téléchargées par Android (réseau mobile de l'opérateur) puis gardées dans des **fichiers privés de l'application**, jamais dans la galerie ni dans les photos du téléphone ; elles sont exclues des sauvegardes. « Effacer les messages » supprime l'historique, **toutes les photos, les dossiers et les fichiers de transfert** (suppression simple, sans écrasement des fichiers ; seule la base de données est écrasée et compactée). Un accusé de réception est envoyé à l'opérateur, comme par toute application de messages. À l'écran maison, jamais d'image : seulement le **texte du message (500 caractères au plus) ou le mot « Photo »**, pour une personne connue et jamais pour un code de sécurité. Seules les images sont gardées (JPEG, PNG, GIF, WebP, vérifiées : octets du fichier conformes au type annoncé, dimensions plafonnées à 50 millions de pixels) ; les autres pièces jointes sont ignorées. Les MMS ne sont pas copiés dans la base MMS d'Android.

**Risques connus des MMS**
- **Annonce forgée** : n'importe qui capable d'envoyer un message WAP-push peut annoncer un MMS avec une adresse de son choix, que le système téléchargerait sur le réseau MMS de l'opérateur (l'adresse IP de l'opérateur du téléphone serait visible du serveur visé, certains opérateurs ajoutent le numéro de ligne). Nous refusons les adresses sans rapport avec un serveur MMS (identifiants dans l'URL, `localhost`, adresses locales, IPv6) et plafonnons le nombre de téléchargements (20 en attente, 60 par heure), mais nous **ne pouvons pas** comparer l'adresse au serveur MMS de l'opérateur (lecture réservée au système) : limite, pas garantie. Les adresses privées restent permises, certains opérateurs y hébergent leur serveur.
- **Expéditeur usurpé** : comme pour les SMS, le numéro d'un MMS peut être falsifié. Pour un MMS, nous préférons l'expéditeur écrit dans le message téléchargé à celui de l'annonce.
- **Fournisseur de fichiers** : il est visible des autres applications car le système doit pouvoir y écrire ; seuls des noms aléatoires de 128 bits, valables quelques minutes, y sont servis. Compromis assumé : le restreindre risquerait de casser le téléchargement sur certains téléphones.

## Envoyer une photo (MMS)
La personne choisit **une** photo, explicitement, à chaque envoi : l'appareil photo du système, ou le sélecteur de photos du système (Présence ne voit que la photo choisie, jamais la photothèque, et ne demande aucune permission d'accès aux photos). Avant l'envoi, un écran montre la photo, le **visage et le nom du destinataire** (et son numéro avec un avertissement s'il n'est pas dans les contacts). La photo est réduite (≤ 1280 px, ≤ 280 Ko) et **ne contient plus ses métadonnées** (position GPS, appareil, date) : seuls les pixels sont envoyés, uniquement à l'opérateur, au destinataire choisi. Une copie réduite reste dans l'historique privé de l'application (effacée par « Effacer les messages »).

**Photo prise avec l'appareil photo** : l'appareil photo du système écrit la photo **d'origine** (pleine résolution, avec sa position GPS) dans un fichier temporaire privé de Présence, jamais dans la galerie. Ce fichier est **supprimé dès que la photo est envoyée, refusée (« Pas cette photo ») ou abandonnée**, et tout fichier temporaire de plus de 20 minutes est supprimé au démarrage ; entre-temps, ce fichier est servi par le fournisseur de fichiers exporté (voir plus haut) : l'application appareil photo qui l'a écrit pourrait le relire pendant ce court délai.

## Limites connues des messages
- La base de messages **n'est pas chiffrée** (elle est protégée par le stockage privé d'Android) : un téléphone « rooté » pourrait la lire. Chiffrement Keystore possible plus tard.
- Les codes de sécurité reçus par SMS sont **conservés en clair** dans cette base (masqués à l'écran, jamais lus à voix haute ni envoyés à l'écran maison).
- « Effacer les messages » supprime l'historique **de Présence** (avec écrasement et compactage de la base). La boîte SMS **du système** (celle d'Android, lue par les autres applications) n'est pas touchée, et elle survit à la désinstallation de Présence.
- À l'écran maison, seuls les SMS de **personnes connues** (présentes dans les contacts) sont affichés ; ceux d'un numéro inconnu ou d'une entreprise restent sur le téléphone.
- Le texte reçu est nettoyé des caractères de contrôle et d'inversion de sens (qui peuvent faire lire un texte autrement que ce qu'il dit).
- Un expéditeur de SMS peut se faire passer pour quelqu'un : un faux SMS « de Marie » (même numéro falsifié) affichera sa photo. C'est inhérent au SMS.
