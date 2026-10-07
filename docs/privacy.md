# Vie privée

- Les photos, appels, messages et contacts sont traités **localement** (téléphone et Raspberry Pi).
- Aucune télémétrie, aucun traceur, aucun envoi vers un service tiers par défaut.
- Permissions Android demandées au strict nécessaire, expliquées en langage simple. Permissions actuelles : `READ_CONTACTS` (favoris uniquement, lecture seule, pour afficher les photos) et `CALL_PHONE` (appel après confirmation explicite ; repli sur le composeur si refusée). Pas de journal d’appels ni de SMS : lecture des notifications uniquement, liste blanche d’applications (ADR 0002).
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
- **Pas encore géré : les MMS** (messages avec image). Ne pas activer le rôle sur le téléphone utilisé au quotidien tant que ce n'est pas indiqué ici.
