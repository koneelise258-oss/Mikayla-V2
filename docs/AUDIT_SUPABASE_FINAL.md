# Mikayala V2 — Audit Supabase : mise en cohérence (Android ↔ base)

**Limite de cette session : aucun accès direct à la base `yrcnneyejqlkumjbetfh`.** Rien n'a été lu ni appliqué
en production par Claude. Tout ce qui touche la base est *préparé* ; la vérification se fait avec
`docs/AUDIT_SUPABASE_REQUETES.sql` (lecture seule), à exécuter par une personne ou un outil ayant l'accès.
La migration déjà appliquée « harden_mikayala_function_search_paths_and_trigger_privileges » n'est ni répétée ni défaite.

## Ce que l'application utilise réellement (relevé dans le code)
| Type | Éléments |
|---|---|
| Tables REST | messages, couples, profiles, partner_nicknames, vault_items, call_signals, device_push_tokens, user_activity |
| RPC | mark_messages_delivered, mark_messages_read, touch_user_activity (+ register_device_push_token, préparée) |
| Temps réel | table messages (insert/update/delete), canal de présence `presence:couple:<id>` |
| Storage | messages-media (privé, URL signées : photos, audio, vidéo, documents, coffre), avatars (privé, URL signées), chat_media (anciens documents, URL publiques) |
| Edge Function | send-fcm |
| **Non utilisés par l'APK** | menstrual_cycles (le cycle reste sur l'appareil), device_authorizations, vault_media (aucune écriture) |

## A. Corrigé et vérifié
| Élément | État réel |
|---|---|
| Code Android : envoi de fichiers en session utilisateur (JWT) au lieu de la clé anon, repli transitoire | ✅ dans la branche, **compilé** (CI) — pas testé sur appareil |
| Tests TypeScript du payload send-fcm (couple « paired », validation du compte de service, cycle neutre, recipientId) | ✅ 9/9 en CI |
| Aucune migration appliquée par Claude | — (aucun accès) |

## B. Corrigé dans le code, pas encore déployé
| Fichier | Contenu | État |
|---|---|---|
| `supabase/migrations_proposees/20261105090000_mikayala_securite_coherente.sql` | **Migration unique et cohérente**, idempotente : (A) `register_device_push_token` avec preuve de possession par secret d'installation ; (B) droits EXECUTE des 3 RPC de l'app (authenticated oui, anon non) ; (C) écritures Storage réservées aux membres du couple, lecture des avatars du partenaire, aucune visibilité de bucket modifiée | **préparée, NON appliquée** |
| `…/20261104090000_claim_device_push_token.sql` | marquée **REMPLACÉE — NE PAS APPLIQUER** (ne vérifiait pas la possession du jeton) | conservée pour l'historique |
| `…/20261104091000_storage_upload_authentifie.sql` | marquée REMPLACÉE (intégrée à la migration unique) | conservée |
| `app/…/services/DeviceInstallation.kt` (nouveau) | secret d'installation aléatoire (256 bits), stocké sur l'appareil | dans la branche |
| `app/…/data/SupabaseService.kt` | appelle `register_device_push_token` (repli sur l'ancien upsert tant que la fonction n'existe pas) ; envoi de documents limité au bucket `chat_media` (l'ancien essai en cascade pouvait écrire dans `avatars`) | dans la branche |
| `app/…/services/MyFirebaseMessagingService.kt` | transmet le secret d'installation | dans la branche |
| `supabase/functions/send-fcm/{index.ts,payload.ts}` | couple `status = 'paired'` obligatoire, JWT vérifié, validation du compte de service Firebase (projet `gen-lang-client-0988385912`) sans exposer la clé, erreurs neutres, liste blanche de types (dont call_declined / call_cancelled / call_ended), cycle sans aucune donnée, TTL 30 s pour les appels, `recipientId` | **non déployée** |
| `docs/AUDIT_SUPABASE_REQUETES.sql` | requêtes 1–15 en lecture seule (fonctions, droits, triggers, jeton dans un trigger, webhooks, Vault, politiques messages, temps réel, Storage) | prêt |

### F. send-fcm — comparaison avec la version déployée
Impossible depuis cette session. Procédure : requête 15 (`supabase functions download send-fcm`) puis comparaison.
Points à vérifier dans la version déployée : `verify_jwt = true` ; contrôle `status = 'paired'` ; aucune donnée de cycle transmise ;
pas de renvoi de message d'erreur interne. Événements d'appel : l'APK envoie `call`, `call_declined`, `call_cancelled` ;
la version déployée doit les accepter, sinon l'annulation d'un appel n'arrête pas la sonnerie.

## C. Bloqué ou nécessitant une validation
1. **Jeton privilégié dans un trigger (§E)** — non visible d'ici. Requêtes 10–12 : localiser sans afficher la valeur, lire son rôle et sa date d'expiration.
2. **Partie C de la migration (Storage)** — à appliquer seulement **après** installation du nouvel APK sur les deux téléphones (l'ancien envoyait avec la clé anon). Puis supprimer manuellement les anciennes politiques d'écriture ouvertes à anon (requête 5), après les avoir notées.
3. **Les 8 fonctions SECURITY DEFINER accessibles à anon** — non modifiées à l'aveugle : vérifier leur code (requêtes 3 et 9) ; celles qui ne sont ni appelées par l'APK ni par un trigger peuvent perdre EXECUTE pour anon.
4. **Jeux du chat** — chaque membre doit pouvoir mettre à jour `media_url` des messages du couple (requête 13). Si la politique UPDATE est limitée à l'expéditeur, le partenaire ne peut pas jouer (« Non enregistré ») ; proposition : politique UPDATE pour les membres du couple + `GRANT UPDATE (media_url, status, delivered_at, read_at)` par colonne, à écrire une fois la politique réelle connue.
5. **Clé de l'APK** — le secret GitHub `SUPABASE_ANON_KEY` contient une clé *publishable* refusée par l'application : utiliser la clé legacy `anon`.

### E. Plan de rotation du jeton privilégié (à exécuter seulement après identification)
1. Requêtes 10–12 : quelle fonction/trigger/webhook l'utilise, rôle (`service_role` ?) et expiration.
2. Rechercher les autres usages : secrets des Edge Functions (`supabase secrets list`, noms seulement), webhooks, tâches planifiées, secrets GitHub (aucun ne doit contenir de clé service).
3. Remplacer l'usage en base par une référence au **Vault** (`vault.decrypted_secrets` lu par la fonction) ou par l'appel d'une Edge Function avec la clé serveur gérée par Supabase — sans changer le comportement.
4. Vérifier que le trigger fonctionne avec la nouvelle référence (test sur un message de test).
5. Seulement ensuite : générer une nouvelle clé (Project Settings → API Keys), mettre à jour le Vault et les secrets des Edge Functions, puis révoquer l'ancienne.
6. Contrôle : requête 10 → plus aucun jeton en clair ; envoi de message → notification reçue.
Aucune de ces étapes n'a été réalisée.

## D. Reste à faire (ordre recommandé)
1. Exécuter `docs/AUDIT_SUPABASE_REQUETES.sql` (lecture seule) et transmettre les résultats.
2. Corriger le secret `SUPABASE_ANON_KEY` (clé legacy anon) → recompiler → installer l'APK sur les deux téléphones.
3. Appliquer les parties A et B de la migration 20261105090000 ; tests A1–A5, B1.
4. Déployer send-fcm (après comparaison) ; tester notifications message, appel, annulation, plusieurs appareils.
5. Vérifier/ajuster la politique UPDATE de `messages` pour les jeux ; tester un morpion à deux.
6. Appliquer la partie C (Storage) puis retirer les anciennes politiques anon ; tests C1–C3.
7. Rotation du jeton privilégié (§E).
8. `menstrual_cycles` et `device_authorizations` : non utilisées par l'APK ; si elles contiennent des données, garder RLS sans politique (refus par défaut) ou politiques « propriétaire uniquement » + exclusion des comptes anonymes.
Tests physiques encore nécessaires : connexion/reconnexion, messages, médias, coffre, notifications sur plusieurs appareils, appels (signalisation), synchronisation, accès croisé des deux membres, refus pour un tiers.
