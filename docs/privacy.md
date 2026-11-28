# Vie privée

- **Aucun accès au réseau** : l'application n'a pas la permission `INTERNET` (la CI vérifie qu'aucune dépendance ne la rajoute). Les photos, appels, messages et contacts sont traités **sur le téléphone seulement**. Seuls passent par le réseau ce que l'utilisateur envoie lui-même par son opérateur (SMS, MMS), le téléchargement des MMS reçus par Android, et les services vocaux du système (voir plus bas).
- Aucune télémétrie, aucun traceur, aucun compte, aucun envoi au développeur ni à un tiers.
- Permissions Android demandées au strict nécessaire, expliquées en langage simple : `READ_CONTACTS` (lecture seule, pour afficher les proches et leurs photos), `CALL_PHONE` (appel après confirmation explicite ; repli sur le composeur si refusée), `VIBRATE` (permission ordinaire : courte vibration à l'envoi ou en cas d'erreur ; désactivable dans « Réglages famille » avec les sons et le « J'appelle… » dit à voix haute), `POST_NOTIFICATIONS` (nouveaux messages), et les permissions SMS/MMS, accordées par Android seulement si Simple devient l'application SMS par défaut (section « SMS » plus bas). Journal d'appels : jamais lu.
- Lecture des notifications (facultative, ADR 0002) : liste blanche **vérifiée avant toute lecture du contenu** (applications Messages et WhatsApp ; les notifications de l'application Téléphone ne sont plus lues depuis la v0.2.0). Seuls l'expéditeur, l'heure et le texte (500 caractères au plus) sont gardés, dans l'historique local.
- Le senior et sa famille peuvent supprimer toutes les données en effaçant les messages dans « Réglages famille » ou en désinstallant l'application.
- Toute fonction qui enverrait des données hors du téléphone devrait être optionnelle, désactivée par défaut et justifiée par un ADR ; il n'y en a aucune aujourd'hui.
- Depuis la v0.2.0, l'**écran maison** (Raspberry Pi) est abandonné : plus aucun envoi vers un hub, plus d'appairage. Les données d'appairage laissées par une version précédente (adresse, empreinte, jeton) sont effacées au premier démarrage.

**Messages conservés sur le téléphone** ([ADR 0005](adr/0005-sms-par-defaut-et-historique-illimite.md)) : tout l'historique, **sans limite de durée ni de nombre**, dans une base privée exclue des sauvegardes ; effaçable depuis « Réglages famille » et supprimé à la désinstallation. Photos de proches choisies par la famille : stockage privé de l'application.

**Limites connues**
- Le filtre des codes à usage unique est volontairement large (faux positifs possibles) ; une option « ne pas afficher le texte » reste à faire.
- La dictée vocale passe par le service du système et peut envoyer l'audio chez son éditeur (ADR 0004).

## SMS (si Simple devient l'application de messages)
Choix **facultatif** de la famille ([ADR 0005](adr/0005-sms-par-defaut-et-historique-illimite.md)), proposé dans la version Google Play depuis la v0.2.0. Simple reçoit alors vos SMS à la place de l'application d'origine (permissions `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, accordées par Android uniquement tant que Simple est l'application par défaut).
- Les SMS sont enregistrés dans la boîte de réception **du système** (comme toute application de messages) et copiés dans l'historique privé de Simple, **sur le téléphone seulement**, sans limite de durée, effaçable depuis « Réglages famille ».
- À l'activation, les SMS déjà présents sont copiés dans l'historique (marqués lus).
- Les codes à usage unique et alertes bancaires sont conservés mais **masqués** dans l'application, jamais lus à voix haute.
- Aucune donnée ne quitte le téléphone (hormis les messages envoyés par l'opérateur au destinataire choisi). Si le rôle est retiré, l'historique reste, mais Simple ne reçoit plus de SMS.
- **MMS** : la réception est codée et testée mais **pas encore essayée avec un vrai opérateur** ([guide d'essai](real-phone-test.md)) ; l'envoi d'une photo est limité à une photo, un destinataire, sans texte ni vidéo.

## MMS (photos reçues par SMS)
Si Simple est l'application de messages, les photos reçues par MMS sont téléchargées par Android (réseau mobile de l'opérateur) puis gardées dans des **fichiers privés de l'application**, jamais dans la galerie ni dans les photos du téléphone ; elles sont exclues des sauvegardes. « Effacer les messages » supprime l'historique, **toutes les photos, les dossiers et les fichiers de transfert** (suppression simple, sans écrasement des fichiers ; seule la base de données est écrasée et compactée). Un accusé de réception est envoyé à l'opérateur, comme par toute application de messages. Seules les images sont gardées (JPEG, PNG, GIF, WebP, vérifiées : octets du fichier conformes au type annoncé, dimensions plafonnées à 50 millions de pixels) ; les autres pièces jointes sont ignorées. Les MMS ne sont pas copiés dans la base MMS d'Android.

**Risques connus des MMS**
- **Annonce forgée** : n'importe qui capable d'envoyer un message WAP-push peut annoncer un MMS avec une adresse de son choix, que le système téléchargerait sur le réseau MMS de l'opérateur (l'adresse IP de l'opérateur du téléphone serait visible du serveur visé, certains opérateurs ajoutent le numéro de ligne). Nous refusons les adresses sans rapport avec un serveur MMS (identifiants dans l'URL, `localhost`, adresses locales, IPv6) et plafonnons le nombre de téléchargements (20 en attente, 60 par heure), mais nous **ne pouvons pas** comparer l'adresse au serveur MMS de l'opérateur (lecture réservée au système) : limite, pas garantie. Les adresses privées restent permises, certains opérateurs y hébergent leur serveur.
- **Expéditeur usurpé** : comme pour les SMS, le numéro d'un MMS peut être falsifié. Pour un MMS, nous préférons l'expéditeur écrit dans le message téléchargé à celui de l'annonce.
- **Fournisseur de fichiers** : il est visible des autres applications car le système doit pouvoir y écrire ; seuls des noms aléatoires de 128 bits, valables quelques minutes, y sont servis. Compromis assumé : le restreindre risquerait de casser le téléchargement sur certains téléphones.

## Envoyer une photo (MMS)
La personne choisit **une** photo, explicitement, à chaque envoi : l'appareil photo du système, ou le sélecteur de photos du système (Simple ne voit que la photo choisie, jamais la photothèque, et ne demande aucune permission d'accès aux photos). Avant l'envoi, un écran montre la photo, le **visage et le nom du destinataire** (et son numéro avec un avertissement s'il n'est pas dans les contacts). La photo est réduite (≤ 1280 px, ≤ 280 Ko) et **ne contient plus ses métadonnées** (position GPS, appareil, date) : seuls les pixels sont envoyés, uniquement à l'opérateur, au destinataire choisi. Une copie réduite reste dans l'historique privé de l'application (effacée par « Effacer les messages »).

**Photo prise avec l'appareil photo** : l'appareil photo du système écrit la photo **d'origine** (pleine résolution, avec sa position GPS) dans un fichier temporaire privé de Simple, jamais dans la galerie. Ce fichier est **supprimé dès que la photo est envoyée, refusée (« Pas cette photo ») ou abandonnée**, et tout fichier temporaire de plus de 20 minutes est supprimé au démarrage ; entre-temps, ce fichier est servi par le fournisseur de fichiers exporté (voir plus haut) : l'application appareil photo qui l'a écrit pourrait le relire pendant ce court délai.

## Répondre à WhatsApp
Le texte que la personne dicte est confirmé à l'écran (« Envoyer sur WhatsApp à Marie »), puis remis à WhatsApp **par la notification WhatsApp** (action « Répondre ») : Simple ne se connecte pas à WhatsApp, n'a pas de compte, ne lit rien d'autre que les notifications autorisées. L'action de réponse n'est conservée qu'en mémoire, tant que la notification existe. La réponse est gardée dans l'historique local avec le statut **« Remis à WhatsApp »** : WhatsApp ne dit pas à Simple ce qui se passe ensuite, donc un échec silencieux de WhatsApp n'est pas détectable.

Garde-fous contre une réponse envoyée à la mauvaise personne (la conversation n'est reconnue que par le **nom** dans la notification, ce qui n'est pas une identité) :
- **jamais pour un groupe** (seules les conversations à deux sont répondables) ;
- si **deux conversations différentes portent le même nom** (autre application WhatsApp, autre discussion), ce nom n'est plus répondable : on propose d'ouvrir WhatsApp ;
- la réponse part **uniquement par la notification qui était là au moment de la confirmation** : si un autre message est arrivé entre-temps, il faut recommencer.

## Limites connues des messages
- La base de messages **n'est pas chiffrée** (elle est protégée par le stockage privé d'Android) : un téléphone « rooté » pourrait la lire. Chiffrement Keystore possible plus tard.
- Les codes de sécurité reçus par SMS sont **conservés en clair** dans cette base (masqués à l'écran, jamais lus à voix haute).
- « Effacer les messages » supprime l'historique **de Simple** (avec écrasement et compactage de la base). La boîte SMS **du système** (celle d'Android, lue par les autres applications) n'est pas touchée, et elle survit à la désinstallation de Simple.
- Le texte reçu est nettoyé des caractères de contrôle et d'inversion de sens (qui peuvent faire lire un texte autrement que ce qu'il dit).
- Un expéditeur de SMS peut se faire passer pour quelqu'un : un faux SMS « de Marie » (même numéro falsifié) affichera sa photo. C'est inhérent au SMS.

## « Réglages famille » protégés
- Un **code famille** (4 à 8 chiffres, pas « 1111 » ni « 1234 ») est demandé à la première ouverture puis à chaque entrée dans les réglages ; ils se reverrouillent quand on les quitte. Le code n'est jamais stocké : seule son empreinte (PBKDF2, sel aléatoire) l'est. Après 5 erreurs, les réglages se bloquent 30 secondes, puis de plus en plus longtemps (15 minutes au plus), même si l'on ferme l'écran.
- **Pas de récupération** : si le code est perdu, il faut effacer les données de l'application dans les réglages d'Android (ce qui efface aussi l'historique des messages).
- Limite : le code protège l'**interface** des réglages. Quelqu'un qui contrôle déjà le téléphone (accès « root ») peut contourner l'application.
- Les réglages sont **exclus des captures d'écran et de la vue des applications récentes** (noms des proches).
- Le **blocage** du code utilise l'horloge interne du téléphone (que le changement de date ne modifie pas), et, après un redémarrage, la date plafonnée à 15 minutes : changer la date ne le contourne pas et ne peut pas bloquer la famille plus de 15 minutes.
- Le **premier** à ouvrir « Réglages famille » après l'installation choisit le code : l'écran le dit (« réservés à la famille ») et propose Retour.
- Limite : sur un téléphone « rooté », l'empreinte du code (4 à 8 chiffres) peut être testée hors de l'application en quelques secondes ; protéger davantage demanderait une clé du Keystore (à envisager).

### Code famille : ce qu'il protège et ses limites
- Le code n'est jamais stocké : seule une empreinte PBKDF2 l'est, et elle est elle-même chiffrée avec une clé du Keystore d'Android (qui ne quitte pas le téléphone) : une copie des fichiers de l'application ne permet pas d'essayer les codes hors du téléphone. Si le Keystore est indisponible, le code reste protégé par PBKDF2 seul (comme avant) et est chiffré dès la prochaine saisie correcte. Une fois le code chiffré, un code en clair remis dans les fichiers est refusé, et un enregistrement illisible n'est jamais effacé automatiquement. Limite : quelqu'un qui a les droits root sur le téléphone peut de toute façon effacer les données de l'application. Le scellement protège contre une copie hors ligne des fichiers, pas contre un téléphone rooté (qui peut déjà agir sous l'identité de l'application). Une panne passagère du Keystore ne compte pas comme une erreur de code. Si la clé du Keystore est perdue, il faut effacer les données de l'application (aucune réinitialisation automatique : on ne peut pas remettre le code à zéro en faisant échouer le Keystore).
- Le verrou anti-force-brute mesure l'attente avec l'horloge monotone du téléphone, jamais avec la date ; un redémarrage relance l'attente entière (au plus 15 minutes). Changer la date ou redémarrer ne l'abrège donc pas.
- Le geste caché de l'accueil n'ouvre jamais le choix du code : tant que la famille n'en a pas choisi, il affiche un écran rassurant. Le code se choisit depuis l'icône « Réglages ».
- Limites connues : l'activité Réglages doit rester ouverte à la famille (icône « Réglages »), donc une autre application du téléphone, ou quiconque ouvre « Réglages » avant la famille, peut choisir le code s'il n'existe pas encore. **Choisissez le code dès l'installation.** Un code à 4 chiffres résiste à une force brute sur le téléphone pendant des semaines, pas indéfiniment : préférez 6 à 8 chiffres.

### État conservé si Android ferme l'application
Si Android ferme Simple pendant que l'appareil photo ou le micro est devant (mémoire faible), l'application revient à la même conversation et au même brouillon de réponse. Seuls l'écran, la clé de la conversation, le numéro d'une nouvelle conversation et le texte du brouillon sont gardés par Android pour cela, en mémoire du système (jamais sur disque par Simple, jamais de photo, jamais d'appel en cours).

Le « J'appelle X » dit à voix haute et la lecture des messages utilisent le moteur de synthèse vocale du téléphone : selon le fabricant, ce moteur peut être en ligne (réglage Android « Sortie de synthèse vocale »). Choisissez un moteur hors ligne si vous ne voulez pas que le nom d'un proche soit envoyé à un service. Un brouillon de réponse WhatsApp retrouvé après une fermeture par Android n'est jamais envoyé tel quel : il faut le dicter de nouveau.

L'onglet « Général » et la recherche de « Qui voulez-vous appeler ? » lisent tous les contacts du téléphone (même permission `READ_CONTACTS`, en lecture seule), seulement sur l'écran, sans les copier ni les envoyer. Tant qu'aucun code famille n'existe, l'accueil propose, pendant 30 minutes seulement, un bouton « Pour la famille » qui permet de choisir le code : choisissez-le tout de suite à l'installation, avant de donner le téléphone. La liste complète des contacts n'est gardée en mémoire que pendant l'affichage de l'écran d'appel.

## SMS en mode direct (versions de test)
Si Android n'accorde pas à Simple le rôle d'application SMS, [ADR 0007](adr/0007-sms-mode-direct.md) : avec les permissions `READ_SMS` et `SEND_SMS` (demandées à ce moment ; versions de test seulement, jamais dans la version Google Play), Simple **copie** les SMS du téléphone dans son historique local (sans rien supprimer dans l'autre application), récupère les nouveaux à l'ouverture et à chaque notification de SMS, et envoie directement. Rien ne quitte le téléphone ; les codes à usage unique et alertes bancaires restent masqués. « Réglages famille › Effacer les messages » supprime la copie.

## Cases « application » de l'accueil
Pour proposer les applications du téléphone, le launcher déclare qu'il veut voir celles qui ont une icône de lancement (`<queries>` MAIN/LAUNCHER) : **pas** la permission « toutes les applications ». La liste n'est affichée que dans « Réglages famille », derrière le code ; seul le nom de l'application choisie (son identifiant Android) est mémorisé, sur le téléphone, dans le fichier d'apparence. Une case ne lance que l'application choisie, par son point d'entrée habituel.

## Messages « sensibles » (codes à usage unique)
Un message qui parle d'un code et contient un nombre (« votre code est 482913 », « mot de passe 1530 ») ou d'une opération bancaire avec un long numéro est masqué dans les listes (« Touchez pour voir »), et jamais lu à voix haute. Les mots sont reconnus **en tant que mots** (« lapin » n'est pas « pin »), et un numéro de téléphone, une année ou une heure seule ne suffisent pas. Un faux positif ne coûte qu'un toucher ; un oubli laisserait fuiter un secret : le détecteur reste volontairement prudent.

## Journal des messages (diagnostic)
Pour comprendre pourquoi un message n'arrive pas, Simple garde sur le téléphone **30 lignes au plus** décrivant ce qu'elle a fait (« Notification WhatsApp : message gardé », « ignorée : catégorie … », « Copie des SMS : 2 nouveau(x) »). Une ligne contient l'heure exacte, le nom de l'application (liste fermée) et le résultat (les catégories de notification sont limitées à la liste d'Android ; l'heure exacte d'arrivée d'un message est une donnée de vie quotidienne, d'où l'accès réservé aux réglages famille et l'effacement avec les messages) : **jamais un nom, un numéro ou un mot d'un message**. Il est visible dans « Réglages famille › Messages » et effacé avec « Effacer les messages ». En mode direct, un récepteur (`SMS_RECEIVED`, permission `RECEIVE_SMS`, versions de test) copie aussitôt les nouveaux SMS dans l'historique.

## Doublons de SMS
Un SMS envoyé par Simple en mode direct est aussi enregistré par le téléphone lui-même ; Simple reconnaît cette copie (même texte, même numéro, quelques minutes d'écart) et ne la rajoute pas. Une seule fois après la mise à jour, les doublons déjà présents de **vos propres envois** sont supprimés de l'historique de Simple, un pour un — jamais un message reçu, jamais une photo (MMS), jamais des SMS du téléphone.
