# Jeux de couple — état et câblage (Mission 06 F5) — RÉVISÉ

> Branche : `mission-06-jeux` · Révision : 2026-11-10

## 0. Corrections de cette révision

| Affirmation antérieure | Vérité | Correction |
|---|---|---|
| « 10 tests unitaires » | `ConnectFourTest.kt` contient **9 méthodes @Test** | Corrigé. Un 10e test (victoire diagonale descendante) reste à écrire — couverture des directions : 3/4 (H, V, diagonale montante) |
| « Synchronisation par media_url = jeu synchronisé » | Vrai mais insuffisant sans analyse | Analyse complète en §3 |

## 1. Ce qui est commité

| Élément | Fichier | État |
|---|---|---|
| Moteur Puissance 4 (règles, gravité, victoires, coups interdits) | `util/ConnectFour.kt` | ✅ commité + relu |
| 9 tests unitaires | `test/.../ConnectFourTest.kt` | ✅ commités — non exécutés (pas de SDK ici) |
| Plateau jouable Compose (bulle) | `ui/components/ConnectFourBoard.kt` | ✅ commité |
| Entrée menu optionnelle | `ChatAttachmentAndGamesBottomSheet.kt` | ✅ — visible dès que ChatScreen fournit `onSendConnectFour` |

## 2. Câblage restant dans ChatScreen.kt (144 Ko, hors limite ~30 Ko des outils)

### 2a. Lancement (après onSendTicTacToe)
```kotlin
onSendConnectFour = {
    showAttachmentSheet = false
    repository.sendMessage(
        content = "Partie de Puissance 4 ! 🔴🟡",
        type = "connect_four",
        mediaUrl = com.example.mikayala.util.ConnectFour.EMPTY
    )
    Toast.makeText(context, "Puissance 4 lancé 🎯", Toast.LENGTH_SHORT).show()
},
```

### 2b. Affichage (branche "connect_four" du when(message.type), après "tictactoe")
```kotlin
"connect_four" -> {
    val gameScope = rememberCoroutineScope()
    val gameContext = LocalContext.current
    com.example.mikayala.ui.components.ConnectFourBoard(
        state = message.mediaUrl ?: "",
        isGameOwner = isSender,
        mySymbol = if (isSender) 'R' else 'J',
        onPlayColumn = { newState ->
            gameScope.launch {
                // Garde anti-collision (voir §3.2) : vérifier l'état frais avant d'écrire
                val fresh = repository.allMessages.value.firstOrNull { it.id == message.id }
                val freshState = fresh?.mediaUrl ?: message.mediaUrl ?: ""
                if (freshState != (message.mediaUrl ?: "")) return@launch // le partenaire vient de jouer
                val ok = repository.playGameMove(message.id, newState, "Puissance 4")
                if (!ok) android.widget.Toast.makeText(
                    gameContext, "Coup non enregistré : vérifiez la connexion",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    )
}
```

### 2c. Rejouer (nouveau message, comme le morpion)
```kotlin
repository.sendMessage(
    content = "Revanche au Puissance 4 ! 🔴🟡",
    type = "connect_four",
    mediaUrl = com.example.mikayala.util.ConnectFour.EMPTY
)
```

## 3. ANALYSE DE SYNCHRONISATION (vérifiée dans le code)

Mécanisme réel vérifié dans `MikayalaRepository.playGameMove` :

1. Mise à jour **optimiste** locale (`_messages.update`) ;
2. `supabaseService.updateMessageFields(messageId, {media_url})` → UPDATE Supabase ;
   si refus (réseau/RLS) → **rollback local** + retour false (« Coup non enregistré ») ;
3. FCM `game_turn` au partenaire (« À toi de jouer ») ;
4. Realtime sur `messages` propage l'UPDATE au partenaire ;
5. L'état vit dans `media_url` → reconstruit au rechargement (sync + cache local).

### 3.1 Fiabilité — vérifications positives
- ✅ Rollback si le serveur refuse → pas d'état fantôme durable.
- ✅ Rechargement après redémarrage : toujours reconstruit.
- ✅ Règles moteur locales : coups interdits refusés (pas son tour, colonne pleine, partie finie).

### 3.2 Faille identifiée — coups quasi simultanés
Deux UPDATE simultanés sur `media_url` = **dernier écrit gagne** (pas de versionnage).
Scénario : A et B jouent en même temps après une notification en retard ; le coup de A
écrase celui de B sans erreur pour personne. Sévérité : réelle mais rare (tours alternés,
fenêtre courte).

**Mitigation client** (intégrée au câblage 2b ci-dessus) : avant d'écrire, revérifier
l'état frais du message ; s'il a changé, refuser le coup — l'UI se rafraîchit via Realtime.

**Solution complète (serveur) — PROPOSITION POUR CHATGPT, NON EXÉCUTÉE :**
```sql
-- Versionnage atomique des coups de jeu (proposition, à valider par le propriétaire)
-- ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS game_version int NOT NULL DEFAULT 0;
-- UPDATE ... SET media_url = :new, game_version = game_version + 1
--   WHERE id = :id AND game_version = :expected;
```

### 3.3 RLS — condition documentée (hors périmètre d'exécution)
La politique UPDATE de `messages` doit autoriser chaque membre du couple à mettre à jour
`media_url` (sinon « Non enregistré »). Requête 13 des audits — à faire vérifier par ChatGPT.

## 4. Améliorations des jeux existants
- Feuille de jeux : badges/onglet unifiés sur la palette (AccentViolet).
- `onSendAiImage` : optionnel non affiché (aucune IA réelle — pas de faux bouton).

## 5. Idées suivantes
1. Bataille navale (7×7, tours). 2. Quiz à score cumulé (contrat SQL à proposer). 3. Jeu de mémoire.

## 6. Statut de la branche
- ✅ Moteur + plateau + entrée menu (optionnelle) commités et relus.
- ✅ Analyse de synchronisation réelle effectuée (playGameMove → updateMessageFields → Realtime + FCM).
- ⚠️ Jeu NON câblé dans ChatScreen (2 insertions fournies) — l'entrée n'apparaît pas tant que le câblage n'est pas fait (paramètre optionnel = pas de faux bouton).
- ⏳ 10e test (diagonale descendante) à ajouter ; tests à exécuter en CI.
