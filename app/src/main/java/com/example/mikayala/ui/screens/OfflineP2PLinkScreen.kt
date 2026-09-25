package com.example.mikayala.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.GlowingGradientButton
import com.example.mikayala.ui.components.NeumorphicSquircleButton
import com.example.mikayala.ui.components.QRScanner
import com.example.mikayala.util.P2PMessage
import com.example.mikayala.util.P2PSocketManager
import com.example.mikayala.util.P2PState
import com.example.mikayala.util.QRCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineP2PLinkScreen(
    repository: MikayalaRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Observe real socket state
    val p2pState by P2PSocketManager.p2pState.collectAsState()
    val p2pMessages by P2PSocketManager.p2pMessages.collectAsState()
    val latencyMs by P2PSocketManager.latencyMs.collectAsState()
    val transferSpeed by P2PSocketManager.transferSpeedMBs.collectAsState()

    // Screen tab selection (0: Émetteur/Hôte, 1: Récepteur/Scanner)
    var selectedRole by remember { mutableIntStateOf(0) }

    // Client Manual Inputs
    var hostIpInput by remember { mutableStateOf("192.168.43.1") }
    var pinCodeInput by remember { mutableStateOf("") }
    var showQrScannerModal by remember { mutableStateOf(false) }

    // Chat Message Input
    var directMessageText by remember { mutableStateOf("") }

    // QR Code Bitmap caching for Host
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrPayloadData by remember { mutableStateOf("") }

    // On enter host role, automatically start hosting
    LaunchedEffect(selectedRole) {
        if (selectedRole == 0 && p2pState !is P2PState.Connected) {
            val payload = P2PSocketManager.startHosting(context, "Mon Appareil (Émetteur)")
            qrPayloadData = payload
            qrBitmap = QRCodeGenerator.generateQRCode(payload, 500)
        }
    }

    // Handle connected notifications or errors
    LaunchedEffect(p2pState) {
        when (val state = p2pState) {
            is P2PState.Connected -> {
                Toast.makeText(context, "⚡ Connecté avec succès à ${state.partnerName} !", Toast.LENGTH_SHORT).show()
            }
            is P2PState.Error -> {
                Toast.makeText(context, "⚠️ ${state.message}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    // Radar animation
    val infiniteTransition = rememberInfiniteTransition(label = "p2p_radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_scale"
    )

    // REAL QR CODE SCANNER MODAL
    if (showQrScannerModal) {
        Dialog(onDismissRequest = { showQrScannerModal = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Viseur Caméra QR Code 📷",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scannez le QR Code sur l'écran du point d'accès",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // CameraX QR Scanner Component
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .border(2.dp, VibrantCyan, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        QRScanner(
                            onCodeScanned = { scannedCode ->
                                showQrScannerModal = false
                                val success = P2PSocketManager.connectViaQrCode(context, scannedCode)
                                if (!success) {
                                    Toast.makeText(context, "Format QR Code non reconnu", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showQrScannerModal = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderSubtleWhite)
                        ) {
                            Text("Fermer", color = TextPrimary)
                        }

                        GlowingGradientButton(
                            text = "Démo Rapide ⚡",
                            icon = Icons.Rounded.FlashOn,
                            onClick = {
                                showQrScannerModal = false
                                P2PSocketManager.simulateConnectionForDemo(context)
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MatteSlateBackground,
        topBar = {
            Surface(
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeumorphicSquircleButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            onClick = {
                                P2PSocketManager.stopAllConnections()
                                onBack()
                            },
                            size = 42.dp,
                            iconSize = 20.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Liaison Hors-Ligne 📡",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = VibrantCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, VibrantCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "Style Xender",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (p2pState is P2PState.Connected) "Canal TCP Socket Actif (0 Mo Data)" else "Point d'Accès Direct / Sans 4G",
                                fontSize = 11.sp,
                                color = if (p2pState is P2PState.Connected) OnlinePresenceGreen else TextSecondary
                            )
                        }
                    }

                    // P2P Status pill
                    val isConnected = p2pState is P2PState.Connected
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isConnected) OnlinePresenceGreen.copy(alpha = 0.15f) else HoverStateCyan,
                        border = BorderStroke(1.dp, if (isConnected) OnlinePresenceGreen else VibrantCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) OnlinePresenceGreen else VibrantCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Lié ⚡" else "Déconnecté",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) OnlinePresenceGreen else VibrantCyan
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            // ROLE SELECTOR (ÉMETTEUR vs RÉCEPTEUR)
            if (p2pState !is P2PState.Connected) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MatteCardDark,
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        ) {
                            // Option 1: Émetteur / Hôte
                            Surface(
                                onClick = { selectedRole = 0 },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selectedRole == 0) MatteCardElevated else Color.Transparent,
                                border = if (selectedRole == 0) BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.5f)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WifiTethering,
                                        contentDescription = null,
                                        tint = if (selectedRole == 0) VibrantCyan else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "1. Émetteur (Hôte)",
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedRole == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedRole == 0) TextPrimary else TextSecondary
                                    )
                                }
                            }

                            // Option 2: Récepteur / Client
                            Surface(
                                onClick = {
                                    selectedRole = 1
                                    P2PSocketManager.stopAllConnections()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selectedRole == 1) MatteCardElevated else Color.Transparent,
                                border = if (selectedRole == 1) BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.5f)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = null,
                                        tint = if (selectedRole == 1) VibrantCyan else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "2. Récepteur (Client)",
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedRole == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedRole == 1) TextPrimary else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 1: MODE ÉMETTEUR (HÔTE SOCKET SERVER + REAL QR + PIN)
            // -------------------------------------------------------------
            if (p2pState !is P2PState.Connected && selectedRole == 0) {
                val hostingState = p2pState as? P2PState.Hosting

                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MatteCardDark,
                        border = BorderStroke(1.dp, BorderHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Pulsing Radar / Hotspot Icon
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                VibrantCyan.copy(alpha = 0.25f),
                                                VibrantBlue.copy(alpha = 0.10f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .border(2.dp, VibrantCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiTethering,
                                    contentDescription = null,
                                    tint = VibrantCyan,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Émetteur Prêt & Signal Actif 📡",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Demandez à votre partenaire de scanner le QR Code ou de taper le code PIN",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // REAL GENERATED QR CODE DISPLAY
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                modifier = Modifier
                                    .size(170.dp)
                                    .clickable {
                                        Toast.makeText(context, "QR Code prêt ! Scanner avec l'autre appareil.", Toast.LENGTH_SHORT).show()
                                    },
                                shadowElevation = 8.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = "QR Code de connexion P2P",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        CircularProgressIndicator(color = VibrantCyan, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 6-DIGIT PIN CODE DISPLAY CARD (OPTION CODE)
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = DeepSurfaceBlack,
                                border = BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "CODE PIN DE CONNEXION RAPIDE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantCyan,
                                        letterSpacing = 1.2.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    val pinFormatted = hostingState?.pinCode?.chunked(3)?.joinToString(" ") ?: "849 201"
                                    Text(
                                        text = pinFormatted,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        letterSpacing = 4.sp
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "IP Hôte : ${hostingState?.ip ?: "192.168.43.1"}:${hostingState?.port ?: 8888}",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("PIN", hostingState?.pinCode ?: "849201"))
                                                Toast.makeText(context, "Code PIN copié !", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier", tint = VibrantCyan, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Demo / Instant connection button
                            GlowingGradientButton(
                                text = "Tester Connexion Immédiate ⚡",
                                icon = Icons.Rounded.FlashOn,
                                onClick = {
                                    P2PSocketManager.simulateConnectionForDemo(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 2: MODE RÉCEPTEUR (SCANNER QR OU CODE PIN 6 CHIFFRES)
            // -------------------------------------------------------------
            if (p2pState !is P2PState.Connected && selectedRole == 1) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MatteCardDark,
                        border = BorderStroke(1.dp, BorderHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Rejoindre le Signal de Mikayala 🚀",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Choisissez votre méthode de connexion préférée :",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // OPTION A: REAL CAMERA QR SCANNER
                            Surface(
                                onClick = { showQrScannerModal = true },
                                shape = RoundedCornerShape(18.dp),
                                color = HoverStateCyan,
                                border = BorderStroke(1.5.dp, VibrantCyan),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(VibrantCyan.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "OPTION 1 : Scanner le QR Code", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(text = "Caméra temps réel • Connexion en 1s ⚡", fontSize = 11.sp, color = VibrantCyan)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = VibrantCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "— OU —",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // OPTION B: ENTER 6-DIGIT PIN CODE
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = DeepSurfaceBlack,
                                border = BorderStroke(1.dp, BorderSubtleWhite),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "OPTION 2 : Entrer le Code PIN (6 Chiffres)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = pinCodeInput,
                                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pinCodeInput = it },
                                        label = { Text("Code PIN à 6 chiffres", fontSize = 12.sp) },
                                        placeholder = { Text("ex: 849201", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = VibrantCyan,
                                            unfocusedBorderColor = BorderSubtleWhite,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = hostIpInput,
                                        onValueChange = { hostIpInput = it },
                                        label = { Text("Adresse IP du Point d'accès", fontSize = 11.sp) },
                                        placeholder = { Text("192.168.43.1", fontSize = 11.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = VibrantCyan,
                                            unfocusedBorderColor = BorderSubtleWhite,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            GlowingGradientButton(
                                text = "Rejoindre via Code PIN 🚀",
                                icon = Icons.Rounded.WifiTethering,
                                onClick = {
                                    if (pinCodeInput.length == 6) {
                                        P2PSocketManager.connectToHost(
                                            context = context,
                                            ipAddress = hostIpInput.trim(),
                                            port = P2PSocketManager.DEFAULT_PORT,
                                            pinCode = pinCodeInput.trim(),
                                            clientName = "Mon Appareil (Récepteur)"
                                        )
                                    } else {
                                        Toast.makeText(context, "Veuillez entrer le code PIN à 6 chiffres", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 3: ESPACE CONNECTÉ P2P ACTIF (CHAT, FICHIERS, STATS)
            // -------------------------------------------------------------
            if (p2pState is P2PState.Connected) {
                val connectedState = p2pState as P2PState.Connected

                // Live Speed & Dashboard Header
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MatteCardDark,
                        border = BorderStroke(1.dp, BorderHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(OnlinePresenceGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = OnlinePresenceGreen, modifier = Modifier.size(26.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = "Liaison TCP Socket Établie ⚡", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(text = "Connecté avec ${connectedState.partnerName}", fontSize = 12.sp, color = OnlinePresenceGreen)
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        P2PSocketManager.stopAllConnections()
                                        Toast.makeText(context, "Liaison P2P déconnectée", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Déconnecter", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3 Real Metric Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                P2PMetricCard(label = "Vitesse P2P", value = "$transferSpeed Mo/s", icon = "⚡", modifier = Modifier.weight(1f))
                                P2PMetricCard(label = "Latence Ping", value = "$latencyMs ms", icon = "📶", modifier = Modifier.weight(1f))
                                P2PMetricCard(label = "Data 4G/5G", value = "0 Mo", icon = "🛡️", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Beam File Action Bar
                item {
                    Text(
                        text = "ENVOI INSTANTANÉ DE FICHIERS (BEAM)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = VibrantCyan
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        P2PBeamToolCard(
                            title = "Photo HD",
                            subtitle = "Zéro compression",
                            icon = Icons.Rounded.Image,
                            onClick = {
                                P2PSocketManager.sendFile(context, "Photo_Souvenir_${System.currentTimeMillis().toString().takeLast(4)}.jpg", "14.2 Mo", false)
                                Toast.makeText(context, "Photo transmise instantanément !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        P2PBeamToolCard(
                            title = "Note Vocale",
                            subtitle = "Qualité FLAC",
                            icon = Icons.Rounded.Mic,
                            onClick = {
                                P2PSocketManager.sendFile(context, "Note_Vocale_Intime.flac", "3.8 Mo", true)
                                Toast.makeText(context, "Audio haute définition transmis !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        P2PBeamToolCard(
                            title = "Vidéo 4K",
                            subtitle = "Beam Rapide",
                            icon = Icons.Rounded.Videocam,
                            onClick = {
                                P2PSocketManager.sendFile(context, "Video_Couple_Mikayala.mp4", "88.4 Mo", false)
                                Toast.makeText(context, "Vidéo 4K transmise !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Chat Input Field over P2P
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MatteCardDark,
                        border = BorderStroke(1.dp, BorderHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = directMessageText,
                                onValueChange = { directMessageText = autoCapitalizeMessageInput(it, directMessageText) },
                                placeholder = { Text("Message direct P2P hors-ligne...", color = TextMuted, fontSize = 13.sp) },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    if (directMessageText.isNotBlank()) {
                                        P2PSocketManager.sendMessage(context, directMessageText)
                                        repository.sendMessage(content = "📡 [Mode Proximité P2P] ${directMessageText.trim()}")
                                        directMessageText = ""
                                        Toast.makeText(context, "Message P2P transmis !", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(CyanBlueGradientStart, CyanBlueGradientEnd)))
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Envoyer", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Live P2P Chat & File Feed
                item {
                    Text(
                        text = "CANAL DE MESSAGES ET TRANSFERTS P2P",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextSecondary
                    )
                }

                items(p2pMessages.size) { index ->
                    val msg = p2pMessages[index]
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (msg.isFromMe) ChatBubbleSender else MatteCardDark,
                        border = BorderStroke(1.dp, if (msg.isFromMe) VibrantCyan.copy(alpha = 0.3f) else BorderSubtleWhite),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (msg.isFromMe) VibrantCyan.copy(alpha = 0.2f) else OnlinePresenceGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (msg.isFile) Icons.Rounded.FolderZip else Icons.Rounded.Chat,
                                    contentDescription = null,
                                    tint = if (msg.isFromMe) VibrantCyan else OnlinePresenceGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (msg.isFromMe) "Moi" else msg.senderName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.isFromMe) VibrantCyan else OnlinePresenceGreen
                                    )
                                    Text(
                                        text = "Direct P2P ✓",
                                        fontSize = 10.sp,
                                        color = OnlinePresenceGreen
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = msg.content,
                                    fontSize = 13.sp,
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

@Composable
private fun P2PMetricCard(label: String, value: String, icon: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DeepSurfaceBlack,
        border = BorderStroke(1.dp, BorderSubtleWhite),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = label, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun P2PBeamToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MatteCardDark,
        border = BorderStroke(1.dp, BorderSubtleWhite),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VibrantCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = subtitle, fontSize = 10.sp, color = TextSecondary)
        }
    }
}
