# Chat et jeux — état réel (comparaison avec WhatsApp)

Légende : ✅ fonctionne (code vérifié, non testé sur téléphone) · 🆕 ajouté/réparé dans cette branche · ⚠️ partiel · ❌ absent

## Messages
| Fonction | État |
|---|---|
| Texte, réponse (citation), modification, copie, partage | ✅ |
| Supprimer pour moi / pour les deux | ✅ |
| Réactions emoji | ✅ |
| Épingler (bannière en haut) | ✅ |
| Favoris | ✅ marquage — 🆕 liste des favoris (menu ⋮ > Messages favoris) |
| Recherche dans la conversation | 🆕 (menu ⋮ > Rechercher, ou profil du partenaire) |
| Sélection multiple (copier, favoris, supprimer) | 🆕 (appui long > Sélectionner) |
| Séparateurs de date (Aujourd'hui / Hier / date) | 🆕 |
| Badge de messages non lus | 🆕 (onglet Discussions) |
| Accusés envoyé / distribué / lu | ✅ — 🆕 respecte le réglage « Accusés de lecture » |
| Indicateur « en train d'écrire » / « enregistre un audio » | ✅ |
| Présence en ligne, dernière connexion | ✅ — 🆕 respecte le réglage « Dernière connexion » |
| Photo éphémère (vue unique) | ✅ |
| Envoyer au coffre | ✅ |
| Infos du message | ✅ — 🆕 sans noms codés en dur |
| Transférer à un autre contact | ⚠️ « Partager » vers une autre application (il n'y a qu'un seul contact dans Mikayala) |
| Messages éphémères (suppression automatique) | ❌ |

## Vocaux et médias
| Fonction | État |
|---|---|
| Enregistrement, pause, reprise, annulation, verrouillage | ✅ |
| Lecture, vitesse de lecture, forme d'onde | ✅ |
| Photos (édition, recadrage), vidéos, documents | ✅ — 🆕 documents en URL signée, qualité photo réglable |
| Plusieurs photos en un envoi, reprise d'un envoi interrompu, barre de progression | ❌ / non vérifié |

## Jeux dans le chat (lancés depuis le trombone > Jeux)
Avant cette branche, **tous étaient des simulations locales** : le résultat n'existait que sur le téléphone qui jouait.
| Jeu | État |
|---|---|
| Morpion | 🆕 vrai jeu à deux : plateau synchronisé, tour par tour (❌ celui qui lance, ⭕ l'autre), victoire / nul, « Rejouer », notification « à toi de jouer » |
| Tu préfères | 🆕 chacun choisit, les deux réponses s'affichent quand vous avez répondu tous les deux |
| Quiz complicité | 🆕 le partenaire tape sa réponse, puis la réponse attendue s'affiche chez les deux |
| Carte à gratter | 🆕 grattée par le destinataire, visible chez les deux |
| Bon d'amour | 🆕 utilisé par le destinataire, statut partagé |
| Roue de l'amour | 🆕 un seul tirage, résultat identique sur les deux téléphones |
| Action ou vérité | ✅ carte envoyée (pas d'état à partager) |
Condition : la politique RLS de la table `messages` doit autoriser chaque membre du couple à mettre à jour `media_url` des messages du couple. Sinon le coup est refusé et l'application affiche « Non enregistré ».

## Jeux de l'espace Couple (onglet Couple > Jeux)
| Élément | État |
|---|---|
| Capsules d'amour | ✅ synchronisées (Supabase) |
| Bons d'amour, liste de rêves, quiz, Tu préfères, Action ou vérité | 🆕 enregistrés sur l'appareil (perdus à chaque fermeture auparavant) — ⚠️ **non partagés** avec le partenaire : il faut des tables Supabase dédiées |
