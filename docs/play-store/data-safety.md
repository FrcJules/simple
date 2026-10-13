# Section « Sécurité des données » (Play Console)

Réponses proposées. **À relire avec les définitions de Google avant de valider**, car vous engagez votre responsabilité.

**Principe** : Google appelle « collecte » le fait de transmettre des données *hors de l'appareil* vers le développeur ou un tiers. Simple ne transmet rien au développeur ni à un tiers : les données restent sur le téléphone, ou vont à l'écran maison *de l'utilisateur* sur son Wi-Fi (appareil qu'il possède).

| Question | Réponse |
|---|---|
| Votre application collecte-t-elle ou partage-t-elle des données utilisateur ? | **Non** |
| Les données sont-elles chiffrées en transit ? | Sans objet (aucune collecte). Le lien téléphone → écran maison est en HTTPS à certificat épinglé |
| Les utilisateurs peuvent-ils demander la suppression des données ? | Sans objet ; désinstaller l'application supprime ses données (historique de messages et photos de proches) ; la boîte SMS d'Android, si Simple était l'application de messages, reste dans Android |
| Déclaration de conformité (famille, etc.) | Application non destinée aux enfants |

**Dictée et lecture vocale** : elles passent par des services du système (reconnaissance et synthèse vocales de l'appareil, généralement Google). L'application n'envoie aucun audio elle-même et ne reçoit que le texte. Ces traitements sont déclenchés par l'utilisateur et relèvent de l'éditeur du service vocal ; à mentionner dans la fiche. À valider avec les définitions de Google avant d'affirmer « aucune donnée partagée ».

⚠️ Si un jour l'application envoie des données à un serveur (sauvegarde, analytics, planificateur, etc.), cette page doit être refaite : contacts, messages et appels deviennent alors des données « collectées ».

**Application SMS par défaut** : les SMS restent sur le téléphone (boîte du système + historique privé) ; rien n'est envoyé au développeur ni à un tiers. La réponse « aucune donnée collectée » reste exacte, mais la déclaration SMS de la Play Console (voir [declarations.md](declarations.md)) est obligatoire en plus.
