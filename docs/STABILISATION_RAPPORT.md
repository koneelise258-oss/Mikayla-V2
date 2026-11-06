# Mikayala V2 — Stabilisation en 5 phases : rapport de validation

Branche : `claude/stabilisation-5-phases` (à partir de `main` @ c98f07c).

**Limite de vérification, valable pour tout ce rapport** : aucun téléphone n'est accessible depuis cet
environnement, ni la base Supabase `yrcnneyejqlkumjbetfh`. Les preuves disponibles sont : lecture du code,
**compilation réelle** de l'APK par GitHub Actions, **tests unitaires** exécutés en CI, Android Lint.
Une compilation n'est pas une preuve de fonctionnement réel : chaque point est donc au mieux
« IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS ».

---

## Phase 1 — Session, couple et messagerie

CI : run `42132559099` — APK construit ✅, 91 tests unitaires ✅ (dont 9 nouveaux), Lint : 0 erreur.

### 1.1 Session et récupération du couple

**C1 — Retour à « créer un couple » après fermeture complète**
- Problème : après fermeture et réouverture, l'application renvoyait parfois vers l'écran de jumelage.
- Cause profonde (2 causes cumulées) :
  1. Au démarrage, le SDK Supabase recharge la session enregistrée **de façon asynchrone**. L'application
     interrogeait la table `couples` avant la fin de ce rechargement, donc **sans jeton utilisateur**
     (clé anon). La RLS ne renvoie alors aucune ligne : réponse `[]`, **pas une erreur**.
     `recoverCoupleFromSupabase()` concluait « aucun couple » et appelait `clearCoupleCache()`.
  2. `getAllCouplesForUser()` transformait toute erreur (réseau, 401, 500, exception) en liste vide, avec le même effet.
- Fichiers : `data/SupabaseService.kt`, `data/repository/MikayalaRepository.kt`.
- Solution : `awaitSessionReady()` (attend `auth.awaitInitialization()`) avant toute requête ;
  nouvelle `fetchCouplesForUser()` qui renvoie `Found(liste)` **ou** `Failed(raison)` et refuse d'interroger
  sans jeton utilisateur ou avec la session d'un autre compte ; nouveau résultat
  `CoupleRecoveryResult.ServerUnavailable` qui **conserve** le couple local. Le cache n'est effacé que sur une
  réponse fiable (jeton de cet utilisateur, HTTP 2xx) indiquant réellement aucun couple.
- Tests : compilation CI. Non testé sur téléphone.
- État : IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS.

**C2 — Création silencieuse d'un nouveau compte anonyme (perte définitive du couple)**
- Problème : un téléphone pouvait changer d'identité Supabase et perdre son couple et ses messages.
- Cause profonde : `syncProfiles()`, appelée toutes les 4 s par la synchronisation, faisait
  `if (!hasActiveSession()) ensureAnonymousSession()`. Au démarrage (session pas encore rechargée) ou lors d'une
  absence passagère de session, cela appelait `signInAnonymously()` : **nouveau compte**, donc plus aucun couple.
- Solution : `syncProfiles()` ne crée plus jamais de session ; `ensureAnonymousSession()` attend d'abord le
  rechargement de la session enregistrée (la création d'un compte ne se fait plus que sur action explicite :
  écran de connexion, création/jonction d'un couple).
- État : IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS.

**C3 — Plusieurs dépôts de données dans le même processus**
- Cause : `MikayalaRepository(...)` était instancié par l'activité, `BootReceiver`, `CallActionReceiver` et
  `DirectReplyReceiver` ; chaque instance lançait sa boucle de synchronisation infinie et son écouteur réseau.
- Solution : `MikayalaRepository.getInstance(context)` (instance unique), utilisé partout.
- Fichiers : `MainActivity.kt`, `services/BootReceiver.kt`, `services/CallActionReceiver.kt`, `services/DirectReplyReceiver.kt`.

**C4 — Cache local et couple actif** : la conversation n'affiche plus que les messages du couple actif
(le cache pouvait contenir ceux d'un ancien couple). Fichier : `ui/screens/ChatScreen.kt`.

### 1.2 Envoi et réception

**C5 — Toutes les mises à jour vers Supabase échouaient (PATCH)**
- Problème : réactions, modification d'un message, suppression pour les deux, coups de jeu, profil, surnoms,
  statut des appels et replis des accusés ne s'enregistraient pas (ou de façon aléatoire).
- Cause profonde : `executePatch()` envoyait un **POST** avec l'en-tête `X-HTTP-Method-Override: PATCH`
  (contournement car `HttpURLConnection` ne gère pas PATCH). PostgREST ignore cet en-tête : la requête est une
  **insertion** d'une ligne incomplète, refusée.
- Solution : vrai `PATCH` via le client HTTP Ktor/OkHttp déjà présent dans le projet ; `updateMessageFields()` et
  `deleteMessageForEveryone()` vérifient qu'au moins une ligne a été modifiée (`[]` = refus RLS = échec).
- Dépendance Supabase : si la politique UPDATE de `messages` est limitée à l'expéditeur, le partenaire ne peut
  toujours pas réagir/jouer sur un message reçu (requête 13 de `AUDIT_SUPABASE_REQUETES.sql`).

**C6 — Message affiché « envoyé » mais jamais enregistré (perte)**
- Cause : le statut initial d'un texte est `sent` quand il y a du réseau ; en cas d'échec de l'insertion, le
  commentaire disait « reste en attente » mais le statut restait `sent`. La synchronisation ne renvoie que
  `pending`/`failed` : le message n'était **jamais** renvoyé.
- Solution : échec ⇒ `pending` ⇒ renvoi automatique.

**C7 — Messages bloqués « en attente » à vie**
- Cause : si l'insertion réussissait mais que la réponse se perdait (coupure), le renvoi recevait `409`
  (identifiant déjà présent), compté comme un échec, indéfiniment.
- Solution : `409`/`23505` ⇒ vérification de l'existence du message ⇒ succès (envoi idempotent).

**C8 — Statut « pending » publié sur le serveur**
- Cause : le renvoi publiait `status = 'pending'` ; `mark_messages_delivered` (qui cherche `sent`) ne le voyait jamais.
- Solution : le serveur reçoit toujours `sent`.

**C9 — Médias republiés sans leur fichier** : la synchronisation pouvait publier une photo/un vocal pendant son
propre envoi, ou après un échec d'upload, avec un `storage_path` inexistant. Désormais : suivi des envois en
cours ; un média en attente n'est republié qu'après un nouvel upload réussi depuis sa copie locale, sinon il passe
en « échec » (à renvoyer) — jamais de message pointant vers un fichier absent.

**C10 — Écrasement de messages entre opérations simultanées** : la liste des messages était modifiée par
lecture puis écriture (49 endroits). Une synchronisation (réseau lent) pouvait réécrire une liste calculée avant
un envoi : le message tout juste écrit disparaissait. Toutes les écritures passent par `_messages.update { }` (atomique).

**C11 — Réponse rapide depuis une notification** : envoyée sans attendre la session (app fermée) et affichée
« Message envoyé ✓ » même en cas d'échec. Désormais : attente de la session, puis de la confirmation réelle
(15 s max) ; sinon « Réponse en attente d'envoi » (renvoyée automatiquement plus tard).

### 1.3 Synchronisation et récupération après fermeture

**C12 — Trois synchronisations concurrentes et complètes**
- Cause : le dépôt (4 s), l'accueil (3 s) et la conversation (2,5 s) téléchargeaient chacun **tout
  l'historique** et appelaient les RPC d'accusés, en parallèle.
- Solution : une seule synchronisation (verrou `Mutex`, pas de doublon en parallèle) :
  - légère toutes les 4 s : seulement les messages plus récents que le dernier connu (`created_at > …`) ;
  - complète (500 derniers messages) au démarrage, au retour au premier plan, au retour d'Internet, à chaque
    ré-abonnement temps réel et au plus toutes les 60 s ;
  - profils/activité toutes les 60 s, coffre toutes les 30 s.
- Fusion sans perte (`mergeMessageLists`) : aucun message local supprimé, aucun doublon, accusés qui ne reculent
  jamais, URL média et aperçu de réponse conservés. Testé : `MessageSyncMergeTest` (9 tests ✅).
- Messages reçus pendant que l'app était fermée : récupérés à la réouverture et marqués « reçu ».

### 1.4 Fiabilité temps réel et pagination

**C13 — Abonnement temps réel qui ne se rétablit pas / présence qui clignote**
- Cause : `connectRealtime()` était rappelé à chaque synchronisation. Le canal des messages n'était épargné que
  s'il était `SUBSCRIBED` : pendant une reconnexion (le SDK attend 7 s), il était détruit et recréé toutes les
  2,5–4 s, donc **jamais** rétabli. Le canal de présence n'avait aucune protection : recréé toutes les 4 s, le
  partenaire passait en boucle « en ligne / hors ligne ».
- Solution : canal conservé s'il est `SUBSCRIBED` ou `SUBSCRIBING` (< 45 s) ; aucun abonnement sans jeton
  utilisateur ; à chaque (ré)abonnement, synchronisation complète pour rattraper la coupure.

**C14 — Pagination** : bouton « Afficher les messages précédents » en haut de la conversation (pages de 100) ;
le défilement automatique ne saute plus en bas quand on charge d'anciens messages.

### Limites connues (phase 1)
- Une session réellement perdue (jeton de rafraîchissement expiré) sur un compte **anonyme** ne peut pas être
  récupérée : c'est une limite des comptes anonymes. Le couple local reste affiché, mais il faudra se reconnecter
  (les comptes e-mail retrouvent leur couple).
- Les suppressions définitives faites pendant une coupure du temps réel ne sont pas rejouées (seule la
  suppression « pour les deux », qui est une modification, l'est).

### Tests sur téléphones à faire (phase 1)
A→B et B→A ; fermeture complète puis réouverture des deux côtés (couple conservé, messages tous présents) ;
envoi en mode avion puis retour réseau ; coupure réseau pendant un envoi ; réponse depuis la notification app
fermée ; chargement des anciens messages.

---

## Phase 2 — Notifications, médias, présence et accusés

CI : run `42134681141` — APK ✅, 99 tests unitaires ✅ (dont 8 nouveaux : `MediaAndTypesTest`),
tests de la fonction `send-fcm` : 16/16 ✅ (dont 7 nouveaux).

### 2.1 Notifications Firebase

**C15 — Aucune notification quand l'application est fermée**
- Cause profonde : pour un message reçu app fermée, Android démarre le processus et appelle
  `onMessageReceived` immédiatement. La session Supabase n'étant pas encore rechargée,
  `getCurrentSessionUid()` valait `null` et la règle « aucune notification sans session active » **ignorait**
  le message. Les notifications n'apparaissaient donc que si l'app tournait déjà.
- Solution : attente courte (5 s max) du rechargement de la session avant toute décision
  (`MyFirebaseMessagingService.kt`). L'accusé « reçu » envoyé depuis la notification ne concerne plus que
  ce message.
- Doublons : pas de notification si la conversation est **visible** (`util/AppForeground.kt`) ; un message
  n'est notifié qu'une fois même s'il est renvoyé après une coupure (l'envoi idempotent ne renotifie que
  le premier succès).

**C16 — `send-fcm` en erreur 500 (« client_email / private_key / project_id »)**
- Cause probable : le secret `FCM_SERVICE_ACCOUNT_JSON` du projet Supabase est mal formé (guillemets en
  trop, `\n` littéraux dans la clé, JSON encodé, collage partiel). Le code ne peut pas le vérifier d'ici.
- Solution dans le code : `normalizeServiceAccount()` accepte ces formats et, en secours, trois secrets
  séparés `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY`, `FCM_PROJECT_ID`. Aucune valeur secrète n'est journalisée.
- **Dépendances externes (non faisables d'ici)** :
  1. vérifier/recoller le secret (fichier JSON du compte de service Firebase, projet `gen-lang-client-0988385912`) ;
  2. déployer : `supabase functions deploy send-fcm` (dossier complet : `index.ts` + `payload.ts`) ;
  3. vérifier dans les journaux de la fonction qu'il n'y a plus de 500. Rien n'indique que la version du dépôt
     soit déployée (procédure de comparaison : requête 15 de `AUDIT_SUPABASE_REQUETES.sql`).
  4. vérifier qu'aucun déclencheur/webhook en base n'envoie AUSSI une notification à l'insertion d'un
     message (requêtes 10–12) : ce serait une source de doublons.
- Autorisations Android : les deux téléphones (Android 7 et 10) n'ont pas d'autorisation de notification à
  l'exécution ; sur Android 13+ la demande existe et la page Notifications signale un blocage avec un lien
  vers les réglages.
- État : IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS ; fonction **non déployée** (BLOQUÉ sans accès Supabase).

### 2.2 Présence et dernière connexion

**C17 — « En ligne » affiché à tort**
- Causes : (1) sans activité connue, le statut était « En ligne » par défaut ; (2) une activité de moins de
  2 min était présentée comme « En ligne » ; (3) la présence temps réel restait publiée tant que le processus
  vivait, même app en arrière-plan ; (4) le canal de présence était recréé toutes les 4 s (phase 1, C13).
- Solution : « En ligne » vient **uniquement** de la présence temps réel ; l'horodatage donne
  « Vu à l'instant / il y a N min / aujourd'hui à… » ; aucune activité ⇒ « Hors ligne ». Passage en
  arrière-plan : présence retirée et heure exacte de dernière connexion enregistrée ; retour : présence
  republiée. Présence non publiée si « Dernière connexion : personne ».
- Fichiers : `MikayalaRepository.kt` (`formatLastSeen`, `onAppBackground/onAppForeground`),
  `SupabaseService.kt` (`retrackPresence`, `presenceTrackingEnabled`), `MainActivity.kt`.

### 2.3 Accusés de réception

**C18 — Accusés globaux et « lu » sans lecture**
- Causes : `mark_messages_delivered/read(target_couple_id)` marquent **tous** les messages du couple ; une
  notification pour un message marquait tout « reçu » ; la conversation restée ouverte en arrière-plan
  marquait « lu » ; les replis REST modifiaient aussi les messages de l'expéditeur lui-même et ne passaient
  jamais un message « envoyé » directement à « lu » ; un appel serveur partait à chaque passage, même sans
  rien à marquer.
- Solution :
  - nouvelles fonctions proposées `mark_messages_delivered_ids` / `mark_messages_read_ids`
    (`supabase/migrations_proposees/20261106090000_accuses_par_message.sql`, **non appliquée**) ; l'app les
    utilise si elles existent, sinon garde les anciennes (aucune régression) ;
  - l'app n'envoie que les identifiants des messages du partenaire réellement présents (reçu) ou affichés
    écran visible (lu) ; rien n'est envoyé s'il n'y a rien à marquer ; état local mis à jour ;
  - replis REST limités aux messages reçus (`sender_id=neq.moi`) avec `delivered_at` / `read_at` ;
  - fusion : un accusé ne recule jamais (phase 1).
- Accusés de lecture désactivés : « reçu » est toujours confirmé, « lu » jamais.
- Dépendance Supabase : appliquer la migration pour un état exact message par message.

### 2.4 Photos, vidéos, vocaux

**C19 — Jeux, documents, positions arrivaient comme du simple texte**
- Cause : à l'envoi, tout type autre que photo/vocal/vidéo était converti en `text`
  (`postMessageEntity`, introduit par le commit `49ab8dd`). Chez le partenaire, un morpion, une roue ou une
  carte à gratter n'était qu'un texte : **les jeux du chat ne pouvaient pas se jouer à deux**.
- Solution : envoi du vrai type ; si la base le refuse (contrainte), renvoi avec le type de base et une
  étiquette invisible en tête du contenu (`util/MessageTypeTag.kt`), relue et retirée à la réception.
  Tests : `MediaAndTypesTest`. Les deux téléphones doivent avoir le nouvel APK (une ancienne version
  afficherait « mk:type » devant ces messages).
- Recommandation Supabase : autoriser les types réels dans `messages.message_type` (requête :
  `select conname, pg_get_constraintdef(oid) from pg_constraint where conrelid = 'public.messages'::regclass;`).

**C20 — Photo/vidéo perdue après un échec d'envoi**
- Cause : les octets n'étaient qu'en mémoire ; après un échec, le message restait « échec » sans aucun moyen
  de le renvoyer.
- Solution : copie locale (`files/outgoing_media`) : aperçu immédiat chez l'expéditeur, renvoi automatique
  par la synchronisation (nouvel upload puis message), essais espacés (15 s → 10 min, remis à zéro au retour
  d'Internet), copie supprimée après succès. Vocaux : déjà copiés localement, même mécanisme.

**C21 — Anciens médias illisibles après 7 jours**
- Cause : le lien signé enregistré dans le message (valable 7 jours) était réutilisé indéfiniment.
- Solution : date d'expiration lue dans le jeton du lien (`util/SignedUrls.kt`, décodage compatible
  Android 7) ; lien expiré ou à moins d'1 h ⇒ nouveau lien ; un seul calcul à la fois par fichier.

**C22 — Envoi de gros fichiers** : upload en flux de taille connue (plus de double copie en mémoire),
délai de réponse porté à 120 s.

- Politiques Storage : inchangées ici ; la partie C de la migration `20261105090000` (écritures réservées
  aux membres du couple) reste à appliquer **après** installation du nouvel APK sur les deux téléphones.
- État 2.4 : IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS.

### Tests sur téléphones à faire (phase 2)
Notification app ouverte / en arrière-plan / fermée / écran verrouillé ; conversation ouverte (pas de
notification) ; présence quand l'autre ferme l'app ; « Vu il y a… » ; accusés ✓ / ✓✓ / lu message par message ;
photo, vidéo, vocal dans les deux sens ; envoi en mode avion puis retour réseau ; médias de plus de 7 jours ;
morpion et roue reçus comme des jeux chez le partenaire.

---

## Phase 3 — Coffre-fort, actions de chat et émojis

CI : run `42136822229` — APK ✅, 102 tests unitaires ✅ (dont 3 nouveaux : `EmojiCatalogTest`), Lint : 0 erreur.

### 3.1 Coffre-fort privé

**C23 — Table `vault_items` vide, « Enregistré » affiché quand même**
- Causes profondes :
  1. identifiant envoyé `v_<uuid>` : refusé si la colonne `id` est de type uuid ;
  2. dates envoyées en millisecondes (`created_at`, `date_added`) : refusées si la colonne est un timestamptz,
     et `date_added` n'existe peut-être pas ;
  3. l'insertion était lancée en arrière-plan sans attendre le résultat : l'élément s'affichait, le message
     « Enregistré » apparaissait, puis tout disparaissait à la réouverture ;
  4. identifiant local (`v_xxxxxxxx`) différent de l'identifiant serveur : doublons et suppression impossible ;
  5. la synchronisation n'ajoutait que les nouveaux éléments (suppressions de l'autre téléphone ignorées) et
     lisait une date ISO comme « maintenant ».
- Solution (`SupabaseService.kt`, `MikayalaRepository.kt`, `VaultScreen.kt`, `ChatScreen.kt`,
  `MessageOptionBottomSheet.kt`) : UUID standard identique partout ; dates laissées au serveur ; colonnes
  absentes retirées automatiquement (erreur PGRST204) ; ajout, enregistrement depuis le chat et suppression
  **attendent la confirmation du serveur** avant tout message de succès (sinon : « vérifiez la connexion ») ;
  suppression vérifiée (une ligne réellement supprimée) et fichier propre au coffre supprimé ; le serveur fait
  foi à chaque synchronisation et à l'ouverture du coffre ; copie locale pour l'affichage hors ligne ; photo du
  coffre supprimée du stockage si l'élément est refusé (pas de fichier orphelin).
- Confidentialité : fichiers dans le bucket **privé** `messages-media` (liens signés) ; politique RLS proposée
  `supabase/migrations_proposees/20261106091000_coffre_prive.sql` (lecture/ajout/suppression réservés aux 2
  membres d'un couple apparié, **non appliquée** — contrôler d'abord les politiques existantes, qui se cumulent).
- État : IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS ; confidentialité serveur BLOQUÉE tant que la migration
  n'est pas appliquée.

### 3.2 Actions de conversation

| Action | Problème initial | Cause | Correction |
|---|---|---|---|
| Réactions | La réaction du partenaire disparaissait | tout l'objet `reactions` local était renvoyé, écrasant l'état serveur | seule ma clé est modifiée, à partir de l'état serveur ; rétablie + message si refus |
| Favoris | visibles chez le partenaire, perdus à la synchro | colonne partagée `is_starred` (peut-être absente) | favoris personnels, enregistrés sur le téléphone (repris des anciens) |
| Supprimer pour moi | le message réapparaissait | colonne `deleted_for` (peut-être absente) écrasée à la synchro | masquage durable sur le téléphone |
| Supprimer pour les deux | échec silencieux | PATCH inopérant (phase 1) ; aucun contrôle | vrai PATCH ; rétabli + message si refus |
| Modifier | non enregistré | date en millisecondes vers une colonne date | date ISO, colonne facultative ; rétabli si refus |
| Épingler | échec silencieux | idem | rétabli + message si refus |
| Répondre | aperçu vide chez le partenaire | colonnes d'aperçu absentes du serveur | aperçu reconstitué depuis le message cité |
| Recherche / filtre favoris | — | (ajouté le 05/11) | inchangé ; utilise les favoris personnels |
| Glisser pour répondre | — | — | **non modifié** (préservé) |
| Document | illisible chez le partenaire | seule l'adresse locale `content://` était envoyée | vrai envoi dans le stockage privé |
| Position | toujours Paris (48.8566, 2.3522) | coordonnées écrites en dur | position réelle (GPS/réseau, autorisation demandée) |
| Contact | faux « +33 6 12 34 56 78 » | texte écrit en dur | vrai contact choisi dans le carnet |
| Audio | faux morceau « 42 s » sans son | texte écrit en dur | vrai fichier audio choisi, envoyé comme un vocal |
| Sondage | question imposée, vote local perdu | contenu en dur, état non partagé | sondage rédigé (2 à 4 choix), votes partagés et synchronisés |
| Événement | « Escapade Spa… samedi 18 h » imposé | contenu en dur | événement rédigé |
| Image IA | message texte prétendant une image générée | aucune génération n'existe | option retirée |

Dépendance Supabase : la politique UPDATE de `messages` doit permettre au **destinataire** de modifier
`reactions` et `media_url` (réactions, jeux, sondages) — requête 13 de `AUDIT_SUPABASE_REQUETES.sql`.

### 3.3 Catalogue d'émojis

- Problème : ~420 entrées écrites à la main, dont **43 mots** (« Map », « Camping », « Hotel »…) insérés tels
  quels dans le message ; catalogue très incomplet (pas de catégorie Personnes, très peu de drapeaux —
  pas de Côte d'Ivoire) ; rendu dépendant de la police du téléphone (émojis récents illisibles sous Android 7).
- Solution : catalogue complet de 1 870 émojis (Unicode ≤ 15.0) avec noms et mots-clés **français**
  (`assets/emoji/emoji_fr.tsv`, source emojibase-data MIT / CLDR) ; recherche insensible aux accents et à la
  casse (plusieurs mots possibles) ; 9 catégories + récents (32) ; barre de catégories défilante, cibles de
  40 dp ; grille adaptée à la largeur ; récents nettoyés des anciens « mots ».
- Rendu homogène : police **Noto Color Emoji embarquée** (`androidx.emoji2:emoji2-bundled`, licence OFL),
  initialisée au démarrage ; même rendu sur les deux téléphones, hors ligne, sans services Google.
  Coût : APK de 41,7 → 51,7 Mo.
- **Visuels Apple : non intégrés** (protégés par le droit d'auteur d'Apple, non redistribuables). Alternatives
  libres analysées dans `docs/LICENCES_TIERCES.md`.
- État : IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS.

**C24 — Stabilité : application figée après une erreur** : le gestionnaire global de plantage remplaçait celui
d'Android ; une exception sur le fil principal laissait l'écran bloqué. Il est désormais chaîné (journalisation
puis comportement normal d'Android). Fichier : `MikayalaApplication.kt`.

### Tests sur téléphones à faire (phase 3)
Coffre : ajout note/photo, fermeture complète, réouverture sur l'autre téléphone, suppression croisée, mode
avion (message d'échec, rien d'affiché à tort). Chat : réactions simultanées des deux côtés, favoris (non visibles
chez l'autre), supprimer pour moi puis synchronisation, supprimer pour les deux, modifier, épingler, répondre
(aperçu chez l'autre), glisser pour répondre, document, position, contact, audio, sondage voté des deux côtés.
Émojis : recherche « coeur », « fete », drapeau de Côte d'Ivoire, rendu identique sur Android 7 et 10.

---

## Phase 4 — Interface et expérience utilisateur

CI : run `42138474525` — APK ✅, tous les tests unitaires ✅, Lint : 0 erreur.
Aucun écran n'a pu être **vu** (pas d'émulateur ni de téléphone ici) : corrections faites à partir du code.

### 4.1 Barre de navigation
| Problème | Cause | Correction (`MainDashboardScreen.kt`, `GlassmorphicFloatingNavBar.kt`) |
|---|---|---|
| Barre trop haute dans Discussions | pastille « Plein écran Chat 💬 » ajoutée au-dessus de la barre (~100 dp au total) | pastille retirée (le plein écran reste dans le menu ⋮) ; barre de 58 dp |
| Barre absente en revenant aux Discussions | changer d'onglet mettait `showNavBarInChat = false` | supprimé : la barre ne disparaît que sur demande |
| Barre au-dessus du clavier | aucune prise en compte du clavier | masquée tant que le clavier est ouvert |
| Saisie cachée par les boutons système (barre masquée) | contenu sans marge système | marge des boutons système ajoutée quand la barre est masquée |
| Libellés coupés (« Paramètres », « Discussions ») en grande taille de texte | hauteur fixe 64 dp, taille de texte multipliée sans limite | taille plafonnée dans la barre, coupure propre (…), hauteur minimale au lieu de fixe |
| Tablette : onglets étirés | largeur pleine | barre centrée, 640 dp max |
| Zones tactiles | — | ≥ 48 dp ; badge élargi pour « 99+ » |

### 4.2 Dimensions et disposition
- Bulles de discussion : largeur max fixe 300 dp (toute la largeur d'un écran de 320 dp, minuscules sur
  tablette) → **78 % de l'écran** (200–440 dp), appliqué aux 13 types de bulles.
- Barre de réactions rapides (8 émojis + « + ») plus large qu'un petit écran → défilante.
- Fenêtre « Réagir avec un émoji » : hauteur fixe 420 dp → 75 % de l'écran, 460 dp max.
- En-tête : un surnom long repoussait les boutons d'appel hors de l'écran → surnom et statut tronqués (…).

### 4.3 Animations et transitions
- À chaque ouverture de la conversation, la liste **défilait en animation depuis le premier message** :
  positionnement immédiat à l'ouverture, animation seulement pour un nouveau message.
- Pendant la lecture d'anciens messages, l'arrivée d'un message ne ramène plus en bas (sauf si l'on était déjà
  en bas ou si c'est son propre message).
- Les animations en boucle restantes sont légitimes (micro pendant l'enregistrement, scanner QR).
- Rafraîchissements intempestifs : supprimés à la source en phase 1 (une seule synchronisation, plus de
  remplacement de toute la liste toutes les 2,5 s) et phase 2 (liens média mis en cache).
- Roue qui tourne seule : traitée en phase 5.

### 4.4 Cohérence générale
- Bouton Retour : vérifié — conversation (sélection, recherche, émojis, pièces jointes), Paramètres (sous-pages),
  accueil (feuilles, onglets → Discussions) ; visionneuses et éditeurs sont des fenêtres qui le gèrent.
- Contenus factices restants retirés : prénom « Mikey » écrit en dur (cycle) ; ceux du trombone en phase 3.
- Confirmations trompeuses remplacées par des confirmations après succès réel (phase 3) ; échecs annoncés.
- État : IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS (aucune vérification visuelle possible ici).

### Tests sur téléphones à faire (phase 4)
Android 7 et 10, thème clair et sombre, taille de texte normale et « très grande » : barre de navigation
(hauteur, libellés), clavier ouvert dans la conversation, plein écran du chat, bulles longues, surnom long,
ouverture de la conversation (pas de défilement animé), bouton Retour sur chaque écran, tablette si disponible.

---

## Phase 5 — Jeux de couple

CI : run `42139502029` — APK ✅, 106 tests unitaires ✅ (dont 4 nouveaux : `WheelGameTest`), Lint : 0 erreur.

### 5.1 Audit des jeux
| Jeu | Où | Règle attendue | État trouvé | État après correction |
|---|---|---|---|---|
| Morpion | chat | ❌ lanceur, ⭕ partenaire, tour par tour, victoire/nul | **simulation locale** (plateau en mémoire d'un seul téléphone) | synchronisé, tours vérifiés (`TicTacToe`, 9 tests) |
| Carte à gratter | chat | grattée par le destinataire, visible chez les deux | locale ; gain toujours identique | synchronisée ; 12 gains tirés au hasard |
| Quiz complicité | chat | le partenaire répond, la réponse attendue s'affiche ensuite | local ; question imposée et **réponse « attendue » inventée** | synchronisé ; question et réponse rédigées par l'expéditeur |
| Tu préfères | chat | chacun choisit, les 2 choix révélés à la fin | local ; un seul dilemme | synchronisé ; 10 dilemmes |
| Bon d'amour | chat | utilisé par le destinataire, statut partagé | local ; un seul bon | synchronisé ; 8 bons |
| Roue | chat | même tirage pour les deux | **tournait seule en boucle**, résultats différents | voir 5.2 |
| Action ou vérité | chat | carte envoyée | 4 cartes | 12 cartes |
| Sondage | chat | votes des deux visibles | vote local, perdu | votes partagés (phase 3) |
| Roue des désirs | espace Couple | résultat = case sous le pointeur | **case annoncée ≠ case montrée** | corrigée, légende, « Jouer à deux » |
| Quiz, bons, Tu préfères, liste de rêves, action/vérité | espace Couple | — | enregistrés sur un seul téléphone | inchangés (voir limite) + bouton « Jouer à deux » vers la version synchronisée du chat |
| Capsules d'amour | espace Couple | partagées | synchronisées (Supabase) | inchangées |

**Cause de la régression des jeux du chat** : le commit `8e57f7a` (« add back navigation handling », 05/11)
a remplacé les branches synchronisées de `ChatScreen.kt` par d'anciennes versions locales (6 appels
`playGameMove` → 0). Elles ont été restaurées depuis `f06bf69`, branche par branche, sans toucher au reste.

### 5.2 Roue et résultats
- Problème : la roue tournait toute seule ; les deux téléphones pouvaient afficher des résultats différents.
- Causes profondes :
  1. à l'affichage d'une roue déjà jouée, un « réalignement » animé de 3,5 s se lançait ;
  2. la fin de **toute** animation (y compris ce réalignement) calculait un résultat et l'enregistrait comme un
     nouveau coup → notification → la roue tournait chez le partenaire → qui réenregistrait : **boucle** ;
  3. la case lue à la fin de l'animation était décalée d'une case par rapport à la formule d'affichage.
- Solution : résultat tiré **au clic**, enregistré (état `index:numéroDuTour`), puis les deux téléphones animent
  vers cette même case ; aucune écriture pendant ou après une animation ; à l'affichage, la roue est directement
  sur le dernier résultat ; formules angle ↔ case exactement inverses (testé de 2 à 12 cases) ; résultat affiché
  seulement à la fin de l'animation ; anciens messages (`index` seul) toujours lisibles.
- Fichiers : `util/ChatGames.kt`, `ui/screens/ChatScreen.kt` (`WheelOfFortuneMessageItem`),
  `ui/screens/couple/CoupleGamesView.kt`.

### 5.3 / 5.4 Règles, déroulement, expérience
- État des parties stocké dans le message : une partie en cours est conservée après fermeture et sur l'autre
  téléphone ; chaque coup refusé par le serveur est annulé à l'écran et signalé.
- Contenus variés (`util/GameContent.kt`), quiz et sondage rédigés par les joueurs, légende de la roue.
- Dépendance Supabase : la politique UPDATE de `messages` doit autoriser **les deux membres** à modifier
  `media_url` ; sinon le partenaire voit « Non enregistré » à chaque coup (requête 13).
- Limite : les listes de l'espace Couple (bons, liste de rêves, quiz, Tu préfères) restent propres à chaque
  téléphone tant qu'aucune table Supabase partagée n'existe ; le partage passe par « Jouer à deux » (chat).
- État : IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS.

---

# Rapport global

**Aucune fonctionnalité n'a pu être vérifiée sur deux téléphones** depuis cet environnement : aucune n'est donc
déclarée « TERMINÉ ET VÉRIFIÉ SUR DEUX APPAREILS ». Preuves disponibles : compilation de l'APK par GitHub
Actions à chaque étape, 106 tests unitaires Android + 16 tests de la fonction `send-fcm`, Android Lint sans erreur.

| Fonctionnalité | État |
|---|---|
| Session conservée après fermeture complète, pas de nouveau compte créé en silence | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Couple conservé (erreur ≠ absence de couple) | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Envoi/réception de messages, renvoi automatique, pas de doublon | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Messages manqués récupérés à la réouverture, temps réel rétabli | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Pagination des anciens messages | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Notifications (app fermée, pas de doublon conversation ouverte) — côté Android | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Fonction `send-fcm` (secret Firebase, déploiement) | BLOQUÉ (accès Supabase nécessaire) |
| Présence et dernière connexion | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Accusés message par message | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS (exactitude complète : migration `20261106090000` à appliquer) |
| Photos, vidéos, vocaux, documents | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Coffre persistant | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Coffre privé côté serveur | BLOQUÉ (migration `20261106091000` à appliquer) |
| Actions du chat (réactions, suppression, réponse, favoris, épingle, modification) | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Glisser pour répondre | Non modifié (préservé) — à revérifier sur téléphone |
| Pièces jointes réelles (position, contact, audio, sondage, événement) | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Sélecteur d'émojis complet, rendu homogène | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Émojis « style Apple » | NON COMMENCÉ — impossible légalement (voir `LICENCES_TIERCES.md`) |
| Barre de navigation, dimensions, animations, bouton Retour | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Jeux du chat synchronisés (morpion, roue, carte, quiz, Tu préfères, bon, sondage) | IMPLÉMENTÉ MAIS NON TESTÉ SUR DEUX APPAREILS |
| Jeux de l'espace Couple partagés nativement | NON COMMENCÉ (tables Supabase à créer ; contournement « Jouer à deux ») |
| Appels audio/vidéo réels (WebRTC) | NON COMMENCÉ (hors plan ; signalisation seule) |

## Actions externes nécessaires (dans l'ordre)
1. **GitHub** : vérifier que le secret `SUPABASE_ANON_KEY` contient la clé *anon* (format JWT) du projet.
2. **Installer le même APK sur les deux téléphones** (artefact de la dernière construction de la branche
   `claude/stabilisation-5-phases`), puis faire les tests listés à la fin de chaque phase.
3. **Supabase — lecture seule d'abord** : exécuter `docs/AUDIT_SUPABASE_REQUETES.sql` (surtout la requête 13 :
   politique UPDATE de `messages`) et la requête de contrainte `message_type` (phase 2, C19).
4. **Supabase — Firebase** : recoller le secret `FCM_SERVICE_ACCOUNT_JSON` (fichier JSON complet du compte de
   service, projet `gen-lang-client-0988385912`) puis `supabase functions deploy send-fcm`.
5. **Supabase — migrations proposées** (non destructives, à relire avant) : `20261105090000` parties A et B,
   `20261106090000` (accusés par message), `20261106091000` (coffre privé, après revue des politiques
   existantes), puis la partie C de `20261105090000` (Storage) une fois le nouvel APK sur les deux téléphones.
6. **Politique UPDATE de `messages`** : autoriser les deux membres du couple à modifier `reactions`,
   `media_url`, `is_pinned`, `is_viewed`, `status`, `delivered_at`, `read_at` (réactions et jeux du partenaire).
7. Fusionner la branche dans `main` après les tests sur téléphones.
