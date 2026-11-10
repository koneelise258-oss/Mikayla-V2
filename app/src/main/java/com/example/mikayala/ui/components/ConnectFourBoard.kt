package com.example.mikayala.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.MikayalaColors
import com.example.mikayala.util.ConnectFour

/**
 * Plateau de Puissance 4 affiché DANS une bulle du chat (Mission 06 F5).
 * - Etat synchronisé : la chaîne de 42 caractères vient de message.mediaUrl,
 *   la mise à jour Realtime de Supabase propage chaque coup aux deux téléphones.
 * - R = rouge = celui qui a lancé la partie (expéditeur du message), J = jaune = le partenaire.
 * - Chaque clic sur une colonne tente le coup via ConnectFour.play : si interdit
 *   (pas son tour, colonne pleine, partie finie), rien ne se passe — impossible de casser la partie.
 * - [onPlayColumn] reçoit le NOUVEL état à écrire dans media_url côté serveur.
 */
@Composable
fun ConnectFourBoard(
    state: String,
    isGameOwner: Boolean,
    mySymbol: Char,
    onPlayColumn: (newState: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = ConnectFour.normalize(state)
    val winner = ConnectFour.result(s)
    val winningCells = remember(s) { ConnectFour.winningLine(s).toSet() }
    val myTurn = winner == null && ConnectFour.nextSymbol(s) == mySymbol

    val red = Color(0xFFD8544F)
    val yellow = Color(0xFFE9B44C)
    val boardBg = MikayalaColors.surfaceSunken
    val boardBorder = MikayalaColors.border
    val accent = MikayalaColors.accent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(boardBg)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Statut de la partie
        Text(
            text = when {
                winner == 'R' -> if (isGameOwner) "Tu as gagné ! 🎉" else "Ton amour a gagné 🎉"
                winner == 'J' -> if (!isGameOwner) "Tu as gagné ! 🎉" else "Ton amour a gagné 🎉"
                winner == 'D' -> "Match nul 🤝"
                myTurn -> "À toi de jouer !"
                else -> "Ton amour réfléchit…"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (winner != null) MikayalaColors.success else MikayalaColors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Grille : 7 colonnes cliquables, remplissage par le bas (gravité)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(boardBorder.copy(alpha = 0.35f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (col in 0 until ConnectFour.COLS) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = myTurn) {
                            val new = ConnectFour.play(s, col, mySymbol)
                            if (new != null) onPlayColumn(new)
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // row 5 (haut) affiché en premier, row 0 (bas) en dernier
                    for (row in (ConnectFour.ROWS - 1) downTo 0) {
                        val idx = col * ConnectFour.ROWS + row
                        val cell = s[idx]
                        val isWinning = winningCells.contains(idx)

                        // Léger zoom animé sur les pions gagnants
                        val winScale by animateFloatAsState(
                            targetValue = if (isWinning) 1.12f else 1f,
                            animationSpec = tween(250),
                            label = "c4_win_scale"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .scale(winScale)
                                .clip(CircleShape)
                                .background(
                                    when (cell) {
                                        'R' -> red
                                        'J' -> yellow
                                        else -> boardBg
                                    }
                                )
                                .then(
                                    if (isWinning) Modifier.background(Color.White.copy(alpha = 0.22f))
                                    else Modifier
                                )
                                .testTag("c4_cell_" + col + "_" + row)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Légende discrète : mes pions / ses pions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (mySymbol == 'R') red else yellow)
            )
            Text(
                text = "toi",
                fontSize = 10.sp,
                color = MikayalaColors.textMuted
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (mySymbol == 'R') yellow else red)
            )
            Text(
                text = "ton amour",
                fontSize = 10.sp,
                color = MikayalaColors.textMuted
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (!myTurn && winner == null) {
                Text(
                    text = "en attente…",
                    fontSize = 10.sp,
                    color = accent.copy(alpha = 0.8f)
                )
            }
        }
    }
}
