package com.example.mikayala.ui.screens

import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.model.ConnectionMode
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.ActiveCallOverlay
import com.example.mikayala.ui.components.GlassNavBarItem
import com.example.mikayala.ui.components.GlassmorphicFloatingNavBar
import com.example.mikayala.ui.components.NeumorphicSquircleButton
import com.example.mikayala.ui.icons.AppleSvgIcons
import java.util.concurrent.TimeUnit

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
    // Recherche / favoris dans la conversation (menu ⋮)
    var chatSearchMode by remember { mutableStateOf<String?>(null) }
    // Badge « non lus » de l'onglet Discussions : messages du partenaire pas encore lus
    val dashboardMessages by repository.allMessages.collectAsState()
    val chatSeenUpTo by repository.chatSeenUpTo.collectAsState()
    val unreadCount = remember(dashboardMessages, chatSeenUpTo) {
        repository.countUnreadFromPartner(dashboardMessages, chatSeenUpTo)
    }
    var showPartnerProfileSheet by remember { mutableStateOf(false) }
    var showOfflineP2PPage by remember { mutableStateOf(false) }
    var activeCallState by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) } // (isActive, isVideo)
    var callIsIncoming by remember { mutableStateOf(false) }
    // Décroché depuis la notification : l'appel démarre sans second appui
    var callAutoAccept by remember { mutableStateOf(false) }
    val callScope = rememberCoroutineScope()
    // Appel sortant : prévient réellement le partenaire (sonnerie) avant d'afficher l'écran d'appel.
    // Avant, les boutons de l'en-tête affichaient l'écran sans rien envoyer.
    val startOutgoingCall: (Boolean) -> Unit = { isVideo ->
        callIsIncoming = false
        callAutoAccept = false
        activeCallState = Pair(true, isVideo)
        callScope.launch { repository.initiateSupabaseCall(isVideo) }
    }

    BackHandler(enabled = showNavDrawerSheet || showPartnerProfileSheet || showOfflineP2PPage || selectedTab != 0) {
        if (showNavDrawerSheet) {
            showNavDrawerSheet = false
        } else if (showPartnerProfileSheet) {
            showPartnerProfileSheet = false
        } else if (showOfflineP2PPage) {
            showOfflineP2PPage = false
        } else if (selectedTab != 0) {
            selectedTab = 0
        }
    }

    // Launcher pour demander la permission audio si AUTO_ANSWER est demandé sans permission
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val isVideo = pendingIntentExtras?.getBoolean("IS_VIDEO", false) ?: false
            callIsIncoming = true
            callAutoAccept = true
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
            extras.getString("CALL_ID")?.takeIf { it.isNotBlank() }?.let { repository.currentIncomingCallId = it }
            if (activeCallState == null) {
                // Notification touchée : écran d'appel entrant (Refuser / Décrocher)
                callIsIncoming = true
                callAutoAccept = false
                activeCallState = Pair(true, isVideo)
            }
            if (isAutoAnswer) {
                val hasAudioPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasAudioPerm) {
                    callIsIncoming = true
                    callAutoAccept = true
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
            // Synchronisation : un seul mécanisme, dans le dépôt (boucle légère + temps réel).
            // Ici, uniquement la détection d'appel entrant.
            repository.requestFullSync()
            while (true) {
                try {
                    val incomingCall = repository.checkForIncomingCall()
                    if (incomingCall != null && activeCallState == null) {
                        callIsIncoming = true
                        callAutoAccept = false
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

    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val navBarVisible = !imeVisible && (selectedTab != 0 || showNavBarInChat)
    val c = MikayalaColors
    val tabStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val partnerDisplayName = userSettings.partnerNickname.ifBlank { "Votre partenaire" }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = c.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    color = c.surface.copy(alpha = 0.96f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Partner Avatar (with integrated live dot) + Name + Subtitle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showPartnerProfileSheet = true }
                                    .testTag("partner_header_button")
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                val isOnline = isPartnerOnline && userSettings.connectionMode == ConnectionMode.ONLINE
                                val dotColor = when {
                                    isPartnerRecording -> c.accent
                                    isPartnerTyping -> c.secondary
                                    isOnline -> c.secondary
                                    else -> c.textMuted
                                }
                                Box(modifier = Modifier.size(40.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(c.accentSoft)
                                            .border(1.dp, c.borderStrong, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (userSettings.partnerAvatarUrl.isNotBlank()) {
                                            coil.compose.AsyncImage(
                                                model = userSettings.partnerAvatarUrl,
                                                contentDescription = "Photo de votre partenaire",
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = AppleSvgIcons.Person,
                                                contentDescription = "Photo de votre partenaire",
                                                tint = c.accent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                            .border(1.5.dp, c.surface, CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = partnerDisplayName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = c.textPrimary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (userSettings.showLoveDaysInHeader && coupleSpace.anniversaryDate > 0L) {
                                            val loveDays = ((System.currentTimeMillis() - coupleSpace.anniversaryDate).coerceAtLeast(0L) / TimeUnit.DAYS.toMillis(1)) + 1
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = c.accentSoft
                                            ) {
                                                Text(
                                                    text = "${loveDays}j",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = c.accent,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    val isOfflineMode = userSettings.connectionMode != ConnectionMode.ONLINE
                                    val statusText = when {
                                        isOfflineMode -> "Mode Hors-Ligne (P2P)"
                                        isPartnerRecording -> "Enregistre un audio…"
                                        isPartnerTyping -> "En train d'écrire…"
                                        isOnline -> "En ligne"
                                        userSettings.partnerStatus.isNotBlank() && userSettings.partnerStatus != "Hors ligne" -> userSettings.partnerStatus
                                        else -> "Hors ligne"
                                    }
                                    val subtitleColor = when {
                                        isOfflineMode -> c.danger
                                        isPartnerRecording -> c.accent
                                        isPartnerTyping || isOnline -> c.secondary
                                        else -> c.textMuted
                                    }
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        fontWeight = if (isOnline || isPartnerTyping || isPartnerRecording || isOfflineMode) FontWeight.Medium else FontWeight.Normal,
                                        color = subtitleColor,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Right iOS 18 Icon Buttons (48dp touch targets, SF Symbols style)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { startOutgoingCall(true) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("header_video_call")
                                ) {
                                    Icon(
                                        imageVector = AppleSvgIcons.Video,
                                        contentDescription = "Appel Vidéo",
                                        tint = c.secondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { startOutgoingCall(false) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("header_audio_call")
                                ) {
                                    Icon(
                                        imageVector = AppleSvgIcons.Phone,
                                        contentDescription = "Appel Audio",
                                        tint = c.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Box {
                                    var menuOpen by remember { mutableStateOf(false) }
                                    val disappearingSecs by repository.disappearingSeconds.collectAsState()
                                    var showDisappearingDialog by remember { mutableStateOf(false) }
                                    if (showDisappearingDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showDisappearingDialog = false },
                                            containerColor = c.modal,
                                            title = { Text("Messages éphémères", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
                                            text = {
                                                Column {
                                                    Text(
                                                        "Les nouveaux messages disparaîtront pour vous deux après la durée choisie.",
                                                        color = c.textSecondary, fontSize = 13.sp
                                                    )
                                                    Spacer(Modifier.height(8.dp))
                                                    listOf(0 to "Désactivé", 86400 to "24 heures", 604800 to "7 jours", 7776000 to "90 jours").forEach { (secs, label) ->
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    showDisappearingDialog = false
                                                                    callScope.launch { repository.setDisappearingSeconds(secs) }
                                                                }
                                                                .padding(vertical = 10.dp)
                                                        ) {
                                                            RadioButton(selected = disappearingSecs == secs, onClick = null)
                                                            Spacer(Modifier.width(10.dp))
                                                            Text(label, color = c.textPrimary)
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { showDisappearingDialog = false }) { Text("Fermer", color = c.accent) }
                                            }
                                        )
                                    }
                                    val isOnlineMode = userSettings.connectionMode == ConnectionMode.ONLINE
                                    IconButton(
                                        onClick = { menuOpen = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("header_more_menu")
                                    ) {
                                        Icon(
                                            AppleSvgIcons.More,
                                            contentDescription = "Plus d'options",
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false },
                                        containerColor = c.modal
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Profil de $partnerDisplayName", color = c.textPrimary) },
                                            leadingIcon = { Icon(AppleSvgIcons.Person, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp)) },
                                            onClick = { menuOpen = false; showPartnerProfileSheet = true }
                                        )
                                        if (selectedTab == 0) {
                                            DropdownMenuItem(
                                                text = { Text("Rechercher", color = c.textPrimary) },
                                                leadingIcon = { Icon(AppleSvgIcons.Search, contentDescription = null, tint = c.secondary, modifier = Modifier.size(18.dp)) },
                                                onClick = { menuOpen = false; chatSearchMode = "search" }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Messages favoris", color = c.textPrimary) },
                                                leadingIcon = { Icon(AppleSvgIcons.Star, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp)) },
                                                onClick = { menuOpen = false; chatSearchMode = "starred" }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "Messages éphémères" + when (disappearingSecs) {
                                                            86400 -> " · 24 h"; 604800 -> " · 7 j"; 7776000 -> " · 90 j"; else -> ""
                                                        },
                                                        color = c.textPrimary
                                                    )
                                                },
                                                leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null, tint = c.secondary, modifier = Modifier.size(18.dp)) },
                                                onClick = { menuOpen = false; showDisappearingDialog = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (showNavBarInChat) "Plein écran" else "Afficher les onglets", color = c.textPrimary) },
                                                leadingIcon = { Icon(if (showNavBarInChat) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) },
                                                onClick = { menuOpen = false; showNavBarInChat = !showNavBarInChat }
                                            )
                                        }
                                        HorizontalDivider(color = c.border, thickness = 0.5.dp)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isOnlineMode) "Passer en P2P Hors-ligne" else "Revenir En ligne",
                                                    color = c.textPrimary
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (isOnlineMode) AppleSvgIcons.Wifi else AppleSvgIcons.Cloud,
                                                    contentDescription = null,
                                                    tint = if (isOnlineMode) c.accent else c.secondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                menuOpen = false
                                                if (isOnlineMode) {
                                                    repository.updateConnectionMode(ConnectionMode.OFFLINE_P2P)
                                                    showOfflineP2PPage = true
                                                } else {
                                                    repository.updateConnectionMode(ConnectionMode.ONLINE)
                                                    Toast.makeText(context, "Mode En Ligne activé", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Raccourcis et verrouillage", color = c.textPrimary) },
                                            leadingIcon = { Icon(AppleSvgIcons.Sparkles, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp)) },
                                            onClick = { menuOpen = false; showNavDrawerSheet = true }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = c.border, thickness = 0.5.dp)
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = navBarVisible,
                    enter = slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(140))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassmorphicFloatingNavBar(
                            items = listOf(
                                GlassNavBarItem(0, "Discussions", AppleSvgIcons.Chat, AppleSvgIcons.ChatFilled, badgeCount = if (selectedTab == 0) 0 else unreadCount),
                                GlassNavBarItem(1, "Appels", AppleSvgIcons.Phone, AppleSvgIcons.Phone),
                                GlassNavBarItem(2, "Couple", AppleSvgIcons.Heart, AppleSvgIcons.HeartFilled),
                                GlassNavBarItem(3, "Coffre", AppleSvgIcons.Lock, AppleSvgIcons.LockOpen),
                                GlassNavBarItem(4, "Réglages", AppleSvgIcons.Settings, AppleSvgIcons.Settings)
                            ),
                            selectedIndex = selectedTab,
                            onItemSelected = { selectedTab = it },
                            navBarStyle = userSettings.navBarStyle
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .then(if (navBarVisible) Modifier else Modifier.navigationBarsPadding())
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(190, easing = FastOutSlowInEasing)) { w -> dir * (w / 10) } + fadeIn(tween(170))) togetherWith
                            (slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { w -> -dir * (w / 10) } + fadeOut(tween(130)))
                    },
                    label = "dashboard_tabs_transition"
                ) { tab ->
                    tabStateHolder.SaveableStateProvider(tab) {
                        when (tab) {
                            0 -> ChatScreen(
                                repository = repository,
                                onStartCall = { isVideo -> startOutgoingCall(isVideo) },
                                searchMode = chatSearchMode,
                                onCloseSearch = { chatSearchMode = null }
                            )
                            1 -> CallsScreen(
                                repository = repository,
                                onStartCall = { isVideo -> startOutgoingCall(isVideo) }
                            )
                            2 -> CoupleSpaceScreen(repository = repository)
                            3 -> VaultScreen(repository = repository)
                            4 -> SettingsScreen(repository = repository)
                        }
                    }
                }
            }
        }

        // iOS 18 Grouped Inset Quick Navigation & Actions Sheet
        if (showNavDrawerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNavDrawerSheet = false },
                containerColor = c.modal,
                scrimColor = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Espaces & Raccourcis",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                            Text(
                                text = "Accès rapide et mode de connexion",
                                fontSize = 12.sp,
                                color = c.textSecondary
                            )
                        }
                        IconButton(
                            onClick = { showNavDrawerSheet = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Close,
                                contentDescription = "Fermer",
                                tint = c.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val isOnlineMode = userSettings.connectionMode == ConnectionMode.ONLINE
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = c.surfaceElevated,
                        border = BorderStroke(0.5.dp, c.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isOnlineMode) c.secondarySoft else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!isOnlineMode) {
                                            repository.updateConnectionMode(ConnectionMode.ONLINE)
                                            Toast.makeText(context, "Mode En Ligne activé", Toast.LENGTH_SHORT).show()
                                        }
                                        showNavDrawerSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppleSvgIcons.Cloud, contentDescription = null, tint = if (isOnlineMode) c.secondary else c.textMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("En ligne", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isOnlineMode) c.secondary else c.textSecondary)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (!isOnlineMode) c.accentSoft else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showNavDrawerSheet = false
                                        repository.updateConnectionMode(ConnectionMode.OFFLINE_P2P)
                                        showOfflineP2PPage = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppleSvgIcons.Wifi, contentDescription = null, tint = if (!isOnlineMode) c.accent else c.textMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hors-ligne P2P", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (!isOnlineMode) c.accent else c.textSecondary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val navTabs = listOf(
                        Triple(0, "Discussions", Triple(AppleSvgIcons.Chat, c.secondary, "Messagerie privée & vocaux")),
                        Triple(1, "Appels", Triple(AppleSvgIcons.Phone, c.accent, "Audio, vidéo & historique")),
                        Triple(2, "Espace Couple", Triple(AppleSvgIcons.Heart, c.accent, "Jeux à deux & calendrier")),
                        Triple(3, "Coffre-Fort", Triple(AppleSvgIcons.Lock, c.secondary, "Souvenirs protégés")),
                        Triple(4, "Paramètres", Triple(AppleSvgIcons.Settings, c.textSecondary, "Apparence, thème & PIN"))
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = c.surfaceElevated,
                        border = BorderStroke(0.5.dp, c.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            navTabs.forEachIndexed { idx, (tabIdx, title, meta) ->
                                val (iconVec, tintCol, subtitle) = meta
                                val isCurrent = selectedTab == tabIdx
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clickable {
                                            selectedTab = tabIdx
                                            showNavDrawerSheet = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(tintCol.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(iconVec, contentDescription = null, tint = tintCol, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isCurrent) c.accent else c.textPrimary
                                        )
                                        Text(
                                            text = subtitle,
                                            fontSize = 11.sp,
                                            color = c.textSecondary
                                        )
                                    }
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = AppleSvgIcons.Check,
                                            contentDescription = "Actif",
                                            tint = c.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (idx < navTabs.lastIndex) {
                                    HorizontalDivider(
                                        color = c.border,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 58.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            showNavDrawerSheet = false
                            onLockApp()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.danger.copy(alpha = 0.14f)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.5.dp, c.danger.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("lock_app_button")
                    ) {
                        Icon(
                            imageVector = AppleSvgIcons.Lock,
                            contentDescription = null,
                            tint = c.danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verrouiller l'application",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.danger
                        )
                    }
                }
            }
        }

        // Partner Profile Modal Sheet (WhatsApp contact details)
        if (showPartnerProfileSheet) {
            com.example.mikayala.ui.components.PartnerProfileBottomSheet(
                repository = repository,
                onDismiss = { showPartnerProfileSheet = false },
                onStartCall = { isVideo -> startOutgoingCall(isVideo) },
                onOpenSearch = { selectedTab = 0; chatSearchMode = "search" }
            )
        }

        // Petites animations violet/vert (cœurs, papillons, étoiles) : couche qui ne capte aucun toucher
        com.example.mikayala.ui.effects.LoveEffectsLayer()

        // Active Call Overlay if active
        activeCallState?.let { (_, isVideo) ->
            // key : un nouvel écran (et un nouvel appel) à chaque appel
            androidx.compose.runtime.key(activeCallState, callIsIncoming) {
                val incoming = callIsIncoming
                ActiveCallOverlay(
                    partnerName = userSettings.partnerNickname.ifBlank { "Mon partenaire" },
                    partnerAvatarUrl = userSettings.partnerAvatarUrl,
                    isVideo = isVideo,
                    isIncoming = incoming,
                    autoAccept = callAutoAccept,
                    resolveCallId = { repository.resolveActiveCallId(incoming) },
                    signaling = repository.callSignaling,
                    onDecline = { id ->
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            repository.declineIncomingCall(id.orEmpty())
                        }
                    },
                    onEndCall = { duration ->
                        // Appel sortant déjà inscrit à l'historique au lancement : on n'ajoute que les appels reçus
                        if (incoming) repository.addCallLog(isVideo = isVideo, durationSeconds = duration)
                        activeCallState = null
                        callAutoAccept = false
                        repository.clearActiveCallIds()
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            if (!incoming) repository.endOutgoingCall(duration)
                        }
                    }
                )
            }
        }
    }
}

private data class NavigationItem(
    val index: Int,
    val label: String,
    val icon: ImageVector
)

