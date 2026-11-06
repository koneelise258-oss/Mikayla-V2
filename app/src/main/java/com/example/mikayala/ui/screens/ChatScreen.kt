package com.example.mikayala.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.mikayala.ui.media.MediaEditorTarget
import com.example.mikayala.ui.components.InAppCameraCaptureDialog
import com.example.mikayala.ui.components.CameraCaptureMode
import com.example.mikayala.ui.media.photo.PhotoEditorScreen
import com.example.mikayala.ui.media.video.VideoEditorScreen
import kotlinx.coroutines.isActive
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.mikayala.util.ChatGames
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.data.model.MessageEntity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.utils.VoiceNoteManager
import com.example.mikayala.utils.VoiceNoteState
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.ChatAttachmentAndGamesBottomSheet
import com.example.mikayala.ui.components.MessageOptionBottomSheet
import com.example.mikayala.ui.components.EmojiPicker
import com.example.mikayala.ui.components.MessageStatusIndicator
import com.example.mikayala.ui.components.NeumorphicSquircleButton
import com.example.mikayala.ui.components.VoicePlayerWaveform
import com.example.mikayala.ui.components.VoiceNoteRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: MikayalaRepository,
    onStartCall: (isVideo: Boolean) -> Unit,
    searchMode: String? = null,
    onCloseSearch: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val voiceNoteManager = remember { VoiceNoteManager(context) }

    // 📸 REAL PHOTO, VIDEO & CAMERA LAUNCHERS (Routed to Media Editor)
    var activeMediaEditorTarget by remember { mutableStateOf<MediaEditorTarget?>(null) }
    var showInAppCamera by remember { mutableStateOf(false) }
    var inAppCameraMode by remember { mutableStateOf(CameraCaptureMode.PHOTO) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d("ChatScreen", "[MIKAYALA_IMAGE] Gallery picker selected URI=$uri")
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.startsWith("video")) {
                activeMediaEditorTarget = MediaEditorTarget.Video(uri)
            } else {
                activeMediaEditorTarget = MediaEditorTarget.PhotoUri(uri)
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d("ChatScreen", "[MIKAYALA_VIDEO] Video launcher selected URI=$uri")
            activeMediaEditorTarget = MediaEditorTarget.Video(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            Log.d("ChatScreen", "[MIKAYALA_IMAGE] Camera captured photo bitmap ${bitmap.width}x${bitmap.height}")
            activeMediaEditorTarget = MediaEditorTarget.PhotoBitmap(bitmap)
        }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone activé 🎙️ Vous pouvez enregistrer", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission microphone requise pour les vocaux", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Vrai envoi du fichier dans le stockage privé du couple (avant : seule l'adresse LOCALE
            // « content://… » partait, illisible sur le téléphone du partenaire)
            val fileName = try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (_: Exception) { null } ?: uri.lastPathSegment ?: "Document"
            repository.sendDocumentMedia(uri, fileName)
            Toast.makeText(context, "Envoi du document…", Toast.LENGTH_SHORT).show()
        }
    }

    // Fichier audio réel (avant : faux morceau « Chanson de notre première danse » sans son)
    val audioFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            repository.sendAudioFile(uri) { ok ->
                if (!ok) Toast.makeText(context, "Fichier audio illisible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Contact réel choisi dans le carnet (avant : faux « +33 6 12 34 56 78 »)
    val contactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0).orEmpty()
                    val number = c.getString(1).orEmpty()
                    repository.sendMessage(content = "$name ($number)".trim(), type = "contact", mediaUrl = number)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Contact illisible", Toast.LENGTH_SHORT).show()
        }
    }

    // Position réelle (avant : position FIXE à Paris envoyée quel que soit l'endroit)
    val sendRealLocation: () -> Unit = {
        coroutineScope.launch {
            Toast.makeText(context, "Recherche de la position…", Toast.LENGTH_SHORT).show()
            val loc = com.example.mikayala.util.OneShotLocation.get(context)
            if (loc == null) {
                Toast.makeText(context, "Position indisponible : activez la localisation", Toast.LENGTH_LONG).show()
            } else {
                repository.sendLocationMessage(loc.latitude, loc.longitude, "Ma position")
            }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) sendRealLocation()
        else Toast.makeText(context, "Autorisation de localisation refusée", Toast.LENGTH_SHORT).show()
    }
    var showPollComposer by remember { mutableStateOf(false) }
    var showEventComposer by remember { mutableStateOf(false) }

    val rawMessages by repository.allMessages.collectAsState()
    val myUserId = remember { repository.getCurrentUserId() }
    val activeCoupleId = repository.activeCoupleSpace.collectAsState().value.id
    val baseMessages = remember(rawMessages, myUserId, activeCoupleId) {
        rawMessages.filter {
            !it.deletedFor.contains(myUserId) &&
                (activeCoupleId.isEmpty() || it.coupleId.isEmpty() || it.coupleId == activeCoupleId)
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    val messages = remember(baseMessages, searchMode, searchQuery) {
        when (searchMode) {
            "starred" -> baseMessages.filter { it.isStarred }
            "search" -> {
                if (searchQuery.isBlank()) {
                    baseMessages
                } else {
                    baseMessages.filter {
                        it.content.contains(searchQuery, ignoreCase = true) ||
                        (it.replyToContent?.contains(searchQuery, ignoreCase = true) == true)
                    }
                }
            }
            else -> baseMessages
        }
    }

    val isPartnerTyping by repository.isPartnerTyping.collectAsState()
    val isPartnerRecording by repository.isPartnerRecordingAudio.collectAsState()
    val userSettings by repository.userSettings.collectAsState()

    // Pagination : bouton « Messages précédents » en haut de la conversation
    val hasOlderMessages by repository.hasOlderMessages.collectAsState()
    var loadingOlder by remember { mutableStateOf(false) }
    val hasOlderHeader = hasOlderMessages && searchMode == null && messages.isNotEmpty()

    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var textInput by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<MessageEntity?>(null) }

    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showEmojiBar by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedMessageIds.isNotEmpty() || searchMode != null || showEmojiBar || showAttachmentSheet) {
        if (selectedMessageIds.isNotEmpty()) {
            selectedMessageIds = emptySet()
        } else if (searchMode != null) {
            searchQuery = ""
            onCloseSearch()
        } else if (showEmojiBar) {
            showEmojiBar = false
        } else if (showAttachmentSheet) {
            showAttachmentSheet = false
        }
    }

    val voiceRecordingState by voiceNoteManager.recordingState.collectAsState()

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }

    // Défilement vers le bas seulement quand un NOUVEAU message arrive en fin de liste
    // (pas quand des messages plus anciens sont chargés en haut).
    val lastMessageId = messages.lastOrNull()?.id
    // Ouverture : positionnement immédiat sur le dernier message (avant : défilement animé depuis le
    // tout premier message à chaque ouverture). Ensuite : animation douce pour chaque nouveau message.
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(lastMessageId) {
        if (messages.isNotEmpty()) {
            val target = messages.size - 1 + if (hasOlderHeader) 1 else 0
            if (!initialScrollDone) {
                listState.scrollToItem(target)
                initialScrollDone = true
            } else {
                // Ne pas arracher la lecture d'anciens messages : on ne descend que si l'on était déjà
                // en bas, ou si le nouveau message vient de moi.
                val info = listState.layoutInfo
                val nearBottom = (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
                val mine = messages.lastOrNull()?.let { repository.isMessageFromMe(it) } == true
                if (nearBottom || mine) listState.animateScrollToItem(target)
            }
        }
    }

    val coupleSpace by repository.activeCoupleSpace.collectAsState()
    DisposableEffect(Unit) {
        com.example.mikayala.util.P2PSocketManager.isChatScreenActive = true
        com.example.mikayala.util.P2PSocketManager.sendReadAckAll()
        onDispose {
            com.example.mikayala.util.P2PSocketManager.isChatScreenActive = false
            voiceNoteManager.release()
        }
    }

    // Accusés : à chaque nouveau message ET au retour au premier plan, uniquement si l'écran est visible
    val chatLifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val chatVisible = chatLifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    LaunchedEffect(lastMessageId, coupleSpace.id, chatVisible) {
        val coupleId = coupleSpace.id
        if (coupleId.isNotEmpty() && chatVisible) {
            repository.markMessagesDelivered(coupleId)
            repository.markMessagesRead(coupleId)
            com.example.mikayala.util.P2PSocketManager.sendReadAckAll()
        }
    }

    // Plus de synchronisation complète toutes les 2,5 s ici (le dépôt s'en charge, et le temps réel
    // met à jour les accusés en direct). À l'ouverture de la conversation : rattrapage immédiat.
    LaunchedEffect(Unit) {
        repository.requestFullSync()
    }

    // Échec d'une action (réaction, modification, épinglage, suppression) : on le dit au lieu de faire semblant
    LaunchedEffect(Unit) {
        repository.actionErrors.collect { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }

    // Broadcast typing status with debounce
    LaunchedEffect(textInput) {
        val isTyping = textInput.isNotBlank()
        repository.sendTypingBroadcast(isTyping)
        if (isTyping) {
            delay(3500)
            repository.sendTypingBroadcast(false)
        }
    }

    // Broadcast recording status to partner
    LaunchedEffect(voiceRecordingState) {
        val isRecording = (voiceRecordingState == VoiceNoteState.RECORDING || voiceRecordingState == VoiceNoteState.LOCKED)
        repository.sendRecordingBroadcast(isRecording)
    }

    // Pinned Message banner
    val pinnedMessage = messages.lastOrNull { it.isPinned }

    val wallpaperBrush = remember(userSettings.chatTheme) {
        when (userSettings.chatTheme) {
            "sunset" -> Brush.verticalGradient(listOf(Color(0xFF2D0B38), Color(0xFF140727), Color(0xFF0F172A)))
            "rose_gold" -> Brush.verticalGradient(listOf(Color(0xFF2A0A3D), Color(0xFF1C0A2A), Color(0xFF090D16)))
            "cyber_cyan" -> Brush.verticalGradient(listOf(Color(0xFF052331), Color(0xFF071924), Color(0xFF070B12)))
            "matte_black" -> Brush.verticalGradient(listOf(Color(0xFF030507), Color(0xFF080C12), Color(0xFF05070A)))
            else -> Brush.verticalGradient(listOf(Color(0xFF0B0F19), Color(0xFF080C14), Color(0xFF05070A)))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Draw the background with blur and opacity customizations (guarded for Android 12+ API 31+)
        val isBlurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && userSettings.wallpaperBlur > 0f
        if (userSettings.chatTheme == "custom_image" && userSettings.customWallpaperUri.isNotEmpty()) {
            val blurModifier = if (isBlurSupported) {
                Modifier.blur(userSettings.wallpaperBlur.dp)
            } else {
                Modifier
            }
            coil.compose.AsyncImage(
                model = userSettings.customWallpaperUri,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = userSettings.wallpaperOpacity,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
        } else {
            val blurModifier = if (isBlurSupported) {
                Modifier.blur(userSettings.wallpaperBlur.dp)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = userSettings.wallpaperOpacity)
                    .then(blurModifier)
                    .background(wallpaperBrush)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Multi-Select Bar Header Banner
            AnimatedVisibility(visible = selectedMessageIds.isNotEmpty()) {
                Surface(
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedMessageIds = emptySet() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Annuler", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${selectedMessageIds.size} sélectionné(s)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Copy text
                            IconButton(onClick = {
                                val combinedText = messages
                                    .filter { it.id in selectedMessageIds }
                                    .joinToString("\n") { it.content }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Copied Messages", combinedText))
                                Toast.makeText(context, "Messages copiés 📋", Toast.LENGTH_SHORT).show()
                                selectedMessageIds = emptySet()
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier", tint = VibrantCyan, modifier = Modifier.size(20.dp))
                            }

                            // Star selected
                            IconButton(onClick = {
                                selectedMessageIds.forEach { id ->
                                    repository.toggleStarred(id)
                                }
                                Toast.makeText(context, "Favoris mis à jour ⭐", Toast.LENGTH_SHORT).show()
                                selectedMessageIds = emptySet()
                            }) {
                                Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = AccentGold, modifier = Modifier.size(20.dp))
                            }

                            // Delete selected
                            IconButton(onClick = {
                                selectedMessageIds.forEach { id ->
                                    repository.deleteMessage(id, false)
                                }
                                Toast.makeText(context, "Messages supprimés 🗑️", Toast.LENGTH_SHORT).show()
                                selectedMessageIds = emptySet()
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer", tint = AccentRose, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // Search or Starred Filter Banner
            AnimatedVisibility(visible = searchMode != null) {
                Surface(
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, if (searchMode == "starred") AccentGold.copy(alpha = 0.5f) else VibrantCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (searchMode == "starred") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "Favoris",
                                    tint = AccentGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Messages favoris ⭐ (${messages.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    onCloseSearch()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Fermer",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // search mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Rechercher",
                                tint = VibrantCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Rechercher dans la conversation...",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Effacer",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${messages.size}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    onCloseSearch()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Fermer la recherche",
                                    tint = AccentRose,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Pinned Banner
            AnimatedVisibility(visible = pinnedMessage != null && selectedMessageIds.isEmpty()) {
                pinnedMessage?.let { pin ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardModalSurface,
                        border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable {
                                val idx = messages.indexOfFirst { it.id == pin.id }
                                if (idx != -1) {
                                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Épinglé",
                                tint = AccentRose,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Message épinglé",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRose
                                )
                                Text(
                                    text = pin.content,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { repository.togglePinned(pin.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Détacher",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
            ) {
                if (hasOlderHeader) {
                    item(key = "older_messages_header") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(
                                enabled = !loadingOlder,
                                onClick = {
                                    loadingOlder = true
                                    coroutineScope.launch {
                                        val n = repository.loadOlderMessages()
                                        loadingOlder = false
                                        if (n < 0) Toast.makeText(context, "Impossible de charger les anciens messages (réseau)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text(if (loadingOlder) "Chargement…" else "Afficher les messages précédents")
                            }
                        }
                    }
                }
                itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                    val currentDay = remember(message.createdAt) { getDayHeaderText(message.createdAt) }
                    val previousDay = if (index > 0) {
                        remember(messages[index - 1].createdAt) { getDayHeaderText(messages[index - 1].createdAt) }
                    } else null

                    if (previousDay != currentDay) {
                        DateHeaderItem(dayText = currentDay)
                    }

                    val isSelected = message.id in selectedMessageIds

                    MessageBubble(
                        message = message,
                        repository = repository,
                        currentUserId = repository.getCurrentUserId(),
                        timeFormatted = timeFormat.format(Date(message.createdAt)),
                        userSettings = userSettings,
                        isSelected = isSelected,
                        isMultiSelectActive = selectedMessageIds.isNotEmpty(),
                        onToggleSelect = {
                            selectedMessageIds = if (isSelected) {
                                selectedMessageIds - message.id
                            } else {
                                selectedMessageIds + message.id
                            }
                        },
                        onLongPress = {
                            if (selectedMessageIds.isEmpty()) {
                                selectedMessageForOptions = message
                            } else {
                                selectedMessageIds = if (isSelected) {
                                    selectedMessageIds - message.id
                                } else {
                                    selectedMessageIds + message.id
                                }
                            }
                        },
                        onReply = { replyToMessage = message },
                        onToggleReaction = { emoji -> repository.toggleReaction(message.id, emoji) },
                        onViewOnceClicked = {
                            repository.markViewOnceAsViewed(message.id)
                        }
                    )
                }

                // Partner presence indicators in chat
                if (isPartnerTyping || isPartnerRecording) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OnlinePresenceGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val partnerDisplayName = userSettings.partnerNickname.ifBlank { "Votre partenaire" }
                            Text(
                                text = if (isPartnerRecording) "$partnerDisplayName enregistre un audio... 🎙️" else "$partnerDisplayName est en train d'écrire... ✨",
                                fontSize = 12.sp,
                                color = AccentViolet
                            )
                        }
                    }
                }
            }

            // Reply Bar Banner
            AnimatedVisibility(visible = replyToMessage != null) {
                replyToMessage?.let { reply ->
                    Surface(
                        color = CardDark,
                        border = BorderStroke(1.dp, BorderHighlight),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AccentRose)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (repository.isMessageFromMe(reply)) "Réponse à vous-même" else "Réponse à Mikayala",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRose
                                )
                                Text(
                                    text = reply.content,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { replyToMessage = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Annuler", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Editing Bar Banner
            AnimatedVisibility(visible = editingMessageId != null) {
                Surface(
                    color = CardElevated,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Modification du message ✏️", fontSize = 12.sp, color = AccentGold, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            editingMessageId = null
                            textInput = ""
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Annuler", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Quick Emoji Drawer removed in favor of premium EmojiPicker below

            // Barre de saisie & Enregistreur vocal WhatsApp haute fidélité
            VoiceNoteRecorder(
                manager = voiceNoteManager,
                textInput = textInput,
                onTextInputChange = { input ->
                    textInput = autoCapitalizeMessageInput(input, textInput)
                },
                onSendText = {
                    val currentEditId = editingMessageId
                    if (currentEditId != null) {
                        repository.editMessage(currentEditId, textInput)
                        editingMessageId = null
                    } else {
                        repository.sendMessage(
                            content = textInput,
                            type = "text",
                            replyToId = replyToMessage?.id,
                            replyToContent = replyToMessage?.content
                        )
                        textInput = ""
                        replyToMessage = null
                    }
                },
                onSendVoiceNote = { file, duration ->
                    repository.sendVoiceNote(file.absolutePath, duration)
                    Toast.makeText(context, "Vocal en cours d'envoi... 🎙️", Toast.LENGTH_SHORT).show()
                },
                onRequestRecordPermission = {
                    recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                hasRecordPermission = { hasRecordAudioPermission() },
                onAttachmentClick = { showAttachmentSheet = true },
                onCameraClick = {
                    inAppCameraMode = CameraCaptureMode.PHOTO
                    showInAppCamera = true
                },
                onEmojiClick = { showEmojiBar = !showEmojiBar },
                showEmojiBar = showEmojiBar
            )

            // Full Premium Emoji Picker Panel
            AnimatedVisibility(visible = showEmojiBar) {
                EmojiPicker(
                    onEmojiSelected = { emoji ->
                        textInput += emoji
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        }

        // Attachment and Chat Games Bottom Sheet Modal (From Trombone 📎)
        if (showAttachmentSheet) {
            ChatAttachmentAndGamesBottomSheet(
                onDismiss = { showAttachmentSheet = false },
                onSendGallery = {
                    showAttachmentSheet = false
                    try {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Impossible d'ouvrir la galerie", Toast.LENGTH_SHORT).show()
                    }
                },
                onSendCamera = {
                    showAttachmentSheet = false
                    inAppCameraMode = CameraCaptureMode.PHOTO
                    showInAppCamera = true
                },
                onSendVideo = {
                    showAttachmentSheet = false
                    try {
                        videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Impossible d'ouvrir le sélecteur vidéo", Toast.LENGTH_SHORT).show()
                    }
                },
                onSendLocation = {
                    showAttachmentSheet = false
                    if (com.example.mikayala.util.OneShotLocation.hasPermission(context)) sendRealLocation()
                    else locationPermissionLauncher.launch(arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                },
                onSendContact = {
                    showAttachmentSheet = false
                    try {
                        contactLauncher.launch(android.content.Intent(
                            android.content.Intent.ACTION_PICK,
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        ))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Carnet d'adresses indisponible", Toast.LENGTH_SHORT).show()
                    }
                },
                onSendDocument = {
                    showAttachmentSheet = false
                    try {
                        documentLauncher.launch("*/*")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Impossible d'ouvrir le sélecteur de fichiers", Toast.LENGTH_SHORT).show()
                    }
                },
                onSendAudio = {
                    showAttachmentSheet = false
                    try { audioFileLauncher.launch("audio/*") } catch (e: Exception) {
                        Toast.makeText(context, "Impossible d'ouvrir le sélecteur audio", Toast.LENGTH_SHORT).show()
                    }
                },
                onSendPoll = {
                    showAttachmentSheet = false
                    showPollComposer = true
                },
                onSendEvent = {
                    showAttachmentSheet = false
                    showEventComposer = true
                },
                // « Image IA » retirée : aucune génération d'image n'existe (seul un texte était envoyé)
                onSendAiImage = null,
                // Chat Games
                onSendScratchCard = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "🎁 Carte Mystère : Massage du dos sensuel aux huiles chaudes (20 min) 💆‍♂️", type = "scratch_card")
                    Toast.makeText(context, "Carte à gratter envoyée 🎴", Toast.LENGTH_SHORT).show()
                },
                onSendTruthOrDare = {
                    showAttachmentSheet = false
                    val challenges = listOf(
                        "🔥 Action : Fais-moi un massage des mains pendant 2 minutes les yeux fermés.",
                        "✨ Vérité : Quel a été le moment exact où tu as su que tu tombais amoureux(se) de moi ?",
                        "🔥 Action : Chuchote à mon oreille la chose la plus douce que tu n'as jamais osé dire.",
                        "✨ Vérité : Quel est ton fantasme secret pour notre prochain voyage à deux ?"
                    )
                    repository.sendMessage(content = challenges.random(), type = "truth_or_dare")
                    Toast.makeText(context, "Défi Action ou Vérité lancé 🔥", Toast.LENGTH_SHORT).show()
                },
                onSendQuiz = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "🧠 Quiz Complicité : Quel est notre souvenir commun le plus émouvant ?", type = "quiz", mediaUrl = "Notre premier voyage en bord de mer au clair de lune.")
                    Toast.makeText(context, "Quiz de couple envoyé 🧠", Toast.LENGTH_SHORT).show()
                },
                onSendTicTacToe = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "Partie de Morpion de Couple ! ⭕❌", type = "tictactoe", mediaUrl = "---------")
                    Toast.makeText(context, "Partie de Morpion lancée dans le chat ⭕❌", Toast.LENGTH_SHORT).show()
                },
                onSendDilemma = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "Tu Préfères ? ⚖️", type = "dilemma", mediaUrl = "Un voyage surprise à Venise en gondole 🛶|Un chalet isolé au coin du feu sous la neige ❄️")
                    Toast.makeText(context, "Dilemme envoyé ⚖️", Toast.LENGTH_SHORT).show()
                },
                onSendLoveCoupon = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "🎟️ Bon d'Amour : Joker Câlin Infini sans condition ! Valable à tout moment ❤️", type = "coupon")
                    Toast.makeText(context, "Bon d'Amour offert 🎟️💖", Toast.LENGTH_SHORT).show()
                },
                onSendWheel = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "Roue de la Fortune d'Amour 🎡", type = "wheel", mediaUrl = "Massage 💆|Dîner aux chandelles 🍷|Soirée Film 🎬|Cuisiner ensemble 🍳|Baiser doux 💋|Vérité Intime 🤫")
                    Toast.makeText(context, "Roue de la Fortune lancée ! 🎡", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showPollComposer) {
            com.example.mikayala.ui.components.PollComposerDialog(
                onDismiss = { showPollComposer = false },
                onSend = { question, options ->
                    showPollComposer = false
                    repository.sendMessage(content = question, type = "poll", mediaUrl = options.joinToString("|"))
                }
            )
        }
        if (showEventComposer) {
            com.example.mikayala.ui.components.EventComposerDialog(
                onDismiss = { showEventComposer = false },
                onSend = { title, whenText ->
                    showEventComposer = false
                    repository.sendMessage(content = if (whenText.isBlank()) title else "$title ($whenText)", type = "event")
                }
            )
        }

        // Option Bottom Sheet Modal
        selectedMessageForOptions?.let { message ->
            MessageOptionBottomSheet(
                message = message,
                isFromMe = repository.isMessageFromMe(message),
                onDismiss = { selectedMessageForOptions = null },
                onReply = {
                    replyToMessage = message
                    selectedMessageForOptions = null
                },
                onEdit = {
                    editingMessageId = message.id
                    textInput = message.content
                    selectedMessageForOptions = null
                },
                onToggleStar = { repository.toggleStarred(message.id) },
                onTogglePin = { repository.togglePinned(message.id) },
                onSaveToVault = {
                    coroutineScope.launch {
                        val ok = repository.saveMessageToVault(message)
                        Toast.makeText(
                            context,
                            if (ok) "Ajouté au coffre" else "Non ajouté au coffre : vérifiez la connexion",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDeleteForMe = { repository.deleteMessage(message.id, false) },
                onDeleteForEveryone = { repository.deleteMessage(message.id, true) },
                onToggleReaction = { emoji -> repository.toggleReaction(message.id, emoji) }
            )
        }

        // 📷 IN-APP PHOTO & VIDEO CAMERA CAPTURE (Switch tabs + flash + flip)
        if (showInAppCamera) {
            InAppCameraCaptureDialog(
                initialMode = inAppCameraMode,
                onDismiss = { showInAppCamera = false },
                onPhotoCaptured = { bitmap ->
                    showInAppCamera = false
                    activeMediaEditorTarget = MediaEditorTarget.PhotoBitmap(bitmap)
                },
                onVideoCaptured = { uri ->
                    showInAppCamera = false
                    activeMediaEditorTarget = MediaEditorTarget.Video(uri)
                }
            )
        }

        // 🎨 NATIVE FULL-SCREEN MULTIMEDIA EDITORS (Before Sending)
        activeMediaEditorTarget?.let { target ->
            Dialog(
                onDismissRequest = { activeMediaEditorTarget = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                when (target) {
                    is MediaEditorTarget.PhotoUri -> {
                        PhotoEditorScreen(
                            imageUri = target.uri,
                            onDismiss = { activeMediaEditorTarget = null },
                            onComplete = { webpBytes, caption, isViewOnce ->
                                activeMediaEditorTarget = null
                                repository.sendEditedImageMedia(webpBytes, caption, isViewOnce)
                                Toast.makeText(context, "Photo éditée en cours d'envoi... 🖼️", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    is MediaEditorTarget.PhotoBitmap -> {
                        PhotoEditorScreen(
                            bitmap = target.bitmap,
                            onDismiss = { activeMediaEditorTarget = null },
                            onComplete = { webpBytes, caption, isViewOnce ->
                                activeMediaEditorTarget = null
                                repository.sendEditedImageMedia(webpBytes, caption, isViewOnce)
                                Toast.makeText(context, "Photo caméra éditée en cours d'envoi... 📸", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    is MediaEditorTarget.Video -> {
                        VideoEditorScreen(
                            videoUri = target.uri,
                            onDismiss = { activeMediaEditorTarget = null },
                            onComplete = { exportedFile, caption ->
                                activeMediaEditorTarget = null
                                repository.sendEditedVideoMedia(exportedFile, caption)
                                Toast.makeText(context, "Vidéo éditée en cours d'envoi... 🎬", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun getDayHeaderText(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Aujourd'hui"

        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Hier"

        else -> {
            val format = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
            format.format(Date(timestamp))
        }
    }
}

@Composable
private fun DateHeaderItem(dayText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0x331E2A3A),
            border = BorderStroke(1.dp, BorderSubtleWhite)
        ) {
            Text(
                text = dayText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    repository: MikayalaRepository,
    currentUserId: String,
    timeFormatted: String,
    userSettings: com.example.mikayala.data.model.UserSettingsEntity,
    isSelected: Boolean = false,
    isMultiSelectActive: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onViewOnceClicked: () -> Unit
) {
    // Largeur des bulles proportionnelle à l'écran (avant : 300 dp fixes, soit toute la largeur d'un
    // petit téléphone et des bulles minuscules sur tablette)
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val bubbleMaxWidth = remember(screenWidthDp) { (screenWidthDp * 0.78f).coerceIn(200f, 440f).dp }
    val context = LocalContext.current
    val isSender = repository.isMessageFromMe(message)

    LaunchedEffect(message.id, message.status, isSender) {
        Log.d("ChatScreen", "[MIKAYALA_MESSAGE] messageId=${message.id} senderId=${message.senderId} currentAuthUid=$currentUserId isSender=$isSender status=${message.status} deliveredAt=${message.deliveredAt} readAt=${message.readAt}")
    }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var replyTriggered by remember { mutableStateOf(false) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "swipe_reply_offset"
    )

    var showQuickReactionPicker by remember { mutableStateOf(false) }
    var showCustomEmojiDialog by remember { mutableStateOf(false) }
    var customEmojiInput by remember { mutableStateOf("") }

    val reactionsMap = remember(message.reactions) {
        try {
            val json = JSONObject(message.reactions)
            val keys = json.keys()
            val list = mutableListOf<String>()
            while (keys.hasNext()) {
                list.add(json.getString(keys.next()))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) VibrantCyan.copy(alpha = 0.25f) else Color.Transparent)
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragStart = { replyTriggered = false },
                    onDragEnd = { dragOffsetX = 0f },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Strictly restrict drag to positive values (swiping to the right only)
                        val newOffset = (dragOffsetX + dragAmount).coerceIn(0f, 90f)
                        dragOffsetX = newOffset
                        if (newOffset > 40f && !replyTriggered) {
                            replyTriggered = true
                            onReply()
                            Toast.makeText(context, "Répondre au message 💬", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
    ) {
        // Swipe-to-reply icon indicator (Appears on the left when swiping right)
        if (animatedDragOffset > 5f) {
            val replyScale by animateFloatAsState(
                targetValue = (animatedDragOffset / 40f).coerceIn(0f, 1.2f),
                label = "reply_icon_scale"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .scale(replyScale)
            ) {
                Surface(
                    shape = CircleShape,
                    color = VibrantCyan,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Reply,
                            contentDescription = "Répondre",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Main message container offset horizontally
        Column(
            modifier = Modifier
                .offset(x = animatedDragOffset.dp)
                .fillMaxWidth(),
            horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
        ) {
            // Quick Emoji Reaction Bar (Revealed on single tap)
            androidx.compose.animation.AnimatedVisibility(
                visible = showQuickReactionPicker,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        // Défilante : 8 émojis + « + » dépassaient la largeur d'un petit écran
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickEmojis = listOf("❤️", "🔥", "🥰", "😂", "😮", "👍", "🙏", "✨")
                        quickEmojis.forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onToggleReaction(emoji)
                                        showQuickReactionPicker = false
                                    }
                                    .padding(4.dp)
                            ) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }

                        // Custom Emoji Picker Button (+)
                        Surface(
                            shape = CircleShape,
                            color = VibrantCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    showQuickReactionPicker = false
                                    showCustomEmojiDialog = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Ajouter émoji",
                                    tint = VibrantCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Clickable Bubble (Single tap toggles selection in multi-select mode or reactions, long press selects/options)
            Column(
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (isMultiSelectActive) {
                            onToggleSelect()
                        } else {
                            showQuickReactionPicker = !showQuickReactionPicker
                        }
                    },
                    onLongClick = onLongPress
                ),
                horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
            ) {
                // Reply quote bubble if present
                if (message.replyToContent != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MatteCardElevated,
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
                            .padding(bottom = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(VibrantCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = message.replyToSender ?: "Citation",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantCyan
                                )
                                Text(
                                    text = message.replyToContent,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

        // Message Content Variants
        when (message.type) {
            "image", "photo" -> {
                ImageMessageItem(
                    message = message,
                    isSender = isSender,
                    timeFormatted = timeFormatted,
                    reactionsMap = reactionsMap,
                    onRetryMedia = {
                        val path = message.storagePath ?: ""
                        if (path.isNotEmpty()) {
                            repository.retryFetchMediaUrl(message.id, path)
                        }
                    },
                    onViewOnceOpened = { onViewOnceClicked() }
                )
            }

            "video" -> {
                VideoMessageItem(
                    message = message,
                    isSender = isSender,
                    timeFormatted = timeFormatted,
                    reactionsMap = reactionsMap,
                    onRetryMedia = {
                        val path = message.storagePath ?: ""
                        if (path.isNotEmpty()) {
                            repository.retryFetchMediaUrl(message.id, path)
                        }
                    }
                )
            }

            "audio", "voice" -> {
                Column(horizontalAlignment = if (isSender) Alignment.End else Alignment.Start) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSender) ChatBubbleSender else ChatBubbleReceiver,
                        border = BorderStroke(1.dp, MatteSquircleBorder),
                        modifier = Modifier.widthIn(min = minOf(220.dp, bubbleMaxWidth), max = bubbleMaxWidth)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            VoicePlayerWaveform(
                                durationSeconds = message.duration,
                                isSender = isSender,
                                audioUrl = message.mediaUrl ?: message.storagePath,
                                onRetryFetch = {
                                    val path = message.storagePath ?: ""
                                    if (path.isNotEmpty()) {
                                        repository.retryFetchMediaUrl(message.id, path)
                                    }
                                }
                            )
                        }
                    }

                    // Persistent Emoji Reactions Row
                    if (reactionsMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardDarkElevated,
                            border = BorderStroke(1.dp, BorderSubtleWhite),
                            shadowElevation = 3.dp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                reactionsMap.forEach { emoji ->
                                    Text(text = emoji, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.isStarred) {
                            Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = AccentGold, modifier = Modifier.size(11.dp))
                        }
                        Text(
                            text = timeFormatted,
                            fontSize = 11.sp,
                            color = TimeStampMuted
                        )
                        if (isSender) {
                            MessageStatusIndicator(status = message.status)
                        }
                    }
                }
            }

            "location" -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.4f)),
                    modifier = Modifier.widthIn(min = minOf(220.dp, bubbleMaxWidth), max = bubbleMaxWidth)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00C853).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "Position partagée 📍", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00C853))
                                Text(text = message.content, fontSize = 13.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            "contact" -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, Color(0xFF00BCD4).copy(alpha = 0.4f)),
                    modifier = Modifier.widthIn(min = minOf(220.dp, bubbleMaxWidth), max = bubbleMaxWidth)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00BCD4).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = Color(0xFF00BCD4), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Fiche Contact 👤", fontSize = 11.sp, color = TextSecondary)
                            Text(text = message.content, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }

            "document" -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.4f)),
                    modifier = Modifier.widthIn(min = minOf(220.dp, bubbleMaxWidth), max = bubbleMaxWidth)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF7E57C2).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = Color(0xFF7E57C2), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Document chiffré 📄", fontSize = 11.sp, color = TextSecondary)
                            Text(text = message.content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }
            }

            "poll" -> {
                // Votes partagés (avant : choix local, perdu et invisible pour le partenaire)
                val pollScope = rememberCoroutineScope()
                val pollContext = LocalContext.current
                val options = com.example.mikayala.util.ChatGames.base(message.mediaUrl).split("|").filter { it.isNotBlank() }
                    .ifEmpty { listOf("Oui", "Non") }
                val pollIsSender = repository.isMessageFromMe(message)
                val (senderVote, partnerVote) = com.example.mikayala.util.ChatGames.dilemmaChoices(message.mediaUrl)
                val myVote = if (pollIsSender) senderVote else partnerVote
                val otherVote = if (pollIsSender) partnerVote else senderVote
                val selectedOptionIndex: Int? = myVote.takeIf { it >= 0 }
                val vote: (Int) -> Unit = { idx ->
                    if (idx != myVote) pollScope.launch {
                        val ok = repository.playGameMove(
                            message.id,
                            com.example.mikayala.util.ChatGames.dilemmaChoose(message.mediaUrl, pollIsSender, idx),
                            "Sondage"
                        )
                        if (!ok) Toast.makeText(pollContext, "Vote non enregistré", Toast.LENGTH_SHORT).show()
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.4f)),
                    modifier = Modifier.widthIn(min = minOf(240.dp, bubbleMaxWidth), max = bubbleMaxWidth)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Poll, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Sondage de Couple", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD600))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = message.content, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(10.dp))

                        options.forEachIndexed { idx, opt ->
                            val isSelected = selectedOptionIndex == idx
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0x33FFD600) else Color(0x22141C2B),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFD600) else BorderSubtleWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { vote(idx) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { vote(idx) },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD600)),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = opt, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                    if (otherVote == idx) {
                                        Text(text = "Partenaire ✓", fontSize = 10.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "event", "calendar" -> {
                CalendarEventMessageItem(
                    message = message,
                    isSender = isSender,
                    timeFormatted = timeFormatted,
                    reactionsMap = reactionsMap
                )
            }

            "wheel", "roue" -> {
                WheelOfFortuneMessageItem(
                    message = message,
                    repository = repository,
                    isSender = isSender,
                    timeFormatted = timeFormatted,
                    reactionsMap = reactionsMap
                )
            }

            "ai_image" -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.widthIn(min = minOf(220.dp, bubbleMaxWidth), max = bubbleMaxWidth)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Création Studio d'IA ✨", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VibrantCyan)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = message.content, fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }

            "scratch_card" -> {
                // Carte partagée : grattée par le destinataire, résultat visible chez les deux
                val gameScope = rememberCoroutineScope()
                val gameContext = LocalContext.current
                fun save(newMedia: String, title: String) {
                    gameScope.launch {
                        if (!repository.playGameMove(message.id, newMedia, title)) {
                            android.widget.Toast.makeText(gameContext, "Non enregistré : vérifiez la connexion", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val isScratched = com.example.mikayala.util.ChatGames.isDone(message.mediaUrl)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(0.5.dp, BorderSubtleWhite),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🎁 Carte mystère", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentGold)
                        Spacer(modifier = Modifier.height(8.dp))
                        when {
                            isScratched || isSender -> {
                                Text(text = message.content, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(
                                    text = if (isScratched) (if (isSender) "Grattée par votre partenaire ✨" else "Grattée ✨") else "Pas encore grattée",
                                    fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            else -> Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentGold.copy(alpha = 0.18f),
                                modifier = Modifier.fillMaxWidth().clickable { save(com.example.mikayala.util.ChatGames.with(message.mediaUrl, "1"), "Carte mystère") }
                            ) {
                                Row(modifier = Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Toucher pour gratter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentGold)
                                }
                            }
                        }
                    }
                }
            }

            "truth_or_dare" -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f)),
                    modifier = Modifier.widthIn(min = minOf(220.dp, bubbleMaxWidth), max = bubbleMaxWidth)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = AccentRose, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Action ou Vérité de Couple 🔥", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = message.content, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            "quiz" -> {
                // Quiz partagé : le partenaire répond, puis la réponse attendue s'affiche chez les deux
                val gameScope = rememberCoroutineScope()
                val gameContext = LocalContext.current
                fun save(newMedia: String, title: String) {
                    gameScope.launch {
                        if (!repository.playGameMove(message.id, newMedia, title)) {
                            android.widget.Toast.makeText(gameContext, "Non enregistré : vérifiez la connexion", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val expected = com.example.mikayala.util.ChatGames.base(message.mediaUrl)
                val reply = com.example.mikayala.util.ChatGames.quizReply(message.mediaUrl)
                var draft by remember(message.id) { mutableStateOf("") }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(0.5.dp, BorderSubtleWhite),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Psychology, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Quiz complicité", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentViolet)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = message.content, fontSize = 14.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        when {
                            reply != null -> {
                                Text("Réponse de ${if (isSender) "votre partenaire" else "vous"} : $reply", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                if (expected.isNotBlank()) Text("Réponse attendue : $expected", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                            }
                            isSender -> Text("En attente de la réponse de votre partenaire…", fontSize = 12.sp, color = TextSecondary)
                            else -> {
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it.take(200).replace("§", "") },
                                    placeholder = { Text("Votre réponse", fontSize = 13.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(enabled = draft.isNotBlank(), onClick = { save(com.example.mikayala.util.ChatGames.with(message.mediaUrl, draft.trim()), "Quiz complicité") }) {
                                    Text("Répondre", color = AccentRose)
                                }
                            }
                        }
                    }
                }
            }

            "tictactoe" -> {
                // Morpion RÉEL à deux : état partagé dans le message (synchronisé), tours vérifiés
                val gameScope = rememberCoroutineScope()
                val gameContext = LocalContext.current
                val state = com.example.mikayala.util.TicTacToe.normalize(message.mediaUrl)
                val mySymbol = if (isSender) 'X' else 'O'
                val result = com.example.mikayala.util.TicTacToe.result(state)
                val myTurn = result == null && com.example.mikayala.util.TicTacToe.nextSymbol(state) == mySymbol
                var sending by remember(message.id) { mutableStateOf(false) }
                val status = when (result) {
                    'D' -> "Match nul"
                    mySymbol -> "Vous avez gagné 🎉"
                    null -> if (myTurn) "À vous de jouer (${if (mySymbol == 'X') "❌" else "⭕"})" else "Au tour de votre partenaire"
                    else -> "Votre partenaire a gagné"
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(0.5.dp, BorderSubtleWhite),
                    modifier = Modifier.width(240.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Grid3x3, contentDescription = null, tint = AccentRose, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Morpion", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Text(text = status, fontSize = 12.sp, color = if (myTurn) AccentRose else TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (row in 0..2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (col in 0..2) {
                                        val idx = row * 3 + col
                                        val cell = state[idx]
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = com.example.mikayala.theme.MikayalaColors.surfaceSunken,
                                            border = BorderStroke(0.5.dp, BorderSubtleWhite),
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clickable(enabled = myTurn && cell == '-' && !sending) {
                                                    val next = com.example.mikayala.util.TicTacToe.play(state, idx, mySymbol) ?: return@clickable
                                                    sending = true
                                                    gameScope.launch {
                                                        val ok = repository.playGameMove(message.id, next, "Morpion")
                                                        sending = false
                                                        if (!ok) android.widget.Toast.makeText(gameContext, "Coup non enregistré : vérifiez la connexion", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = when (cell) { 'X' -> "❌"; 'O' -> "⭕"; else -> "" },
                                                    fontSize = 22.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (result != null) {
                            TextButton(onClick = {
                                repository.sendMessage(content = "Revanche au morpion ! ⭕❌", type = "tictactoe", mediaUrl = com.example.mikayala.util.TicTacToe.EMPTY)
                            }) { Text("Rejouer", color = AccentRose) }
                        }
                    }
                }
            }

            "dilemma" -> {
                // Tu préfères partagé : chacun choisit, les deux choix s'affichent quand vous avez répondu tous les deux
                val gameScope = rememberCoroutineScope()
                val gameContext = LocalContext.current
                fun save(newMedia: String, title: String) {
                    gameScope.launch {
                        if (!repository.playGameMove(message.id, newMedia, title)) {
                            android.widget.Toast.makeText(gameContext, "Non enregistré : vérifiez la connexion", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val parts = com.example.mikayala.util.ChatGames.base(message.mediaUrl).split("|").filter { it.isNotBlank() }.ifEmpty { listOf("Option A", "Option B") }
                val (senderChoice, partnerChoice) = com.example.mikayala.util.ChatGames.dilemmaChoices(message.mediaUrl)
                val mine = if (isSender) senderChoice else partnerChoice
                val theirs = if (isSender) partnerChoice else senderChoice
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(0.5.dp, BorderSubtleWhite),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Balance, contentDescription = null, tint = Color(0xFF7FAF93), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Tu préfères ?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7FAF93))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        parts.forEachIndexed { idx, part ->
                            val isChosen = mine == idx
                            val partnerHere = mine >= 0 && theirs == idx
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChosen) Color(0x337FAF93) else com.example.mikayala.theme.MikayalaColors.surfaceSunken,
                                border = BorderStroke(if (isChosen) 1.dp else 0.5.dp, if (isChosen) Color(0xFF7FAF93) else BorderSubtleWhite),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clickable(enabled = mine < 0) { save(com.example.mikayala.util.ChatGames.dilemmaChoose(message.mediaUrl, isSender, idx), "Tu préfères") }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = part, fontSize = 13.sp, fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal, color = TextPrimary, modifier = Modifier.weight(1f))
                                    if (isChosen) Text("vous", fontSize = 11.sp, color = TextSecondary)
                                    if (partnerHere) Text(" · partenaire", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                        Text(
                            text = when {
                                mine < 0 -> "Choisissez votre réponse"
                                theirs < 0 -> "En attente de votre partenaire…"
                                mine == theirs -> "Mêmes goûts 💞"
                                else -> "Vous n'êtes pas d'accord 😄"
                            },
                            fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            "coupon" -> {
                // Bon d'amour partagé : utilisé par le destinataire, statut visible chez les deux
                val gameScope = rememberCoroutineScope()
                val gameContext = LocalContext.current
                fun save(newMedia: String, title: String) {
                    gameScope.launch {
                        if (!repository.playGameMove(message.id, newMedia, title)) {
                            android.widget.Toast.makeText(gameContext, "Non enregistré : vérifiez la connexion", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val used = com.example.mikayala.util.ChatGames.isDone(message.mediaUrl)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(0.5.dp, BorderSubtleWhite),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🎟️ Bon d'amour", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentRose)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = message.content, fontSize = 13.sp, color = TextPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        when {
                            used -> Text("Bon utilisé ✅", fontSize = 12.sp, color = TextSecondary)
                            isSender -> Text("Pas encore utilisé", fontSize = 12.sp, color = TextSecondary)
                            else -> Button(
                                onClick = { save(com.example.mikayala.util.ChatGames.with(message.mediaUrl, "1"), "Bon d'amour") },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(text = "Utiliser ce bon", fontSize = 13.sp, color = TextPrimary) }
                        }
                    }
                }
            }

            else -> {
                // Text Message Bubble
                Column(horizontalAlignment = if (isSender) Alignment.End else Alignment.Start) {
                    val bubbleColors = when (userSettings.bubbleStyle) {
                        "neon_cyan" -> listOf(Color(0xFF00B4D8), Color(0xFF0077B6))
                        "dark_gold" -> listOf(Color(0xFFFFB703), Color(0xFFFB8500))
                        "emerald_luxury" -> listOf(Color(0xFF2EC4B6), Color(0xFF0E7490))
                        "frosted_glass" -> if (userSettings.isDarkMode) listOf(Color(0x44FFFFFF), Color(0x22FFFFFF)) else listOf(Color(0x99FFFFFF), Color(0x66FFFFFF))
                        else -> listOf(Color(0xFFE91E63), Color(0xFFAD1457)) // default "gradient_pink"
                    }

                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isSender) 18.dp else 4.dp,
                            bottomEnd = if (isSender) 4.dp else 18.dp
                        ),
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (userSettings.bubbleStyle == "frosted_glass") Color.White.copy(alpha = 0.4f) else Color.Transparent
                        ),
                        modifier = Modifier.widthIn(min = minOf(80.dp, bubbleMaxWidth), max = bubbleMaxWidth)
                    ) {
                        Column(
                            modifier = if (isSender) {
                                Modifier.background(Brush.linearGradient(bubbleColors))
                            } else {
                                Modifier.background(if (userSettings.isDarkMode) ChatBubbleReceiver else Color(0xFFE5E7EB))
                            }.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = message.content,
                                fontSize = 14.sp,
                                color = if (isSender) Color.White else (if (userSettings.isDarkMode) TextPrimary else Color(0xFF1F2937)),
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Persistent Emoji Reactions Row (Positioned clearly below the bubble)
                    if (reactionsMap.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardDarkElevated,
                            border = BorderStroke(1.dp, BorderSubtleWhite),
                            shadowElevation = 3.dp,
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                reactionsMap.forEach { emoji ->
                                    Text(text = emoji, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Timestamp and Delivery Checks Status
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.isStarred) {
                            Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = AccentGold, modifier = Modifier.size(11.dp))
                        }
                        if (message.editedAt != null) {
                            Text(text = "modifié", fontSize = 10.sp, color = TimeStampMuted)
                        }
                        Text(text = timeFormatted, fontSize = 11.sp, color = TimeStampMuted)
                        if (isSender) {
                            MessageStatusIndicator(status = message.status)
                        }
                    }
                }
            }
        }
    }

    // Custom Emoji Picker Dialog
    if (showCustomEmojiDialog) {
        Dialog(onDismissRequest = { showCustomEmojiDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = BorderStroke(1.dp, MatteSquircleBorder),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .heightIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Réagir avec un émoji ✨",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    EmojiPicker(
                        onEmojiSelected = { emoji ->
                            onToggleReaction(emoji)
                            showCustomEmojiDialog = false
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
}
}

@Composable
fun ImageMessageItem(
    message: MessageEntity,
    isSender: Boolean,
    timeFormatted: String,
    reactionsMap: List<String> = emptyList(),
    onRetryMedia: () -> Unit,
    onViewOnceOpened: () -> Unit
) {
    var showFullScreenPhoto by remember { mutableStateOf(false) }
    var loadState by remember(message.mediaUrl) { mutableStateOf("INIT") }

    LaunchedEffect(message.id, message.storagePath, message.mediaUrl) {
        Log.d(
            "ChatScreen",
            "[MIKAYALA_MEDIA_UI] messageId=${message.id} type=${message.type} storagePath=${message.storagePath} mediaUrl=${message.mediaUrl} loadState=$loadState"
        )
    }

    Column(
        horizontalAlignment = if (isSender) Alignment.End else Alignment.Start,
        modifier = Modifier.widthIn(max = 260.dp)
    ) {
        if (message.isViewOnce) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (message.isViewed) CardDarkElevated else MatteCardDark,
                border = BorderStroke(1.dp, if (message.isViewed) Color.Gray.copy(alpha = 0.3f) else AccentRose),
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 240.dp)
                    .clickable(enabled = !message.isViewed) {
                        showFullScreenPhoto = true
                        onViewOnceOpened()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (message.isViewed) Icons.Rounded.VisibilityOff else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (message.isViewed) TextSecondary else AccentRose,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (message.isViewed) "Photo vue 👁️" else "Photo éphémère (Appuyer) 🔒",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (message.isViewed) TextSecondary else TextPrimary
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, MatteSquircleBorder),
                modifier = Modifier
                    .width(240.dp)
                    .heightIn(min = 180.dp, max = 260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        if (!message.mediaUrl.isNullOrEmpty()) {
                            showFullScreenPhoto = true
                        } else if (!message.storagePath.isNullOrEmpty()) {
                            onRetryMedia()
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!message.mediaUrl.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = message.mediaUrl,
                            contentDescription = "Photo partagée",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            onLoading = {
                                loadState = "LOADING"
                                Log.d("ChatScreen", "[MIKAYALA_MEDIA_UI] messageId=${message.id} type=image storagePath=${message.storagePath} mediaUrl=${message.mediaUrl} loadState=LOADING")
                            },
                            onSuccess = {
                                loadState = "SUCCESS"
                                Log.d("ChatScreen", "[MIKAYALA_MEDIA_UI] messageId=${message.id} type=image storagePath=${message.storagePath} mediaUrl=${message.mediaUrl} loadState=SUCCESS")
                            },
                            onError = { errorState ->
                                loadState = "ERROR"
                                Log.e("ChatScreen", "[MIKAYALA_MEDIA_UI_ERROR] messageId=${message.id} type=image url=${message.mediaUrl} error=${errorState.result.throwable.message}")
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (loadState == "LOADING") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x55000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = VibrantCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else if (loadState == "ERROR") {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xD91F0C16))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BrokenImage,
                                    contentDescription = "Erreur",
                                    tint = AccentRose,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Erreur de chargement",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = onRetryMedia,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "Réessayer 🔄", fontSize = 11.sp, color = VibrantCyan)
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = VibrantCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Envoi / Chargement photo...",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            if (!message.storagePath.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = onRetryMedia,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "Rafraîchir 🔄", fontSize = 10.sp, color = VibrantCyan)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (message.content.isNotBlank() && message.content != "Photo partagée 🖼️" && message.content != "Photo éphémère 📸" && message.content != "Photo instantanée 📸") {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.content,
                fontSize = 13.sp,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Persistent Emoji Reactions Row
        if (reactionsMap.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardDarkElevated,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                shadowElevation = 3.dp,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reactionsMap.forEach { emoji ->
                        Text(text = emoji, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (message.isStarred) {
                Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = AccentGold, modifier = Modifier.size(11.dp))
            }
            Text(
                text = timeFormatted,
                fontSize = 11.sp,
                color = TimeStampMuted
            )
            if (isSender) {
                MessageStatusIndicator(status = message.status)
            }
        }
    }

    if (showFullScreenPhoto && !message.mediaUrl.isNullOrEmpty()) {
        FullScreenPhotoDialog(
            imageUrl = message.mediaUrl,
            onRetry = onRetryMedia,
            onDismiss = { showFullScreenPhoto = false }
        )
    }
}

@Composable
fun VideoMessageItem(
    message: MessageEntity,
    isSender: Boolean,
    timeFormatted: String,
    reactionsMap: List<String> = emptyList(),
    onRetryMedia: () -> Unit
) {
    var showFullScreenVideo by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = if (isSender) Alignment.End else Alignment.Start,
        modifier = Modifier.widthIn(max = 260.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MatteCardDark,
            border = BorderStroke(1.dp, MatteSquircleBorder),
            modifier = Modifier
                .width(240.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable {
                    if (!message.mediaUrl.isNullOrEmpty()) {
                        showFullScreenVideo = true
                    } else if (!message.storagePath.isNullOrEmpty()) {
                        onRetryMedia()
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (!message.mediaUrl.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VibrantCyan,
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Lire vidéo",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Videocam,
                                    contentDescription = null,
                                    tint = VibrantCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("VIDÉO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = VibrantCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Préparation de la vidéo...", fontSize = 11.sp, color = TextSecondary)
                        if (!message.storagePath.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = onRetryMedia,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Rafraîchir 🔄", fontSize = 10.sp, color = VibrantCyan)
                            }
                        }
                    }
                }
            }
        }

        if (message.content.isNotBlank() && message.content != "Vidéo partagée 🎬") {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.content,
                fontSize = 13.sp,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Persistent Emoji Reactions Row
        if (reactionsMap.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardDarkElevated,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                shadowElevation = 3.dp,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reactionsMap.forEach { emoji ->
                        Text(text = emoji, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (message.isStarred) {
                Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = AccentGold, modifier = Modifier.size(11.dp))
            }
            Text(
                text = timeFormatted,
                fontSize = 11.sp,
                color = TimeStampMuted
            )
            if (isSender) {
                MessageStatusIndicator(status = message.status)
            }
        }
    }

    if (showFullScreenVideo && !message.mediaUrl.isNullOrEmpty()) {
        FullScreenVideoDialog(
            videoUrl = message.mediaUrl,
            onDismiss = { showFullScreenVideo = false }
        )
    }
}

@Composable
fun FullScreenPhotoDialog(
    imageUrl: String,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var loadError by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            coil.compose.AsyncImage(
                model = imageUrl,
                contentDescription = "Photo plein écran",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                onError = {
                    loadError = true
                    Log.e("ChatScreen", "[MIKAYALA_MEDIA_UI_ERROR] FullScreenPhotoDialog error loading $imageUrl: ${it.result.throwable.message}")
                },
                onSuccess = {
                    loadError = false
                },
                modifier = Modifier.fillMaxSize()
            )

            if (loadError) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = AccentRose, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Impossible de charger la photo", color = TextPrimary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan)
                    ) {
                        Text("Recharger l'image 🔄", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Fermer",
                    tint = Color.White
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullScreenVideoDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val exoPlayer = remember(videoUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    hasError = true
                    errorMessage = error.localizedMessage ?: "Erreur de lecture"
                    Log.e("ChatScreen", "[MIKAYALA_MEDIA_UI_ERROR] Video playback error for $videoUrl: ${error.message}", error)
                }
            })
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (ignored: Exception) {}
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (hasError) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.VideocamOff, contentDescription = null, tint = AccentRose, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Impossible de lire cette vidéo", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(errorMessage, color = TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            hasError = false
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan)
                    ) {
                        Text("Réessayer 🔄", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Fermer",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Automatically capitalizes the first letter of a message or sentence.
 */
internal fun autoCapitalizeMessageInput(input: String, previousText: String): String {
    if (input.isEmpty()) return input

    // Ensure the very first non-whitespace character of the message is capitalized
    val firstCharIdx = input.indexOfFirst { !it.isWhitespace() }
    val base = if (firstCharIdx != -1 && input[firstCharIdx].isLowerCase()) {
        val chars = input.toCharArray()
        chars[firstCharIdx] = chars[firstCharIdx].titlecaseChar()
        String(chars)
    } else {
        input
    }

    // If typing a new character right after a sentence boundary (. ! ? or newline) + whitespace, capitalize it
    if (base.length == previousText.length + 1 && base.last().isLowerCase()) {
        val prefix = base.dropLast(1)
        val trimmedPrefix = prefix.trimEnd()
        if (trimmedPrefix.isNotEmpty() && (trimmedPrefix.endsWith('.') || trimmedPrefix.endsWith('!') || trimmedPrefix.endsWith('?') || prefix.endsWith('\n'))) {
            return prefix + base.last().titlecaseChar()
        }
    }

    return base
}

@Composable
fun WheelOfFortuneMessageItem(
    message: MessageEntity,
    repository: MikayalaRepository,
    isSender: Boolean,
    timeFormatted: String,
    reactionsMap: List<String> = emptyList()
) {
    val coroutineScope = rememberCoroutineScope()
    val baseMedia = remember(message.mediaUrl) {
        ChatGames.base(message.mediaUrl)
    }
    val items = remember(baseMedia) {
        val parsed = baseMedia.split("|").map { it.trim() }.filter { it.isNotBlank() }
        if (parsed.isNotEmpty()) parsed else listOf("Massage 💆", "Dîner 🍷", "Film 🎬", "Cuisiner 🍳", "Baiser 💋", "Vérité 🤫")
    }

    val syncedResultIndex = remember(message.mediaUrl) {
        ChatGames.wheelResult(message.mediaUrl)
    }
    val spinCount = remember(message.mediaUrl) { ChatGames.wheelSpinCount(message.mediaUrl) }
    val wheelContext = LocalContext.current

    // Rotation : à l'affichage, la roue est DIRECTEMENT sur le dernier résultat (aucune animation).
    // Avant : elle se « réalignait » en tournant 3,5 s à chaque affichage, et la fin de CETTE animation
    // enregistrait un nouveau coup → notification → la roue tournait chez le partenaire → boucle.
    val rotation = remember {
        androidx.compose.animation.core.Animatable(
            if (syncedResultIndex != null && syncedResultIndex in items.indices)
                ChatGames.wheelAngleFor(syncedResultIndex, items.size) else 0f
        )
    }
    var lastShownSpin by remember { mutableIntStateOf(spinCount) }
    var isSpinning by remember { mutableStateOf(false) }
    var requestPending by remember { mutableStateOf(false) }

    // Un NOUVEAU tour (le mien ou celui du partenaire) : même animation vers le même résultat
    LaunchedEffect(spinCount, syncedResultIndex) {
        val idx = syncedResultIndex
        if (spinCount > lastShownSpin && idx != null && idx in items.indices) {
            lastShownSpin = spinCount
            isSpinning = true
            val current = rotation.value
            val target = ChatGames.wheelAngleFor(idx, items.size)
            val delta = ((target - current % 360f) % 360f + 360f) % 360f
            rotation.animateTo(
                current + 5 * 360f + delta,
                androidx.compose.animation.core.tween(durationMillis = 3200, easing = FastOutSlowInEasing)
            )
            isSpinning = false
        } else if (spinCount < lastShownSpin) {
            lastShownSpin = spinCount // état rétabli (coup refusé)
        }
    }
    val animatedRotation = rotation.value

    val currentResultLabel = remember(syncedResultIndex, items) {
        if (syncedResultIndex != null && syncedResultIndex in items.indices) {
            items[syncedResultIndex]
        } else null
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardDarkElevated,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF7928CA)))),
        modifier = Modifier.widthIn(min = 250.dp, max = 310.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Casino, contentDescription = null, tint = Color(0xFFFF007F), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Roue de la Fortune d'Amour 🎡",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF007F)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(animatedRotation)
                ) {
                    val sweepAngle = 360f / items.size
                    val colors = listOf(
                        Color(0xFFFF2D55), Color(0xFF00B4D8), Color(0xFFFFD600),
                        Color(0xFF9C27B0), Color(0xFF00C853), Color(0xFFFF9100)
                    )

                    items.forEachIndexed { index, _ ->
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = index * sweepAngle - 90f,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = CardDarkElevated,
                    border = BorderStroke(2.dp, Color.White),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "💖", fontSize = 18.sp)
                    }
                }

                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = (-10).dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (currentResultLabel != null && !isSpinning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33FF007F),
                    border = BorderStroke(1.dp, Color(0xFFFF007F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Résultat : $currentResultLabel 🎉",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val hasAlreadySpun = syncedResultIndex != null
            Button(
                onClick = {
                    if (!isSpinning && !requestPending) {
                        // Résultat tiré maintenant, enregistré, puis animé sur les deux téléphones
                        requestPending = true
                        val idx = items.indices.random()
                        coroutineScope.launch {
                            val ok = repository.playGameMove(message.id, ChatGames.wheelSpin(message.mediaUrl, idx), "Roue de la Fortune")
                            requestPending = false
                            if (!ok) Toast.makeText(wheelContext, "Tour non enregistré : vérifiez la connexion", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSpinning && !requestPending,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        isSpinning || requestPending -> "La roue tourne... 🎡"
                        hasAlreadySpun -> "Rejouer la Roue ! 🎡"
                        else -> "Tourner la Roue ! 🎡"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun CalendarEventMessageItem(
    message: MessageEntity,
    isSender: Boolean,
    timeFormatted: String,
    reactionsMap: List<String> = emptyList()
) {
    var isConfirmed by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardDarkElevated,
        border = BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.5f)),
        modifier = Modifier.widthIn(min = 240.dp, max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE91E63).copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Event,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Événement de Couple 📅",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE91E63)
                    )
                    Text(
                        text = message.content,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x22141C2B),
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Prochainement", fontSize = 11.sp, color = TextPrimary)
                    }
                    Text(
                        text = if (isConfirmed) "Confirmé ✅" else "En attente ⏳",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConfirmed) Color(0xFF00C853) else AccentGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { isConfirmed = !isConfirmed },
                colors = ButtonDefaults.buttonColors(containerColor = if (isConfirmed) Color(0xFF00C853) else Color(0xFFE91E63)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isConfirmed) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConfirmed) "Inscrit à l'agenda de couple" else "Ajouter à mon agenda 📅",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }
    }
}


