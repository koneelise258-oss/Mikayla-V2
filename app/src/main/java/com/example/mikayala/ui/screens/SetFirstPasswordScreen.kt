package com.example.mikayala.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*

@Composable
fun SetFirstPasswordScreen(
    repository: MikayalaRepository,
    onPasswordSet: () -> Unit,
    onSkipPassword: () -> Unit = onPasswordSet
) {
    val context = LocalContext.current
    var isSettingPin by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    fun handleDigit(digit: String) {
        if (!isConfirming) {
            if (firstPin.length < 4) {
                val updated = firstPin + digit
                firstPin = updated
                if (updated.length == 4) {
                    isConfirming = true
                    Toast.makeText(context, "Confirmez votre code PIN", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            if (confirmPin.length < 4) {
                val updated = confirmPin + digit
                confirmPin = updated
                if (updated.length == 4) {
                    if (updated == firstPin) {
                        repository.updatePinCode(updated)
                        repository.setPairingSetupCompleted(true)
                        Toast.makeText(context, "Code secret enregistré avec succès ! 🔒", Toast.LENGTH_SHORT).show()
                        onPasswordSet()
                    } else {
                        isError = true
                        Toast.makeText(context, "Les codes ne correspondent pas. Recommencez.", Toast.LENGTH_SHORT).show()
                        confirmPin = ""
                        firstPin = ""
                        isConfirming = false
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (isConfirming) {
            if (confirmPin.isNotEmpty()) {
                confirmPin = confirmPin.dropLast(1)
            } else {
                isConfirming = false
                firstPin = firstPin.dropLast(1)
            }
        } else {
            if (firstPin.isNotEmpty()) {
                firstPin = firstPin.dropLast(1)
            }
        }
    }

    val currentLength = if (isConfirming) confirmPin.length else firstPin.length

    if (!isSettingPin) {
        // Option screen: "Protéger mon espace" (Définir un code OU Passer pour le moment)
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
            Spacer(modifier = Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(CardDark)
                        .border(1.dp, BorderHighlight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Sécurité",
                        tint = AccentRose,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Protéger mon espace",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sécurisez vos conversations intimes, photos et souvenirs avec un code secret et l'empreinte digitale.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Feature items card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardDark,
                    border = BorderStroke(1.dp, BorderSubtleWhite)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = AccentRose,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Code PIN confidentiel à 4 chiffres",
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                tint = VibrantCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Déverrouillage par empreinte digitale",
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Bottom Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { isSettingPin = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "Définir un code 🔒",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        repository.setPairingSetupCompleted(true)
                        onSkipPassword()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Passer pour le moment",
                        fontSize = 15.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    } else {
        // PIN entry & confirmation keypad
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

            // Top Shield & Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(CardDark)
                        .border(1.dp, BorderHighlight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Sécurité",
                        tint = AccentRose,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isConfirming) "Confirmez le code" else "Définir un code PIN",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = isConfirming,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "subtitle_fade"
                ) { confirming ->
                    Text(
                        text = if (confirming) {
                            "Confirmez votre code secret à 4 chiffres"
                        } else {
                            "Choisissez un code secret à 4 chiffres"
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 4-Dot PIN Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < currentLength
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

            // Numeric Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "BACK")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (item in row) {
                            when (item) {
                                "BACK" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .clickable { handleBackspace() },
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
                                "" -> {
                                    Box(modifier = Modifier.size(64.dp))
                                }
                                else -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = CardDark,
                                        border = BorderStroke(1.dp, BorderSubtleWhite),
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clickable { handleDigit(item) }
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

            TextButton(
                onClick = {
                    repository.setPairingSetupCompleted(true)
                    onSkipPassword()
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Passer pour le moment",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
