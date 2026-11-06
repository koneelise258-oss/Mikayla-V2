# Ressources tierces intégrées

| Ressource | Usage dans Mikayala | Licence | Conditions |
|---|---|---|---|
| **Noto Color Emoji** (Google), via `androidx.emoji2:emoji2-bundled` 1.5.0 | Police d'émojis embarquée : rendu identique sur tous les téléphones (Android 7 → 16), hors ligne | SIL Open Font License 1.1 (police) / Apache 2.0 (bibliothèque) | Redistribution autorisée dans une application ; ne pas vendre la police seule |
| **emojibase-data** 18.0.0 (Miles Johnson) | `app/src/main/assets/emoji/emoji_fr.tsv` : liste, ordre, noms et mots-clés français (Unicode ≤ 15.0) | MIT | Conserver la mention de copyright (en-tête du fichier + ce document) |
| **Unicode CLDR** (annotations françaises, reprises par emojibase) | Noms et mots-clés français des émojis | Unicode License v3 | Mention de la source |

## Émojis « style Apple » : non intégrés
Les dessins d'émojis d'Apple (police Apple Color Emoji) sont protégés par le droit d'auteur d'Apple et
ne sont pas distribuables dans une application Android. Les alternatives libres possibles sont :
Noto Color Emoji (choisie : la plus complète, maintenue, intégrée nativement par AndroidX),
Twemoji (CC BY 4.0, maintenance communautaire) ou OpenMoji (CC BY-SA 4.0, style très différent).
