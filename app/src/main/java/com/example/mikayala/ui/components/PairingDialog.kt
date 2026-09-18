package com.example.mikayala.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import com.example.mikayala.util.QRCodeGenerator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.theme.*
import kotlinx.coroutines.delay

@Composable
fun PairingDialog(
    currentPairingCode: String = "LOVE26",
    onDismiss: () -> Unit,
    onPairSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Mon Code, 1 = Saisir un Code, 2 = Scanner QR
    var inputCode by remember { mutableStateOf("") }
    var isPairingSuccess by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CardModalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(AccentRose.copy(alpha = 0.3f), Color.Transparent))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Jumelage Espace Couple",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Reliez vos deux appareils pour synchroniser messages et souvenirs en direct",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Tab selector
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            TabButton(
                                text = "Mon Code",
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.weight(1f)
                            )
                            TabButton(
                                text = "Saisir",
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.weight(1f)
                            )
                            TabButton(
                                text = "Scanner",
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    when (selectedTab) {
                        0 -> {
                            // My Code & QR Code display
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Dynamic QR Code Canvas
                                SimulatedQrCanvas(code = currentPairingCode)

                                Spacer(modifier = Modifier.height(16.dp))

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = CardDark,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Code Jumelage", currentPairingCode))
                                            Toast.makeText(context, "Code copié ! 📋", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = "CODE UNIQUE DE JUMELAGE", fontSize = 10.sp, color = AccentViolet, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = currentPairingCode,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 4.sp,
                                                color = AccentRose,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Copier",
                                            tint = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Manual Input Code
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OutlinedTextField(
                                    value = inputCode,
                                    onValueChange = { inputCode = it.uppercase().take(8) },
                                    label = { Text("Code à 6 caractères de votre partenaire") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentRose,
                                        unfocusedBorderColor = BorderSubtleWhite,
                                        focusedLabelColor = AccentRose,
                                        cursorColor = AccentRose
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (inputCode.isNotBlank()) {
                                            isPairingSuccess = true
                                            onPairSuccess(inputCode)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Lier les deux appareils ✨", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                        2 -> {
                            // Simulated QR Scanner Viewfinder
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black)
                                        .border(2.dp, AccentRose, RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = "Scanner",
                                        tint = AccentViolet,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        isPairingSuccess = true
                                        onPairSuccess("MIKAYALA_QR_CONNECTED")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Simuler détection du QR code 📸", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = onDismiss) {
                        Text("Fermer", color = TextSecondary)
                    }
                }

                // Heart shower celebration on success
                if (isPairingSuccess) {
                    HeartShowerOverlay(onFinished = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AccentRose else Color.Transparent,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else TextSecondary
            )
        }
    }
}

@Composable
private fun SimulatedQrCanvas(code: String) {
    val qrBitmap = remember(code) {
        QRCodeGenerator.generateQRCode(code, 512)
    }

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.White)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR Code Jumelage",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                color = AccentRose,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun HeartShowerOverlay(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "💖", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Connectés pour toujours !", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentRose)
            Text(text = "Espace couple synchronisé avec succès", fontSize = 12.sp, color = TextPrimary)
        }
    }
}
