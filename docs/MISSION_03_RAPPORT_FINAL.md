# MISSION 03 — RAPPORT FINAL (révisé)

> Branche : `mission-03-vocaux` · Révision : 2026-11-10 · Agent Android (Claude).
> ChatGPT reste responsable du côté Supabase ; rien n'a été exécuté sur le compte Supabase.

## 1. Problèmes initiaux signalés

- Notifications FCM non reçues (arrière-plan / app fermée / déconnexion / changement de compte).
- Actions de notification d'appel (OPEN_CALL / AUTO_ANSWER) douteuses.
- Messages vocaux : pause = arrêt définitif, reprise fictive ; expérience attendue = WhatsApp.

## 2. Constat de la révision (2026-11-10)

Le repli « append » initial pour Android 6 était **invalide** : `MediaRecorder.setOutputFile()`
sur un M4A existant n'ajoute JAMAIS à la fin du fichier — le nouvel enregistrement écrase le
conteneur depuis le début (corruption). Ce repli a été **supprimé** et remplacé par une
solution par segments.

## 3. Architecture finale des notes vocales

| Scénario | Implémentation | Fichiers |
|---|---|---|
| Android 7+ (API 24+, vos téléphones) | Pause/reprise **natives** `MediaRecorder.pause()/resume()` — fichier unique jamais fermé, aucune perte audio, chrono excluant le temps en pause | `VoiceNoteManager.kt` |
| Android 6 (API < 24) | **Segments** : pause finalise le segment courant (M4A lisible), reprise ouvre un NOUVEAU segment temporaire, l'arrêt **assemble** les segments en un M4A unique via `MediaExtractor` + `MediaMuxer` (décalages recalculés, garde 20 ms) | `VoiceNoteManager.kt` + `AudioSegmentsMerger.kt` (nouveau) |
| Anti-fuite micro (B4) | Instance active suivie statiquement ; `MainActivity.ON_STOP` coupe toute captation (micro rendu, fichiers temporaires supprimés) | `VoiceNoteManager.kt` + `MainActivity.kt` |
| Focus audio | Demandé au démarrage/reprise, abandonné à chaque pause/stop/annulation — pas de capture fantôme | `VoiceNoteManager.kt` |

Cycle de vie vérifié (relecture complète) : démarrage (permission + focus + RECORDING),
verrouillage (→LOCKED), pause (→PAUSED, chrono figé), reprise (→LOCKED, chrono relancé),
envoi (stop + fusion + IDLE, fichier non vide vérifié), annulation (tout supprimé, segments inclus).
Double envoi impossible : `stopAndGetResult()` repasse en IDLE et ne renvoie le fichier qu'une fois.

## 4. Tests

Automatisés (JVM) — **non exécutés dans cette session** (pas de SDK Android) :
- `VoiceSegmentsTest.kt` (nouveau, 3 tests) : plan de fusion (segments vides/inexistants écartés),
  décalages d'horodatages cumulés avec garde 20 ms, cas vide.
- `VoiceAndPushUnitTest.kt` (existant) : lecture exclusive, masquage token, sync FCM.

⚠️ `MediaRecorder`/`MediaMuxer` exigent un appareil : non couverts par les tests JVM,
à valider physiquement (V5–V7).

Physiques — **NON RÉALISÉS** (2 téléphones requis) :

| # | Test | Attendu |
|---|---|---|
| V5 | Vocal : maintenir, verrouiller, pause 5 s, reprise, arrêt | audio d'APRÈS la reprise présent dans le fichier envoyé (Android 7/10) |
| V5b | Android 6 : pause/reprise | M4A final lisible d'un bout à l'autre, audio des 2 segments présent |
| V6 | Lecture, pause, reprise | reprise à la position exacte ; un seul vocal à la fois |
| V7 | Vocal envoyé | le partenaire le reçoit et le lit |
| V8 | Quitter l'écran en pleine captation | micro rendu, aucun fichier fantôme en cache |
| V9 | Déconnexion puis message entrant | aucune notification affichée |
| V10 | Appel : Décrocher / Refuser depuis la notification | écran d'appel + AUTO_ANSWER / rejet + notification partenaire |

## 5. Modifications backend attendues (ChatGPT — non exécutées)

1. Déployer `send-fcm` du dépôt après comparaison (`supabase functions download send-fcm`).
2. La version déployée doit accepter `call_declined`, `call_cancelled`, `call_ended`, `calendar_event`, `cycle`.
3. RPC `register_device_push_token` (l'app garde le repli upsert — compatible).
4. Vérifier `hidePreview` déployé : le serveur ne doit plus masquer selon le réglage de l'expéditeur.

## 6. Statut de la branche

- ✅ Pause/reprise natives conservées (Android 7+) — conformes à la demande.
- ✅ Repli Android 6 reconstruit : segments + fusion propre (plus de corruption).
- ✅ Anti-fuite micro, focus audio, gestion d'erreurs, pas de double envoi.
- ✅ 3 tests JVM ajoutés ; tests existants conservés.
- ⏳ **NON VALIDÉE** : compilation + tests à lancer (CI/Android Studio) ; tests physiques V5–V10 à faire.
