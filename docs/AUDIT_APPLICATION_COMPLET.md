# AUDIT APPLICATION COMPLET — Mikayala V2

> Branche : `mission-04-audit` · Date : 2026-11-10
> Méthode : lecture du code via GitHub (aucune compilation possible dans cette session — les conclusions « compile/OK » restent à confirmer par la CI).
> Légende : ✅ vérifié dans le code · ⚠️ partiel/à confirmer · ❌ manquant/cassé

## 1. Cartographie générale

| Élément | Valeur |
|---|---|
| Type | Android natif, Kotlin + Jetpack Compose, module unique `:app` |
| applicationId | `com.mikayala.app` (namespace Gradle encore `com.example.mikayala` — cosmétique) |
| SDK | minSdk 23 · targetSdk 36 · compileSdk 36 · Java 17 |
| Version | versionCode 2 · versionName « 1.1 » |
| Backend | Supabase (Auth, Postgres, Realtime, Storage) + FCM via Edge Function `send-fcm` |
| Média | Coil (images), Media3/ExoPlayer (vidéo), MediaPlayer (vocaux) |
| Caméra/QR | CameraX + ML Kit + ZXing |
| Signature | clé hors dépôt, décodée en CI depuis les secrets (fallback debug sinon) ⚠️ |

## 2. Organisation du code

```
com.example.mikayala
├── MainActivity.kt (19 Ko)        — navigation globale + intents d'appel
├── data/  (SupabaseService 95 Ko, MikayalaRepository 174 Ko, modèle)
├── services/ (FCM, appels, boot, cycle/calendrier)
├── theme/ (palette centralisée vert+violet, alias historiques)
├── ui/
│   ├── screens/  (ChatScreen 144 Ko, SettingsScreen 91 Ko, Onboarding 60 Ko…)
│   ├── components/ (lecteur vocal, recorder, feuilles, QR…)
│   └── media/ (photo, vidéo)
├── util/  (NotificationHelper 28 Ko, P2PSocketManager 20 Ko, jeux…)
└── utils/ (VoiceNoteManager, VoiceRecorderManager déprécié)
```

## 3. Constats techniques (Mission 04)

### 3.1 Architecture
| # | Constat | Gravité | Action |
|---|---|---|---|
| A1 | **Aucun ViewModel** alors que la dépendance est là : tout l'état vit dans `MikayalaRepository` (singleton 174 Ko) et les écrans | ⚠️ moyen | Migrer écran par écran (Strangler Fig), pas de big-bang |
| A2 | **Fichiers géants** : MikayalaRepository 174 Ko, ChatScreen 144 Ko, SupabaseService 95 Ko, SettingsScreen 91 Ko, Onboarding 60 Ko | ⚠️ moyen | Découpage progressif par responsabilité |
| A3 | **Deux packages `util/` et `utils/`** — confusion + doublon potentiel | faible | Fusionner à l'occasion d'un chantier les touchant |
| A4 | `VoiceRecorderManager` `@Deprecated` encore présent (code mort) | faible | Supprimer après vérif qu'aucun écran ne l'utilise |
| A5 | `SupabaseService` mêle SDK et appels REST bruts | moyen | Isoler les appels REST dans un client dédié (plus tard) |
| A6 | `applicationId` ≠ `namespace` | faible | Renommer le namespace en `com.mikayala.app` (migration mécanique) |

### 3.2 Cycle de vie, coroutines, états
| # | Constat | Gravité |
|---|---|---|
| B1 | ✅ `MainActivity` gère `onNewIntent` (intents d'appel), lock timeout réel (ON_STOP/ON_START + grâce 5 s), `FLAG_SECURE` piloté par les réglages, écran d'erreur de démarrage | — |
| B2 | ✅ `FcmTokenSyncEngine` : mutex, jetons en attente, désinscriptions différées — pas de course détectée | — |
| B3 | ✅ `CallEventGate` : dédup thread-safe (ConcurrentHashMap), purge manuelle compatible Android 6 | — |
| B4 | ⚠️ `VoiceNoteManager` utilise `CoroutineScope(Dispatchers.Main + SupervisorJob())` sans lien au cycle de vie — `release()` doit être appelé par l'écran (à vérifier dans `VoiceNoteRecorder`) | moyen |
| B5 | ⚠️ `SupabaseService.awaitSessionReady(5s)` en `runBlocking` dans `onMessageReceived` — acceptable (fil BG FCM) mais bloquant 5 s max | faible |

### 3.3 Réseau, erreurs, synchronisation
| # | Constat | Gravité |
|---|---|---|
| C1 | ✅ Accusés délivré/lus (RPC + repli), présence, resync au retour au premier plan (`onAppForeground`) | — |
| C2 | ⚠️ Repli silencieux : si `SUPABASE_URL`/`SUPABASE_ANON_KEY` absents au build, **valeurs factices compilées dans l'APK** → erreurs runtime obscures. Recommandation : faire échouer le build release si absent | moyen |
| C3 | ⚠️ Realtime limité à la table `messages` + canal présence : les actions (réactions, édition) dépendent d'updates Realtime — à tester sur réseau instable | à tester |
| C4 | ⚠️ Polling `checkForIncomingCall` documenté dans les audits précédents : consommation batterie potentielle | moyen |

### 3.4 Permissions & Android
| # | Constat | Gravité |
|---|---|---|
| D1 | ✅ `POST_NOTIFICATIONS` demandée (API 33+), canaux créés au démarrage, son/vibration par notification pour Android < 8 | — |
| D2 | ⚠️ Pas d'écran pédagogique si permission refusée (constat N10 des audits précédents, toujours vrai dans le code lu) | moyen |
| D3 | ⚠️ `SCHEDULE_EXACT_ALARM` sans gestion de refus documentée | moyen |
| D4 | ⚠️ `USE_FULL_SCREEN_INTENT` absent du Manifest (constat N11) — Android 14+ peut refuser l'affichage plein écran des appels | à tester |

### 3.5 Tests présents
| Fichier | Couverture |
|---|---|
| `VoiceAndPushUnitTest.kt` (24 Ko) | ✅ Coordinateur de lecture exclusive, masquage de token, ignore self-notif, résolution camouflage, sync engine (upsert, renouvellement, déconnexion, multi-comptes), dates d'appels |
| `NotificationEventsTest.kt` (14 Ko) | Événements de notification |
| `MessageSyncMergeTest.kt` | Fusion de synchronisation des messages |
| `MediaAndTypesTest.kt` | Types de médias |
| `EmojiCatalogTest.kt`, `WheelGameTest.kt` | Emoji, jeu de la roue |
| `androidTest/ui` | Tests instrumentés UI (non exécutés ici) |

**Résultat :** les tests unitaires couvrent bien le périmètre Mission 03. Ils n'ont **pas été exécutés dans cette session** (pas de SDK) ; la CI les exécutait d'après les documents précédents — à relancer pour confirmer.

### 3.6 OPEN_CALL / AUTO_ANSWER (fin de vérification C2)
✅ `MainActivity` transmet `pendingIntentExtras` à `MainDashboardScreen` — le traitement de l'ouverture d'appel vit dans le dashboard (37 Ko, non relu intégralement cette session). **Point restant : tracer le chemin extras → écran d'appel pour valider C2 à 100 %.**

## 4. Bugs corrigés dans le cadre Mission 03/04
- ✅ Pause/reprise réelles de l'enregistrement vocal (`VoiceNoteManager`, branche `mission-03-vocaux`).

## 5. Plan de stabilisation recommandé (ordre)
1. **C2** : tracer `pendingIntentExtras` → écran d'appel dans `MainDashboardScreen` ; ajouter `USE_FULL_SCREEN_INTENT` + écran pédagogique permission refusée.
2. **C2-build** : échec du build release si `SUPABASE_URL`/`SUPABASE_ANON_KEY` absents (supprimer le repli factice).
3. **B4** : vérifier l'appel à `VoiceNoteManager.release()` dans `VoiceNoteRecorder` (fuite potentielle).
4. **A4/A3** : supprimer `VoiceRecorderManager` déprécié ; fusionner `util/`+`utils/`.
5. **A1/A2** : introduire des ViewModels écran par écran, en commençant par les écrans moyens (CallsScreen, CoupleSpaceScreen) avant les géants.
6. **C4** : remplacer le polling d'appels par Realtime sur `call_signals` si le backend le permet (à coordonner avec ChatGPT).

## 6. Ce qui reste à valider sur appareils
- Compilation + tests unitaires (CI ou Android Studio).
- V1–V10 du `MISSION_03_RAPPORT_FINAL.md`.
- Verrouillage automatique, FLAG_SECURE, écran de calculatrice leurre, QR pairing.
- Réseau instable : reconnexion Realtime, reprise des envois en attente.
