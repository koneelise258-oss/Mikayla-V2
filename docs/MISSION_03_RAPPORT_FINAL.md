# MISSION 03 — RAPPORT FINAL

> Branche : `mission-03-vocaux` · Date : 2026-11-10
> Rédigé par l'agent Android (Claude). ChatGPT reste responsable du côté Supabase ; rien n'a été exécuté sur le compte Supabase.

## 1. Problèmes initiaux signalés

- Notifications FCM non reçues (arrière-plan / app fermée / déconnexion / changement de compte).
- Actions de notification d'appel (OPEN_CALL / AUTO_ANSWER) douteuses.
- Messages vocaux : pause = arrêt définitif de l'enregistrement, reprise fictive ; expérience attendue = WhatsApp.

## 2. Ce qui a été examiné

| Fichier | Rôle | Verdict |
|---|---|---|
| `services/MyFirebaseMessagingService.kt` | Réception FCM | ✅ attend 5 s la session au démarrage à froid (app fermée) ; ignore l'expéditeur = moi ; dédup par événement ; respecte `recipientId`, catégories et mode de confidentialité ; accusé « délivré » envoyé |
| `services/FcmTokenSyncEngine.kt` | Sync tokens | ✅ jetons en attente, désinscriptions différées, multi-comptes, mutex anti-course |
| `services/DeviceInstallation.kt` | Preuve de possession | ✅ secret 256 bits local, transmis à `register_device_push_token` (RPC NON déployée — voir §7) |
| `services/CallActionReceiver.kt` | Décrocher/Refuser | ✅ refuse → `declineIncomingCall` + notif partenaire ; décrocher → MainActivity avec `OPEN_CALL`+`AUTO_ANSWER` |
| `services/CallEventGate.kt` | Anti-doublon appels | ✅ clé type+id ; appel terminé mémorisé ; `call` en retard ignoré |
| `ui/components/VoicePlaybackCoordinator.kt` | Lecture exclusive | ✅ un seul média actif dans l'app |
| `ui/components/VoicePlayerWaveform.kt` | Lecteur | ✅ pause garde le MediaPlayer (reprise à la position exacte), seek, vitesse 1x/1.5x/2x, libération rigoureuse |
| `utils/VoiceNoteManager.kt` | Enregistreur | ❌ **BUG C3** (corrigé, voir §3) |

## 3. Bug C3 corrigé — pause/reprise de l'enregistrement

**Avant :** `pauseRecording()` faisait `stop()+release()` du MediaRecorder (fichier finalisé, plus aucune écriture possible) et `resumeRecording()` ne changeait qu'un flag d'état. Tout ce qui était dit après la « reprise » était perdu.

**Correction (commit `b8bd1a7`) :**
- Android 7+ (API 24+, vos téléphones Android 7 et 10) : vraie pause via `MediaRecorder.pause()` / `resume()` — le fichier reste ouvert, aucune perte audio, le chrono n'exclut pas le temps en pause.
- Android ≤ 6 : repli documenté — finalisation du fichier puis nouvelle session « append » dans le même fichier.
- `stopAndGetResult()` réveille le recorder en pause native avant le stop final (header MP4 correct).
- Reprise redemande le focus audio ; le polling de la waveform redémarre.

## 4. Tests exécutés

- **Tests automatisés :** aucun exécuté dans cette session (pas de SDK Android disponible). Le code est vérifié par relecture complète, pas compilé ici. Les tests unitaires existants (payload send-fcm : 9/9 en CI d'après `AUDIT_SUPABASE_FINAL.md`) restent valables côté Edge Function.
- **Tests physiques : NON FAITS.** La Mission 03 n'est donc **PAS validée** — voir §6.

## 5. Modifications backend attendues (à transmettre à ChatGPT — non exécutées)

1. Déployer `supabase/functions/send-fcm` (index.ts + payload.ts du dépôt) après comparaison avec la version déployée. Le dépôt GitHub **peut différer** de la version déployée ; à comparer via `supabase functions download send-fcm`.
2. La fonction déployée doit accepter `call_declined`, `call_cancelled`, `call_ended`, `calendar_event`, `cycle` (sinon annulation d'appel = sonnerie qui ne s'arrête jamais).
3. Appliquer (après validation) `supabase/migrations_proposees/20261105090000_mikayala_securite_coherente.sql` pour la RPC `register_device_push_token` (l'app a déjà le repli upsert tant que la fonction n'existe pas — compatible).
4. `hidePreview` : la version du dépôt accepte encore le paramètre de l'expéditeur et masque le texte côté serveur — à faire vérifier par ChatGPT dans la version déployée ; le principe voulu est que le DESTINATAIRE décide de l'affichage (déjà le cas dans l'app Android).

## 6. Vérifications physiques restantes (2 téléphones Android réels)

| # | Test | Attendu |
|---|---|---|
| V1 | Message texte, app au 1er plan / fond / fermée | notification correcte, pas de doublon, pas d'auto-notification |
| V2 | Appel entrant : Décrocher depuis la notification | ouverture écran d'appel + AUTO_ANSWER |
| V3 | Appel entrant : Refuser | sonnerie stoppée chez le destinataire + notification chez l'appelant |
| V4 | Raccrocher avant décrochage | `call_cancelled` retire la sonnerie du partenaire |
| V5 | Vocal : maintenir, verrouiller, pause 5 s, reprise, arrêt | l'audio enregistré APRÈS la reprise est présent dans le fichier envoyé |
| V6 | Vocal : lecture, pause, reprise | reprise à la position exacte ; un seul vocal à la fois |
| V7 | Vocal envoyé | le partenaire le reçoit et le lit |
| V8 | Déconnexion puis message entrant | aucune notification affichée |
| V9 | Changement de compte sur le même téléphone | pas de notification de l'ancien compte |
| V10 | Android 14+ : permission notifications refusée puis accordée | lien vers les réglages fonctionnel |

## 7. État

- ✅ Code C1 (FCM) et C2 (appels) : conforme, rien à corriger côté Android dans cette passe.
- ✅ Code C3 (vocaux) : corrigé sur la branche `mission-03-vocaux`.
- ⏳ Validation finale : bloque sur les tests physiques V1–V10 et la décision ChatGPT/propriétaire sur §5.
