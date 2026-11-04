# Refonte UI/UX « Soft » — Mikayala V2

## 1. Système de design (theme/)
| Élément | Fichier | Contenu |
|---|---|---|
| Couleurs sémantiques | `theme/Color.kt` | `MikayalaPalette` : fond, surface, surface en avant, en creux, modale, 3 niveaux de texte, 2 lignes, accent rose poudré, lavande, bleu brume, sable, avertissement, succès, danger, bulles. Versions `SoftLight` (crème / blanc doux / gris chaud / anthracite) et `SoftDark` (anthracite profond / gris nuancés / texte clair). |
| Alias historiques | `theme/Color.kt` | `DeepNight`, `CardDark`, `VibrantCyan`, `TextPrimary`… pointent vers la palette active : tous les écrans existants suivent le thème sans réécriture. |
| Typographie | `theme/Type.kt` | Poppins embarquée (OFL), échelle Material ; police au choix (Poppins, système, serif, manuscrite). |
| Taille du texte | `theme/Theme.kt` | Multiplicateur appliqué à toute l'app (petite / normale / grande / très grande), en plus du réglage Android. |
| Styles de conversation | `theme/SoftChatStyles.kt` | 5 couleurs de bulles pastel pleines, 5 fonds unis + photo personnelle. |
| Espacements, rayons, durées | `theme/Dimens.kt` | grille de 4 dp, marge d'écran 16 dp, largeur max 640 dp (tablette), rayons 8/12/18/24, animations 150–220 ms. |
| Composants | `ui/components/SoftComponents.kt` | bouton principal, secondaire, texte, champ de saisie, en-tête de section, état vide, chargement, bandeau d'erreur/info, largeur de contenu. |
| Boutons partagés | `ui/components/NeumorphicComponents.kt` | bouton icône rond et bouton d'action, plats (plus de halo coloré de 12 dp ni de dégradé). |
| Navigation | `ui/components/GlassmorphicFloatingNavBar.kt` | Barre sobre : surface unie, filet, pastille de l'onglet actif, badge. Plus de flou ni d'ombre. |

Règles appliquées : pas de dégradé fluo, pas de halo, pas d'animation en boucle décorative, ombres retirées des éléments principaux, fenêtres réservées aux confirmations et choix rapides.

## 2. Paramètres — audit réglage par réglage
Constat initial : la plupart des réglages n'étaient **ni enregistrés ni utilisés** (ils revenaient à leur valeur par défaut et ne changeaient rien).

| Réglage | Avant | Maintenant |
|---|---|---|
| Mode sombre | non enregistré, n'affectait qu'une partie de l'interface | enregistré, thème complet clair/sombre |
| Taille du texte | sans effet | appliquée à toute l'app |
| Police | sans effet | appliquée à toute l'app |
| Bloquer les captures d'écran / masquer dans les récents (2 réglages fusionnés) | sans effet | FLAG_SECURE réel |
| Accusés de lecture | sans effet | désactivé ⇒ le statut « lu » n'est plus envoyé |
| Dernière connexion | sans effet | désactivée ⇒ l'activité n'est plus publiée |
| Délai de verrouillage | sans effet | verrouillage réel au retour dans l'app (5 s de grâce pour « immédiat ») |
| Déverrouillage par empreinte | non enregistré | enregistré, utilisé par l'écran de verrouillage |
| Touche Entrée pour envoyer | sans effet | bouton « Envoyer » du clavier |
| Qualité des photos | sans effet | HD (2560 px) ou économie (1600 px), redimensionnement |
| Couleur des bulles / fond de conversation | non enregistrés, couleurs fluo | enregistrés, pastels Soft |
| Photo de fond | perdue au redémarrage (autorisation galerie temporaire) | copiée dans l'app |
| Icône de l'application | factice | 10 icônes réelles (alias Android) |
| Stockage | chiffres inventés (« 1,2 Mo », « 4,6 Mo »), « Vider le cache » ne faisait rien | page : tailles mesurées sur l'appareil, vrai vidage du cache |
| « Pense à moi » | affichait « envoyé » sans rien envoyer | vraie notification au partenaire |
| Se déconnecter | absent (seule la dissociation du couple déconnectait) | ajouté dans Mon compte, avec confirmation |
| Notifications (son, vibration, catégories, aperçu) | partiellement factice | réels (mission précédente) |

Retirés de l'interface (fonction absente du code, à développer plus tard — rien n'a été supprimé côté données) :
- **Messages éphémères** : aucun mécanisme de suppression automatique n'existe.
- **Visibilité des médias dans la galerie** et **téléchargement automatique** : aucun code ne les utilisait.
- **Flou du fond d'écran** : non pris en charge avant Android 12 (téléphones Android 7 et 10).
- **Importer une icône depuis la galerie** et icônes « Calculatrice / Notes / Météo » : impossible ou sans image.

Fenêtres remplacées par des pages : Confidentialité, Paramètres de discussion, Police, Fond d'écran, Style des bulles, Notifications.
Fenêtres conservées (choix rapide ou confirmation) : code PIN, code leurre, jumelage, anniversaire, effacer les discussions, dissocier le couple, icône de l'application, profil, stockage, serveur Supabase.

## 3. Écrans
| Écran | Changements |
|---|---|
| Démarrage | logo « deux bulles », nom en Poppins, une animation courte |
| Accueil (en-tête) | partenaire + statut à gauche, appels + menu ⋮ à droite (Espaces, profil, plein écran), point « en ligne » sans clignotement |
| Navigation | barre Soft, visible par défaut, libellés Discussions / Appels / Couple / Coffre / Paramètres |
| Conversation | bulles pastel lisibles en clair et sombre, fond uni doux |
| Connexion / onboarding | halos décoratifs retirés, fond du thème |
| Espace Couple | onglets lisibles : Notre histoire, Jeux, Calendrier, Cycle |
| Cycle | dates présentées comme estimations + avertissement « ni avis médical ni contraception » |
| Coffre, Appels | états vides explicites ; grille du coffre adaptative (plus de colonnes sur tablette) |
| Tous les écrans | couleurs du thème, Poppins, texte blanc forcé remplacé (71 endroits), 128 couleurs fluo adoucies, ombres limitées à 1–2 dp, 81 bordures affinées |

## 4. Fonctionnalités conservées
Messagerie (réponses, réactions, édition, suppression, épinglage), notes vocales, photos/vidéos/documents, appels (signalisation), coffre, cycle, jeux, calendrier, P2P, jumelage, codes PIN et calculatrice leurre, notifications : aucun point d'entrée retiré. Les deux boutons carrés de l'en-tête sont désormais dans le menu ⋮.

## 5. Restant à faire / à décider
- Écrans encore à reprendre en détail : jeux, cycle, calendrier, coffre, appels, P2P, éditeurs photo/vidéo (ils suivent déjà les couleurs du thème mais gardent leur mise en page).
- Couleurs écrites en dur restantes dans certains écrans (≈80 occurrences, surtout dans le chat : icônes de pièces jointes, médias).
- Les éditeurs photo/vidéo, la caméra et l'appel restent volontairement sombres.
- Fonctions absentes : messages éphémères, téléchargement automatique, médias dans la galerie.
- Rien n'est testé sur téléphone : vérifier en mode clair ET sombre, sur Android 7 et 10.
