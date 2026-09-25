package com.example.mikayala.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.mikayala.ui.media.MediaEditorTarget
import com.example.mikayala.ui.components.InAppCameraCaptureDialog
import com.example.mikayala.ui.components.CameraCaptureMode
import com.example.mikayala.ui.media.photo.PhotoEditorScreen
import com.example.mikayala.ui.media.video.VideoEditorScreen
import kotlinx.coroutines.isActive
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.mikayala.utils.VoiceRecorderManager
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.ChatAttachmentAndGamesBottomSheet
import com.example.mikayala.ui.components.MessageOptionBottomSheet
import com.example.mikayala.ui.components.EmojiPicker
import com.example.mikayala.ui.components.MessageStatusIndicator
import com.example.mikayala.ui.components.NeumorphicSquircleButton
import com.example.mikayala.ui.components.VoicePlayerWaveform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class RecordingState { IDLE, RECORDING, LOCKED, PAUSED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: MikayalaRepository,
    onStartCall: (isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val voiceRecorder = remember { VoiceRecorderManager(context) }

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
            val fileName = uri.lastPathSegment ?: "Document.pdf"
            repository.sendMessage(
                content = "Fichier envoyé : $fileName 📄",
                type = "document",
                mediaUrl = uri.toString()
            )
            Toast.makeText(context, "Document partagé ! 📄", Toast.LENGTH_SHORT).show()
        }
    }

    val rawMessages by repository.allMessages.collectAsState()
    val myUserId = remember { repository.getCurrentUserId() }
    val messages = remember(rawMessages, myUserId) {
        rawMessages.filter { !it.deletedFor.contains(myUserId) }
    }
    val isPartnerTyping by repository.isPartnerTyping.collectAsState()
    val isPartnerRecording by repository.isPartnerRecordingAudio.collectAsState()
    val userSettings by repository.userSettings.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<MessageEntity?>(null) }

    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showEmojiBar by remember { mutableStateOf(false) }

    // Recording State
    var recordingState by remember { mutableStateOf(RecordingState.IDLE) }
    var recordingTimerSeconds by remember { mutableIntStateOf(0) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val coupleSpace by repository.activeCoupleSpace.collectAsState()
    DisposableEffect(Unit) {
        com.example.mikayala.util.P2PSocketManager.isChatScreenActive = true
        com.example.mikayala.util.P2PSocketManager.sendReadAckAll()
        onDispose {
            com.example.mikayala.util.P2PSocketManager.isChatScreenActive = false
        }
    }

    LaunchedEffect(messages.size, coupleSpace.id) {
        val coupleId = coupleSpace.id
        if (coupleId.isNotEmpty()) {
            repository.markMessagesDelivered(coupleId)
            repository.markMessagesRead(coupleId)
            com.example.mikayala.util.P2PSocketManager.sendReadAckAll()
        }
    }

    // Fast periodic sync loop while viewing chat screen so message checkmark statuses update live in real-time
    LaunchedEffect(Unit) {
        while (true) {
            try {
                repository.syncWithSupabase()
                val cId = coupleSpace.id
                if (cId.isNotEmpty()) {
                    repository.markMessagesDelivered(cId)
                    repository.markMessagesRead(cId)
                    com.example.mikayala.util.P2PSocketManager.sendReadAckAll()
                }
            } catch (e: Exception) {
                // Ignore transient network errors
            }
            delay(2500)
        }
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

    // Broadcast recording status
    LaunchedEffect(recordingState) {
        val isRecording = (recordingState == RecordingState.RECORDING || recordingState == RecordingState.LOCKED)
        repository.sendRecordingBroadcast(isRecording)
    }

    // Audio recording timer loop
    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.LOCKED) {
            recordingTimerSeconds = 0
            while (recordingState == RecordingState.RECORDING || recordingState == RecordingState.LOCKED) {
                delay(1000)
                recordingTimerSeconds++
            }
        }
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
        // Draw the background with blur and opacity customizations
        if (userSettings.chatTheme == "custom_image" && userSettings.customWallpaperUri.isNotEmpty()) {
            val blurModifier = if (userSettings.wallpaperBlur > 0f) {
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
            val blurModifier = if (userSettings.wallpaperBlur > 0f) {
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
            // Pinned Banner
            AnimatedVisibility(visible = pinnedMessage != null) {
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
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        repository = repository,
                        currentUserId = repository.getCurrentUserId(),
                        timeFormatted = timeFormat.format(Date(message.createdAt)),
                        userSettings = userSettings,
                        onLongPress = { selectedMessageForOptions = message },
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

            // Input Bar (Bottom) - Matching WhatsApp layout with Dark Luxury styling
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main capsule input container
                Surface(
                    color = Color(0x381E2A3A),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Smiley / Emoji Button
                        IconButton(
                            onClick = { showEmojiBar = !showEmojiBar },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("chat_emoji_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                                contentDescription = "Emojis",
                                tint = if (showEmojiBar) VibrantCyan else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        when (recordingState) {
                            RecordingState.IDLE -> {
                                // Text input field
                                TextField(
                                    value = textInput,
                                    onValueChange = { input ->
                                        textInput = autoCapitalizeMessageInput(input, textInput)
                                    },
                                    placeholder = { Text("Message", color = TextMuted, fontSize = 15.sp) },
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = VibrantCyan
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_message_input"),
                                    maxLines = 4
                                )

                                // Trombone 📎 Button (Attachments & Chat Games)
                                IconButton(
                                    onClick = { showAttachmentSheet = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("chat_attachment_trombone_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AttachFile,
                                        contentDescription = "Pièces jointes et jeux",
                                        tint = TextSecondary,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .rotate(-45f)
                                    )
                                }

                                // Camera 📷 Button (In-app camera with photo/video switch & flash)
                                IconButton(
                                    onClick = {
                                        inAppCameraMode = CameraCaptureMode.PHOTO
                                        showInAppCamera = true
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("chat_camera_quick_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoCamera,
                                        contentDescription = "Caméra",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            RecordingState.RECORDING, RecordingState.LOCKED, RecordingState.PAUSED -> {
                                // New recording UI
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Enregistrement",
                                        tint = AccentRose,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer {
                                                if (recordingState != RecordingState.PAUSED) {
                                                    alpha = (0.5f + 0.5f * kotlin.math.sin(System.currentTimeMillis() / 250.0)).toFloat()
                                                }
                                            }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = String.format("%02d:%02d", recordingTimerSeconds / 60, recordingTimerSeconds % 60),
                                        fontSize = 14.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    if (recordingState == RecordingState.LOCKED || recordingState == RecordingState.PAUSED) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        // Pause/Resume Button
                                        IconButton(
                                            onClick = {
                                                if (recordingState == RecordingState.PAUSED) {
                                                    voiceRecorder.resumeRecording()
                                                    recordingState = RecordingState.LOCKED
                                                } else {
                                                    voiceRecorder.pauseRecording()
                                                    recordingState = RecordingState.PAUSED
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (recordingState == RecordingState.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                                contentDescription = if (recordingState == RecordingState.PAUSED) "Reprendre" else "Pause",
                                                tint = VibrantCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        if (recordingState == RecordingState.PAUSED) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(modifier = Modifier.weight(1f)) {
                                                VoicePlayerWaveform(
                                                    durationSeconds = recordingTimerSeconds,
                                                    isSender = true,
                                                    audioUrl = voiceRecorder.getCurrentFilePath()
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Cancel Button
                                    IconButton(
                                        onClick = {
                                            voiceRecorder.cancelRecording()
                                            recordingState = RecordingState.IDLE
                                            Toast.makeText(context, "Enregistrement annulé 🗑️", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Annuler",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Outside Right Button with Floating Lock Indicator & 3 Gesture Modes
                Box(contentAlignment = Alignment.BottomCenter) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val lockThresholdPx = remember(density) { with(density) { 80.dp.toPx() } }
                    val isLockThresholdMet = dragOffsetY < -lockThresholdPx

                    // Floating Lock Prompt Indicator when holding/sliding
                    androidx.compose.animation.AnimatedVisibility(
                        visible = recordingState == RecordingState.RECORDING,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CardDarkElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isLockThresholdMet) AccentRose else VibrantCyan
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .offset(y = (-58).dp)
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = if (isLockThresholdMet) AccentRose else VibrantCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isLockThresholdMet) "Relâchez pour verrouiller" else "Glissez vers le haut pour verrouiller",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLockThresholdMet) AccentRose else TextPrimary
                                )
                            }
                        }
                    }

                    // Main Circular Action Button (Mic or Send)
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(46.dp)
                            .pointerInput(textInput, recordingState) {
                                if (textInput.isNotBlank()) return@pointerInput

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startTime = System.currentTimeMillis()
                                    var isLockedViaDrag = false
                                    var maxDragUp = 0f

                                    if (recordingState == RecordingState.IDLE) {
                                        if (!hasRecordAudioPermission()) {
                                            recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            return@awaitEachGesture
                                        }
                                        val started = voiceRecorder.startRecording("vocal_${System.currentTimeMillis()}.m4a")
                                        if (started) {
                                            recordingState = RecordingState.RECORDING
                                            dragOffsetY = 0f
                                        } else {
                                            Toast.makeText(context, "Impossible d'initialiser l'enregistrement", Toast.LENGTH_SHORT).show()
                                            return@awaitEachGesture
                                        }
                                    }

                                    var isCanceledViaSwipe = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.pressed) {
                                            val dragY = change.position.y - down.position.y
                                            val dragX = change.position.x - down.position.x
                                            dragOffsetY = dragY
                                            if (dragY < maxDragUp) maxDragUp = dragY

                                            if (dragX < -100f && recordingState == RecordingState.RECORDING) {
                                                voiceRecorder.cancelRecording()
                                                recordingState = RecordingState.IDLE
                                                isCanceledViaSwipe = true
                                                Toast.makeText(context, "Enregistrement annulé 🗑️", Toast.LENGTH_SHORT).show()
                                                break
                                            }

                                            // Deliberate swipe up threshold (~80dp)
                                            if (dragY < -lockThresholdPx && recordingState == RecordingState.RECORDING) {
                                                recordingState = RecordingState.LOCKED
                                                isLockedViaDrag = true
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    val duration = System.currentTimeMillis() - startTime
                                    dragOffsetY = 0f

                                    if (isCanceledViaSwipe) {
                                        // Already canceled via swipe left
                                    } else if (recordingState == RecordingState.LOCKED || isLockedViaDrag) {
                                        // Stay in hands-free locked mode
                                    } else {
                                        // Hold -> Releasing finger sends vocal
                                        if (duration >= 500) {
                                            val filePath = voiceRecorder.stopRecording()
                                            recordingState = RecordingState.IDLE
                                            if (filePath != null && File(filePath).length() > 0) {
                                                repository.sendVoiceNote(filePath, recordingTimerSeconds)
                                                Toast.makeText(context, "Vocal en cours d'envoi... 🎙️", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            // Too short / single tap -> Cancel cleanly and instruct user in-app
                                            voiceRecorder.cancelRecording()
                                            recordingState = RecordingState.IDLE
                                            Toast.makeText(context, "Maintenir le bouton pour enregistrer un message vocal 🎙️", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    when {
                                        textInput.isNotBlank() -> Brush.linearGradient(listOf(Color(0xFF00B4D8), Color(0xFF0077B6)))
                                        recordingState == RecordingState.LOCKED || recordingState == RecordingState.PAUSED -> Brush.linearGradient(listOf(AccentRose, Color(0xFFE84393)))
                                        recordingState == RecordingState.RECORDING -> Brush.linearGradient(listOf(VibrantCyan, Color(0xFF00B4D8)))
                                        else -> Brush.linearGradient(listOf(Color(0xFF25D366), Color(0xFF075E54)))
                                    }
                                )
                                .clickable(
                                    enabled = textInput.isNotBlank() || recordingState == RecordingState.LOCKED || recordingState == RecordingState.PAUSED
                                ) {
                                    if (recordingState == RecordingState.LOCKED || recordingState == RecordingState.PAUSED) {
                                        val filePath = voiceRecorder.stopRecording()
                                        recordingState = RecordingState.IDLE
                                        if (filePath != null && File(filePath).length() > 0) {
                                            repository.sendVoiceNote(filePath, recordingTimerSeconds)
                                            Toast.makeText(context, "Vocal en cours d'envoi... 🎙️", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (textInput.isNotBlank()) {
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
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    textInput.isNotBlank() || recordingState == RecordingState.LOCKED || recordingState == RecordingState.PAUSED -> Icons.AutoMirrored.Rounded.Send
                                    else -> Icons.Rounded.Mic
                                },
                                contentDescription = if (textInput.isNotBlank() || recordingState == RecordingState.LOCKED || recordingState == RecordingState.PAUSED) "Envoyer" else "Enregistrer audio",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

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
                    repository.sendMessage(content = "Notre café romantique préféré 📍", type = "location", mediaUrl = "48.8566, 2.3522")
                    Toast.makeText(context, "Position partagée 📍", Toast.LENGTH_SHORT).show()
                },
                onSendContact = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "Mikayala ❤️ (+33 6 12 34 56 78)", type = "contact")
                    Toast.makeText(context, "Fiche contact partagée 👤", Toast.LENGTH_SHORT).show()
                },
                onSendDocument = {
                    showAttachmentSheet = false
                    try {
                        documentLauncher.launch("*/*")
                    } catch (e: Exception) {
                        repository.sendMessage(content = "Nos_Billets_Weekend_Rome.pdf 📄 (2.4 Mo)", type = "document")
                        Toast.makeText(context, "Document chiffré partagé 📄", Toast.LENGTH_SHORT).show()
                    }
                },
                onSendAudio = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "Chanson de notre première danse 🎵", type = "audio", duration = 42)
                    Toast.makeText(context, "Morceau audio partagé 🎧", Toast.LENGTH_SHORT).show()
                },
                onSendPoll = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "Ce soir pour notre date ? 🍽️", type = "poll", mediaUrl = "Italien aux chandelles 🍝|Sushi fait maison 🍣|Soirée Burgers & Plaid 🍔")
                    Toast.makeText(context, "Sondage de couple lancé 📊", Toast.LENGTH_SHORT).show()
                },
                onSendEvent = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "Escapade Weekend Spa & Jacuzzi 🧖‍♀️ (Ce Samedi à 18h)", type = "event")
                    Toast.makeText(context, "Événement de couple planifié 📅", Toast.LENGTH_SHORT).show()
                },
                onSendAiImage = {
                    showAttachmentSheet = false
                    repository.sendMessage(content = "Portrait d'art magique de nous deux sous les étoiles ✨🎨", type = "ai_image")
                    Toast.makeText(context, "Image d'IA générée ✨", Toast.LENGTH_SHORT).show()
                },
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
                onSaveToVault = { repository.saveMessageToVault(message) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    repository: MikayalaRepository,
    currentUserId: String,
    timeFormatted: String,
    userSettings: com.example.mikayala.data.model.UserSettingsEntity,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onViewOnceClicked: () -> Unit
) {
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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

            // Clickable Bubble (Single tap toggles reactions, long press opens options)
            Column(
                modifier = Modifier.combinedClickable(
                    onClick = { showQuickReactionPicker = !showQuickReactionPicker },
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
                            .widthIn(max = 280.dp)
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
                        modifier = Modifier.widthIn(min = 220.dp, max = 310.dp)
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
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
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
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
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
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
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
                val options = message.mediaUrl?.split("|") ?: listOf("Option 1", "Option 2")
                var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.4f)),
                    modifier = Modifier.widthIn(min = 240.dp, max = 300.dp)
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
                                    .clickable { selectedOptionIndex = idx }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedOptionIndex = idx },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD600)),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = opt, fontSize = 12.sp, color = TextPrimary)
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
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
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
                var isScratched by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.6f)),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🎁 Carte Mystère à Gratter", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentGold)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isScratched) {
                            Text(text = message.content, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentGold.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isScratched = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Toucher pour gratter ✨", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGold)
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
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
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
                var showAnswer by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.6f)),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Psychology, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Quiz Complicité 🧠", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentViolet)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = message.content, fontSize = 13.sp, color = TextPrimary)

                        if (message.mediaUrl != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (showAnswer) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentViolet.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Réponse : ${message.mediaUrl}",
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { showAnswer = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = "Révéler la réponse 👁️", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            "tictactoe" -> {
                var board by remember { mutableStateOf(listOf("", "", "", "", "", "", "", "", "")) }
                var currentTurn by remember { mutableStateOf("X") }
                var winner by remember { mutableStateOf<String?>(null) }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.width(240.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Grid3x3, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Morpion de Couple ⭕❌", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VibrantCyan)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // 3x3 Grid
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (row in 0..2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (col in 0..2) {
                                        val idx = row * 3 + col
                                        val cellValue = board[idx]
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0x331E2A3A),
                                            border = BorderStroke(1.dp, BorderSubtleWhite),
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clickable {
                                                    if (cellValue.isEmpty() && winner == null) {
                                                        val newBoard = board.toMutableList()
                                                        newBoard[idx] = currentTurn
                                                        board = newBoard
                                                        currentTurn = if (currentTurn == "X") "O" else "X"
                                                    }
                                                }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = cellValue,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (cellValue == "X") VibrantCyan else AccentRose
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "dilemma" -> {
                val parts = message.mediaUrl?.split("|") ?: listOf("Option A", "Option B")
                var selectedChoice by remember { mutableStateOf<String?>(null) }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Balance, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Tu Préfères ? ⚖️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        parts.forEach { part ->
                            val isChosen = selectedChoice == part
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChosen) Color(0x334CAF50) else Color(0x22141C2B),
                                border = BorderStroke(1.dp, if (isChosen) Color(0xFF4CAF50) else BorderSubtleWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { selectedChoice = part }
                            ) {
                                Text(
                                    text = part,
                                    fontSize = 12.sp,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            "coupon" -> {
                var isRedeemed by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardModalSurface,
                    border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f)),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🎟️ Bon d'Amour Romantique", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = message.content, fontSize = 12.sp, color = TextPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                isRedeemed = true
                                Toast.makeText(context, "Bon d'Amour validé ! ❤️", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !isRedeemed,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRedeemed) Color.Gray else AccentRose),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isRedeemed) "Bon Utilisé ✅" else "Utiliser ce Bon 💖", fontSize = 11.sp, color = Color.White)
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
                        modifier = Modifier.widthIn(min = 80.dp, max = 300.dp)
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
                    .height(420.dp)
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
    isSender: Boolean,
    timeFormatted: String,
    reactionsMap: List<String> = emptyList()
) {
    val items = remember(message.mediaUrl) {
        val parsed = message.mediaUrl?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        if (parsed.isNotEmpty()) parsed else listOf("Massage 💆", "Dîner 🍷", "Film 🎬", "Dîner 🍳", "Baiser 💋", "Vérité 🤫")
    }
    var targetRotation by remember { mutableFloatStateOf(0f) }
    var selectedResult by remember { mutableStateOf<String?>(null) }
    var isSpinning by remember { mutableStateOf(false) }

    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
        label = "wheel_spin",
        finishedListener = {
            isSpinning = false
            val normalizedDegree = (targetRotation % 360 + 360) % 360
            val segmentAngle = 360f / items.size
            val winningIndex = (((360 - normalizedDegree + segmentAngle / 2) % 360) / segmentAngle).toInt() % items.size
            selectedResult = items[winningIndex]
        }
    )

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

            if (selectedResult != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33FF007F),
                    border = BorderStroke(1.dp, Color(0xFFFF007F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Résultat : $selectedResult 🎉",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    if (!isSpinning) {
                        isSpinning = true
                        selectedResult = null
                        val randomExtraSpins = (5..10).random() * 360f
                        val randomDegree = (0..359).random().toFloat()
                        targetRotation += randomExtraSpins + randomDegree
                    }
                },
                enabled = !isSpinning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isSpinning) "La roue tourne... 🎡" else "Tourner la Roue ! 🎡",
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


