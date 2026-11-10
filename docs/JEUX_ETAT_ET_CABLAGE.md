# Jeux de couple — état et câblage (Mission 06 F5)

> Branche : `mission-06-jeux` · Mis à jour : 2026-11-10

## 1. Ce qui est commité et prêt

| Élément | Fichier | État |
|---|---|---|
| Moteur Puissance 4 (règles, gravité, victoires 4 directions, coups interdits) | `util/ConnectFour.kt` | ✅ commité + relu |
| 10 tests unitaires du moteur | `test/.../ConnectFourTest.kt` | ✅ commités (à exécuter en CI) |
| Plateau jouable Compose (bulle de chat, sync Realtime) | `ui/components/ConnectFourBoard.kt` | ✅ commité |
| Entrée « Puissance 4 » dans le trombone (optionnelle, comme Images d'IA) | `ui/components/ChatAttachmentAndGamesBottomSheet.kt` | ✅ commitée — apparaît dès que ChatScreen fournit `onSendConnectFour` |

## 2. Câblage restant dans ChatScreen.kt (2 insertions, code fourni)

`ChatScreen.kt` fait 144 Ko : trop volumineux pour une réécriture via l'outil de commit en une passe.
Les deux modifications exactes (à appliquer dans Android Studio, ou par l'agent quand un éditeur
de gros fichiers sera disponible) :

### 2a. Dans l'appel à `ChatAttachmentAndGamesBottomSheet` (après `onSendTicTacToe = { ... },`)

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

### 2b. Dans le `when (message.type)` de la bulle (après la branche `"tictactoe" -> { ... }`)

```kotlin
"connect_four" -> {
    // Puissance 4 RÉEL à deux : même mécanisme que le morpion
    val gameScope = rememberCoroutineScope()
    val gameContext = LocalContext.current
    com.example.mikayala.ui.components.ConnectFourBoard(
        state = message.mediaUrl ?: "",
        isGameOwner = isSender,
        mySymbol = if (isSender) 'R' else 'J',
        onPlayColumn = { newState ->
            gameScope.launch {
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

### 2c. Rejouer (optionnel, après une victoire)

Le bouton « Rejouer » du morpion envoie un NOUVEAU message `type = "tictactoe"`. Équivalent :

```kotlin
repository.sendMessage(
    content = "Revanche au Puissance 4 ! 🔴🟡",
    type = "connect_four",
    mediaUrl = com.example.mikayala.util.ConnectFour.EMPTY
)
```

## 3. Contrat Supabase (aucune modification backend requise)

Le Puissance 4 réutilise **exactement** le mécanisme du morpion déjà en production :
- État = chaîne de 42 caractères dans `messages.media_url`
- Coup = `playGameMove(messageId, newState)` → UPDATE de `media_url`
- Propagation = Realtime sur la table `messages`

⚠️ Condition héritée du morpion (documentée dans `CHAT_ET_JEUX_ETAT.md`) : la politique RLS
UPDATE de `messages` doit autoriser **chaque membre du couple** à mettre à jour `media_url`.
Sinon le coup est refusé (« Non enregistré »). À faire vérifier par ChatGPT (requête 13 des
audits) — aucune migration exécutée par l'agent Android.

## 4. Améliorations apportées aux jeux existants

- **Feuille de jeux** : badges et couleurs d'entrée unifiés sur la palette vert+violet
  (`AccentViolet` au lieu du rose pour l'onglet Jeux et les badges).
- Le paramètre `onSendAiImage` était déjà neutralisé (aucune IA réelle) : conservé en
  optionnel non affiché.

## 5. Idées suivantes (priorisées, mécanisme identique = coût faible)

1. **Bataille navale** (7×7, « touché/coulé » synchronisé) — le plus captivant à deux, tours.
2. **Quiz de couple à score cumulé** (table dédiée pour le score, SQL à proposer à ChatGPT).
3. **Jeu de mémoire** (paires de photos du couple retournées tour à tour).
