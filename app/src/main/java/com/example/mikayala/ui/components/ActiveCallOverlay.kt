package com.example.mikayala.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Écran d'appel HONNÊTE.
 * Mikayala gère la signalisation (sonnerie, refus, annulation, historique) mais pas encore le transport
 * audio/vidéo (WebRTC non intégré). L'ancien écran simulait un appel en cours (minuteur, caméra, micro,
 * « Appel Vidéo HD P2P Chiffré ») sans aucune communication réelle : il est remplacé par cet écran.
 */
@Composable
fun ActiveCallOverlay(
    partnerName: String = "Mon partenaire",
    isVideo: Boolean = true,
    isIncoming: Boolean = false,
    onEndCall: (durationSeconds: Int) -> Unit
) {
    val bg = Color(0xFF1B1A1E)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier.size(112.dp).clip(CircleShape).background(Color(0xFF3A2C30)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = Color(0xFFE6A3AD), modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(partnerName, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEEEAE5))
            Spacer(Modifier.height(6.dp))
            Text(
                text = (if (isVideo) "Appel vidéo" else "Appel vocal") + " · " +
                    (if (isIncoming) "appel reçu" else "sonnerie chez votre partenaire…"),
                fontSize = 15.sp,
                color = Color(0xFFADA7A1)
            )
            Spacer(Modifier.height(28.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF26252A), modifier = Modifier.widthIn(max = 340.dp)) {
                Text(
                    text = "Le son et la vidéo des appels ne sont pas encore disponibles dans Mikayala. " +
                        "La sonnerie, le refus et l'historique fonctionnent.",
                    fontSize = 13.sp,
                    color = Color(0xFFADA7A1),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(Modifier.height(48.dp))
            IconButton(
                onClick = { onEndCall(0) },
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFC4605C))
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "Raccrocher", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Raccrocher", fontSize = 13.sp, color = Color(0xFFADA7A1))
        }
    }
}
