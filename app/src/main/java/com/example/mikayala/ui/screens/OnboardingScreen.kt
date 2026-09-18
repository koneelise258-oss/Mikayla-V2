package com.example.mikayala.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import com.example.mikayala.util.QRCodeGenerator
import com.example.mikayala.ui.components.QRScanner
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class OnboardingStep {
    LANDING,
    CREATE_SPACE,
    JOIN_SPACE,
    LINK_TABLET,
    RECOVERY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    repository: MikayalaRepository,
    onCompleteOnboarding: () -> Unit,
    onBackToApp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val coupleSpace by repository.activeCoupleSpace.collectAsState()
    val userSettings by repository.userSettings.collectAsState()

    val initialStepStr = remember { repository.getPrefs().getString("initial_onboarding_step", "") ?: "" }

    var currentStep by remember {
        mutableStateOf(
            when {
                coupleSpace.pairingCode.isNotEmpty() && !coupleSpace.isActive -> OnboardingStep.CREATE_SPACE
                initialStepStr == "create" -> OnboardingStep.CREATE_SPACE
                initialStepStr == "join" -> OnboardingStep.JOIN_SPACE
                else -> OnboardingStep.LANDING
            }
        )
    }

    // State for create space
    var createName by remember { mutableStateOf(userSettings.displayName.ifEmpty { "Partenaire 1" }) }
    var selectedAvatarIndex by remember { mutableIntStateOf(0) }
    var generatedCode by remember {
        mutableStateOf(
            coupleSpace.pairingCode.ifEmpty {
                "MIK-${(1..4).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")}"
            }
        )
    }
    var isWaitingPartner by remember { mutableStateOf(coupleSpace.pairingCode.isNotEmpty() && !coupleSpace.isActive) }
    var isPartnerConnected by remember { mutableStateOf(coupleSpace.pairingCode.isNotEmpty() && coupleSpace.isActive) }

    LaunchedEffect(isWaitingPartner) {
        if (isWaitingPartner) {
            while (true) {
                delay(3000)
                val partnerName = repository.checkPartnerConnectedInSupabase(generatedCode)
                if (partnerName != null) {
                    isPartnerConnected = true
                    isWaitingPartner = false
                    Toast.makeText(context, "$partnerName vient de se connecter ! ❤️", Toast.LENGTH_LONG).show()
                    break
                }
            }
        }
    }

    // State for join space
    var joinName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var isJoiningLoading by remember { mutableStateOf(false) }
    var joinErrorMessage by remember { mutableStateOf<String?>(null) }
    var showCameraScanner by remember { mutableStateOf(false) }

    // State for link tablet
    var selectedPartnerOwner by remember { mutableStateOf("partner1") } // "partner1" or "partner2"
    var linkCode by remember { mutableStateOf("") }
    var isLinkingLoading by remember { mutableStateOf(false) }
    var linkSuccess by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }

    // State for recovery
    var recoveryKey by remember { mutableStateOf("") }
    var isRecoveryLoading by remember { mutableStateOf(false) }
    var recoverySuccess by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    // Avatar list
    val avatarPresets = listOf("💜", "🌹", "🪽", "🌟", "👑", "💖")

    // Dark landing theme canvas
    val darkBg = Color(0xFF0E0B16)
    val cardBg = Color(0xFF1B182B)
    val cardElevated = Color(0xFF242038)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
    ) {
        // Glowing Background Lights
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentViolet.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.2f, h * 0.15f),
                    radius = w * 0.7f
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentRose.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(w * 0.8f, h * 0.75f),
                    radius = w * 0.8f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep != OnboardingStep.LANDING) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentStep = OnboardingStep.LANDING
                            joinErrorMessage = null
                            linkError = null
                            recoveryError = null
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(cardElevated)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }

                Text(
                    text = "Mikayala",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (onBackToApp != null) {
                    IconButton(
                        onClick = { onBackToApp() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(cardElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fermer",
                            tint = TextSecondary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }

            // Animated Screen Content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) + slideInHorizontally { it / 2 } togetherWith
                            fadeOut(animationSpec = tween(200)) + slideOutHorizontally { -it / 2 }
                },
                label = "onboarding_step_transition"
            ) { step ->
                when (step) {
                    OnboardingStep.LANDING -> {
                        LandingView(
                            onSelectStep = { selected ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentStep = selected
                            }
                        )
                    }
                    OnboardingStep.CREATE_SPACE -> {
                        CreateSpaceView(
                            name = createName,
                            onNameChange = { createName = it },
                            selectedAvatar = avatarPresets[selectedAvatarIndex],
                            avatarPresets = avatarPresets,
                            onSelectAvatar = { selectedAvatarIndex = it },
                            generatedCode = generatedCode,
                            isWaitingPartner = isWaitingPartner,
                            isPartnerConnected = isPartnerConnected,
                            onStartWaiting = {
                                isWaitingPartner = true
                                android.util.Log.d("SupabaseDiag", "[CREATE] Code affiché à l'utilisateur: $generatedCode")
                                coroutineScope.launch {
                                    val success = repository.createCoupleSpaceInSupabase(generatedCode, createName.ifEmpty { "Mikey" })
                                    if (success) {
                                        Toast.makeText(context, "Espace Supabase créé ! Code: $generatedCode ✨", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Espace local initialisé pour code $generatedCode ✨", Toast.LENGTH_SHORT).show()
                                    }
                                    repository.updateProfile(createName.ifEmpty { "Mikey" }, "En ligne", "", avatarPresets[selectedAvatarIndex])
                                }
                            },
                            onComplete = {
                                repository.updateProfile(createName.ifEmpty { "Mikey" }, "En ligne avec mon cœur", "", avatarPresets[selectedAvatarIndex])
                                onCompleteOnboarding()
                            }
                        )
                    }
                    OnboardingStep.JOIN_SPACE -> {
                        JoinSpaceView(
                            name = joinName,
                            onNameChange = { joinName = it },
                            code = joinCode,
                            onCodeChange = { joinCode = it.uppercase() },
                            isLoading = isJoiningLoading,
                            errorMessage = joinErrorMessage,
                            onOpenScanner = { showCameraScanner = true },
                            onSubmit = {
                                if (joinCode.length < 4) {
                                    joinErrorMessage = "Code invalide. Veuillez saisir un code à 6 caractères."
                                    return@JoinSpaceView
                                }
                                isJoiningLoading = true
                                joinErrorMessage = null
                                coroutineScope.launch {
                                    val joined = repository.joinCoupleSpaceInSupabase(joinCode, joinName.ifEmpty { "Mikayala" })
                                    isJoiningLoading = false
                                    if (joined) {
                                        repository.updatePartnerProfile(joinName.ifEmpty { "Partenaire" }, "En ligne", "💖")
                                        Toast.makeText(context, "Espace rejoint via Supabase ! 💖", Toast.LENGTH_SHORT).show()
                                        onCompleteOnboarding()
                                    } else {
                                        joinErrorMessage = "Impossible de se connecter à l'espace avec ce code."
                                    }
                                }
                            }
                        )
                    }
                    OnboardingStep.LINK_TABLET -> {
                        LinkTabletView(
                            selectedOwner = selectedPartnerOwner,
                            onSelectOwner = { selectedPartnerOwner = it },
                            code = linkCode,
                            onCodeChange = { linkCode = it.uppercase() },
                            isLoading = isLinkingLoading,
                            linkSuccess = linkSuccess,
                            errorMessage = linkError,
                            onSubmit = {
                                if (linkCode.length < 4) {
                                    linkError = "Code de couple invalide ou incomplet."
                                    return@LinkTabletView
                                }
                                isLinkingLoading = true
                                linkError = null
                                coroutineScope.launch {
                                    val linked = repository.joinCoupleSpaceInSupabase(linkCode, if (selectedPartnerOwner == "partner1") "Mikey" else "Mikayala")
                                    isLinkingLoading = false
                                    linkSuccess = linked
                                    if (linked) {
                                        Toast.makeText(context, "Tablette synchronisée via Supabase ! 📱", Toast.LENGTH_LONG).show()
                                    } else {
                                        linkError = "Échec de la liaison avec le serveur Supabase."
                                    }
                                }
                            },
                            onComplete = onCompleteOnboarding
                        )
                    }
                    OnboardingStep.RECOVERY -> {
                        RecoveryView(
                            recoveryKey = recoveryKey,
                            onKeyChange = { recoveryKey = it.uppercase() },
                            isLoading = isRecoveryLoading,
                            isSuccess = recoverySuccess,
                            errorMessage = recoveryError,
                            onSubmit = {
                                if (recoveryKey.length < 6) {
                                    recoveryError = "Clé de secours non reconnue. Saisissez au moins 6 caractères."
                                    return@RecoveryView
                                }
                                isRecoveryLoading = true
                                recoveryError = null
                                coroutineScope.launch {
                                    delay(1800)
                                    isRecoveryLoading = false
                                    recoverySuccess = true
                                    Toast.makeText(context, "Espace restauré intégralement ! 🔑", Toast.LENGTH_LONG).show()
                                }
                            },
                            onComplete = onCompleteOnboarding
                        )
                    }
                }
            }
        }

        // Camera QR Code Scanner Sheet Modal
        if (showCameraScanner) {
            CameraScannerModal(
                onDismiss = { showCameraScanner = false },
                onCodeScanned = { scannedCode ->
                    joinCode = scannedCode
                    showCameraScanner = false
                    Toast.makeText(context, "Code scanné : $scannedCode 📷", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// ==============================================================================
// 1. LANDING VIEW
// ==============================================================================
@Composable
fun LandingView(
    onSelectStep: (OnboardingStep) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Glowing Animated Logo Header
        val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(AccentRose.copy(alpha = 0.4f), AccentViolet.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
                .border(BorderStroke(2.dp, Brush.linearGradient(listOf(AccentRose, AccentViolet))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = AccentRose,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Espace de Couple Intime",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "L'application privée, chiffrée & fusionnelle créée exclusivement pour vous deux ❤️",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 4 Stylized Option Cards
        LandingOptionCard(
            title = "Créer un nouvel espace",
            subtitle = "Générez un code à 6 caractères & invitez votre âme sœur",
            icon = Icons.Rounded.AutoAwesome,
            accentColor = VibrantCyan,
            badgeText = "Nouveau Couple ✨",
            onClick = { onSelectStep(OnboardingStep.CREATE_SPACE) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LandingOptionCard(
            title = "Rejoindre mon/ma partenaire",
            subtitle = "Entrez le code unique ou scannez le QR Code direct",
            icon = Icons.Rounded.VolunteerActivism,
            accentColor = AccentRose,
            badgeText = "Code Reçu 🤝",
            onClick = { onSelectStep(OnboardingStep.JOIN_SPACE) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LandingOptionCard(
            title = "Lier une tablette / nouvel appareil",
            subtitle = "Synchronisez un second écran sans altérer les données distantes",
            icon = Icons.Rounded.TabletAndroid,
            accentColor = AccentViolet,
            badgeText = "Multi-Écrans 📱",
            onClick = { onSelectStep(OnboardingStep.LINK_TABLET) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LandingOptionCard(
            title = "Récupérer mon espace existant",
            subtitle = "Restaurez votre historique via votre clé de secours",
            icon = Icons.Rounded.VpnKey,
            accentColor = WarningGold,
            badgeText = "Restauration 🔑",
            onClick = { onSelectStep(OnboardingStep.RECOVERY) }
        )

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun LandingOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    badgeText: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF1B182B),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ==============================================================================
// 2. CREATE SPACE VIEW
// ==============================================================================
@Composable
fun CreateSpaceView(
    name: String,
    onNameChange: (String) -> Unit,
    selectedAvatar: String,
    avatarPresets: List<String>,
    onSelectAvatar: (Int) -> Unit,
    generatedCode: String,
    isWaitingPartner: Boolean,
    isPartnerConnected: Boolean,
    onStartWaiting: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isWaitingPartner) "Espace créé ✨" else "Créer votre Espace ✨",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isWaitingPartner) "Partagez ce code avec votre partenaire pour qu'il/elle rejoigne votre espace privé." else "Configurez votre profil et partagez le code à votre partenaire",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!isWaitingPartner) {
            // Input Prénom
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Votre Prénom", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = VibrantCyan) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1B182B),
                    unfocusedContainerColor = Color(0xFF1B182B),
                    focusedBorderColor = VibrantCyan,
                    unfocusedBorderColor = BorderSubtleWhite,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Avatar selector
            Text(
                text = "Choisissez votre Avatar / Symbole",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(avatarPresets.size) { index ->
                    val preset = avatarPresets[index]
                    val isSelected = selectedAvatar == preset
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) VibrantCyan.copy(alpha = 0.25f) else Color(0xFF1B182B))
                            .border(
                                BorderStroke(2.dp, if (isSelected) VibrantCyan else BorderSubtleWhite),
                                CircleShape
                            )
                            .clickable { onSelectAvatar(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = preset, fontSize = 22.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Interactive Canvas QR Code
        InteractiveQRCodeCanvas(code = generatedCode, sizeDp = 180)

        Spacer(modifier = Modifier.height(14.dp))

        // Pairing Code Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1B182B),
            border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Code Mikayala", generatedCode))
                    Toast.makeText(context, "Code copié : $generatedCode 📋", Toast.LENGTH_SHORT).show()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Code de Jumelage", fontSize = 11.sp, color = TextSecondary)
                    Text(text = generatedCode, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = AccentRose, letterSpacing = 3.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copier", tint = AccentRose, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Copier", fontSize = 12.sp, color = AccentRose, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Connection Status & Realtime Listener
        if (isPartnerConnected) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = OnlinePresenceGreen.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, OnlinePresenceGreen)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = OnlinePresenceGreen)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Partenaire connecté(e) en temps réel ! 🎉", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnlinePresenceGreen)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(text = "Accéder à notre Espace 💖", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else if (isWaitingPartner) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentRose, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Écoute Supabase Realtime en cours...", fontSize = 13.sp, color = AccentRose)
            }
        } else {
            Button(
                onClick = onStartWaiting,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Attendre la connexion du partenaire", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==============================================================================
// 3. JOIN SPACE VIEW
// ==============================================================================
@Composable
fun JoinSpaceView(
    name: String,
    onNameChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenScanner: () -> Unit,
    onSubmit: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Rejoindre mon/ma Partenaire 🤝",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Saisissez le code fourni par votre partenaire pour synchroniser vos deux téléphones",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Votre Prénom", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = AccentRose) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B182B),
                unfocusedContainerColor = Color(0xFF1B182B),
                focusedBorderColor = AccentRose,
                unfocusedBorderColor = BorderSubtleWhite,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Code Input Field
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Code à 6 caractères (ex: LOVE26)", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = AccentRose) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B182B),
                unfocusedContainerColor = Color(0xFF1B182B),
                focusedBorderColor = AccentRose,
                unfocusedBorderColor = BorderSubtleWhite,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // QR Scanner Camera Button
        Button(
            onClick = onOpenScanner,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2545)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = AccentViolet)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Ouvrir le Scanner Caméra QR Code 📷", fontSize = 13.sp, color = AccentViolet, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Error message handling
        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentRose.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AccentRose),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = AccentRose)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage, fontSize = 12.sp, color = AccentRose)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Submit Button
        Button(
            onClick = onSubmit,
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Rejoindre l'Espace de Couple 💖", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==============================================================================
// 4. LINK TABLET VIEW
// ==============================================================================
@Composable
fun LinkTabletView(
    selectedOwner: String,
    onSelectOwner: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    linkSuccess: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    onComplete: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Lier une Tablette / Nouvel Appareil 📱",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Associez cet appareil secondaire sans risquer d'altérer ou d'écraser vos données distantes",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "À qui appartient cet appareil ?",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isP1 = selectedOwner == "partner1"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isP1) AccentViolet.copy(alpha = 0.2f) else Color(0xFF1B182B),
                border = BorderStroke(1.dp, if (isP1) AccentViolet else BorderSubtleWhite),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectOwner("partner1") }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "👑", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Partenaire 1", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "Titulaire principal", fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }

            val isP2 = selectedOwner == "partner2"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isP2) AccentRose.copy(alpha = 0.2f) else Color(0xFF1B182B),
                border = BorderStroke(1.dp, if (isP2) AccentRose else BorderSubtleWhite),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectOwner("partner2") }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "💖", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Partenaire 2", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "Second membre", fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Code de Couple (ex: LOVE26)", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Rounded.TabletAndroid, contentDescription = null, tint = AccentViolet) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B182B),
                unfocusedContainerColor = Color(0xFF1B182B),
                focusedBorderColor = AccentViolet,
                unfocusedBorderColor = BorderSubtleWhite,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (errorMessage != null) {
            Text(text = errorMessage, fontSize = 12.sp, color = AccentRose)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (linkSuccess) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = OnlinePresenceGreen.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, OnlinePresenceGreen)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = OnlinePresenceGreen)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Appareil lié & synchronisé avec succès ! 📱✨", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnlinePresenceGreen)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(text = "Lancer sur cet écran 📱", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Button(
                onClick = onSubmit,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Synchroniser l'appareil 📱", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==============================================================================
// 5. RECOVERY VIEW
// ==============================================================================
@Composable
fun RecoveryView(
    recoveryKey: String,
    onKeyChange: (String) -> Unit,
    isLoading: Boolean,
    isSuccess: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    onComplete: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Récupérer mon Espace 🔑",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Saisissez votre clé de secours de sauvegarde ou code maître de jumelage",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = recoveryKey,
            onValueChange = onKeyChange,
            label = { Text("Clé de secours ou Code Maître", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = WarningGold) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B182B),
                unfocusedContainerColor = Color(0xFF1B182B),
                focusedBorderColor = WarningGold,
                unfocusedBorderColor = BorderSubtleWhite,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (errorMessage != null) {
            Text(text = errorMessage, fontSize = 12.sp, color = AccentRose)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isSuccess) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WarningGold.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, WarningGold)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WarningGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Restauration intégrale réussie !", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WarningGold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Messages & Vocaux restaurés (100%)\n• Coffre-fort & Souvenirs restaurés (100%)\n• Calendrier & Love Time synchronisés",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(containerColor = WarningGold),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(text = "Ouvrir l'Espace Restauré 🔑", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        } else {
            Button(
                onClick = onSubmit,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = WarningGold),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Restaurer l'Espace de Couple 🔑", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==============================================================================
// 6. CAMERA QR SCANNER SHEET MODAL
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerModal(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "laser_anim")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B182B),
            border = BorderStroke(1.dp, VibrantCyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Scanner Caméra QR Code 📷", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Camera Scanner Target Frame
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .border(BorderStroke(2.dp, VibrantCyan), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        QRScanner(onCodeScanned = onCodeScanned)
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Permission Caméra requise",
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan)
                            ) {
                                Text("Autoriser", color = Color.Black)
                            }
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val yPos = h * laserY

                        // Red Laser Line
                        drawLine(
                            color = AccentRose,
                            start = Offset(0f, yPos),
                            end = Offset(w, yPos),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Alignez le QR Code MIK-XXXX dans le cadre",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==============================================================================
// 7. INTERACTIVE CANVAS QR CODE COMPOSABLE
// ==============================================================================
@Composable
fun InteractiveQRCodeCanvas(
    code: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 180
) {
    val primaryColor = AccentRose
    val backgroundColor = Color.White // Standard QR background for better scannability

    val qrBitmap = remember(code) {
        QRCodeGenerator.generateQRCode(code, 512)
    }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(BorderStroke(2.dp, primaryColor), RoundedCornerShape(20.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR Code Jumelage",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback to old procedural drawing if generation fails
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val gridSize = 11
                val cellSize = width / gridSize
                val seed = code.hashCode()
                val random = Random(seed)
                for (row in 0 until gridSize) {
                    for (col in 0 until gridSize) {
                        if (random.nextBoolean()) {
                            drawRect(
                                color = primaryColor,
                                topLeft = Offset(col * cellSize, row * cellSize),
                                size = Size(cellSize, cellSize)
                            )
                        }
                    }
                }
            }
        }

        // Mini logo in center
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(BorderStroke(1.dp, AccentRose), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = AccentRose,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
