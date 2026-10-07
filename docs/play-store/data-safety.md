# Section « Sécurité des données » (Play Console)

Réponses proposées. **À relire avec les définitions de Google avant de valider**, car vous engagez votre responsabilité.

**Principe** : Google appelle « collecte » le fait de transmettre des données *hors de l'appareil* vers le développeur ou un tiers. Présence ne transmet rien au développeur ni à un tiers : les données restent sur le téléphone, ou vont à l'écran maison *de l'utilisateur* sur son Wi-Fi (appareil qu'il possède).

| Question | Réponse |
|---|---|
| Votre application collecte-t-elle ou partage-t-elle des données utilisateur ? | **Non** |
| Les données sont-elles chiffrées en transit ? | Sans objet (aucune collecte). Le lien téléphone → écran maison est en HTTPS à certificat épinglé |
| Les utilisateurs peuvent-ils demander la suppression des données ? | Sans objet ; désinstaller l'application supprime tout (messages récents et photos stockés localement inclus) |
| Déclaration de conformité (famille, etc.) | Application non destinée aux enfants |

⚠️ Si un jour l'application envoie des données à un serveur (sauvegarde, analytics, planificateur, etc.), cette page doit être refaite : contacts, messages et appels deviennent alors des données « collectées ».
