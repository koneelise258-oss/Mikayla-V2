# Audit Mikayala V2 — état au 2026-11-03

> Méthode : lecture complète des fichiers notifications/FCM/Edge Function/migration, lecture ciblée (grep + extraits) du reste.
> **Aucune compilation ni test n'a pu être exécuté** (pas de SDK Android dans l'environnement). Rien ci-dessous n'est « testé sur appareil ».

## 1. Cartographie
- Android Kotlin / Compose, package `com.example.mikayala`, ~37 000 lignes, un seul module `:app`, minSdk 23 / targetSdk 36.
- Backend : Supabase accédé via un `SupabaseService` (1 682 l.) mêlant SDK + appels REST bruts ; `MikayalaRepository` (2 870 l.) = tout l'état.
- Gros fichiers à scinder : `MikayalaRepository`, `ChatScreen` (2 426), `SettingsScreen` (2 212), `CoupleGamesView` (1 738), `OnboardingScreen` (1 509).
- Dépôt : `supabase/functions/send-fcm` + 1 seule migration (`device_push_tokens`). Les autres tables/RLS/buckets ne sont **pas versionnés** → impossible de les auditer depuis le code ; à exporter depuis le projet Supabase réel.
- Pas de `google-services.json` dans le dépôt (normal, à fournir) ; l'URL/clé Supabase viennent de variables d'environnement au build (OK, pas de secret en dur).

## 2. Notifications — constats et état
| # | Constat | Statut |
|---|---|---|
| N1 | Mode par défaut `discreet` : par défaut le destinataire voyait « Nouveau message chiffré », jamais le nom ni l'aperçu. | **Corrigé** (défaut `romantic`) |
| N2 | L'expéditeur imposait `hidePreview` selon **son propre** réglage → aperçu masqué côté serveur pour le partenaire. | **Corrigé** (le serveur envoie le contenu, le destinataire choisit l'affichage) |
| N3 | Aucun réglage par catégorie (messages/appels/jeux/calendrier/cycle/autres). | **Ajouté** (`NotificationPrefs` + interrupteurs dans Paramètres > Notifications, lus même app fermée) |
| N4 | `notificationSound`, vibration, alertes cycle, widget : **non persistés** (perdus à la fermeture). | **Corrigé** (SharedPreferences, rechargés au démarrage) |
| N5 | Push reçu sans session active (après déconnexion) affiché quand même. | **Corrigé** (ignoré) ; `cancelAll()` à la déconnexion |
| N6 | Type `calendar_event` absent côté service ; `cycle` non déclaré côté Edge Function. | **Corrigé** (liste blanche de types, `cycle` sans corps) |
| N7 | Un seul canal Android pour tout ; le choix du son n'est pas appliqué aux canaux. | **À faire** (canaux par catégorie : messages, appels, jeux, calendrier) |
| N8 | Refus d'appel envoyé comme `missed_call` (« Appel manqué » trompeur). | **À faire** (type `call_declined`) |
| N9 | Pas de suppression de la notification d'appel si l'appelant raccroche avant réponse. | **À faire** (push `call_cancelled`) |
| N10 | Permission `POST_NOTIFICATIONS` demandée dans `MainActivity` mais sans écran d'explication ni lien vers réglages si refusée ; `SCHEDULE_EXACT_ALARM` sans gestion de refus. | **À faire** |
| N11 | Appel entrant : `fullScreenIntent` ok, mais pas de service de premier plan ni `USE_FULL_SCREEN_INTENT` (Android 14+ à vérifier). | **À tester** |
Edge Function : authentifie l'appelant, vérifie l'appartenance au couple, ne supprime un token que sur `UNREGISTERED`/404, TTL 30 s pour les appels : **correct**. Pas de redéploiement fait (à valider par vous : `supabase functions deploy send-fcm`).
Limite honnête : après « Forcer l'arrêt » ou sur certaines surcouches (Xiaomi, Huawei, Oppo), Android peut bloquer la livraison — non contournable.

## 3. Autres modules (lecture partielle — à confirmer au test)
- **Appels** : signalisation via table Supabase (`initiateCall`, `fetchActiveCall`, polling `checkForIncomingCall`). **Aucune dépendance WebRTC** → pas de flux audio/vidéo réel : l'appel est une **signalisation seule**. Mission lourde (WebRTC + STUN/TURN).
- **P2P** : `P2PSocketManager` (sockets) + `OfflineP2PLinkScreen` existent ; Wi-Fi Direct (`WifiP2p`) non trouvé → la découverte/connexion reste à valider. Sync de retour d'Internet à vérifier.
- **Jeux / Calendrier / Cycle** : vues présentes (`CoupleGamesView`, `CoupleCalendarView`, `FloCycleTrackerView`, rappels locaux via `CycleAndCalendarScheduler`). Persistance Supabase et RLS à vérifier sur la base réelle.
- **Stockage** : `chat_media` utilise `publicUrl` → fichiers de conversation lisibles par quiconque a l'URL. Le coffre devrait passer en bucket privé + URLs signées.
- **Sécurité Supabase (à vérifier sur le projet réel, non vérifiable ici)** : buckets publics, `search_path` mutable, fonctions `SECURITY DEFINER` exécutables par `anon`, `device_authorizations` sans politique. Aucune modification faite en production.
- **Paramètres** : `SettingsScreen` = une liste de boîtes de dialogue modales ; plusieurs préférences n'étaient pas persistées (N4). Réorganisation en catégories (compte / notifications / confidentialité / discussions / appels / stockage / apparence) à faire.
- Tests : 2 tests unitaires présents ; non exécutés.

## 4. Plan recommandé (ordre)
1. Compiler + exécuter tests dans Android Studio / CI (bloquant : jamais validé ici).
2. Notifications : N7–N11, puis test réel sur les 2 téléphones (app ouverte / fond / fermée / verrouillé / déconnexion / changement de compte).
3. Exporter le schéma Supabase (tables, RLS, buckets) dans `supabase/migrations`, puis durcir : buckets privés + URLs signées, `search_path`, `REVOKE EXECUTE` sur `anon`, politique `device_authorizations`.
4. Refonte des Paramètres (écran à sections, style WhatsApp) + persistance complète.
5. Refonte UI/UX (inspiration Pinterest : grille/cartes, navigation simple, suppression des boutons sans action).
6. WebRTC (appels réels).
7. P2P Wi-Fi Direct + sync.
8. Jeux, calendrier, cycle, coffre : vérification + RLS.
