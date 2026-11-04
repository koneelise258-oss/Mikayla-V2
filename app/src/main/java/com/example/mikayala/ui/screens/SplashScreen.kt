package com.example.mikayala.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Écran de démarrage sobre, façon WhatsApp : logo au centre, nom de l'application en bas.
 * Une seule animation courte (apparition), aucune boucle : rien de coûteux sur les téléphones modestes.
 * Fond identique à celui de la fenêtre (thème) et de l'écran système Android 12+ : aucun saut visuel.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
        launch { scale.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        delay(1100)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.mikayala.theme.MikayalaColors.background)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_mikayala_logo),
            contentDescription = "Mikayala",
            modifier = Modifier
                .align(Alignment.Center)
                .size(104.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(RoundedCornerShape(28.dp))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .graphicsLayer { this.alpha = alpha.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "Mikayala",
                fontSize = 22.sp,
                fontFamily = com.example.mikayala.theme.Poppins,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = com.example.mikayala.theme.MikayalaColors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Messagerie privée du couple",
                fontSize = 12.sp,
                fontFamily = com.example.mikayala.theme.Poppins,
                color = com.example.mikayala.theme.MikayalaColors.textSecondary
            )
        }
    }
}

