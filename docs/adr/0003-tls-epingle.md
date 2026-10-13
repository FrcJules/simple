# 0003 — TLS avec certificat auto-signé épinglé

Statut : accepté (2026-10-06)

## Contexte
Le téléphone envoie au hub un jeton et le texte de messages. En HTTP clair, sur un autre Wi-Fi qu'à la maison, une machine en `192.168.x.x` pourrait les recevoir ; une autorité de certification publique n'est pas utilisable pour une adresse IP privée.

## Décision
- Le hub génère à son premier démarrage un certificat auto-signé (ECDSA P-256, `openssl`), conservé dans `SIMPLE_TLS_DIR`.
- Il expose deux écouteurs sur la même application : HTTP sur `127.0.0.1` (écran du Pi) et HTTPS sur le réseau local (téléphone).
- Au démarrage il affiche l'empreinte SHA-256 du certificat ; la famille la saisit dans « Réglages famille ». Le téléphone n'accepte que ce certificat précis (épinglage) et désactive le HTTP en clair.

## Conséquences
+ Jeton et messages chiffrés ; un hub imposteur sur un autre réseau est refusé, même avec une adresse privée identique.
− Saisie manuelle de l'empreinte (QR code à prévoir) ; si le certificat est régénéré, il faut réappairer.
− Certificat valable 10 ans, pas de révocation : en cas de fuite de la clé, supprimer `SIMPLE_TLS_DIR` et réappairer.

## Alternatives
Autorité privée / mTLS (plus sûr, plus lourd à installer) ; Tailscale ou tunnel (dépendance externe, contraire à « local d'abord »).
