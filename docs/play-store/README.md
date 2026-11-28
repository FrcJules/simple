# Publier Simple sur Google Play

## Où on en est (28 novembre 2026)
- Compte développeur, application `app.simple.launcher`, clé d'import, compte de service : **faits**.
- Chaque tag `vX.Y.Z` construit l'AAB signé et l'envoie en **brouillon** sur la piste *test interne*, après votre validation dans GitHub (*Actions* → run *Release* → *Review deployments* → *Approve*). Voir [docs/release.md](../release.md).
- Politique de confidentialité publique : https://frcjules.github.io/simple/privacy-policy
- Fiche (fr, en), icône, visuel : `fastlane/metadata/android/`. Réponses aux formulaires : [declarations.md](declarations.md), [data-safety.md](data-safety.md).
- Depuis la v0.2.0 : plus d'écran maison, **pas d'accès à Internet**, Simple est proposé comme **application SMS par défaut** dans la version publiée.

Je n'ai pas accès à la Play Console : tout ce qui suit se fait avec votre compte.

## À faire dans la Play Console, dans l'ordre
1. **Publier le brouillon en test interne** : *Tests* → *Test interne* → la release en brouillon → *Vérifier la release* → *Lancer le déploiement*. L'installer sur votre téléphone (lien de test).
2. **Essai réel** (indispensable avant la suite, [real-phone-test.md](../real-phone-test.md)) : SMS reçu et envoyé, **MMS reçu et envoyé avec votre opérateur**, dictée, WhatsApp. Un MMS perdu serait grave : ne passez pas à l'étape 4 sans cela.
3. **Fiche principale** (*Croissance* → *Fiche du Store*) : copier titre, description courte et complète de `fastlane/metadata/android/fr-FR/` (et `en-US` dans *Traductions*), icône 512×512, visuel 1024×500, captures `phoneScreenshots/`.
4. **Contenu de l'application** (*Règles* → *Contenu de l'application*) :
   - Politique de confidentialité (URL ci-dessus), Publicité : non, Accès : aucune connexion + instructions de revue ([declarations.md](declarations.md)).
   - Classification du contenu (questionnaire IARC), Public cible : 18+.
   - Sécurité des données : [data-safety.md](data-safety.md).
   - **Déclaration SMS et journal d'appels** + **vidéo** : [declarations.md](declarations.md). La revue est plus longue et peut être refusée : comptez une à plusieurs semaines.
5. **Test fermé : 12 testeurs pendant 14 jours** (obligatoire pour un compte personnel récent avant la production) : *Tests* → *Test fermé* → créer une piste, une liste d'au moins 12 adresses Gmail (famille, amis), promouvoir la release interne vers cette piste, envoyer le lien d'inscription. Si le nombre de testeurs repasse sous 12, le compteur repart à zéro.
6. Après 14 jours : *Tableau de bord* → **Demander l'accès à la production** (questionnaire sur le test), puis promouvoir la release en production.

## Mises à jour
Tag `vX.Y.Z` → validation dans GitHub → brouillon en test interne → promotion manuelle (interne → fermé → production) dans la Console. `versionCode` = numéro du run GitHub, toujours croissant.
