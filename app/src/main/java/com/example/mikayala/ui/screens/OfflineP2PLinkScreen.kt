package com.example.mikayala.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.GlowingGradientButton
import com.example.mikayala.ui.components.NeumorphicSquircleButton
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineP2PLinkScreen(
    repository: MikayalaRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // State of connection
    var isConnectedP2P by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableIntStateOf(0) } // 0: Émetteur (Point d'accès), 1: Récepteur (Scanner)

    // Hotspot Info
    var hotspotSSID by remember { mutableStateOf("MIKAYALA_LINK_5G_9924") }
    var hotspotPassword by remember { mutableStateOf("mikayala@2026!") }
    var showPassword by remember { mutableStateOf(false) }
    var frequencyBand by remember { mutableStateOf("5 GHz (Ultra-Vitesse)") }

    // Client connection state
    var clientPasswordInput by remember { mutableStateOf("") }
    var isScanningNetworks by remember { mutableStateOf(false) }
    var networkDetected by remember { mutableStateOf(false) }
    var showQrScannerModal by remember { mutableStateOf(false) }

    // Direct Chat & File Transfers
    var directMessageText by remember { mutableStateOf("") }
    val transferHistory = remember {
        mutableStateListOf(
            P2PTransferItem("Photo_Romantique_HD.jpg", 100, "48 Mo/s", true, "14.2 Mo"),
            P2PTransferItem("Vocal_Secret_Mikayala.aac", 100, "32 Mo/s", true, "3.8 Mo")
        )
    }

    // Radar pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "p2p_radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_scale"
    )

    // Scanner laser animation for QR modal
    val scanLaserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    // QR Code Scanner Modal
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
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Viseur Caméra QR Code 📷",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pointez vers l'écran du point d'accès de Mikayala",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Scanner view simulation with laser
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .border(2.dp, VibrantCyan, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val laserY = size.height * scanLaserOffset
                            drawLine(
                                color = VibrantCyan,
                                start = Offset(0f, laserY),
                                end = Offset(size.width, laserY),
                                strokeWidth = 4f
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = VibrantCyan.copy(alpha = 0.4f),
                            modifier = Modifier.size(100.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    GlowingGradientButton(
                        text = "Valider la Connexion Instantanée",
                        icon = Icons.Rounded.CheckCircle,
                        onClick = {
                            showQrScannerModal = false
                            isConnectedP2P = true
                            Toast.makeText(context, "Connecté avec succès au Point d'Accès de Mikayala ! 🚀", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            onClick = onBack,
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
                                        text = "P2P Direct",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isConnectedP2P) "Canal Direct 5GHz Actif (0 Mo Data)" else "Wi-Fi Direct / Sans 4G ni Internet",
                                fontSize = 11.sp,
                                color = if (isConnectedP2P) OnlinePresenceGreen else TextSecondary
                            )
                        }
                    }

                    // P2P Status pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isConnectedP2P) OnlinePresenceGreen.copy(alpha = 0.15f) else HoverStateCyan,
                        border = BorderStroke(1.dp, if (isConnectedP2P) OnlinePresenceGreen else VibrantCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnectedP2P) OnlinePresenceGreen else VibrantCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnectedP2P) "Lié ⚡" else "Déconnecté",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnectedP2P) OnlinePresenceGreen else VibrantCyan
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
            // MODE SELECTOR (ÉMETTEUR vs RÉCEPTEUR)
            if (!isConnectedP2P) {
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
                            // Role 0: Hôte / Point d'accès
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
                                        text = "Créer Point d'accès",
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedRole == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedRole == 0) TextPrimary else TextSecondary
                                    )
                                }
                            }

                            // Role 1: Récepteur / Scanner
                            Surface(
                                onClick = {
                                    selectedRole = 1
                                    isScanningNetworks = true
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
                                        imageVector = Icons.Rounded.WifiFind,
                                        contentDescription = null,
                                        tint = if (selectedRole == 1) VibrantCyan else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Rejoindre / Scanner",
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
            // SECTION 1: POINT D'ACCÈS ACTIF (ÉMETTEUR / HÔTE)
            // -------------------------------------------------------------
            if (!isConnectedP2P && selectedRole == 0) {
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
                                    .size(90.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                VibrantCyan.copy(alpha = 0.25f),
                                                VibrantBlue.copy(alpha = 0.1f),
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
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Point d'Accès P2P Actif 📡",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Demandez à votre partenaire de scanner ou de se connecter",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Interactive Simulated QR Code
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                modifier = Modifier
                                    .size(160.dp)
                                    .clickable {
                                        Toast.makeText(context, "QR Code de liaison prêt pour scan !", Toast.LENGTH_SHORT).show()
                                    },
                                shadowElevation = 8.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCode2,
                                        contentDescription = "QR Code",
                                        tint = Color(0xFF1E2128),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // SSID & Password Container
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = DeepSurfaceBlack,
                                border = BorderStroke(1.dp, BorderSubtleWhite),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // SSID Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = "NOM DU RÉSEAU WI-FI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VibrantCyan)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = hotspotSSID, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        }
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("SSID", hotspotSSID))
                                                Toast.makeText(context, "Nom du réseau copié !", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier", tint = VibrantCyan, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = BorderSubtleWhite)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Password Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = "MOT DE PASSE SÉCURISÉ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (showPassword) hotspotPassword else "••••••••••••",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextPrimary
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = { showPassword = !showPassword }) {
                                                Icon(
                                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                    contentDescription = "Afficher",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Password", hotspotPassword))
                                                    Toast.makeText(context, "Mot de passe copié !", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier", tint = AccentRose, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Action button to simulate partner joined
                            GlowingGradientButton(
                                text = "Simuler Connexion Partenaire ✓",
                                icon = Icons.Rounded.Link,
                                onClick = {
                                    isConnectedP2P = true
                                    Toast.makeText(context, "Mikayala s'est connectée au canal direct !", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 2: RÉCEPTEUR / CLIENT (SCANNER & CONNEXION)
            // -------------------------------------------------------------
            if (!isConnectedP2P && selectedRole == 1) {
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
                                text = "Rechercher le Signal de Mikayala 🔍",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scannez le QR Code de votre partenaire ou choisissez le réseau détecté",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // QR Scan Fast Action
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
                                        Text(text = "Scanner le QR Code Partenaire", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(text = "Connexion sans mot de passe en 1 sec ⚡", fontSize = 11.sp, color = VibrantCyan)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = VibrantCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "OU CONNEXION PAR MOT DE PASSE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Detected network card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = DeepSurfaceBlack,
                                border = BorderStroke(1.dp, if (networkDetected) VibrantCyan else BorderSubtleWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { networkDetected = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Wifi,
                                        contentDescription = null,
                                        tint = if (networkDetected) OnlinePresenceGreen else VibrantCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "MIKAYALA_LINK_5G_9924", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(text = "Signal 5GHz • Très fort (-42 dBm)", fontSize = 11.sp, color = OnlinePresenceGreen)
                                    }
                                    RadioButton(
                                        selected = networkDetected,
                                        onClick = { networkDetected = true },
                                        colors = RadioButtonDefaults.colors(selectedColor = VibrantCyan)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Password input
                            OutlinedTextField(
                                value = clientPasswordInput,
                                onValueChange = { clientPasswordInput = it },
                                label = { Text("Mot de passe du point d'accès", fontSize = 12.sp) },
                                placeholder = { Text("ex: mikayala@2026!", fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VibrantCyan,
                                    unfocusedBorderColor = BorderSubtleWhite,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            GlowingGradientButton(
                                text = "Rejoindre le Réseau P2P 🚀",
                                icon = Icons.Rounded.WifiTethering,
                                onClick = {
                                    isConnectedP2P = true
                                    Toast.makeText(context, "Liaison directe P2P établie avec succès !", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 3: ÉTAT CONNECTÉ P2P ACTIF (TRANSFERTS & CHAT DIRECT)
            // -------------------------------------------------------------
            if (isConnectedP2P) {
                // High-Speed Status Dashboard
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
                                        Text(text = "Liaison Directe Établie ⚡", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(text = "Connecté à Mikayala • Canal Chiffré", fontSize = 12.sp, color = OnlinePresenceGreen)
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        isConnectedP2P = false
                                        Toast.makeText(context, "Liaison directe déconnectée", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Couper", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3 Metric Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                P2PMetricCard(label = "Débit Direct", value = "48 Mo/s", icon = "⚡", modifier = Modifier.weight(1f))
                                P2PMetricCard(label = "Data 4G/5G", value = "0 Mo", icon = "🛡️", modifier = Modifier.weight(1f))
                                P2PMetricCard(label = "Portée Signal", value = "99%", icon = "📶", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Fast Action Beam Tools
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
                                transferHistory.add(
                                    0,
                                    P2PTransferItem("Photo_Souvenir_${System.currentTimeMillis().toString().takeLast(4)}.png", 100, "52 Mo/s", true, "18.5 Mo")
                                )
                                Toast.makeText(context, "Photo transmise instantanément en 0.3s !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        P2PBeamToolCard(
                            title = "Note Vocale",
                            subtitle = "Ultra-Qualité",
                            icon = Icons.Rounded.Mic,
                            onClick = {
                                transferHistory.add(
                                    0,
                                    P2PTransferItem("Audio_Intime_Mikayala.flac", 100, "41 Mo/s", true, "6.2 Mo")
                                )
                                Toast.makeText(context, "Audio haute définition transmis !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        P2PBeamToolCard(
                            title = "Vidéo 4K",
                            subtitle = "Beam Rapide",
                            icon = Icons.Rounded.Videocam,
                            onClick = {
                                transferHistory.add(
                                    0,
                                    P2PTransferItem("Video_Vacances_${System.currentTimeMillis().toString().takeLast(3)}.mp4", 100, "49 Mo/s", true, "142 Mo")
                                )
                                Toast.makeText(context, "Vidéo transmise à 49 Mo/s !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Direct P2P Offline Chat Input
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
                                onValueChange = { directMessageText = it },
                                placeholder = { Text("Message direct hors-ligne à Mikayala...", color = TextMuted, fontSize = 13.sp) },
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
                                        repository.sendMessage(content = "📡 [P2P Direct] ${directMessageText.trim()}")
                                        transferHistory.add(
                                            0,
                                            P2PTransferItem("Message: \"${directMessageText.trim()}\"", 100, "Instantané", true, "1 Ko")
                                        )
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

                // Transfer History List
                item {
                    Text(
                        text = "HISTORIQUE DES TRANSFERTS HORS-LIGNE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextSecondary
                    )
                }

                items(transferHistory.size) { index ->
                    val item = transferHistory[index]
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MatteCardDark,
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular Progress Ring Indicator (as in reference image)
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { item.progress / 100f },
                                    modifier = Modifier.fillMaxSize(),
                                    color = VibrantCyan,
                                    trackColor = ProgressRingTrack,
                                    strokeWidth = 3.dp,
                                    strokeCap = StrokeCap.Round
                                )
                                Text(
                                    text = "${item.progress}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantCyan
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.filename, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "${item.size} • ${item.speed} • Terminé ✓", fontSize = 11.sp, color = OnlinePresenceGreen)
                            }

                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = OnlinePresenceGreen, modifier = Modifier.size(20.dp))
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

private data class P2PTransferItem(
    val filename: String,
    val progress: Int,
    val speed: String,
    val isCompleted: Boolean,
    val size: String
)
