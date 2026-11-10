# AUDIT APPLICATION COMPLET — Mikayala V2 (révisé)

> Branche : `mission-04-audit` · Révision : 2026-11-10
> Méthode : lecture du code via GitHub (aucune compilation possible dans cette session — les conclusions « compile/OK » restent à confirmer par la CI).
> Légende : ✅ vérifié dans le code · ⚠️ partiel/à confirmer · ❌ manquant/cassé

## 0. CORRECTIONS DE CE RAPPORT (révision 2026-11-10)

| Affirmation antérieure | Vérité vérifiée dans le code | Correction |
|---|---|---|
| « `USE_FULL_SCREEN_INTENT` absent du Manifest » | **FAUX** — la permission EST présente (`<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />`) | Le risque Android 14+ n'est donc pas l'absence de la permission mais le fait que **Android 14 peut la refuser par défaut** pour les apps non vérifiées (Play Store) : pour un APK side-loadé (notre cas), elle reste accordée. Point restant : tester visuellement l'appel entrant plein écran sur Android 14+ |
| « Le garde-fou Release repose sur taskNames » | Vrai à l'origine, **corrigé** | Remplacé par `androidComponents.onVariants` : toute tâche d'une variante release (`build`, `bundle`, `install`, `compileReleaseKotlin`…) échoue si les secrets manquent |
| « Signature : seulement le chemin du keystore vérifié » | Vrai à l'origine, **corrigé** | Les 4 secrets de signature sont maintenant vérifiés (chemin, mot de passe store, alias, mot de passe clé) ; sinon repli explicite clé debug documenté |
| « 10 tests ConnectFour » (doc jeux) | Le fichier en contient **9** | Corrigé dans `docs/JEUX_ETAT_ET_CABLAGE.md` (branche mission-06-jeux) |

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
| Signature | clé hors dépôt, décodée en CI depuis les secrets (fallback debug sinon, désormais explicite) |

## 2. Audit du Manifeste Android (revu intégralement, 2026-11-10)

### 2.1 Permissions — toutes justifiées par une fonctionnalité réelle
| Permission | Fonctionnalité qui l'utilise |
|---|---|
| INTERNET, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE | Supabase, sync, P2P |
| CAMERA (+ `uses-feature required=false` ✅) | Photo/vidéo, QR pairing, appels vidéo (UI) |
| RECORD_AUDIO, MODIFY_AUDIO_SETTINGS | Notes vocales, appels |
| READ_EXTERNAL_STORAGE (≤32), WRITE_EXTERNAL_STORAGE (≤29) | Anciennes versions Android |
| READ_MEDIA_IMAGES/VIDEO/AUDIO | Galerie Android 13+ |
| USE_BIOMETRIC, USE_FINGERPRINT | Verrou biométrique du coffre |
| VIBRATE, POST_NOTIFICATIONS | Notifications, haptique |
| **USE_FULL_SCREEN_INTENT** | Appels entrants plein écran (présente — l'affirmation contraire de l'audit précédent était fausse) |
| SCHEDULE_EXACT_ALARM | Rappels calendrier/cycle |
| RECEIVE_BOOT_COMPLETED | Reprogrammer les rappels après redémarrage |
| ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, BLUETOOTH*, ACCESS_FINE/COARSE_LOCATION | Mode P2P hors ligne (Wi-Fi Direct/BLE ; la localisation est requise par Wi-Fi Direct pour la découverte) |

**Aucune permission ne semble fantôme.** Point de vigilance restant : Android 12+ exige `BLUETOOTH_SCAN` avec `neverForLocation` si le BLE n'est pas utilisé pour la localisation — à vérifier au chantier P2P (Mission 06 F7).

### 2.2 Exportation des composants — conforme
- `MainActivity` : `exported=true` (obligatoire, point d'entrée lanceur) via activity-aliases (1 activé, 8 désactivés = choix d'icône, `AppIconManager`).
- Tous les services/receivers : `exported=false` sauf `BootReceiver` (`exported=true` obligatoire pour BOOT_COMPLETED, protégé par le système).
- FCM : auto-init activé, canal par défaut `mikayala_general_notifs` ✅.
- Emoji : police embarquée via `MikayalaApplication` (initializer automatique retiré — cohérent hors ligne) ✅.

### 2.3 Sauvegardes et sécurité
- `allowBackup="true"` + `backup_rules` / `dataExtractionRules` présents — **à lire en détail** : une sauvegarde des préférences contenant des données sensibles (PIN hash, secret d'installation FCM) serait un risque. ⚠️ Point ouvert.

## 3. Organisation du code

```
com.example.mikayala
├── MainActivity.kt (19 Ko)        — navigation globale + intents d'appel
├── data/  (SupabaseService 95 Ko, MikayalaRepository 174 Ko, modèle)
├── services/ (FCM, appels, boot, cycle/calendrier)
├── theme/ (palette sémantique SoftLight/SoftDark, alias historiques)
├── ui/
│   ├── screens/  (ChatScreen 144 Ko, SettingsScreen 91 Ko, Onboarding 60 Ko…)
│   ├── components/ (lecteur vocal, recorder, feuilles, QR, jeux…)
│   └── media/ (photo, vidéo)
├── util/  (NotificationHelper 28 Ko, P2PSocketManager 20 Ko, jeux…)
└── utils/ (VoiceNoteManager, AudioSegmentsMerger, VoiceRecorderManager déprécié)
```

## 4. Constats techniques

### 4.1 Architecture
| # | Constat | Gravité |
|---|---|---|
| A1 | Aucun ViewModel (dépendance présente) : tout l'état vit dans `MikayalaRepository` singleton | ⚠️ moyen — migration écran par écran |
| A2 | Fichiers géants : Repository 174 Ko, ChatScreen 144 Ko, SupabaseService 95 Ko, Settings 91 Ko | ⚠️ moyen — découpage progressif |
| A3 | Deux packages `util/` et `utils/` en doublon | faible |
| A4 | `VoiceRecorderManager` `@Deprecated` encore présent | faible — supprimer après vérif d'usage |
| A6 | `applicationId` ≠ `namespace` | faible |

### 4.2 Cycle de vie, coroutines, états
| # | Constat | Gravité |
|---|---|---|
| B1 | ✅ MainActivity : onNewIntent, lock timeout réel, FLAG_SECURE piloté par réglages, écran d'erreur | — |
| B2 | ✅ FcmTokenSyncEngine : mutex, jetons en attente, désinscriptions différées | — |
| B3 | ✅ CallEventGate : dédup thread-safe | — |
| B4 | ✅ **Corrigé + révisé** : anti-fuite micro centralisé (`cancelAnyActiveRecording` appelé par ON_STOP) + segments vocaux fiables (mission-03-vocaux) | — |
| B5 | ⚠️ `awaitSessionReady(5s)` en runBlocking dans onMessageReceived (acceptable, fil BG) | faible |

### 4.3 Réseau et build
| # | Constat | Gravité |
|---|---|---|
| C1 | ✅ Accusés, présence, resync au retour au premier plan | — |
| C2-build | ✅ **Corrigé + révisé** : garde-fou `androidComponents.onVariants` — toute tâche release échoue sans secrets, quelle que soit la commande | — |
| C3 | ⚠️ Realtime limité à `messages` + présence — actions à tester sur réseau instable | à tester |
| C4 | ⚠️ Polling `checkForIncomingCall` — consommation batterie | moyen |

### 4.4 Permissions & Android
| # | Constat | Gravité |
|---|---|---|
| D1 | ✅ POST_NOTIFICATIONS demandée, canaux créés, son/vibration par notification (Android < 8) | — |
| D2 | ⚠️ Pas d'écran pédagogique si permission refusée | moyen |
| D3 | ⚠️ SCHEDULE_EXACT_ALARM sans gestion de refus | moyen |
| D4 | **Corrigé (rapport)** : USE_FULL_SCREEN_INTENT EST présente ; reste à tester le plein écran sur Android 14+ | à tester |
| D5 | ⚠️ BLUETOOTH_SCAN sans `neverForLocation` (Android 12+) — chantier P2P | moyen |

## 5. Tests présents
| Fichier | Couverture |
|---|---|
| `VoiceAndPushUnitTest.kt` | Lecture exclusive, masquage token, self-notif, sync FCM |
| `VoiceSegmentsTest.kt` (branche mission-03) | Plan de fusion segments, décalages horodatages |
| `NotificationEventsTest.kt` | Événements de notification |
| `MessageSyncMergeTest.kt` | Fusion de synchronisation |
| `MediaAndTypesTest.kt` | Types de médias |
| `EmojiCatalogTest.kt`, `WheelGameTest.kt` | Emoji, roue |
| `ConnectFourTest.kt` (branche mission-06) | **9 tests** : tours, gravité, victoires H/V/diagonale, colonne pleine, partie finie, ligne gagnante, normalisation |

**Résultat : aucun test exécuté dans cette session (pas de SDK).** À lancer via CI/Android Studio.

## 6. Plan de stabilisation (ordre)
1. D2 : écran pédagogique permission notifications refusée.
2. D5 : `BLUETOOTH_SCAN neverForLocation` (chantier P2P).
3. C4 : remplacer le polling d'appels par Realtime sur `call_signals` (à coordonner avec ChatGPT).
4. A4/A3 : supprimer `VoiceRecorderManager` déprécié ; fusionner util/utils.
5. A1/A2 : ViewModels écran par écran (Strangler Fig).
6. 2.3 : relecture des règles de sauvegarde (PIN hash, secret d'installation).

## 7. Ce qui reste à valider sur appareils
- Compilation + tests (CI ou Android Studio).
- Appels entrants plein écran sur Android 14+ (D4 révisé).
- Les V5–V10 de `MISSION_03_RAPPORT_FINAL.md` (branche mission-03-vocaux).
- Réseau instable : reconnexion Realtime, reprise des envois.
