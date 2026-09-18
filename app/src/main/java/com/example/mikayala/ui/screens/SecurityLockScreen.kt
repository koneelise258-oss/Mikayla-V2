package com.example.mikayala.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*

@Composable
fun SecurityLockScreen(
    repository: MikayalaRepository,
    onUnlockSuccess: () -> Unit,
    onDecoyTriggered: () -> Unit
) {
    val context = LocalContext.current
    val settings by repository.userSettings.collectAsState()

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    // Concentric ripple animation for biometric fingerprint scanner
    val infiniteTransition = rememberInfiniteTransition(label = "biometric_ripple")
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple_scale"
    )

    fun handlePinDigit(digit: String) {
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            if (newPin.length == 4) {
                if (newPin == settings.fakePinCode) {
                    enteredPin = ""
                    onDecoyTriggered()
                } else if (newPin == settings.pinCode) {
                    enteredPin = ""
                    onUnlockSuccess()
                } else {
                    isError = true
                    Toast.makeText(context, "Code PIN incorrect", Toast.LENGTH_SHORT).show()
                    enteredPin = ""
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Lock & Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CardDark)
                    .border(1.dp, BorderHighlight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Verrou",
                    tint = AccentRose,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Espace Sécurisé",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Déverrouillez pour accéder à vos conversations et souvenirs",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // Biometric Fingerprint Button with Concentric Glow
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            AccentRose.copy(alpha = 0.35f * rippleScale),
                            AccentViolet.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
                .clickable {
                    onUnlockSuccess()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = "Empreinte Digitale",
                tint = AccentRose,
                modifier = Modifier.size(52.dp)
            )
        }

        // 4-Dot PIN Status
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val isFilled = i < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFilled) {
                                if (isError) Color(0xFFFF5252) else AccentRose
                            } else CardDark
                        )
                        .border(
                            1.dp,
                            if (isFilled) AccentRose else BorderSubtleWhite,
                            CircleShape
                        )
                )
            }
        }

        // Numeric Keypad (0-9, backspace)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("FINGER", "0", "BACK")
            )

            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (item in row) {
                        when (item) {
                            "FINGER" -> {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .clickable { onUnlockSuccess() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fingerprint,
                                        contentDescription = "Biométrie",
                                        tint = AccentViolet,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            "BACK" -> {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                        contentDescription = "Effacer",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            else -> {
                                Surface(
                                    shape = CircleShape,
                                    color = CardDark,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite),
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clickable { handlePinDigit(item) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = item,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
