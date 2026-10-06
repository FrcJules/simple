# 0001 — Monorepo et choix de stack

Statut : accepté

## Contexte
Deux produits (launcher Android, écran Raspberry Pi) partageant des formats d'événements, développés par une petite équipe, open source, avec un objectif de publication sur le Play Store.

## Décision
- Monorepo unique.
- Android natif : Kotlin + Jetpack Compose (accès fiable à HOME, appels, notifications, TTS/STT, exigé par un launcher).
- Pi : hub Node/Fastify + UI React/Vite en kiosque Chromium ; contrats partagés en TypeScript/Zod.
- Licence Apache-2.0 (brevets, compatible Play Store et contributions externes).

## Conséquences
+ Un seul dépôt, CI filtrée par chemin, contrats partagés côté Pi.
− Le contrat n'est pas partagé avec Kotlin : un schéma JSON généré depuis Zod servira de référence (à faire).

## Alternatives
Flutter/React Native (rejeté : launcher et API système natives) ; Python sur le Pi (rejeté : pas de partage de types avec l'UI).
