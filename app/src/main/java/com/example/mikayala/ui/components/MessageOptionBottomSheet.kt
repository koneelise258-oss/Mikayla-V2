package com.example.mikayala.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.model.MessageEntity
import com.example.mikayala.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageOptionBottomSheet(
    message: MessageEntity,
    isFromMe: Boolean = false,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onToggleStar: () -> Unit,
    onTogglePin: () -> Unit,
    onSaveToVault: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onToggleReaction: (String) -> Unit,
    /** Passe en sélection multiple avec ce message déjà sélectionné. */
    onSelect: () -> Unit = {}
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }

    val c = MikayalaColors
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = c.modal,
            title = {
                Text("Détails du message", color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Expéditeur : ${if (isFromMe) "Vous" else "Votre partenaire"}", color = c.textSecondary, fontSize = 13.sp)
                    Text("Date d'envoi : ${dateFormat.format(Date(message.createdAt))}", color = c.textSecondary, fontSize = 13.sp)
                    Text("Statut : ${message.status.uppercase()}", color = c.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    message.readAt?.let {
                        Text("Lu à : ${dateFormat.format(Date(it))}", color = c.accent, fontSize = 13.sp)
                    }
                    if (message.editedAt != null) {
                        Text("Dernière modification : ${dateFormat.format(Date(message.editedAt))}", color = AccentGold, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fermer", color = Color.White)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.modal,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick Emojis Row
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = c.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("❤️", "🔥", "😘", "🥺", "✨", "😂", "😍", "👍").forEach { emoji ->
                        IconButton(
                            onClick = {
                                onToggleReaction(emoji)
                                onDismiss()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
            }

            // Group 1: Conversation actions
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OptionItem(
                        icon = Icons.Rounded.Reply,
                        title = "Répondre",
                        subtitle = "Citer dans le fil de discussion",
                        iconColor = c.accent,
                        onClick = { onReply(); onDismiss() }
                    )
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                    OptionItem(
                        icon = Icons.Rounded.Checklist,
                        title = "Sélectionner",
                        subtitle = "Choisir plusieurs messages",
                        iconColor = c.accent,
                        onClick = { onSelect(); onDismiss() }
                    )
                    if (message.content.isNotBlank()) {
                        HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                        OptionItem(
                            icon = Icons.Rounded.ContentCopy,
                            title = "Copier le texte",
                            subtitle = "Copie dans le presse-papier",
                            iconColor = c.textPrimary,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Mikayala Message", message.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )
                    }
                    if (isFromMe && message.type == "text") {
                        HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                        OptionItem(
                            icon = Icons.Rounded.Edit,
                            title = "Modifier",
                            subtitle = "Corriger le message",
                            iconColor = c.secondary,
                            onClick = { onEdit(); onDismiss() }
                        )
                    }
                }
            }

            // Group 2: Organize & Share
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OptionItem(
                        icon = if (message.isStarred) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                        title = if (message.isStarred) "Retirer des favoris" else "Ajouter aux favoris",
                        subtitle = "Conserver parmi les messages importants",
                        iconColor = AccentGold,
                        onClick = { onToggleStar(); onDismiss() }
                    )
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                    OptionItem(
                        icon = Icons.Rounded.PushPin,
                        title = if (message.isPinned) "Détacher du haut" else "Épingler en haut",
                        subtitle = "Afficher en bannière dans le chat",
                        iconColor = c.accent,
                        onClick = { onTogglePin(); onDismiss() }
                    )
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                    OptionItem(
                        icon = Icons.Rounded.Lock,
                        title = "Enregistrer dans le Coffre-Fort",
                        subtitle = "Copier dans l'album privé du couple",
                        iconColor = c.secondary,
                        onClick = { onSaveToVault(); onDismiss() }
                    )
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                    OptionItem(
                        icon = Icons.Rounded.Share,
                        title = "Partager",
                        subtitle = "Envoyer via une autre application",
                        iconColor = c.textSecondary,
                        onClick = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Mikayala")
                                    putExtra(Intent.EXTRA_TEXT, message.content)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Partager"))
                            } catch (_: Exception) {
                            }
                            onDismiss()
                        }
                    )
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                    OptionItem(
                        icon = Icons.Rounded.Info,
                        title = "Infos du message",
                        subtitle = "Horodatages d'envoi et de lecture",
                        iconColor = c.textSecondary,
                        onClick = { showInfoDialog = true }
                    )
                }
            }

            // Group 3: Destructive actions
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OptionItem(
                        icon = Icons.Rounded.DeleteOutline,
                        title = "Supprimer pour moi",
                        subtitle = "Masquer sur cet appareil",
                        iconColor = c.textSecondary,
                        onClick = { onDeleteForMe(); onDismiss() }
                    )
                    if (isFromMe) {
                        HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                        OptionItem(
                            icon = Icons.Rounded.DeleteForever,
                            title = "Supprimer pour tout le monde",
                            subtitle = "Effacer chez les deux partenaires",
                            iconColor = c.danger,
                            onClick = { onDeleteForEveryone(); onDismiss() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    val c = MikayalaColors
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (iconColor == c.danger) c.danger else c.textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = c.textSecondary
                )
            }
        }
    }
}
