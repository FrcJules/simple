# 0006 — Météo de l'écran maison par Open-Meteo (désactivée par défaut)

Statut : proposé (2026-10-09) — exception au principe « pas de cloud par défaut », **désactivée tant que la famille ne la configure pas**. À confirmer par la personne qui installe.

## Contexte
L'écran maison doit montrer la météo du jour (vision : « Matin : date, anniversaire du jour, nouvelle photo, météo »). Il n'existe pas de source de météo entièrement locale : il faut interroger un service en ligne. Le principe 3 (vie privée locale d'abord) demande qu'une telle exception passe par un ADR et envoie le strict minimum.

## Décision
- Service : **Open-Meteo** (`api.open-meteo.com`), sans compte ni clé d'API, gratuit pour un usage non commercial, données ouvertes.
- **Désactivée par défaut.** Elle ne s'active que si la famille renseigne `PRESENCE_WEATHER_LAT` et `PRESENCE_WEATHER_LON` dans `/etc/presence/hub.env`.
- **Le Pi appelle le service, pas le téléphone ni le navigateur** : une seule requête HTTPS toutes les 30 minutes au plus (2 minutes après un échec), avec un délai d'attente de 5 secondes, sans suivre de redirection, réponse limitée à 64 Ko. Le navigateur de l'écran n'appelle que le hub local : le code de l'écran n'a pas d'autre adresse, et la page porte une politique de sécurité (CSP `connect-src`) qui interdit tout autre appel.
- Ce qui part : **les coordonnées arrondies à 0,1° (environ 11 km)** — celles d'une ville, pas d'une maison — et les paramètres de forme de la demande (`current`, `daily`, `forecast_days=1`, `timezone=auto`), plus ce que voit tout service : l'adresse IP du Pi, le nom du serveur (résolution DNS et SNI, visibles aussi du routeur de la maison) et l'agent HTTP par défaut de Node. Rien d'autre : ni nom, ni identifiant, ni cookie, ni contenu de l'écran.
- Ce qui revient : température, code météo, minimum et maximum du jour, validés (schéma) avant affichage. En cas d'échec la météo disparaît, l'écran continue.
- Rien n'est journalisé ni conservé sur disque.

## Conséquences
+ La météo du jour sur l'écran, sans compte, sans clé, sans application côté famille.
+ Une seule exception réseau sortante, identifiable, désactivable (vider les deux variables).
− Open-Meteo apprend que « une machine à cette adresse IP s'intéresse à la météo de cette zone de 11 km » (comme un site météo). Les conditions d'usage du service s'appliquent (usage non commercial ; à revérifier avant toute distribution commerciale).
− Les données d'Open-Meteo sont sous licence **CC BY 4.0** : l'écran affiche « Open-Meteo.com » sous la météo (attribution exigée).
− Dépendance à un service tiers : s'il disparaît, la météo disparaît sans autre effet.

## Alternatives
- Station météo locale / capteurs : ne donne pas de prévisions.
- Météo-France, OpenWeatherMap : compte et clé d'API à gérer, donc une donnée personnelle de plus.
- Appel depuis le navigateur de l'écran : expose le navigateur à Internet et rend le kiosque moins sûr.
- Appel depuis le téléphone : le téléphone n'est pas toujours là, et sa position serait en jeu.
