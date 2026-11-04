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
    val rawMessages by repository.allMessages.collectAsState()
    val myUserId = remember { repository.getCurrentUserId() }
    val messages = remember(rawMessages, myUserId) {
        rawMessages.filter { !it.deletedFor.contains(myUserId) }
    }
    val vaultItems by repository.allVaultItems.collectAsState()

    // Dialog & BottomSheet state toggles
    var showProfileModal by remember { mutableStateOf(false) }
    var showAppIconModal by remember { mutableStateOf(false) }
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
            // Copie dans l'application : l'autorisation de lecture de la galerie est temporaire,
            // la photo resterait sinon illisible après un redémarrage.
            val saved = try {
                val dest = java.io.File(context.filesDir, "chat_wallpaper.jpg")
                context.contentResolver.openInputStream(it)?.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
                android.net.Uri.fromFile(dest).toString() + "?v=" + System.currentTimeMillis()
            } catch (e: Exception) { null }
            if (saved != null) {
                repository.updateWallpaper("custom_image", saved)
                Toast.makeText(context, "Fond de conversation appliqué", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Impossible de lire cette photo", Toast.LENGTH_LONG).show()
            }
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
                            .background(com.example.mikayala.theme.MikayalaColors.surfaceSunken)
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
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(13.dp))
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
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(13.dp))
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


    // 3. App Icon & Camouflage Modal
    if (showAppIconModal) {
        // Changement RÉEL de l'icône de l'écran d'accueil (alias Android, voir util/AppIconManager.kt)
        var currentIcon by remember { mutableStateOf(com.example.mikayala.util.AppIconManager.current(context)) }
        Dialog(onDismissRequest = { showAppIconModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(text = "Icône de l'application", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "L'icône change sur l'écran d'accueil. Le téléphone peut mettre quelques secondes à l'afficher, et certains lanceurs retirent le raccourci : l'application reste alors dans la liste des applications.",
                        fontSize = 12.sp, color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    com.example.mikayala.util.AppIconManager.ICONS.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { icon ->
                                val selected = currentIcon == icon.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(84.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (icon.id != currentIcon) {
                                                if (com.example.mikayala.util.AppIconManager.apply(context, icon.id)) {
                                                    currentIcon = icon.id
                                                    Toast.makeText(context, "Icône « ${icon.label} » appliquée", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Le téléphone a refusé le changement d'icône.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                        .padding(6.dp)
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = icon.preview),
                                        contentDescription = icon.label,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .border(if (selected) 3.dp else 0.dp, if (selected) AccentRose else Color.Transparent, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = icon.label,
                                        fontSize = 10.sp,
                                        color = if (selected) TextPrimary else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showAppIconModal = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Fermer", color = AccentRose)
                    }
                }
            }
        }
    }

    // 4. Font & Typography Modal


    // 5. Bubble Style Modal


    // 6. WhatsApp-Style Privacy & Security Modal


    // 7. WhatsApp-Style Chats & Media Modal


    // 9. Storage Manager Modal


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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC4605C))
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC4605C))
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

    var section by remember { mutableStateOf<String?>(null) }
    // Préférences de notification : source de vérité = NotificationPrefs (persistant, local à l'appareil)
    var notifMode by remember { mutableStateOf(com.example.mikayala.util.NotificationPrefs.privacyMode(context)) }
    var notifSound by remember { mutableStateOf(com.example.mikayala.util.NotificationPrefs.sound(context)) }
    var notifVibration by remember { mutableStateOf(com.example.mikayala.util.NotificationPrefs.vibration(context)) }
    var notifCycle by remember { mutableStateOf(com.example.mikayala.util.NotificationPrefs.isEnabled(context, com.example.mikayala.util.NotificationPrefs.Category.CYCLE)) }
    androidx.activity.compose.BackHandler(enabled = section != null) { section = null }

    // --- MAIN SETTINGS SCREEN LAYOUT ---
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNight)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
    ) {
        if (section != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { section = null }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
                    }
                    Text(
                        text = when (section) {
                            "couple" -> "Mon compte"
                            "privacy" -> "Confidentialité et sécurité"
                            "notifications" -> "Notifications"
                            "conversations" -> "Conversations"
                            "calls" -> "Appels"
                            "appearance" -> "Apparence"
                            "calendar_cycle" -> "Calendrier et cycle"
                            "games" -> "Jeux"
                            "storage" -> "Stockage et données"
                            else -> "P2P et connexion locale"
                        },
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                }
            }
        }
        // --- Pages dédiées : Notifications, Appels, Jeux, Calendrier & cycle (réglages appliqués immédiatement) ---
        if (section == "notifications") {
            item {
                // Permission refusée (Android 13+) ou notifications coupées dans les réglages système
                val systemEnabled = remember(section) {
                    androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
                if (!systemEnabled) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x33C4605C),
                        modifier = Modifier.fillMaxWidth().clickable { openAppNotificationSettings(context) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Notifications bloquées par Android", fontWeight = FontWeight.Bold, color = Color(0xFFD98C97), fontSize = 15.sp)
                            Text("Aucune notification ne peut s'afficher. Touchez ici pour les autoriser dans les réglages du téléphone.", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            item { SettingsGroupTitle("Affichage") }
            item {
                Column {
                    listOf(
                        Triple("romantic", "Nom et aperçu", "Affiche le prénom de l'expéditeur et le début du message"),
                        Triple("discreet", "Discret", "Affiche « Mikayala » et « Nouveau message chiffré »"),
                        Triple("weather_camouflage", "Camouflage météo", "Affiche une fausse notification météo"),
                        Triple("system_camouflage", "Camouflage système", "Affiche « Mise à jour système prête »")
                    ).forEach { (id, title, desc) ->
                        SettingsRadioRow(
                            selected = notifMode == id,
                            title = title,
                            subtitle = desc,
                            onClick = {
                                notifMode = id
                                repository.updateNotificationSettings(id, notifSound, notifVibration, notifCycle, settings.loveWidgetEnabled)
                            }
                        )
                    }
                }
            }
            item { SettingsGroupTitle("Son et vibration") }
            item {
                Column {
                    com.example.mikayala.util.NotificationPrefs.AVAILABLE_SOUNDS.forEach { (id, label) ->
                        SettingsRadioRow(
                            selected = notifSound == id,
                            title = label,
                            subtitle = if (id == com.example.mikayala.util.NotificationPrefs.SOUND_SILENT) "Coupe aussi la vibration" else "Sonnerie choisie dans les réglages du téléphone",
                            onClick = {
                                notifSound = id
                                repository.updateNotificationSettings(notifMode, id, notifVibration, notifCycle, settings.loveWidgetEnabled)
                            }
                        )
                    }
                    SettingsSwitchRow(
                        title = "Vibration",
                        subtitle = if (notifSound == com.example.mikayala.util.NotificationPrefs.SOUND_SILENT) "Indisponible en mode silencieux" else "Double pulsation façon battement de cœur",
                        checked = notifVibration && notifSound != com.example.mikayala.util.NotificationPrefs.SOUND_SILENT,
                        enabled = notifSound != com.example.mikayala.util.NotificationPrefs.SOUND_SILENT,
                        onCheckedChange = {
                            notifVibration = it
                            repository.updateNotificationSettings(notifMode, notifSound, it, notifCycle, settings.loveWidgetEnabled)
                        }
                    )
                    Text(
                        text = "Android 8+ : si vous modifiez une catégorie dans les réglages du téléphone, ce choix système reste prioritaire.",
                        fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            item { SettingsGroupTitle("Catégories") }
            item {
                Column {
                    com.example.mikayala.util.NotificationPrefs.Category.values().forEach { category ->
                        NotificationCategorySwitch(category, onChanged = { cat, on ->
                            if (cat == com.example.mikayala.util.NotificationPrefs.Category.CYCLE) notifCycle = on
                        })
                    }
                }
            }
            item {
                SettingsCategoryRow(Icons.Rounded.Settings, TextSecondary, "Réglages système des notifications", "Autorisation, écran verrouillé, sons par catégorie") {
                    openAppNotificationSettings(context)
                }
            }
        }

        if (section == "storage") {
            item {
                var usage by remember { mutableStateOf<com.example.mikayala.util.StorageStats.Usage?>(null) }
                var refresh by remember { mutableIntStateOf(0) }
                LaunchedEffect(refresh) {
                    usage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.mikayala.util.StorageStats.measure(context) }
                }
                val fmt = com.example.mikayala.util.StorageStats::format
                Column {
                    SettingsGroupTitle("Sur cet appareil")
                    val u = usage
                    if (u == null) {
                        com.example.mikayala.ui.components.SoftLoading("Calcul de l'espace utilisé…")
                    } else {
                        SettingsInfoRow("Total utilisé", fmt(u.totalBytes))
                        SettingsInfoRow("Cache (images, fichiers temporaires)", fmt(u.cacheBytes))
                        SettingsInfoRow("Fichiers de l'application", fmt(u.filesBytes))
                        SettingsInfoRow("Données locales", fmt(u.databasesBytes))
                    }
                    SettingsInfoRow("Messages chargés", "${messages.size}")
                    SettingsInfoRow("Éléments du coffre", "${vaultItems.size}")
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsCategoryRow(Icons.Rounded.CleaningServices, AccentGold, "Vider le cache", "Libère de la place. Les messages, le coffre et vos réglages ne sont pas touchés.") {
                        scope.launch {
                            val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.mikayala.util.StorageStats.clearCache(context) }
                            Toast.makeText(context, "${fmt(freed)} libérés", Toast.LENGTH_SHORT).show()
                            refresh++
                        }
                    }
                    SettingsCategoryRow(Icons.Rounded.DeleteSweep, Color(0xFFC4605C), "Effacer toutes les discussions", "Une confirmation sera demandée") { showClearChatDialog = true }
                    Text(
                        "Les messages et le coffre sont sauvegardés sur le serveur du couple : ils reviennent après une réinstallation.",
                        fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        if (section == "calls") {
            item {
                Column {
                    NotificationCategorySwitch(com.example.mikayala.util.NotificationPrefs.Category.CALLS)
                    SettingsCategoryRow(Icons.Rounded.Mic, VibrantCyan, "Autorisations micro et caméra", "Ouvre les autorisations de Mikayala dans Android") {
                        openAppDetailsSettings(context)
                    }
                }
            }
        }

        if (section == "games") {
            item {
                Column {
                    NotificationCategorySwitch(com.example.mikayala.util.NotificationPrefs.Category.GAMES)
                }
            }
        }

        if (section == "calendar_cycle") {
            item {
                Column {
                    NotificationCategorySwitch(com.example.mikayala.util.NotificationPrefs.Category.CALENDAR)
                    NotificationCategorySwitch(com.example.mikayala.util.NotificationPrefs.Category.CYCLE, onChanged = { _, on -> notifCycle = on })
                    Text(
                        text = "Les rappels du cycle affichent uniquement « Vous avez une nouvelle notification » : aucune donnée de santé n'apparaît sur l'écran verrouillé.",
                        fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        if (section == null) {
            item {
                Text(text = "Paramètres", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (section == null) {
            item {
                Column {
                    SettingsCategoryRow(Icons.Rounded.Person, AccentRose, "Mon compte", "Profils, surnoms, jumelage, appareils, serveur") { section = "couple" }
                    SettingsCategoryRow(Icons.Rounded.Lock, AccentViolet, "Confidentialité et sécurité", "Présence, accusés de lecture, codes PIN, verrouillage") { section = "privacy" }
                    SettingsCategoryRow(Icons.Rounded.Notifications, AccentGold, "Notifications", "Aperçus, son, vibration, catégories") { section = "notifications" }
                    SettingsCategoryRow(Icons.Rounded.Chat, VibrantCyan, "Conversations", "Fond d'écran, bulles, police, médias") { section = "conversations" }
                    SettingsCategoryRow(Icons.Rounded.Call, VibrantCyan, "Appels", "Notifications d'appel, micro et caméra") { section = "calls" }
                    SettingsCategoryRow(Icons.Rounded.Storage, TextSecondary, "Stockage et données", "Espace utilisé, cache, sauvegardes") { section = "storage" }
                    SettingsCategoryRow(Icons.Rounded.Palette, AccentRose, "Apparence", "Thème sombre ou clair, icône de l'application") { section = "appearance" }
                    SettingsCategoryRow(Icons.Rounded.CalendarMonth, AccentRose, "Calendrier et cycle", "Anniversaire, rappels, notifications du cycle") { section = "calendar_cycle" }
                    SettingsCategoryRow(Icons.Rounded.SportsEsports, AccentGold, "Jeux", "Notifications d'invitation et de tour") { section = "games" }
                    SettingsCategoryRow(Icons.Rounded.WifiTethering, AccentViolet, "P2P et connexion locale", "En ligne ou hors-ligne (liaison directe)") { section = "connection" }
                }
            }
        }
        // --- 1. HERO COUPLE PROFILES & STATUS HEADER ---
        if (section == null) {
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
        }

        // --- 2. SECTION: PERSONNALISATION VISUELLE & DESIGN ---

        if (section == "appearance" || section == "conversations") {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
                border = BorderStroke(0.5.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section == "appearance") {
                    SettingRow(
                        icon = Icons.Rounded.AppShortcut,
                        iconTint = AccentGold,
                        title = "Changer l'Icône de l'Application",
                        subtitle = "10 icônes au choix pour l'écran d'accueil",
                        onClick = { showAppIconModal = true }
                    )
                    }

                    // App theme mode switch (Mode Sombre / Clair)
                    if (section == "appearance") {
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
        }
        }

        // --- 3. SECTION: ESPACE COUPLE & SURNOMS ---

        if (section == "couple" || section == "calendar_cycle") {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
                border = BorderStroke(0.5.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section == "couple") {
                    SettingRow(
                        icon = Icons.Rounded.Loyalty,
                        iconTint = AccentRose,
                        title = "Surnom du Partenaire & Profils",
                        subtitle = "Personnaliser son surnom ('${settings.partnerNickname}') et avatar",
                        onClick = { showProfileModal = true }
                    )
                    }

                    if (section == "calendar_cycle") {
                    SettingRow(
                        icon = Icons.Rounded.FavoriteBorder,
                        iconTint = AccentRose,
                        title = "Date d'Anniversaire & Citation",
                        subtitle = "Compteur de relation et devise d'amour",
                        onClick = { showAnniversaryDialog = true }
                    )
                    }

                    if (section == "couple") {
                    SettingRow(
                        icon = Icons.Rounded.Vibration,
                        iconTint = VibrantCyan,
                        title = "Pense à moi",
                        subtitle = "Envoie une notification « je pense à toi » à votre partenaire",
                        onClick = {
                            scope.launch {
                                val sent = repository.sendLovePulse()
                                Toast.makeText(
                                    context,
                                    if (sent) "Envoyé à ${settings.partnerNickname} 💓" else "Envoi impossible : vérifiez la connexion et le jumelage",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    }

                    if (section == "couple") {
                    SettingRow(
                        icon = Icons.Rounded.QrCode,
                        iconTint = AccentViolet,
                        title = "Code de Jumelage Couple (${coupleSpace.pairingCode})",
                        subtitle = "Gérer la liaison ou scanner le QR code",
                        onClick = { showPairingDialog = true }
                    )
                    }

                    if (section == "couple") {
                    SettingRow(
                        icon = Icons.Rounded.AutoAwesome,
                        iconTint = VibrantCyan,
                        title = "Module d'Onboarding & Jumelage Multi-appareils",
                        subtitle = "Créer, rejoindre, lier une tablette ou restaurer l'espace",
                        onClick = { showOnboardingScreen = true }
                    )
                    }

                    if (section == "couple") {
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
        }
        }

        // --- 4. SECTION: CONFIDENTIALITÉ & SÉCURITÉ (STYLE WHATSAPP) ---

        if (section == "privacy") {
            item { SettingsGroupTitle("Visibilité") }
            item {
                Column {
                    SettingsSwitchRow(
                        title = "Accusés de lecture",
                        subtitle = "Votre partenaire voit quand vous avez lu ses messages",
                        checked = settings.readReceiptsEnabled,
                        onCheckedChange = { on -> repository.updateUiSettings { it.copy(readReceiptsEnabled = on) } }
                    )
                    SettingsSwitchRow(
                        title = "Dernière connexion",
                        subtitle = if (settings.lastSeenPrivacy != "nobody") "Votre activité récente est partagée avec votre partenaire" else "Votre activité n'est plus publiée",
                        checked = settings.lastSeenPrivacy != "nobody",
                        onCheckedChange = { on -> repository.updateUiSettings { it.copy(lastSeenPrivacy = if (on) "couple_only" else "nobody") } }
                    )
                }
            }
            item { SettingsGroupTitle("Protection de l'écran") }
            item {
                SettingsSwitchRow(
                    title = "Bloquer les captures d'écran",
                    subtitle = "Empêche captures et enregistrements, et masque l'application dans les applications récentes",
                    checked = settings.antiScreenshotEnabled || settings.screenBlurOnSwitch,
                    onCheckedChange = { on -> repository.updateUiSettings { it.copy(antiScreenshotEnabled = on, screenBlurOnSwitch = on) } }
                )
            }
            item { SettingsGroupTitle("Verrouillage") }
            item {
                Column {
                    SettingsSwitchRow(
                        title = "Déverrouillage par empreinte",
                        subtitle = if (settings.hasLocalPassword) "Proposé sur l'écran de verrouillage" else "Définissez d'abord un code PIN",
                        checked = settings.biometricEnabled,
                        enabled = settings.hasLocalPassword,
                        onCheckedChange = { on -> repository.updateUiSettings { it.copy(biometricEnabled = on) } }
                    )
                    Text(
                        text = "Verrouiller après",
                        fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                    listOf(0 to "Quelques secondes", 60 to "1 minute", 300 to "5 minutes", -1 to "Jamais").forEach { (secs, label) ->
                        SettingsRadioRow(
                            selected = settings.lockTimeoutSeconds == secs,
                            title = label,
                            subtitle = if (secs == 0) "Dès que vous quittez l'application (5 secondes de grâce)" else "",
                            onClick = { repository.updateLockTimeout(secs) }
                        )
                    }
                    SettingsCategoryRow(Icons.Rounded.Password, AccentRose, "Code PIN", if (settings.hasLocalPassword) "Modifier le code de déverrouillage" else "Créer un code de déverrouillage") { showPinDialog = true }
                    SettingsCategoryRow(Icons.Rounded.Calculate, AccentViolet, "Code leurre", "Un second code qui ouvre une calculatrice fonctionnelle") { showDecoyPinDialog = true }
                }
            }
        }

        if (section == "conversations") {
            item { SettingsGroupTitle("Saisie et envoi") }
            item {
                Column {
                    SettingsSwitchRow(
                        title = "Touche Entrée pour envoyer",
                        subtitle = "La touche du clavier envoie le message au lieu d'aller à la ligne",
                        checked = settings.enterIsSend,
                        onCheckedChange = { on -> repository.updateUiSettings { it.copy(enterIsSend = on) } }
                    )
                    Text("Qualité des photos envoyées", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    SettingsRadioRow(settings.photoUploadQuality != "standard", "Haute qualité", "Jusqu'à 2560 px, plus lourd") {
                        repository.updateUiSettings { it.copy(photoUploadQuality = "hd") }
                    }
                    SettingsRadioRow(settings.photoUploadQuality == "standard", "Économie de données", "Jusqu'à 1600 px, envoi plus rapide") {
                        repository.updateUiSettings { it.copy(photoUploadQuality = "standard") }
                    }
                }
            }
            item { SettingsGroupTitle("Apparence des messages") }
            item {
                Column {
                    Text("Couleur de vos bulles", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
                    com.example.mikayala.theme.SoftChatStyles.BUBBLES.forEach { (id, label) ->
                        val sw = com.example.mikayala.theme.SoftChatStyles.bubble(id, com.example.mikayala.theme.MikayalaColors, true).background
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.padding(start = 8.dp).size(18.dp).clip(CircleShape).background(sw).border(0.5.dp, BorderSubtleWhite, CircleShape))
                            SettingsRadioRow(settings.bubbleStyle == id, label, "") { repository.updateBubbleStyle(id) }
                        }
                    }
                    Text("Fond de conversation", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    com.example.mikayala.theme.SoftChatStyles.WALLPAPERS.forEach { (id, label) ->
                        SettingsRadioRow(settings.chatTheme == id || (id == "twilight" && settings.chatTheme !in com.example.mikayala.theme.SoftChatStyles.WALLPAPERS.map { it.first } && settings.chatTheme != "custom_image"), label, "") {
                            repository.updateWallpaper(id, "")
                        }
                    }
                    SettingsRadioRow(settings.chatTheme == "custom_image", "Ma photo", if (settings.chatTheme == "custom_image") "Touchez pour en choisir une autre" else "Choisir une photo de la galerie") {
                        wallpaperPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (settings.chatTheme == "custom_image") {
                        Text("Visibilité de la photo : ${(settings.wallpaperOpacity * 100).toInt()} %", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                        Slider(
                            value = settings.wallpaperOpacity,
                            onValueChange = { repository.updateWallpaperControls(it, 0f) },
                            valueRange = 0.2f..1f,
                            colors = SliderDefaults.colors(thumbColor = AccentRose, activeTrackColor = AccentRose)
                        )
                    }
                }
            }
            item { SettingsGroupTitle("Texte") }
            item {
                Column {
                    Text("Taille du texte", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
                    listOf("small" to "Petite", "medium" to "Normale", "large" to "Grande", "xlarge" to "Très grande").forEach { (id, label) ->
                        SettingsRadioRow(settings.fontSizeScale == id, label, "") { repository.updateFontSize(id) }
                    }
                    Text("Police", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    listOf("default" to "Poppins (par défaut)", "system" to "Police du téléphone", "serif" to "Élégante (serif)", "cursive" to "Manuscrite").forEach { (id, label) ->
                        SettingsRadioRow(settings.fontFamilyPreference == id, label, "") { repository.updateFontFamily(id) }
                    }
                }
            }
            item { SettingsGroupTitle("Historique") }
            item {
                SettingsCategoryRow(Icons.Rounded.DeleteSweep, Color(0xFFC4605C), "Effacer toutes les discussions", "Supprime l'historique de cet appareil (une confirmation sera demandée)") {
                    showClearChatDialog = true
                }
            }
        }

        // --- 6. SECTION: MODES DE CONNEXION (2 OPTIONS) ---

        if (section == "connection") {
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
                        color = if (isOnline) CardDarkElevated else com.example.mikayala.theme.MikayalaColors.surfaceSunken,
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
                        color = if (!isOnline) CardDarkElevated else com.example.mikayala.theme.MikayalaColors.surfaceSunken,
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
                            tint = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ouvrir le Gestionnaire de Liaison Directe 📡",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
        }

        // --- 7. FOOTER: DISSOCIATION ---
        if (section == null) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x22C4605C),
                border = BorderStroke(1.dp, Color(0x44C4605C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDissolveDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = Color(0xFFC4605C), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Dissocier l'Espace Couple", color = Color(0xFFC4605C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
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
                            color = if (success) Color(0x227FAF93) else Color(0x22C4605C),
                            border = BorderStroke(1.dp, if (success) Color(0xFF7FAF93) else Color(0xFFC4605C)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                color = if (success) Color(0xFF7FAF93) else Color(0xFFC4605C),
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
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsGroupTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AccentRose,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = if (enabled) TextPrimary else TextSecondary)
            Text(text = subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRose)
        )
    }
}

@Composable
private fun SettingsRadioRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = AccentRose))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, color = TextPrimary)
            if (subtitle.isNotBlank()) Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

/** Interrupteur d'une catégorie, lu et écrit directement dans NotificationPrefs (persistant, local). */
@Composable
private fun NotificationCategorySwitch(
    category: com.example.mikayala.util.NotificationPrefs.Category,
    onChanged: (com.example.mikayala.util.NotificationPrefs.Category, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var enabled by remember(category) {
        mutableStateOf(com.example.mikayala.util.NotificationPrefs.isEnabled(context, category))
    }
    SettingsSwitchRow(
        title = "Notifications : " + category.label.replaceFirstChar { it.lowercase() },
        subtitle = if (enabled) "Activées" else "Désactivées : rien n'est affiché pour cette catégorie",
        checked = enabled,
        onCheckedChange = {
            enabled = it
            com.example.mikayala.util.NotificationPrefs.setEnabled(context, category, it)
            onChanged(category, it)
        }
    )
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        // Android 7 : écran des détails de l'application
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
    }
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (_: Exception) { openAppDetailsSettings(context) }
}

private fun openAppDetailsSettings(context: Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (_: Exception) {}
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(text = subtitle, fontSize = 13.sp, color = TextSecondary, maxLines = 2)
        }
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
