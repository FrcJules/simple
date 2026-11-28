# Vision

## Origine
Jean-Pierre passe beaucoup de temps chez lui, utilise peu la technologie, n'emporte pas toujours son téléphone et lit peu : il comprend bien mieux les photos et les interfaces visuelles. Besoins : appeler, recevoir/consulter des messages, prendre et regarder des photos, communiquer simplement, dicter plutôt que taper.

## Objectif
**Réduire la solitude** en donnant de la *présence* à la famille dans le quotidien, sans remplacer les interactions humaines.

## Principes
1. La technologie s'adapte à Jean-Pierre.
2. La famille ne change pas ses habitudes (téléphone, WhatsApp, SMS). Simple fonctionne *autour* de ces outils et rend visibles les interactions existantes.
3. Tout reste sur le téléphone : l'application n'a pas d'accès à Internet ([privacy.md](privacy.md)).

## Le produit : l'application Android
- **Écran d'accueil** radicalement simplifié (Appeler, Messages, Photos, Appareil photo, cases d'applications choisies par la famille), contacts représentés par leur photo.
- **Messages** : Simple peut devenir l'application SMS du téléphone (SMS et MMS) ; les messages WhatsApp sont lus dans leurs notifications et on peut y répondre. Tout l'historique reste sur le téléphone.
- **Voix** : dictée plutôt que clavier, lecture à voix haute (TTS).
- **Réglages famille** protégés par un code, apparence modifiable ([customization.md](customization.md)).

## Journée type
Matin : l'accueil affiche l'heure ; une pastille signale un nouveau message. Dans Messages, Jean-Pierre reconnaît la photo de Marie, écoute son message et répond en dictant. Plus tard : il appelle son fils en touchant sa photo, après une confirmation. Une photo reçue par MMS s'ouvre en grand.

## Hors périmètre
Créer une nouvelle messagerie ; exiger une application côté famille.

## Feuille de route
*État au 28 novembre 2026 : rien n'est encore essayé sur un vrai téléphone ([détail et priorités](roadmap-launcher.md)).*

- **v0.1** (octobre 2026) — Accueil, contacts visuels, appel avec confirmation, appareil photo et galerie du système, messages lus dans les notifications, dictée / TTS, réglages famille, apparence par JSON. AAB envoyés en brouillon sur la piste test interne de Google Play.
- **v0.2.0** (28 novembre 2026) — Simple est proposé comme **application SMS par défaut** dans la version Google Play (SMS, MMS) ; plus aucun accès à Internet ; réponse WhatsApp par la notification.
- **Ensuite** — essai sur un vrai téléphone, test fermé puis production sur Google Play.

## Abandonné
**Écran maison (Raspberry Pi)** — abandonné le **2026-11-28** par le propriétaire du produit. Il devait afficher dans le salon l'heure, les photos de famille, les appels manqués, les anniversaires, les rappels et la météo, alimenté par un hub local qui recevait les événements du téléphone. Le code (`apps/hub`, `apps/display`, `packages/contracts`) a été retiré du dépôt et l'application Android ne communique plus avec lui. Les ADR qui le concernaient sont marqués caducs ([adr/](adr/)). Idées liées non reprises : musique, télécommande TV, résultats du Loto.

## Questions ouvertes
- Fragilité du parsing des notifications (WhatsApp, applications de messages) selon les applications et constructeurs ([ADR 0002](adr/0002-notifications-plutot-que-journal-d-appels.md)) : prévoir des tests par application.
- Photos de famille : comment les montrer à Jean-Pierre sans changer les habitudes de la famille (MMS, notification WhatsApp avec image…) ? À trancher par ADR.
