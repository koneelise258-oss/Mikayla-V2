package com.example.mikayala.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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

    // 📸 REAL PHOTO, VIDEO & CAMERA LAUNCHERS
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.startsWith("video")) {
                repository.sendVideoMedia(uri)
                Toast.makeText(context, "Vidéo en cours d'envoi... 🎬", Toast.LENGTH_SHORT).show()
            } else {
                repository.sendImageMedia(uri)
                Toast.makeText(context, "Photo en cours d'envoi... 🖼️", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            repository.sendVideoMedia(uri)
            Toast.makeText(context, "Vidéo en cours d'envoi... 🎬", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            repository.sendCameraPhoto(bitmap)
            Toast.makeText(context, "Photo caméra en cours d'envoi... 📸", Toast.LENGTH_SHORT).show()
        }
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

    val messages by repository.allMessages.collectAsState()
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
    LaunchedEffect(messages.size, coupleSpace.id) {
        val coupleId = coupleSpace.id
        if (coupleId.isNotEmpty()) {
            repository.markMessagesDelivered(coupleId)
            repository.markMessagesRead(coupleId)
        }
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
                            Text(
                                text = if (isPartnerRecording) "Mikayala enregistre un audio... 🎙️" else "Mikayala est en train d'écrire... ✨",
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
                                    text = if (reply.senderId == "me") "Réponse à vous-même" else "Réponse à Mikayala",
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

            // Quick Emoji Drawer above input
            AnimatedVisibility(visible = showEmojiBar) {
                Surface(
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val quickEmojis = listOf("❤️", "🥰", "😘", "🔥", "💋", "🥺", "😂", "💖", "✨", "💍")
                        quickEmojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        textInput += emoji
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }

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
                                    onValueChange = { textInput = it },
                                    placeholder = { Text("Message", color = TextMuted, fontSize = 15.sp) },
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

                                 // Camera 📷 Button (Quick snap)
                                IconButton(
                                    onClick = {
                                        try {
                                            cameraLauncher.launch(null)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Impossible d'ouvrir la caméra", Toast.LENGTH_SHORT).show()
                                        }
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
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
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
                                if (dragOffsetY < -60f) AccentRose else VibrantCyan
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
                                    tint = if (dragOffsetY < -60f) AccentRose else VibrantCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (dragOffsetY < -60f) "Relâchez pour verrouiller" else "Glissez pour verrouiller",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dragOffsetY < -60f) AccentRose else TextPrimary
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
                                        recordingState = RecordingState.RECORDING
                                        dragOffsetY = 0f
                                        voiceRecorder.startRecording("vocal_${System.currentTimeMillis()}.m4a")
                                    }

                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.pressed) {
                                            val dragY = change.position.y - down.position.y
                                            dragOffsetY = dragY
                                            if (dragY < maxDragUp) maxDragUp = dragY

                                            // Drag up to lock (-60px upward)
                                            if (dragY < -60f && recordingState == RecordingState.RECORDING) {
                                                recordingState = RecordingState.LOCKED
                                                isLockedViaDrag = true
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    val duration = System.currentTimeMillis() - startTime
                                    dragOffsetY = 0f

                                    if (recordingState == RecordingState.LOCKED || isLockedViaDrag) {
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
                                            // Too short, cancel cleanly
                                            voiceRecorder.cancelRecording()
                                            recordingState = RecordingState.IDLE
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
                    try {
                        cameraLauncher.launch(null)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Impossible d'ouvrir la caméra", Toast.LENGTH_SHORT).show()
                    }
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
                }
            )
        }

        // Option Bottom Sheet Modal
        selectedMessageForOptions?.let { message ->
            MessageOptionBottomSheet(
                message = message,
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    currentUserId: String,
    timeFormatted: String,
    userSettings: com.example.mikayala.data.model.UserSettingsEntity,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onViewOnceClicked: () -> Unit
) {
    val context = LocalContext.current
    val isSender = (currentUserId.isNotEmpty() && message.senderId == currentUserId) || message.senderId == "me"

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
                    onViewOnceOpened = { onViewOnceClicked() }
                )
            }

            "video" -> {
                VideoMessageItem(
                    message = message,
                    isSender = isSender,
                    timeFormatted = timeFormatted
                )
            }

            "audio", "voice" -> {
                Column(horizontalAlignment = if (isSender) Alignment.End else Alignment.Start) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ChatBubbleReceiver,
                        border = BorderStroke(1.dp, MatteSquircleBorder),
                        modifier = Modifier.widthIn(min = 220.dp, max = 310.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            VoicePlayerWaveform(
                                durationSeconds = message.duration,
                                isSender = isSender,
                                audioUrl = message.mediaUrl
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = timeFormatted,
                        fontSize = 11.sp,
                        color = TimeStampMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
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

            "event" -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.4f)),
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
                                .background(Color(0xFFE91E63).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Event, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Rendez-vous amoureux 📅", fontSize = 11.sp, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                            Text(text = message.content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }
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
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Réagir avec un émoji ✨",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Grid of popular emojis
                    val popularEmojis = listOf(
                        "😍", "🥳", "🤍", "🔐", "👑", "💌", "💋", "🎉",
                        "💍", "🌹", "🚀", "💫", "🧸", "💎", "🍕", "🥂"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        popularEmojis.take(8).forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onToggleReaction(emoji)
                                        showCustomEmojiDialog = false
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        popularEmojis.drop(8).take(8).forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onToggleReaction(emoji)
                                        showCustomEmojiDialog = false
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Free text field to type or paste any custom emoji
                    OutlinedTextField(
                        value = customEmojiInput,
                        onValueChange = { customEmojiInput = it },
                        placeholder = { Text("Tapez un émoji personnalisé...", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomEmojiDialog = false }) {
                            Text("Annuler", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (customEmojiInput.isNotBlank()) {
                                    onToggleReaction(customEmojiInput.trim())
                                    customEmojiInput = ""
                                    showCustomEmojiDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ajouter", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
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
    onViewOnceOpened: () -> Unit
) {
    var showFullScreenPhoto by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
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
                    .widthIn(min = 180.dp, max = 260.dp)
                    .heightIn(min = 180.dp, max = 320.dp)
                    .clickable {
                        if (!message.mediaUrl.isNullOrEmpty()) {
                            showFullScreenPhoto = true
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
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = VibrantCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = timeFormatted,
            fontSize = 11.sp,
            color = TimeStampMuted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    if (showFullScreenPhoto && !message.mediaUrl.isNullOrEmpty()) {
        FullScreenPhotoDialog(imageUrl = message.mediaUrl, onDismiss = { showFullScreenPhoto = false })
    }
}

@Composable
fun VideoMessageItem(
    message: MessageEntity,
    isSender: Boolean,
    timeFormatted: String
) {
    var showFullScreenVideo by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MatteCardDark,
            border = BorderStroke(1.dp, MatteSquircleBorder),
            modifier = Modifier
                .width(240.dp)
                .height(180.dp)
                .clickable {
                    if (!message.mediaUrl.isNullOrEmpty()) {
                        showFullScreenVideo = true
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
                    CircularProgressIndicator(
                        color = VibrantCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = timeFormatted,
            fontSize = 11.sp,
            color = TimeStampMuted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    if (showFullScreenVideo && !message.mediaUrl.isNullOrEmpty()) {
        FullScreenVideoDialog(videoUrl = message.mediaUrl, onDismiss = { showFullScreenVideo = false })
    }
}

@Composable
fun FullScreenPhotoDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
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
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

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

@Composable
fun FullScreenVideoDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
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
                    android.widget.VideoView(ctx).apply {
                        val mediaController = android.widget.MediaController(ctx)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)
                        setVideoURI(android.net.Uri.parse(videoUrl))
                        setOnPreparedListener { mp ->
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

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

