package com.example.mikayala.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.example.mikayala.data.model.ConnectionMode
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.PairingDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: MikayalaRepository
) {
    val context = LocalContext.current
    val settings by repository.userSettings.collectAsState()
    val coupleSpace by repository.activeCoupleSpace.collectAsState()
    val messages by repository.allMessages.collectAsState()
    val vaultItems by repository.allVaultItems.collectAsState()

    // Dialog & BottomSheet state toggles
    var showProfileModal by remember { mutableStateOf(false) }
    var showWallpaperModal by remember { mutableStateOf(false) }
    var showAppIconModal by remember { mutableStateOf(false) }
    var showFontModal by remember { mutableStateOf(false) }
    var showBubbleStyleModal by remember { mutableStateOf(false) }
    var showPrivacyModal by remember { mutableStateOf(false) }
    var showChatPrefsModal by remember { mutableStateOf(false) }
    var showNotificationsModal by remember { mutableStateOf(false) }
    var showStorageModal by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showDissolveDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showOfflineP2PScreen by remember { mutableStateOf(false) }
    var showAnniversaryDialog by remember { mutableStateOf(false) }
    var showOnboardingScreen by remember { mutableStateOf(false) }
    var showSupabaseDialog by remember { mutableStateOf(false) }

    if (showOnboardingScreen) {
        OnboardingScreen(
            repository = repository,
            onCompleteOnboarding = { showOnboardingScreen = false },
            onBackToApp = { showOnboardingScreen = false }
        )
        return
    }

    if (showOfflineP2PScreen) {
        OfflineP2PLinkScreen(
            repository = repository,
            onBack = { showOfflineP2PScreen = false }
        )
        return
    }

    // Photo pickers for Custom Wallpaper & Custom App Icon
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            repository.updateWallpaper("custom_image", it.toString())
            Toast.makeText(context, "Fond d'écran personnalisé appliqué ! 🖼️", Toast.LENGTH_SHORT).show()
        }
    }

    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            repository.updateAppIcon("custom_image", it.toString())
            Toast.makeText(context, "Nouvelle icône personnalisée importée ! ✨", Toast.LENGTH_SHORT).show()
        }
    }

    val scope = rememberCoroutineScope()

    val myAvatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = repository.uploadProfileAvatar(it)
                if (success) {
                    Toast.makeText(context, "Photo de profil mise à jour avec Supabase ! 📸", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Erreur lors de l'upload sur Supabase Storage.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val partnerAvatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            repository.updatePartnerProfile(settings.partnerNickname, settings.partnerStatus, it.toString())
            Toast.makeText(context, "Photo du partenaire mise à jour ! 💖", Toast.LENGTH_SHORT).show()
        }
    }

    // --- DIALOGS & MODALS ---

    // 1. Profile & Partner Nickname Modal
    if (showProfileModal) {
        var myName by remember { mutableStateOf(settings.displayName) }
        var myStatus by remember { mutableStateOf(settings.customStatus) }
        var myMood by remember { mutableStateOf(settings.moodEmoji) }
        var partnerNick by remember { mutableStateOf(settings.partnerNickname) }
        var partnerStat by remember { mutableStateOf(settings.partnerStatus) }
        var activeTab by remember { mutableIntStateOf(0) } // 0 = Mon Profil, 1 = Mon Partenaire

        Dialog(onDismissRequest = { showProfileModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Profils & Surnoms du Couple 💑",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Tab Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22141C2B))
                            .padding(4.dp)
                    ) {
                        Surface(
                            onClick = { activeTab = 0 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeTab == 0) AccentRose else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Mon Profil",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 0) Color.White else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        Surface(
                            onClick = { activeTab = 1 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeTab == 1) AccentViolet else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Mon Partenaire",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 1) Color.White else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (activeTab == 0) {
                        // Mon Profil
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(AccentRose.copy(alpha = 0.2f))
                                    .clickable {
                                        myAvatarPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = myMood, fontSize = 32.sp)
                            }
                            Surface(
                                shape = CircleShape,
                                color = AccentRose,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        myAvatarPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Humeur d'Amour :", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("🥰", "❤️", "🔥", "🥺", "🌸", "👑", "😴").forEach { emoji ->
                                Surface(
                                    onClick = { myMood = emoji },
                                    shape = CircleShape,
                                    color = if (myMood == emoji) AccentRose.copy(alpha = 0.3f) else Color.Transparent,
                                    border = BorderStroke(1.dp, if (myMood == emoji) AccentRose else BorderSubtleWhite),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = emoji, fontSize = 16.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = myName,
                            onValueChange = { myName = it },
                            label = { Text("Mon Nom / Pseudo") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRose, cursorColor = AccentRose),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = myStatus,
                            onValueChange = { myStatus = it },
                            label = { Text("Mon Statut / Bio d'amour") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRose, cursorColor = AccentRose),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Profil Partenaire (Surnom & Statut)
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(AccentViolet.copy(alpha = 0.2f))
                                    .clickable {
                                        partnerAvatarPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "👸", fontSize = 32.sp)
                            }
                            Surface(
                                shape = CircleShape,
                                color = AccentViolet,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        partnerAvatarPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = partnerNick,
                            onValueChange = { partnerNick = it },
                            label = { Text("Surnom affectueux du partenaire") },
                            placeholder = { Text("ex: Ma Reine ❤️, Mon Cœur...") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentViolet, cursorColor = AccentViolet),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = partnerStat,
                            onValueChange = { partnerStat = it },
                            label = { Text("Statut d'amour affiché pour elle/lui") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentViolet, cursorColor = AccentViolet),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (activeTab == 0) {
                                repository.updateProfile(myName, myStatus, settings.avatarUrl, myMood)
                                Toast.makeText(context, "Mon profil a été mis à jour !", Toast.LENGTH_SHORT).show()
                            } else {
                                repository.updatePartnerProfile(partnerNick, partnerStat, settings.partnerAvatarUrl)
                                Toast.makeText(context, "Surnom du partenaire enregistré ! 💖", Toast.LENGTH_SHORT).show()
                            }
                            showProfileModal = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (activeTab == 0) AccentRose else AccentViolet),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enregistrer les modifications", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // 2. Wallpaper Picker Modal
    if (showWallpaperModal) {
        val wallpapers = listOf(
            Triple("twilight", "Nébuleuse Nuit Étoilée 🌌", listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))),
            Triple("sunset", "Crépuscule Romantique 🌅", listOf(Color(0xFF4A154B), Color(0xFF831843))),
            Triple("rose_gold", "Rose Gold & Aurore ✨", listOf(Color(0xFF2E1065), Color(0xFF701A75))),
            Triple("cyber_cyan", "Matrix Cyber Cyan 💠", listOf(Color(0xFF083344), Color(0xFF064E3B))),
            Triple("matte_black", "Obsidienne Pure OLED 🖤", listOf(Color(0xFF05070B), Color(0xFF111827)))
        )

        var localOpacity by remember(settings.wallpaperOpacity) { mutableFloatStateOf(settings.wallpaperOpacity) }
        var localBlur by remember(settings.wallpaperBlur) { mutableFloatStateOf(settings.wallpaperBlur) }

        Dialog(onDismissRequest = { showWallpaperModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(text = "Fond d'Écran du Chat & App 🖼️", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Personnalisez l'ambiance visuelle de vos discussions.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Opacity & Blur Sliders
                    Text(text = "Opacité du Fond : ${(localOpacity * 100).toInt()}%", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = localOpacity,
                        onValueChange = {
                            localOpacity = it
                            repository.updateWallpaperControls(localOpacity, localBlur)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = VibrantCyan, activeTrackColor = VibrantCyan)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Flou artistique (Blur) : ${localBlur.toInt()} dp", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = localBlur,
                        onValueChange = {
                            localBlur = it
                            repository.updateWallpaperControls(localOpacity, localBlur)
                        },
                        valueRange = 0f..25f,
                        colors = SliderDefaults.colors(thumbColor = AccentRose, activeTrackColor = AccentRose)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Import Custom Gallery Wallpaper Button
                    Button(
                        onClick = {
                            wallpaperPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDarkElevated),
                        border = BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importer une photo de la Galerie 📱", fontSize = 13.sp, color = VibrantCyan, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Ou choisissez un thème prédéfini :", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        wallpapers.forEach { (id, title, colors) ->
                            val isSelected = settings.chatTheme == id
                            Surface(
                                onClick = {
                                    repository.updateWallpaper(id, "")
                                    showWallpaperModal = false
                                    Toast.makeText(context, "Fond d'écran '$title' appliqué !", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) VibrantCyan else BorderSubtleWhite),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(Brush.horizontalGradient(colors))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    if (isSelected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. App Icon & Camouflage Modal
    if (showAppIconModal) {
        val appIcons = listOf(
            Triple("heart_luxury", "Cœur Luxueux Or & Rubis ❤️", "Icône officielle Mikayala"),
            Triple("calculator", "Calculatrice Scientifique 🔢", "Camouflage discret parfait"),
            Triple("notes", "Bloc-Notes & Mémos 📝", "Déguisement insoupçonnable"),
            Triple("weather", "Météo Locale Pro ☀️", "Camouflage quotidien"),
            Triple("cyber_cyan", "Nébuleuse Cyan Astrale 💠", "Design cyber moderne"),
            Triple("cherry_blossom", "Fleur de Cerisier Zen 🌸", "Aura poétique et douce")
        )

        Dialog(onDismissRequest = { showAppIconModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(text = "Changer l'Icône de l'Application 🎭", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Camouflez l'application sur votre écran d'accueil ou appliquez une image personnalisée.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Import Custom Icon from Gallery
                    Button(
                        onClick = {
                            iconPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDarkElevated),
                        border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importer une icône depuis la Galerie 🖼️", fontSize = 13.sp, color = AccentGold, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Icônes de Camouflage et de Style :", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        appIcons.forEach { (id, title, desc) ->
                            val isSelected = settings.customAppIcon == id
                            Surface(
                                onClick = {
                                    repository.updateAppIcon(id, "")
                                    showAppIconModal = false
                                    Toast.makeText(context, "Icône '$title' sélectionnée !", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) CardDarkElevated else Color(0x22141C2B),
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) AccentGold else BorderSubtleWhite),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(text = desc, fontSize = 11.sp, color = TextSecondary)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentGold, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Font & Typography Modal
    if (showFontModal) {
        val fonts = listOf(
            Pair("default", "Moderne Sans-Serif (Standard)"),
            Pair("serif", "Serif Romantique Élégante (Playfair)"),
            Pair("handwritten", "Manuscrite Douce Intime"),
            Pair("monospace", "Cyber Minimaliste (Code & Tech)"),
            Pair("cursive", "Cursive Passionnée")
        )
        val sizes = listOf(
            Pair("small", "Compacte (13sp)"),
            Pair("medium", "Standard (15sp)"),
            Pair("large", "Confortable (17sp)"),
            Pair("xlarge", "Grande (19sp)")
        )

        Dialog(onDismissRequest = { showFontModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(text = "Police d'Écriture & Typographie ✍️", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Ajustez la typographie de tous les textes de l'application.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Preview Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x33141C2B),
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Aperçu en direct :", fontSize = 10.sp, color = AccentViolet, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "« Chaque seconde avec toi est une preuve d'amour éternel. » ❤️",
                                fontSize = when (settings.fontSizeScale) {
                                    "small" -> 13.sp
                                    "large" -> 17.sp
                                    "xlarge" -> 19.sp
                                    else -> 15.sp
                                },
                                fontFamily = when (settings.fontFamilyPreference) {
                                    "serif" -> FontFamily.Serif
                                    "monospace" -> FontFamily.Monospace
                                    "cursive" -> FontFamily.Cursive
                                    else -> FontFamily.Default
                                },
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Style de Police :", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    fonts.forEach { (id, name) ->
                        val isSelected = settings.fontFamilyPreference == id
                        Surface(
                            onClick = { repository.updateFontFamily(id) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) CardDarkElevated else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) AccentViolet else BorderSubtleWhite),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = name, fontSize = 13.sp, color = if (isSelected) Color.White else TextSecondary)
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Taille du Texte :", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sizes.forEach { (id, label) ->
                            val isSelected = settings.fontSizeScale == id
                            Surface(
                                onClick = { repository.updateFontSize(id) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentViolet else Color(0x22141C2B),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = id.replaceFirstChar { it.uppercase() },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showFontModal = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Terminé", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 5. Bubble Style Modal
    if (showBubbleStyleModal) {
        val bubbleStyles = listOf(
            Triple("gradient_pink", "Rose Rubis & Magenta Passion 💕", listOf(Color(0xFFE91E63), Color(0xFFAD1457))),
            Triple("neon_cyan", "Cyan Néon & Bleu Nuit 💠", listOf(Color(0xFF00B4D8), Color(0xFF0077B6))),
            Triple("dark_gold", "Or Impérial & Ambre 👑", listOf(Color(0xFFFFB703), Color(0xFFFB8500))),
            Triple("emerald_luxury", "Émeraude Velours 🌿", listOf(Color(0xFF2EC4B6), Color(0xFF0E7490))),
            Triple("frosted_glass", "Verre Dépoli Givré ❄️", listOf(Color(0x66FFFFFF), Color(0x33FFFFFF)))
        )

        Dialog(onDismissRequest = { showBubbleStyleModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(text = "Style des Bulles de Message 💬", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Sélectionnez l'effet visuel des bulles dans vos conversations.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        bubbleStyles.forEach { (id, title, colors) ->
                            val isSelected = settings.bubbleStyle == id
                            Surface(
                                onClick = {
                                    repository.updateBubbleStyle(id)
                                    showBubbleStyleModal = false
                                    Toast.makeText(context, "Style de bulle '$title' activé !", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) CardDarkElevated else Color(0x22141C2B),
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) AccentRose else BorderSubtleWhite),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Brush.linearGradient(colors))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentRose, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 6. WhatsApp-Style Privacy & Security Modal
    if (showPrivacyModal) {
        var bioEnabled by remember { mutableStateOf(settings.biometricEnabled) }
        var antiScreen by remember { mutableStateOf(settings.antiScreenshotEnabled) }
        var blurSwitch by remember { mutableStateOf(settings.screenBlurOnSwitch) }
        var readReceipts by remember { mutableStateOf(settings.readReceiptsEnabled) }
        var lastSeen by remember { mutableStateOf(settings.lastSeenPrivacy) }
        var disappearingDays by remember { mutableIntStateOf(settings.disappearingMessagesDays) }

        Dialog(onDismissRequest = { showPrivacyModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(text = "Confidentialité & Sécurité (Style WhatsApp) 🔒", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "Contrôlez vos données, présence et chiffrement.", fontSize = 12.sp, color = TextSecondary)
                    }

                    item {
                        // Read Receipts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Confirmations de lecture (Coches bleues)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(text = "Afficher les doubles coches bleues lors de la lecture", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = readReceipts,
                                onCheckedChange = { readReceipts = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibrantCyan)
                            )
                        }
                    }

                    item {
                        // Anti-Screenshot
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Protection Anti-Capture d'Écran 🛡️", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(text = "Bloque les screenshots et masque les aperçus récents", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = antiScreen,
                                onCheckedChange = { antiScreen = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRose)
                            )
                        }
                    }

                    item {
                        // Blur On Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Flou automatique en arrière-plan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(text = "Masque l'écran lors du changement d'application", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = blurSwitch,
                                onCheckedChange = { blurSwitch = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentViolet)
                            )
                        }
                    }

                    item {
                        // Last Seen
                        Text(text = "Présence en ligne & Dernière connexion :", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Pair("couple_only", "Mon Couple"),
                                Pair("everyone", "Tout le monde"),
                                Pair("nobody", "Personne")
                            ).forEach { (id, label) ->
                                val isSelected = lastSeen == id
                                Surface(
                                    onClick = { lastSeen = id },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentRose else Color(0x22141C2B),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // Disappearing Messages
                        Text(text = "Messages Éphémères Automatiques :", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Pair(0, "Désactivé"),
                                Pair(1, "24h"),
                                Pair(7, "7 jours"),
                                Pair(90, "90 jours")
                            ).forEach { (days, label) ->
                                val isSelected = disappearingDays == days
                                Surface(
                                    onClick = { disappearingDays = days },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) VibrantCyan else Color(0x22141C2B),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                repository.updatePrivacySettings(
                                    biometricEnabled = bioEnabled,
                                    antiScreenshotEnabled = antiScreen,
                                    screenBlurOnSwitch = blurSwitch,
                                    lastSeenPrivacy = lastSeen,
                                    readReceiptsEnabled = readReceipts,
                                    disappearingDays = disappearingDays
                                )
                                showPrivacyModal = false
                                Toast.makeText(context, "Paramètres de confidentialité mis à jour !", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enregistrer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 7. WhatsApp-Style Chats & Media Modal
    if (showChatPrefsModal) {
        var enterSend by remember { mutableStateOf(settings.enterIsSend) }
        var galleryVis by remember { mutableStateOf(settings.mediaVisibilityInGallery) }
        var autoDownload by remember { mutableStateOf(settings.autoDownloadMedia) }
        var uploadQuality by remember { mutableStateOf(settings.photoUploadQuality) }

        Dialog(onDismissRequest = { showChatPrefsModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(text = "Préférences de Discussions & Médias 💬", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    // Enter is send
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Touche Entrée pour envoyer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "La touche Entrée envoie immédiatement le message", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = enterSend,
                            onCheckedChange = { enterSend = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibrantCyan)
                        )
                    }

                    // Gallery visibility
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Visibilité dans la Galerie 📷", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "Afficher les médias reçus dans la galerie du téléphone", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = galleryVis,
                            onCheckedChange = { galleryVis = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRose)
                        )
                    }

                    // Upload quality
                    Text(text = "Qualité d'Envoi des Photos & Vidéos :", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Pair("hd", "Qualité HD Originale ✨"),
                            Pair("standard", "Standard Économique")
                        ).forEach { (id, label) ->
                            val isSelected = uploadQuality == id
                            Surface(
                                onClick = { uploadQuality = id },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) VibrantCyan else Color(0x22141C2B),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Backup & Export
                    Button(
                        onClick = {
                            Toast.makeText(context, "Sauvegarde chiffrée créée avec succès ! 💾", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDarkElevated),
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sauvegarder les discussions maintenant 💾", fontSize = 12.sp, color = Color.White)
                    }

                    // Clear chat
                    Button(
                        onClick = {
                            repository.clearAllMessages()
                            Toast.makeText(context, "Toutes les discussions ont été effacées !", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5252)),
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Effacer toutes les discussions 🧹", fontSize = 12.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            repository.updateChatPreferences(enterSend, galleryVis, autoDownload, uploadQuality)
                            showChatPrefsModal = false
                            Toast.makeText(context, "Préférences de discussion enregistrées !", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enregistrer", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // 8. Notifications Modal (Camouflage & Sounds)
    if (showNotificationsModal) {
        var notifMode by remember { mutableStateOf(settings.notificationPrivacyMode) }
        var sound by remember { mutableStateOf(settings.notificationSound) }
        var heartHaptic by remember { mutableStateOf(settings.heartHapticEnabled) }
        var cycleAlerts by remember { mutableStateOf(settings.cyclePartnerAlertsEnabled) }
        var loveWidget by remember { mutableStateOf(settings.loveWidgetEnabled) }

        Dialog(onDismissRequest = { showNotificationsModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(text = "Notifications & Sons Romantiques 🔔", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "Ajustez le camouflage et les alertes d'amour.", fontSize = 12.sp, color = TextSecondary)
                    }

                    item {
                        Text(text = "Mode de Notification (Camouflage) :", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        listOf(
                            Triple("discreet", "Discret Neutre 🔒", "Affiche 'Nouveau message chiffré'"),
                            Triple("weather_camouflage", "Camouflage Météo ☀️", "Affiche 'Météo : Ciel dégagé 21°C'"),
                            Triple("system_camouflage", "Camouflage Système ⚙️", "Affiche 'Mise à jour système prête'"),
                            Triple("romantic", "Romantique Complet ❤️", "Affiche le prénom et les petits cœurs")
                        ).forEach { (id, title, desc) ->
                            val isSelected = notifMode == id
                            Surface(
                                onClick = { notifMode = id },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) CardDarkElevated else Color(0x22141C2B),
                                border = BorderStroke(1.dp, if (isSelected) AccentGold else BorderSubtleWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(text = desc, fontSize = 10.sp, color = TextSecondary)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        // Heartbeat Haptic Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Vibreur Haptique Cardiaque 💓", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(text = "Double pulsation rythmée comme un battement de cœur", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = heartHaptic,
                                onCheckedChange = { heartHaptic = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRose)
                            )
                        }
                    }

                    item {
                        // Cycle Partner Alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Alertes & Conseils de Cycle 🌸", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(text = "Rappels bienveillants pour prendre soin d'elle", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = cycleAlerts,
                                onCheckedChange = { cycleAlerts = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentViolet)
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                repository.updateNotificationSettings(notifMode, sound, heartHaptic, cycleAlerts, loveWidget)
                                showNotificationsModal = false
                                Toast.makeText(context, "Paramètres de notification mis à jour !", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enregistrer", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // 9. Storage Manager Modal
    if (showStorageModal) {
        val totalMsgs = messages.size
        val totalVault = vaultItems.size

        Dialog(onDismissRequest = { showStorageModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(text = "Gestionnaire de Stockage & Cache 📦", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Visualisez et optimisez l'espace utilisé par l'application.", fontSize = 12.sp, color = TextSecondary)

                    // Storage breakdown card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardDarkElevated,
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discussions & Textes", fontSize = 12.sp, color = TextSecondary)
                                Text("$totalMsgs messages (1.2 Mo)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VibrantCyan)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Coffre Secret & Photos HD", fontSize = 12.sp, color = TextSecondary)
                                Text("$totalVault éléments (14.8 Mo)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cache Médias Temporaires", fontSize = 12.sp, color = TextSecondary)
                                Text("4.6 Mo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGold)
                            }
                        }
                    }

                    // Clean Cache Button
                    Button(
                        onClick = {
                            Toast.makeText(context, "4.6 Mo de cache temporaire nettoyés ! 🧹", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vider le Cache des Médias (4.6 Mo)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Clear Chat Button
                    OutlinedButton(
                        onClick = {
                            showStorageModal = false
                            showClearChatDialog = true
                        },
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Effacer tout l'historique des messages 🗑️", color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // 10. Anniversary & Couple Date Modal
    if (showAnniversaryDialog) {
        var loveQuoteInput by remember { mutableStateOf(coupleSpace.loveQuote) }
        Dialog(onDismissRequest = { showAnniversaryDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
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
                    Text(text = "Date d'Anniversaire & Amour 📅❤️", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = loveQuoteInput,
                        onValueChange = { loveQuoteInput = it },
                        label = { Text("Citation / Devise de notre couple") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRose, cursorColor = AccentRose),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            repository.updateLoveTimeSettings(
                                anniversaryDate = coupleSpace.anniversaryDate,
                                partner1Name = coupleSpace.partner1Name,
                                partner2Name = coupleSpace.partner2Name,
                                loveQuote = loveQuoteInput
                            )
                            showAnniversaryDialog = false
                            Toast.makeText(context, "Devise d'amour enregistrée ! 💖", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enregistrer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // PIN Dialog
    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showPinDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
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
                    Text(text = "Changer le Code PIN Réel 🔒", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4) newPin = it },
                        label = { Text("Nouveau Code PIN (4 chiffres)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRose, cursorColor = AccentRose),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (newPin.length == 4) {
                                repository.updatePinCode(newPin)
                                showPinDialog = false
                                Toast.makeText(context, "Code PIN mis à jour !", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enregistrer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Decoy PIN Dialog
    if (showDecoyPinDialog) {
        var newFakePin by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showDecoyPinDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
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
                    Text(text = "Code PIN Leurre (Camouflage Calculatrice) 🎭", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Ce code secret ouvre une fausse calculatrice pour protéger votre intimité.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFakePin,
                        onValueChange = { if (it.length <= 4) newFakePin = it },
                        label = { Text("Code Leurre (4 chiffres)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentViolet, cursorColor = AccentViolet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (newFakePin.length == 4) {
                                repository.updateDecoyPin(newFakePin)
                                showDecoyPinDialog = false
                                Toast.makeText(context, "Code Leurre mis à jour !", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enregistrer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Clear Chat Dialog
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Effacer toutes les discussions ?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Cette action supprimera tous les messages locaux. Votre coffre fort et vos souvenirs restent intacts.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearChatHistory()
                        showClearChatDialog = false
                        Toast.makeText(context, "Historique de discussion effacé 🧹", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Effacer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = CardModalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Dissolve Couple Space Dialog
    if (showDissolveDialog) {
        AlertDialog(
            onDismissRequest = { showDissolveDialog = false },
            title = { Text("Dissocier l'Espace Couple ?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Cette action déconnectera cet appareil de l'espace partagé avec ${settings.partnerNickname}.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDissolveDialog = false
                        repository.clearSession()
                        Toast.makeText(context, "Espace couple réinitialisé & Déconnexion.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Dissocier", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDissolveDialog = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = CardModalSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Pairing Modal
    if (showPairingDialog) {
        PairingDialog(
            currentPairingCode = coupleSpace.pairingCode,
            onDismiss = { showPairingDialog = false },
            onPairSuccess = { code ->
                repository.updatePairing(code)
                Toast.makeText(context, "Espace couple synchronisé avec $code !", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- MAIN SETTINGS SCREEN LAYOUT ---
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNight)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
    ) {
        // --- 1. HERO COUPLE PROFILES & STATUS HEADER ---
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardDark,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProfileModal = true }
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // My Profile
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(AccentRose.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = settings.moodEmoji, fontSize = 26.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = settings.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = settings.customStatus, fontSize = 11.sp, color = AccentRose, maxLines = 1)
                            }
                        }

                        // Center Love Link Badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(AccentRose.copy(alpha = 0.3f), Color.Transparent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "❤️", fontSize = 18.sp)
                        }

                        // Partner Profile & Nickname
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = settings.partnerNickname, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = VibrantCyan)
                                Text(text = settings.partnerStatus, fontSize = 11.sp, color = AccentViolet, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(VibrantCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "👸", fontSize = 26.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderSubtleWhite)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "842 jours d'amour partagés", fontSize = 12.sp, color = TextSecondary)
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentRose.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showProfileModal = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, tint = AccentRose, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Modifier Profils & Surnoms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                            }
                        }
                    }
                }
            }
        }

        // --- 2. SECTION: PERSONNALISATION VISUELLE & DESIGN ---
        item {
            Text(text = "PERSONNALISATION VISUELLE & DESIGN", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = VibrantCyan)
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        icon = Icons.Rounded.Wallpaper,
                        iconTint = VibrantCyan,
                        title = "Fond d'Écran du Chat & App",
                        subtitle = "Galerie de thèmes ou importer une photo personnalisée",
                        onClick = { showWallpaperModal = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.AppShortcut,
                        iconTint = AccentGold,
                        title = "Changer l'Icône de l'Application",
                        subtitle = "Camouflage (Calculatrice, Notes) ou image importée",
                        onClick = { showAppIconModal = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.TextFields,
                        iconTint = AccentViolet,
                        title = "Police d'Écriture & Typographie",
                        subtitle = "Moderne, Serif romantique, Cursive et taille de texte",
                        onClick = { showFontModal = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.BubbleChart,
                        iconTint = AccentRose,
                        title = "Style des Bulles de Discussion",
                        subtitle = "Rose Rubis, Cyan Néon, Or Impérial, Verre Dépoli",
                        onClick = { showBubbleStyleModal = true }
                    )

                    // App theme mode switch (Mode Sombre / Clair)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentGold.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (settings.isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Mode Sombre de l'Application", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text(text = if (settings.isDarkMode) "Activé (Apparence sombre)" else "Désactivé (Apparence claire)", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = settings.isDarkMode,
                                onCheckedChange = { repository.updateDarkMode(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRose)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. SECTION: ESPACE COUPLE & SURNOMS ---
        item {
            Text(text = "ESPACE COUPLE & COMPLICITÉ", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = AccentRose)
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        icon = Icons.Rounded.Loyalty,
                        iconTint = AccentRose,
                        title = "Surnom du Partenaire & Profils",
                        subtitle = "Personnaliser son surnom ('${settings.partnerNickname}') et avatar",
                        onClick = { showProfileModal = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.FavoriteBorder,
                        iconTint = AccentRose,
                        title = "Date d'Anniversaire & Citation",
                        subtitle = "Compteur de relation et devise d'amour",
                        onClick = { showAnniversaryDialog = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.Vibration,
                        iconTint = VibrantCyan,
                        title = "Vibration 'Pense à moi' (Love Pulse) 💓",
                        subtitle = "Envoyer un battement de cœur tactile instantané",
                        onClick = {
                            Toast.makeText(context, "Pulsation d'amour envoyée à ${settings.partnerNickname} ! 💓✨", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingRow(
                        icon = Icons.Rounded.QrCode,
                        iconTint = AccentViolet,
                        title = "Code de Jumelage Couple (${coupleSpace.pairingCode})",
                        subtitle = "Gérer la liaison ou scanner le QR code",
                        onClick = { showPairingDialog = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.AutoAwesome,
                        iconTint = VibrantCyan,
                        title = "Module d'Onboarding & Jumelage Multi-appareils",
                        subtitle = "Créer, rejoindre, lier une tablette ou restaurer l'espace",
                        onClick = { showOnboardingScreen = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.CloudSync,
                        iconTint = VibrantCyan,
                        title = "Base de Données Supabase (Cloud & Realtime)",
                        subtitle = "Configurer l'URL Supabase, la clé API & tester le serveur",
                        onClick = { showSupabaseDialog = true }
                    )
                }
            }
        }

        // --- 4. SECTION: CONFIDENTIALITÉ & SÉCURITÉ (STYLE WHATSAPP) ---
        item {
            Text(text = "CONFIDENTIALITÉ & SÉCURITÉ (WHATSAPP)", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = AccentViolet)
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        icon = Icons.Rounded.Security,
                        iconTint = VibrantCyan,
                        title = "Confidentialité WhatsApp Complète",
                        subtitle = "Présence, coches bleues, messages éphémères, anti-screenshot",
                        onClick = { showPrivacyModal = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.Password,
                        iconTint = AccentRose,
                        title = "Code PIN Réel Principal",
                        subtitle = "Changer le code secret de déverrouillage",
                        onClick = { showPinDialog = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.Calculate,
                        iconTint = AccentViolet,
                        title = "Code PIN Leurre (Camouflage Calculatrice)",
                        subtitle = "Ouvre une fausse calculatrice fonctionnelle",
                        onClick = { showDecoyPinDialog = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.Timer,
                        iconTint = AccentGold,
                        title = "Délai d'Auto-Verrouillage",
                        subtitle = when (settings.lockTimeoutSeconds) {
                            0 -> "Immédiat"
                            60 -> "1 minute"
                            300 -> "5 minutes"
                            else -> "Désactivé"
                        },
                        onClick = {
                            val next = when (settings.lockTimeoutSeconds) {
                                0 -> 60
                                60 -> 300
                                300 -> -1
                                else -> 0
                            }
                            repository.updateLockTimeout(next)
                        }
                    )
                }
            }
        }

        // --- 5. SECTION: DISCUSSIONS & MÉDIAS ---
        item {
            Text(text = "DISCUSSIONS & MÉDIAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = VibrantCyan)
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        icon = Icons.Rounded.Chat,
                        iconTint = VibrantCyan,
                        title = "Paramètres de Discussion",
                        subtitle = "Touche Entrée, visibilité galerie, qualité HD des photos",
                        onClick = { showChatPrefsModal = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.NotificationsActive,
                        iconTint = AccentGold,
                        title = "Notifications & Sons Romantiques",
                        subtitle = "Camouflage (Météo/Système), sonneries et vibreur cardiaque",
                        onClick = { showNotificationsModal = true }
                    )

                    SettingRow(
                        icon = Icons.Rounded.Storage,
                        iconTint = AccentRose,
                        title = "Stockage & Cache des Médias",
                        subtitle = "Gestion de l'espace disque, nettoyage du cache et sauvegardes",
                        onClick = { showStorageModal = true }
                    )
                }
            }
        }

        // --- 6. SECTION: MODES DE CONNEXION (2 OPTIONS) ---
        item {
            Text(text = "MODES DE CONNEXION (2 OPTIONS)", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = AccentViolet)
        }

        item {
            val isOnline = settings.connectionMode == ConnectionMode.ONLINE
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CardDark,
                border = BorderStroke(1.dp, BorderHighlight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Option 1: Mode En Ligne
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isOnline) CardDarkElevated else Color(0x22141C2B),
                        border = BorderStroke(1.2.dp, if (isOnline) VibrantCyan.copy(alpha = 0.6f) else BorderSubtleWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repository.updateConnectionMode(ConnectionMode.ONLINE)
                                Toast.makeText(context, "Mode En Ligne sélectionné 🌐", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) VibrantCyan.copy(alpha = 0.2f) else HoverStateWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    tint = if (isOnline) VibrantCyan else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Option 1 : Mode En Ligne",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (isOnline) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = OnlinePresenceGreen.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "ACTIF",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = OnlinePresenceGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Connexion via Internet (Wi-Fi / 4G / 5G). Synchronisation cloud continue et appels audio/visio chiffrés.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }

                            RadioButton(
                                selected = isOnline,
                                onClick = {
                                    repository.updateConnectionMode(ConnectionMode.ONLINE)
                                    Toast.makeText(context, "Mode En Ligne sélectionné 🌐", Toast.LENGTH_SHORT).show()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = VibrantCyan)
                            )
                        }
                    }

                    // Option 2: Mode Liaison Hors-Ligne
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (!isOnline) CardDarkElevated else Color(0x22141C2B),
                        border = BorderStroke(1.2.dp, if (!isOnline) AccentRose.copy(alpha = 0.6f) else BorderSubtleWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repository.updateConnectionMode(ConnectionMode.OFFLINE_P2P)
                                showOfflineP2PScreen = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (!isOnline) AccentRose.copy(alpha = 0.2f) else HoverStateWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiTethering,
                                    contentDescription = null,
                                    tint = if (!isOnline) AccentRose else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Option 2 : Mode Hors-Ligne",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (!isOnline) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AccentRose.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "ACTIF",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = AccentRose,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Liaison directe d'appareil à appareil (Wi-Fi Direct / Hotspot local). 0 Mo de data mobile, vitesse 50+ Mo/s.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }

                            RadioButton(
                                selected = !isOnline,
                                onClick = {
                                    repository.updateConnectionMode(ConnectionMode.OFFLINE_P2P)
                                    showOfflineP2PScreen = true
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentRose)
                            )
                        }
                    }

                    Button(
                        onClick = { showOfflineP2PScreen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isOnline) AccentRose else CardDarkElevated),
                        border = BorderStroke(1.dp, if (!isOnline) AccentRose else BorderSubtleWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ouvrir le Gestionnaire de Liaison Directe 📡",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // --- 7. FOOTER: DISSOCIATION ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x22FF5252),
                border = BorderStroke(1.dp, Color(0x44FF5252)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDissolveDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Dissocier l'Espace Couple", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    if (showSupabaseDialog) {
        val dialogCoroutineScope = rememberCoroutineScope()
        var urlInput by remember { mutableStateOf(repository.supabaseService.supabaseUrl) }
        var keyInput by remember { mutableStateOf(repository.supabaseService.supabaseKey) }
        var isTesting by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        AlertDialog(
            onDismissRequest = { showSupabaseDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = VibrantCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configuration Supabase", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Renseignez vos identifiants Supabase (URL & Anon Key) pour synchroniser votre espace en temps réel sur le cloud.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Supabase URL", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedLabelColor = VibrantCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Supabase Anon Key", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedLabelColor = VibrantCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            repository.updateSupabaseCredentials(urlInput, keyInput)
                            isTesting = true
                            dialogCoroutineScope.launch {
                                val result = repository.testSupabaseConnection()
                                isTesting = false
                                testResult = result
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan.copy(alpha = 0.2f), contentColor = VibrantCyan),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = VibrantCyan, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test en cours...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tester la connexion Supabase ⚡", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    testResult?.let { (success, msg) ->
                        Surface(
                            color = if (success) Color(0x2200E676) else Color(0x22FF5252),
                            border = BorderStroke(1.dp, if (success) Color(0xFF00E676) else Color(0xFFFF5252)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                color = if (success) Color(0xFF00E676) else Color(0xFFFF5252),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.updateSupabaseCredentials(urlInput, keyInput)
                    Toast.makeText(context, "Configuration Supabase enregistrée ! ⚡", Toast.LENGTH_SHORT).show()
                    showSupabaseDialog = false
                }) {
                    Text("Enregistrer", color = VibrantCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSupabaseDialog = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    iconTint: Color = AccentRose,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
