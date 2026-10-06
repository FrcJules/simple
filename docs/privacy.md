# Vie privée

- Les photos, appels, messages et contacts sont traités **localement** (téléphone et Raspberry Pi).
- Aucune télémétrie, aucun traceur, aucun envoi vers un service tiers par défaut.
- Permissions Android demandées au strict nécessaire, expliquées en langage simple. Permissions actuelles : `READ_CONTACTS` (favoris uniquement, lecture seule, pour afficher les photos) et `CALL_PHONE` (appel après confirmation explicite ; repli sur le composeur si refusée). Pas de journal d’appels ni de SMS : lecture des notifications uniquement, liste blanche d’applications (ADR 0002).
- Le hub n'accepte que les requêtes authentifiées par un jeton d'appairage.
- Le senior et sa famille peuvent supprimer toutes les données en effaçant l'application / le dossier de données du Pi.
- Toute fonction envoyant des données hors du foyer doit être optionnelle, désactivée par défaut et justifiée par un ADR.

## Statut d'implémentation
Lecture des notifications (ADR 0002) : **décidée, pas encore livrée**. Le client d'envoi vers le hub (et son jeton d'appairage) non plus : ils devront être revus par le `privacy-reviewer` avant fusion.
