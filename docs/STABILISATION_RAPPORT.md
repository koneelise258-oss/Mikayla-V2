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
