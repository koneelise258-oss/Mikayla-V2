package com.example.mikayala.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.model.MessageEntity
import com.example.mikayala.theme.MikayalaColors
import com.example.mikayala.ui.effects.LoveEffects
import com.example.mikayala.util.ChatGames
import com.example.mikayala.util.Connect4

/** Couleurs des jeux : violet (celui qui lance) et vert (le partenaire). */
val GameViolet = Color(0xFF8B5CF6)
val GameGreen = Color(0xFF10B981)

@Composable
fun GameCard(title: String, accent: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MikayalaColors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = c.surfaceElevated,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier.widthIn(min = 240.dp, max = 310.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent)))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Petite pluie d'étoiles et de papillons quand une partie se termine SOUS NOS YEUX (pas en relisant). */
@Composable
fun CelebrateOnFinish(messageId: String, finished: Boolean) {
    var wasFinished by remember(messageId) { mutableStateOf(finished) }
    LaunchedEffect(finished) {
        if (finished && !wasFinished) LoveEffects.burst(LoveEffects.Theme.CELEBRATION, count = 22)
        wasFinished = finished
    }
}

@Composable
private fun ChoiceChip(label: String, color: Color, enabled: Boolean = true, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MikayalaColors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) color else color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.7f else 0.25f)),
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        )
    }
}

// ------------------------------------------------------------------ Carte à gratter (vraiment grattée)

@Composable
fun ScratchCardGame(message: MessageEntity, isSender: Boolean, onSave: (String) -> Unit) {
    val c = MikayalaColors
    val done = ChatGames.isDone(message.mediaUrl)
    val prize = message.content.substringAfter(":", message.content).trim()
    CelebrateOnFinish(message.id, done)
    GameCard("🎁 Carte à gratter", GameViolet) {
        when {
            done || isSender -> {
                Text(prize, fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (done) (if (isSender) "Grattée par ton amour ✨" else "Gagné ✨") else "En attente de grattage…",
                    fontSize = 11.sp, color = c.textSecondary
                )
            }
            else -> {
                // Grattage au doigt : la couche violette s'efface là où on passe le doigt
                val strokes = remember(message.id) { mutableStateListOf<Offset>() }
                val grid = remember(message.id) { BooleanArray(24 * 10) }
                var revealed by remember(message.id) { mutableStateOf(false) }
                val coverAlpha by animateFloatAsState(if (revealed) 0f else 1f, tween(450), label = "scratch")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GameGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(prize, fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen, alpha = coverAlpha)
                            .pointerInput(message.id) {
                                fun mark(p: Offset) {
                                    strokes.add(p)
                                    val gx = ((p.x / size.width) * 24).toInt()
                                    val gy = ((p.y / size.height) * 10).toInt()
                                    for (dx in -2..2) for (dy in -1..1) {
                                        val x = gx + dx; val y = gy + dy
                                        if (x in 0 until 24 && y in 0 until 10) grid[y * 24 + x] = true
                                    }
                                    if (!revealed && grid.count { it } > grid.size * 0.55f) {
                                        revealed = true
                                        onSave(ChatGames.with(message.mediaUrl, "1"))
                                    }
                                }
                                detectDragGestures(
                                    onDragStart = { mark(it) },
                                    onDrag = { change, _ -> change.consume(); mark(change.position) }
                                )
                            }
                    ) {
                        drawRect(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA), Color(0xFF34D399))))
                        // Petites paillettes sur la couche à gratter
                        for (i in 0 until 26) {
                            val x = (i * 37 % 100) / 100f * size.width
                            val y = (i * 53 % 100) / 100f * size.height
                            drawCircle(Color.White.copy(alpha = 0.35f), radius = 2.2f, center = Offset(x, y))
                        }
                        val r = 22.dp.toPx()
                        for (p in strokes) drawCircle(Color.Transparent, radius = r, center = p, blendMode = BlendMode.Clear)
                    }
                    if (strokes.isEmpty()) {
                        Text("Gratte avec ton doigt ✨", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- Action ou vérité

@Composable
fun TruthOrDareGame(message: MessageEntity, isSender: Boolean, onSave: (String) -> Unit, onAnswer: () -> Unit) {
    val c = MikayalaColors
    val isDare = message.content.contains("Action", ignoreCase = true)
    val accent = if (isDare) GameGreen else GameViolet
    val done = ChatGames.isDone(message.mediaUrl)
    val question = message.content.substringAfter(":", message.content).trim()
    CelebrateOnFinish(message.id, done)
    GameCard(if (isDare) "🔥 Action" else "✨ Vérité", accent) {
        Text(question, fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        when {
            done -> Text(if (isDare) "Défi relevé ✅" else "Réponse donnée ✅", fontSize = 12.sp, color = c.textSecondary)
            isSender -> Text("En attente de ton amour…", fontSize = 12.sp, color = c.textSecondary)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(if (isDare) "Je réponds 📸" else "Je réponds 💬", accent, modifier = Modifier.weight(1f)) { onAnswer() }
                ChoiceChip("C'est fait ✅", accent, selected = true, modifier = Modifier.weight(1f)) {
                    onSave(ChatGames.with(message.mediaUrl, "1"))
                }
            }
        }
    }
}

// ------------------------------------------------------------------- Pierre-feuille-ciseaux

private val RPS = listOf("🪨" to "Pierre", "📄" to "Feuille", "✂️" to "Ciseaux")

@Composable
fun RockPaperScissorsGame(message: MessageEntity, isSender: Boolean, onSave: (String) -> Unit, onRematch: () -> Unit) {
    val c = MikayalaColors
    val (s, p) = ChatGames.pairChoices(message.mediaUrl)
    val mine = if (isSender) s else p
    val theirs = if (isSender) p else s
    val finished = s >= 0 && p >= 0
    CelebrateOnFinish(message.id, finished)
    GameCard("🪨📄✂️ Pierre, feuille, ciseaux", GameViolet) {
        if (!finished) {
            Text(
                if (mine < 0) "Choisis en secret !" else "Tu as choisi ${RPS[mine].first}. ${if (theirs < 0) "En attente de l'autre…" else ""}",
                fontSize = 13.sp, color = c.textSecondary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RPS.forEachIndexed { i, (emoji, label) ->
                    ChoiceChip("$emoji\n$label", if (isSender) GameViolet else GameGreen, enabled = mine < 0, selected = mine == i, modifier = Modifier.weight(1f)) {
                        onSave(ChatGames.pairChoose(message.mediaUrl, isSender, i))
                    }
                }
            }
        } else {
            val outcome = ChatGames.rpsOutcome(s, p) * (if (isSender) 1 else -1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(RPS[mine].first, fontSize = 40.sp)
                Text("vs", fontSize = 14.sp, color = c.textSecondary)
                Text(RPS[theirs].first, fontSize = 40.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (outcome) { 1 -> "Tu as gagné ! 🏆"; -1 -> "Ton amour a gagné 💜"; else -> "Égalité 🤝" },
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (outcome >= 0) GameViolet else GameGreen
            )
            Spacer(Modifier.height(8.dp))
            ChoiceChip("Revanche 🔁", GameGreen, onClick = onRematch)
        }
    }
}

// ------------------------------------------------------------------------- Qui de nous deux ?

@Composable
fun WhoOfUsGame(message: MessageEntity, isSender: Boolean, onSave: (String) -> Unit, onNext: () -> Unit) {
    val c = MikayalaColors
    val (s, p) = ChatGames.pairChoices(message.mediaUrl)
    val mine = if (isSender) s else p
    val finished = s >= 0 && p >= 0
    val agree = ChatGames.whoOfUsAgree(s, p)
    CelebrateOnFinish(message.id, finished && agree)
    GameCard("👀 Qui de nous deux ?", GameGreen) {
        Text(message.content, fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        if (!finished) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("🙋 Moi", GameViolet, enabled = mine < 0, selected = mine == 0, modifier = Modifier.weight(1f)) {
                    onSave(ChatGames.pairChoose(message.mediaUrl, isSender, 0))
                }
                ChoiceChip("👉 Toi", GameGreen, enabled = mine < 0, selected = mine == 1, modifier = Modifier.weight(1f)) {
                    onSave(ChatGames.pairChoose(message.mediaUrl, isSender, 1))
                }
            }
            if (mine >= 0) {
                Spacer(Modifier.height(6.dp))
                Text("Réponse cachée jusqu'à ce que vous ayez répondu tous les deux 🤫", fontSize = 11.sp, color = c.textSecondary, textAlign = TextAlign.Center)
            }
        } else {
            // La personne désignée par l'expéditeur : 0 = lui-même, 1 = le partenaire
            fun who(choiceOfSender: Boolean, choice: Int): String {
                val pointsToSender = if (choiceOfSender) choice == 0 else choice == 1
                return if (pointsToSender == isSender) "toi" else "ton amour"
            }
            Text("Tu as dit : ${who(isSender, mine)}", fontSize = 13.sp, color = c.textPrimary)
            Text("Ton amour a dit : ${who(!isSender, if (isSender) p else s)}", fontSize = 13.sp, color = c.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (agree) "Vous êtes d'accord ! 💜💚" else "Vous n'êtes pas d'accord… à débattre 😏",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (agree) GameViolet else c.textSecondary
            )
            Spacer(Modifier.height(8.dp))
            ChoiceChip("Question suivante ➡️", GameGreen, onClick = onNext)
        }
    }
}

// ------------------------------------------------------------------------------ Puissance 4

@Composable
fun Connect4Game(message: MessageEntity, isSender: Boolean, onSave: (String) -> Unit, onRematch: () -> Unit) {
    val c = MikayalaColors
    val board = Connect4.normalize(ChatGames.state(message.mediaUrl) ?: ChatGames.base(message.mediaUrl))
    val mySymbol = if (isSender) 'R' else 'J'
    val winner = Connect4.winner(board)
    val myTurn = winner == null && Connect4.nextSymbol(board) == mySymbol
    CelebrateOnFinish(message.id, winner == mySymbol)
    GameCard("🔴 Puissance 4", GameViolet) {
        Text(
            when (winner) {
                null -> if (myTurn) "À toi de jouer ! Touche une colonne" else "Au tour de ton amour…"
                'D' -> "Égalité 🤝"
                mySymbol -> "Tu as gagné ! 🏆"
                else -> "Ton amour a gagné 💜"
            },
            fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Connect4.COLS / Connect4.ROWS.toFloat())
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2E1065))
                .pointerInput(board, myTurn) {
                    detectTapGestures { pos ->
                        if (!myTurn) return@detectTapGestures
                        val col = ((pos.x / size.width) * Connect4.COLS).toInt().coerceIn(0, Connect4.COLS - 1)
                        Connect4.play(board, col, mySymbol)?.let { onSave(ChatGames.with(message.mediaUrl, it)) }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val cw = size.width / Connect4.COLS
                val ch = size.height / Connect4.ROWS
                val r = minOf(cw, ch) * 0.38f
                for (row in 0 until Connect4.ROWS) for (col in 0 until Connect4.COLS) {
                    val sym = board[row * Connect4.COLS + col]
                    val color = when (sym) {
                        'R' -> GameViolet
                        'J' -> GameGreen
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    drawCircle(color, radius = r, center = Offset(col * cw + cw / 2, row * ch + ch / 2))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (isSender) GameViolet else GameGreen))
            Spacer(Modifier.width(4.dp))
            Text("Toi", fontSize = 11.sp, color = c.textSecondary)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (isSender) GameGreen else GameViolet))
            Spacer(Modifier.width(4.dp))
            Text("Ton amour", fontSize = 11.sp, color = c.textSecondary)
        }
        if (winner != null) {
            Spacer(Modifier.height(8.dp))
            ChoiceChip("Revanche 🔁", GameGreen, onClick = onRematch)
        }
    }
}

/** Petit liseré violet/vert réutilisable pour les cartes de jeux existantes. */
fun Modifier.gameBorder(): Modifier = this.border(1.dp, Brush.linearGradient(listOf(GameViolet, GameGreen)), RoundedCornerShape(20.dp))
