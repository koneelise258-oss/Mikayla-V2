package com.example.mikayala.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.R
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.icons.AppleSvgIcons
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AuthScreen(
    repository: MikayalaRepository,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val userSettings by repository.userSettings.collectAsState()
    val isFirebase = userSettings.backendProvider == "firebase"

    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val palette = MikayalaColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = Spacing.maxContentWidth)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(palette.accent.copy(alpha = 0.22f), palette.secondary.copy(alpha = 0.22f))
                        )
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_mikayala_logo),
                    contentDescription = "Mikayala",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Mikayala",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textPrimary
            )

            Text(
                text = "Votre espace privé, fluide et protégé",
                fontSize = 14.sp,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)
            )

            // Sélecteur de serveur cloud : Firebase (principal) vs Supabase (secondaire)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.surface,
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppleSvgIcons.Cloud,
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SERVEUR CLOUD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = palette.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceSunken)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                errorMessage = null
                                coroutineScope.launch { repository.setBackendProvider("firebase") }
                            },
                            shape = RoundedCornerShape(11.dp),
                            color = if (isFirebase) palette.secondary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("backend_firebase_tab")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    text = "Firebase (Principal)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFirebase) Color.White else palette.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                errorMessage = null
                                coroutineScope.launch { repository.setBackendProvider("supabase") }
                            },
                            shape = RoundedCornerShape(11.dp),
                            color = if (!isFirebase) palette.accent else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("backend_supabase_tab")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    text = "Supabase (Secondaire)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isFirebase) Color.White else palette.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.danger.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = palette.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (isFirebase) {
                // Version Avant-Projet Firebase : Connexion sécurisée via Google (Credential Manager)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.secondary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(palette.secondarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Shield,
                                contentDescription = "Connexion Firebase sécurisée",
                                tint = palette.secondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Cloud Firebase (Principal)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Auth Google, Firestore temps réel, stockage médias & notifications. Supabase reste sauvegardé sur le côté.",
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                errorMessage = null
                                val activity = context.findActivity() ?: context
                                isLoading = true
                                loadingMessage = "Connexion Google en cours..."
                                coroutineScope.launch {
                                    val result = repository.signInWithGoogle(activity)
                                    isLoading = false
                                    if (result.isSuccess) {
                                        onAuthSuccess()
                                    } else {
                                        val msg = result.exceptionOrNull()?.localizedMessage
                                            ?: "Connexion Google annulée ou indisponible."
                                        errorMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("firebase_google_signin_button")
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Sparkles,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continuer avec Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Version Supabase principale : Créer ou rejoindre un espace
                Card(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        errorMessage = null
                        isLoading = true
                        loadingMessage = "Génération de votre identité sécurisée..."
                        coroutineScope.launch {
                            val success = repository.ensureAnonymousSession("Partenaire 1")
                            if (success) {
                                repository.getPrefs().edit().putString("initial_onboarding_step", "create").apply()
                                onAuthSuccess()
                            } else {
                                isLoading = false
                                val err = repository.supabaseService.lastAuthError ?: "Erreur de connexion Supabase."
                                errorMessage = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.28f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("supabase_create_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(palette.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Sparkles,
                                contentDescription = "Créer un espace",
                                tint = palette.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Créer un espace",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Générez un code unique et invitez votre partenaire",
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                Card(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        errorMessage = null
                        isLoading = true
                        loadingMessage = "Génération de votre identité sécurisée..."
                        coroutineScope.launch {
                            val success = repository.ensureAnonymousSession("Partenaire 2")
                            if (success) {
                                repository.getPrefs().edit().putString("initial_onboarding_step", "join").apply()
                                onAuthSuccess()
                            } else {
                                isLoading = false
                                val err = repository.supabaseService.lastAuthError ?: "Erreur de connexion Supabase."
                                errorMessage = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(1.dp, palette.secondary.copy(alpha = 0.28f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supabase_join_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(palette.secondarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Heart,
                                contentDescription = "Rejoindre avec un code",
                                tint = palette.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rejoindre avec un code",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Saisissez le code reçu pour rejoindre votre partenaire",
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (isLoading) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.modal,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = loadingMessage,
                            color = palette.textPrimary,
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
