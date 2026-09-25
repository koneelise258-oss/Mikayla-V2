package com.example.mikayala.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.model.ConnectionMode
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.ActiveCallOverlay
import com.example.mikayala.ui.components.GlassNavBarItem
import com.example.mikayala.ui.components.GlassmorphicFloatingNavBar
import com.example.mikayala.ui.components.GlowingGradientButton
import com.example.mikayala.ui.components.NeumorphicSquircleButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    repository: MikayalaRepository,
    onLockApp: () -> Unit
) {
    val context = LocalContext.current
    // Tabs: 0: Chat, 1: Appels, 2: Espace Couple, 3: Coffre, 4: Réglages
    var selectedTab by remember { mutableIntStateOf(0) }
    var showNavBarInChat by remember { mutableStateOf(false) }
    var showNavDrawerSheet by remember { mutableStateOf(false) }
    var showPartnerProfileSheet by remember { mutableStateOf(false) }
    var showOfflineP2PPage by remember { mutableStateOf(false) }
    var activeCallState by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) } // (isActive, isVideo)

    val coupleSpace by repository.activeCoupleSpace.collectAsState()
    val userSettings by repository.userSettings.collectAsState()
    val isPartnerOnline by repository.isPartnerOnline.collectAsState()
    val isPartnerTyping by repository.isPartnerTyping.collectAsState()
    val isPartnerRecording by repository.isPartnerRecordingAudio.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_green_dot")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    // Background Realtime Sync for Supabase (Messages, Shared Vault, Call Signals)
    LaunchedEffect(coupleSpace.pairingCode, coupleSpace.isPaired) {
        if (coupleSpace.pairingCode.isNotEmpty() && coupleSpace.isPaired) {
            while (true) {
                try {
                    repository.syncWithSupabase()
                    if (coupleSpace.id.isNotEmpty()) {
                        repository.markMessagesDelivered(coupleSpace.id)
                    }
                    val incomingCall = repository.checkForIncomingCall()
                    if (incomingCall != null && activeCallState == null) {
                        activeCallState = incomingCall
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    if (showOfflineP2PPage) {
        OfflineP2PLinkScreen(
            repository = repository,
            onBack = { showOfflineP2PPage = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MatteSlateBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    color = MatteCardDark,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Top Header Row (Style Reference Image)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Squircle Buttons (Navigation & Nav Bar Toggle)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeumorphicSquircleButton(
                                    icon = Icons.Rounded.Widgets,
                                    contentDescription = "Navigation & Espaces",
                                    onClick = { showNavDrawerSheet = true },
                                    size = 44.dp,
                                    iconSize = 22.dp,
                                    tint = VibrantCyan
                                )

                                if (selectedTab == 0) {
                                    NeumorphicSquircleButton(
                                        icon = if (showNavBarInChat) Icons.Rounded.Navigation else Icons.Rounded.Explore,
                                        contentDescription = if (showNavBarInChat) "Masquer la barre" else "Afficher la barre",
                                        onClick = {
                                            showNavBarInChat = !showNavBarInChat
                                            val msg = if (showNavBarInChat) "Barre de navigation affichée 🧭" else "Plein écran Chat activé 💬"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        },
                                        size = 44.dp,
                                        iconSize = 20.dp,
                                        tint = if (showNavBarInChat) VibrantCyan else TextSecondary,
                                        backgroundColor = if (showNavBarInChat) CardDarkElevated else MatteSquircle
                                    )
                                }
                            }

                            // Center Title / Active Partner (WhatsApp style info click)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showPartnerProfileSheet = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Brush.radialGradient(listOf(VibrantCyan.copy(alpha = 0.3f), Color.Transparent)))
                                        .border(1.5.dp, VibrantCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = "Avatar",
                                        tint = VibrantCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = userSettings.partnerNickname,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val isOnline = isPartnerOnline && userSettings.connectionMode == com.example.mikayala.data.model.ConnectionMode.ONLINE
                                        val statusText = when {
                                            isPartnerRecording -> "Enregistre un audio... 🎙️"
                                            isPartnerTyping -> "En train d'écrire... ✨"
                                            isOnline -> "En ligne"
                                            userSettings.partnerStatus.isNotBlank() && userSettings.partnerStatus != "Hors ligne" -> userSettings.partnerStatus
                                            else -> "Hors ligne"
                                        }
                                        val dotColor = when {
                                            isPartnerRecording -> AccentRose
                                            isPartnerTyping -> VibrantCyan
                                            isOnline -> OnlinePresenceGreen
                                            else -> TextMuted
                                        }
                                        val textColor = when {
                                            isPartnerRecording -> AccentRose
                                            isPartnerTyping -> VibrantCyan
                                            isOnline -> TextSecondary
                                            else -> TextMuted
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isOnline && !isPartnerRecording && !isPartnerTyping) {
                                                        OnlinePresenceGreen.copy(alpha = pulseAlpha)
                                                    } else {
                                                        dotColor
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = statusText,
                                            fontSize = 11.sp,
                                            color = textColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Right Squircle Buttons (Search & Calls)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeumorphicSquircleButton(
                                    icon = Icons.Rounded.Videocam,
                                    contentDescription = "Appel Vidéo",
                                    onClick = { activeCallState = Pair(true, true) },
                                    size = 44.dp,
                                    iconSize = 20.dp,
                                    tint = VibrantCyan
                                )
                                NeumorphicSquircleButton(
                                    icon = Icons.Rounded.Call,
                                    contentDescription = "Appel Audio",
                                    onClick = { activeCallState = Pair(true, false) },
                                    size = 44.dp,
                                    iconSize = 20.dp,
                                    tint = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Discreet Connection Mode Pill Selector (Option 1: En Ligne / Option 2: Hors-Ligne)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0x1F141C2B),
                                border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isOnline = userSettings.connectionMode == ConnectionMode.ONLINE

                                    // Option 1: En Ligne (Discreet)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isOnline) Color(0x3800E5FF) else Color.Transparent,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                if (!isOnline) {
                                                    repository.updateConnectionMode(ConnectionMode.ONLINE)
                                                    Toast.makeText(context, "Mode En Ligne activé 🌐", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isOnline) VibrantCyan else TextMuted)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "En Ligne",
                                                fontSize = 11.sp,
                                                fontWeight = if (isOnline) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isOnline) Color.White else TextSecondary
                                            )
                                        }
                                    }

                                    // Option 2: Hors-Ligne (Discreet)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (!isOnline) Color(0x38FF4081) else Color.Transparent,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                repository.updateConnectionMode(ConnectionMode.OFFLINE_P2P)
                                                showOfflineP2PPage = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (!isOnline) AccentRose else TextMuted)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Hors-Ligne",
                                                fontSize = 11.sp,
                                                fontWeight = if (!isOnline) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (!isOnline) Color.White else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = selectedTab != 0 || showNavBarInChat,
                    enter = slideInVertically { it } + fadeIn(tween(250)),
                    exit = slideOutVertically { it } + fadeOut(tween(250))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedTab == 0) {
                            Surface(
                                shape = CircleShape,
                                color = CardDarkElevated,
                                border = BorderStroke(1.dp, BorderSubtleWhite),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clip(CircleShape)
                                    .clickable { showNavBarInChat = false }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Masquer",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Plein écran Chat 💬",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        GlassmorphicFloatingNavBar(
                            items = listOf(
                                GlassNavBarItem(0, "Chat", Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Rounded.Chat),
                                GlassNavBarItem(1, "Appels", Icons.Rounded.Call, Icons.Rounded.Call),
                                GlassNavBarItem(2, "Couple", Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
                                GlassNavBarItem(3, "Coffre", Icons.Rounded.Lock, Icons.Rounded.LockOpen),
                                GlassNavBarItem(4, "Réglages", Icons.Rounded.Tune, Icons.Rounded.Settings)
                            ),
                            selectedIndex = selectedTab,
                            onItemSelected = {
                                selectedTab = it
                                if (it != 0) showNavBarInChat = false
                            },
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width / 3 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(220)))
                        } else {
                            (slideInHorizontally { width -> -width / 3 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally { width -> width / 3 } + fadeOut(tween(220)))
                        }
                    },
                    label = "dashboard_tabs_transition"
                ) { tab ->
                    when (tab) {
                        0 -> ChatScreen(
                            repository = repository,
                            onStartCall = { isVideo -> activeCallState = Pair(true, isVideo) }
                        )
                        1 -> CallsScreen(
                            repository = repository,
                            onStartCall = { isVideo -> activeCallState = Pair(true, isVideo) }
                        )
                        2 -> CoupleSpaceScreen(repository = repository)
                        3 -> VaultScreen(repository = repository)
                        4 -> SettingsScreen(repository = repository)
                    }
                }
            }
        }

        // Quick Navigation Drawer / Bottom Sheet Modal
        if (showNavDrawerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNavDrawerSheet = false },
                containerColor = CardModalSurface,
                scrimColor = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(VibrantCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Widgets,
                                    contentDescription = null,
                                    tint = VibrantCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Navigation & Espaces 📱",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Sélectionnez votre onglet destination",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(onClick = { showNavDrawerSheet = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Fermer",
                                tint = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // List of 5 Main Navigation Spaces
                    val navTabs = listOf(
                        Triple(0, "💬 Chat Confidentiel", "Messagerie privée & vocaux"),
                        Triple(1, "📞 Appels P2P Directs", "Appels vidéo & audio chiffrés"),
                        Triple(2, "💖 Espace de Couple", "Love Time, 8 Jeux & Calendrier"),
                        Triple(3, "🔐 Coffre-Fort Secret", "Photos & souvenirs protégés"),
                        Triple(4, "⚙️ Paramètres & Code PIN", "Thèmes & Sécurité")
                    )

                    navTabs.forEach { (index, title, subtitle) ->
                        val isCurrent = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isCurrent) VibrantCyan.copy(alpha = 0.15f) else MatteCardElevated,
                            border = BorderStroke(1.dp, if (isCurrent) VibrantCyan else BorderSubtleWhite),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedTab = index
                                    showNavDrawerSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) VibrantCyan else TextPrimary
                                    )
                                    Text(
                                        text = subtitle,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                if (isCurrent) {
                                    Surface(
                                        shape = CircleShape,
                                        color = VibrantCyan,
                                        modifier = Modifier.size(10.dp)
                                    ) {}
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action: Lock App
                    Button(
                        onClick = {
                            showNavDrawerSheet = false
                            onLockApp()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = AccentRose,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verrouiller l'application 🔒",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRose
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // Partner Profile Modal Sheet (WhatsApp contact details)
        if (showPartnerProfileSheet) {
            com.example.mikayala.ui.components.PartnerProfileBottomSheet(
                repository = repository,
                onDismiss = { showPartnerProfileSheet = false },
                onStartCall = { isVideo -> activeCallState = Pair(true, isVideo) },
                onOpenSearch = { selectedTab = 0 }
            )
        }

        // Active Call Overlay if active
        activeCallState?.let { (_, isVideo) ->
            ActiveCallOverlay(
                partnerName = "Mikayala",
                isVideo = isVideo,
                onEndCall = { duration ->
                    repository.addCallLog(isVideo = isVideo, durationSeconds = duration)
                    activeCallState = null
                }
            )
        }
    }
}

private data class NavigationItem(
    val index: Int,
    val label: String,
    val icon: ImageVector
)

