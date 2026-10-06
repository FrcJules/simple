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

**Transport** : hub ↔ téléphone en HTTPS ; le certificat auto-signé du hub est généré une fois, son empreinte SHA-256 est affichée au démarrage du hub et saisie dans « Réglages famille » : le téléphone refuse tout autre certificat (voir [ADR 0003](adr/0003-tls-epingle.md)). Le HTTP en clair est désactivé sur le téléphone ; l'écran du Pi parle au hub en loopback.

**Limites connues (à traiter avant publication sur le Play Store)**
- Le jeton est dans des préférences privées non chiffrées (Keystore à prévoir).
- « Réglages famille » est accessible depuis la liste des applications : prévoir un code famille.
- Les codes à usage unique reçus par SMS sont transmis ; prévoir un filtre et une option « ne pas afficher le texte ».
- La saisie manuelle de l'empreinte est pénible : prévoir un QR code d'appairage affiché par le hub.
