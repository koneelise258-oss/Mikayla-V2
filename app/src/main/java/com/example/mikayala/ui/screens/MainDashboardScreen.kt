package com.example.mikayala.ui.screens

import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    pendingIntentExtras: Bundle? = null,
    onClearIntentExtras: () -> Unit = {},
    onLockApp: () -> Unit
) {
    val context = LocalContext.current
    // Tabs: 0: Chat, 1: Appels, 2: Espace Couple, 3: Coffre, 4: Réglages
    var selectedTab by remember { mutableIntStateOf(0) }
    // Barre de navigation visible par défaut (masquable depuis le menu ⋮ pour le plein écran)
    var showNavBarInChat by remember { mutableStateOf(true) }
    var showNavDrawerSheet by remember { mutableStateOf(false) }
    var showPartnerProfileSheet by remember { mutableStateOf(false) }
    var showOfflineP2PPage by remember { mutableStateOf(false) }
    var activeCallState by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) } // (isActive, isVideo)

    // Launcher pour demander la permission audio si AUTO_ANSWER est demandé sans permission
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val isVideo = pendingIntentExtras?.getBoolean("IS_VIDEO", false) ?: false
            activeCallState = Pair(true, isVideo)
        } else {
            Toast.makeText(context, "Permission microphone requise pour décrocher l'appel", Toast.LENGTH_SHORT).show()
        }
    }

    // Gestion propre des intentions de notifications ("OPEN_CALL", "AUTO_ANSWER", "OPEN_CHAT", "OPEN_GAMES")
    LaunchedEffect(pendingIntentExtras) {
        val extras = pendingIntentExtras ?: return@LaunchedEffect
        val isOpenCall = extras.getBoolean("OPEN_CALL", false)
        val isAutoAnswer = extras.getBoolean("AUTO_ANSWER", false)
        val isOpenChat = extras.getBoolean("OPEN_CHAT", false)
        val isOpenGames = extras.getBoolean("OPEN_GAMES", false)
        val isVideo = extras.getBoolean("IS_VIDEO", false)

        if (isOpenCall) {
            selectedTab = 1
            if (isAutoAnswer) {
                val hasAudioPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasAudioPerm) {
                    activeCallState = Pair(true, isVideo)
                } else {
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        } else if (isOpenChat) {
            selectedTab = 0
        } else if (isOpenGames) {
            selectedTab = 2
        }
        onClearIntentExtras()
    }

    val coupleSpace by repository.activeCoupleSpace.collectAsState()
    val userSettings by repository.userSettings.collectAsState()
    val isPartnerOnline by repository.isPartnerOnline.collectAsState()
    val isPartnerTyping by repository.isPartnerTyping.collectAsState()
    val isPartnerRecording by repository.isPartnerRecordingAudio.collectAsState()


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
                    color = DeepNight,
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
                                        .background(com.example.mikayala.theme.MikayalaColors.accentSoft),
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
                                                        OnlinePresenceGreen
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
                                // Menu ⋮ : regroupe les actions secondaires (auparavant deux boutons carrés)
                                Box {
                                    var menuOpen by remember { mutableStateOf(false) }
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d'options", tint = TextSecondary)
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Espaces et raccourcis") },
                                            leadingIcon = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                                            onClick = { menuOpen = false; showNavDrawerSheet = true }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Profil de mon partenaire") },
                                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                            onClick = { menuOpen = false; showPartnerProfileSheet = true }
                                        )
                                        if (selectedTab == 0) {
                                            DropdownMenuItem(
                                                text = { Text(if (showNavBarInChat) "Plein écran (masquer la barre)" else "Afficher la barre de navigation") },
                                                leadingIcon = { Icon(if (showNavBarInChat) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit, contentDescription = null) },
                                                onClick = { menuOpen = false; showNavBarInChat = !showNavBarInChat }
                                            )
                                        }
                                    }
                                }
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
                                color = com.example.mikayala.theme.MikayalaColors.surfaceSunken,
                                border = BorderStroke(0.5.dp, BorderSubtleWhite),
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
                                        color = if (isOnline) com.example.mikayala.theme.MikayalaColors.accentSoft else Color.Transparent,
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
                                                color = if (isOnline) TextPrimary else TextSecondary
                                            )
                                        }
                                    }

                                    // Option 2: Hors-Ligne (Discreet)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (!isOnline) com.example.mikayala.theme.MikayalaColors.accentSoft else Color.Transparent,
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
                                                color = if (!isOnline) TextPrimary else TextSecondary
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
                                border = BorderStroke(0.5.dp, BorderSubtleWhite),
                                shadowElevation = 1.dp,
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
                                GlassNavBarItem(0, "Discussions", Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Rounded.Chat),
                                GlassNavBarItem(1, "Appels", Icons.Rounded.Call, Icons.Rounded.Call),
                                GlassNavBarItem(2, "Couple", Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
                                GlassNavBarItem(3, "Coffre", Icons.Rounded.Lock, Icons.Rounded.LockOpen),
                                GlassNavBarItem(4, "Paramètres", Icons.Rounded.Settings, Icons.Rounded.Settings)
                            ),
                            selectedIndex = selectedTab,
                            onItemSelected = {
                                selectedTab = it
                                if (it != 0) showNavBarInChat = false
                            }
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
                partnerName = userSettings.partnerNickname.ifBlank { "Mon partenaire" },
                isVideo = isVideo,
                onEndCall = { duration ->
                    repository.addCallLog(isVideo = isVideo, durationSeconds = duration)
                    activeCallState = null
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        repository.endOutgoingCall(duration)
                    }
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

