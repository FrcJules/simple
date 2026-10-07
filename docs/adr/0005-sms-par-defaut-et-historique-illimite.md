# 0005 — Application SMS par défaut, historique illimité, photos du téléphone intouchées

Statut : accepté (2026-10-07). **Implémenté en partie** : historique illimité et SMS (réception, envoi, import de l'historique, conversations) sont codés et testés ; les **MMS ne le sont pas encore**, donc le rôle n'est proposé que dans les versions de test (`SMS_ROLE_OFFERED`) et **ne doit pas être publié** avant les MMS. Remplace, pour les points ci-dessous, certaines décisions de l'[ADR 0004](0004-messages-conserves-sur-le-telephone.md).

## Décisions du propriétaire du projet
1. **Photos** : Présence ne lit ni ne gère la photothèque du téléphone (« trop personnel »). Les tuiles « Photo » et « Mes photos » ouvrent l'appareil photo et la galerie du téléphone, sans développement propre. Aucune permission d'accès aux photos. (Les photos de **proches** choisies par la famille dans « Réglages famille » via le sélecteur système restent possibles : elles n'ouvrent pas la photothèque.)
2. **Historique** : les messages sont conservés **indéfiniment, sur le téléphone seulement**. Cela remplace la limite « 30 messages / 7 jours » de l'ADR 0004.
3. **Envoi** : l'application devient l'**application SMS par défaut** (rôle `ROLE_SMS`) pour recevoir et envoyer des SMS. Cela remplace « pas d'envoi de SMS » (ADR 0004) et le rejet du rôle SMS (ADR 0002 / 0004). Option qui avait été déconseillée pendant la discussion, choisie en connaissance de cause.
4. **Interface modifiable** : thème et tuiles décrits par un fichier JSON validé ([customization.md](../customization.md)) — **livré**.
5. **WhatsApp** : toujours lu via les notifications ; la réponse passerait par l'action « Répondre » de la notification.

## Conséquences à traiter avant d'implémenter 2 et 3
- Un historique illimité exige un stockage fiable (base SQLite/Room, voire chiffrement Keystore) à la place du fichier actuel, une purge manuelle (« Réglages famille »), et la mise à jour de [privacy.md](../privacy.md), de la politique de confidentialité et de la section « Sécurité des données ».
- Les permissions `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` ne sont admises par Google Play que pour l'application par défaut, avec le **formulaire de déclaration des permissions SMS / Journal d'appels** (revue plus longue, refus possible).
- Le rôle impose de gérer toute la messagerie : réception, envoi, **MMS** (photos reçues par SMS), notifications de nouveaux messages, écriture dans la base système. Un MMS non géré est **perdu** : ne pas définir l'application par défaut sur le téléphone de Jean-Pierre tant que les MMS ne sont pas pris en charge ou que ce risque n'est pas accepté explicitement.
- L'application doit continuer à fonctionner (dégradation gracieuse) si le rôle est retiré.

## Alternative écartée par le propriétaire
Pré-remplir le message dans l'application Messages du téléphone (comportement actuel, ADR 0004), sans responsabilité sur le rôle SMS.

## Mise en œuvre (état du 7 octobre 2026)
- **Réception** : `SmsDeliverReceiver` → boîte de réception du système (obligatoire pour les autres applications), historique local, écran maison, notification. Les codes à usage unique et alertes bancaires sont **conservés mais masqués** (jamais lus à voix haute ni envoyés à l'écran maison).
- **Envoi** : `SmsSender` (SMS longs découpés, statut « envoi / envoyé / remis / non envoyé » suivi par pièce, échec visible).
- **Import** : à l'acquisition du rôle, les SMS existants du téléphone sont copiés dans l'historique (tous marqués lus).
- **Conversations** : regroupées par personne (`0612…` et `+33612…` ensemble) ; WhatsApp reste séparé (répondre changerait de canal).
- **Garde-fou** : le rôle (permissions SMS, récepteurs, service, filtre `sms:`) n'est déclaré que dans `src/debug/AndroidManifest.xml` ; le manifeste **release** n'en contient rien, donc Android ne peut pas proposer l'application comme application SMS tant que les MMS ne sont pas gérés (un MMS reçu serait perdu). À déplacer dans `src/main` avec les MMS.
- **Écran maison** : seuls les SMS de personnes présentes dans les contacts y sont envoyés. **Effacement** : écrasement et compactage de la base ; la boîte SMS du système n'est pas touchée.
- **Vérification** : tests unitaires (Robolectric, vrai PDU GSM) et test de bout en bout sur émulateur (SMS envoyé à soi-même, reçu et conservé).
