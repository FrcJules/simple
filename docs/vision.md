# Vision

## Origine
Jean-Pierre passe beaucoup de temps chez lui, utilise peu la technologie, n'emporte pas toujours son téléphone et lit peu : il comprend bien mieux les photos et les interfaces visuelles. Besoins : appeler, recevoir/consulter des messages, prendre et regarder des photos, communiquer simplement, dicter plutôt que taper.

## Objectif
**Réduire la solitude** en donnant de la *présence* à la famille dans le quotidien, sans remplacer les interactions humaines.

## Principes
1. La technologie s'adapte à Jean-Pierre.
2. La famille ne change pas ses habitudes (téléphone, WhatsApp, SMS). Présence fonctionne *autour* de ces outils et rend visibles les interactions existantes.

## Deux briques
- **Launcher Android** : écran d'accueil radicalement simplifié (Appeler, Messages, Photos, Appareil photo), contacts représentés par leur photo, dictée vocale et lecture à voix haute (TTS).
- **Écran maison (Raspberry Pi)** : heure, date, photos de famille plein écran, anniversaires, appels manqués (« Marie vous a appelé » + Rappeler), météo, rappels, résultats du Loto, musique, télécommande TV simplifiée (Pi → Zigbee → contrôleur IR → TV).

## Journée type
Matin : date, anniversaire du jour, nouvelle photo, météo. Journée : « 📞 Marie vous a appelé », rappel possible. Plus tard : nouvelle photo en grand. Soir : Télévision, Musique, Photos, Demain.

## Hors périmètre
Créer une nouvelle messagerie ; exiger une application côté famille.

## Feuille de route
- **v0.1** — Launcher : accueil + contacts visuels + appel/photo/galerie. Écran : horloge, date, carte « appel manqué » via hub.
- **v0.2** — Détection d'appels manqués côté téléphone → hub ; diaporama de photos ; anniversaires.
- **v0.3** — Dictée / TTS ; météo ; rappels.
- **v0.4** — Musique ; télécommande TV (Zigbee/IR) ; lecture des notifications WhatsApp (à étudier, voir questions ouvertes).

## Questions ouvertes
- Fragilité du parsing des notifications selon les applications et constructeurs ([ADR 0002](adr/0002-notifications-plutot-que-journal-d-appels.md)) : prévoir des tests par application.
- Photos de famille : comment les récupérer sans changer les habitudes (notification WhatsApp avec image, album partagé…) ? À trancher par ADR.
