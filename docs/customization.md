# Personnaliser l'interface (JSON)

L'apparence de l'écran d'accueil du launcher est décrite par un fichier JSON : couleurs, taille du texte, liste et ordre des tuiles, libellés, icônes. Aucun code à modifier.

- **Défaut livré** : [`apps/launcher-android/app/src/main/assets/ui-config.default.json`](../apps/launcher-android/app/src/main/assets/ui-config.default.json).
- **Sur un téléphone** : « Réglages famille » → *Apparence (avancé)* → *Importer un fichier d'apparence (JSON)*. Le fichier est vérifié ; s'il est refusé, la raison est affichée et rien ne change. *Revenir à l'apparence par défaut* annule l'import.
- **Pour un développeur** : modifiez le fichier par défaut dans le dépôt. Un schéma JSON pour votre éditeur est fourni : [`ui-config.schema.json`](ui-config.schema.json) (`"$schema"` accepté mais facultatif).

## Format (version 1)
```json
{
  "schemaVersion": 1,
  "theme": {
    "background": "#FFFFFF",
    "text": "#000000",
    "primary": "#0B3D91",
    "onPrimary": "#FFFFFF",
    "tile": "#E3E8F2",
    "accent": "#DCE6FF",
    "fontScale": 1.0
  },
  "home": {
    "columns": 2,
    "tiles": [
      { "id": "call", "action": "call", "icon": "call" },
      { "id": "messages", "action": "messages", "label": "Courrier", "icon": "sms" }
    ]
  }
}
```
Tous les champs sauf `schemaVersion` sont facultatifs : ce qui manque prend la valeur par défaut.

| Champ | Règle |
|---|---|
| `theme.*` (couleurs) | `#RRGGBB` |
| `theme.fontScale` | de 1 à 1,6. Multiplie la taille de tout le texte ; **jamais en dessous de 1** pour garder le texte à 24 sp minimum |
| `home.columns` | 1 ou 2 |
| `home.tiles` | de 1 à 6 tuiles, `id` unique (1–32 caractères) |
| `tiles[].action` | `call`, `messages`, `camera`, `gallery` (liste fermée : le JSON ne peut pas lancer autre chose) |
| `tiles[].label` | facultatif, 1–24 caractères ; sinon libellé français par défaut de l'action |
| `tiles[].icon` | `call`, `sms`, `camera`, `photos`, `favorite`, `home`, `person`, `music`, `tv`, `sun` ; sinon l'icône de l'action |

## Garde-fous pour les seniors
Un mauvais fichier ne doit jamais rendre le téléphone illisible, donc il est **refusé** (pas corrigé en silence) si :
- le contraste (WCAG) est inférieur à **4,5:1** entre `text` et `background`, `tile` ou `accent`, ou entre `onPrimary` et `primary` (visez 7:1, comme la palette par défaut) ;
- le texte serait plus petit que par défaut ;
- le fichier dépasse 20 000 caractères ou contient une action inconnue.

## Ce qui n'est pas (encore) personnalisable
Les textes des autres écrans (appel, messages), la disposition de ces écrans et l'ajout de nouvelles actions : cela demande du code. Les nouvelles actions sont ajoutées dans `TileAction` (`config/UiConfig.kt`).
