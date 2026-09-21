package com.example.mikayala.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.R
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    repository: MikayalaRepository,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val darkBg = Color(0xFF0E0B16)
    val cardBg = Color(0xFF1B182B)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
    ) {
        // Glowing background effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentRose.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(w * 0.8f, h * 0.2f),
                    radius = w * 0.7f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentViolet.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(w * 0.2f, h * 0.8f),
                    radius = w * 0.8f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            // Glowing Animated Logo Header
            val infiniteTransition = rememberInfiniteTransition(label = "auth_logo_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "auth_pulse_scale"
            )

            Box(
                modifier = Modifier
                    .size(118.dp)
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AccentViolet.copy(alpha = 0.5f),
                                AccentRose.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFE085FF),
                                    Color(0xFF8C52FF),
                                    Color(0xFF4A154B)
                                )
                            )
                        ),
                        RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_butterfly_logo),
                    contentDescription = "Mikayala Logo Papillon Liquid Glass",
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(28.dp))
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Mikayala",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Text(
                text = "Votre sanctuaire de couple chiffré et intime",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 40.dp)
            )

            // Option 1: Créer un espace de couple
            Card(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isLoading = true
                    loadingMessage = "Génération de votre identité sécurisée..."
                    coroutineScope.launch {
                        val success = repository.ensureAnonymousSession("Partenaire 1")
                        if (success) {
                            repository.getPrefs().edit().putString("initial_onboarding_step", "create").apply()
                            onAuthSuccess()
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Erreur de connexion Supabase.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(VibrantCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = VibrantCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Créer un espace",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Générez un code unique et invitez votre partenaire",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Option 2: Rejoindre avec un code
            Card(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isLoading = true
                    loadingMessage = "Génération de votre identité sécurisée..."
                    coroutineScope.launch {
                        val success = repository.ensureAnonymousSession("Partenaire 2")
                        if (success) {
                            repository.getPrefs().edit().putString("initial_onboarding_step", "join").apply()
                            onAuthSuccess()
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Erreur de connexion Supabase.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AccentRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolunteerActivism,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rejoindre avec un code",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Saisissez le code reçu pour rejoindre votre partenaire",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        if (isLoading) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = AccentRose, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = loadingMessage,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
