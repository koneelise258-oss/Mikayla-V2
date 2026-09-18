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
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onToggleStar: () -> Unit,
    onTogglePin: () -> Unit,
    onSaveToVault: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onToggleReaction: (String) -> Unit
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = CardModalSurface,
            title = {
                Text("Détails du message ℹ️", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Expéditeur : ${if (message.senderId == "me") "Moi (Partenaire 1)" else "Mikayala (Partenaire 2)"}", color = TextSecondary, fontSize = 13.sp)
                    Text("Date d'envoi : ${dateFormat.format(Date(message.createdAt))}", color = TextSecondary, fontSize = 13.sp)
                    Text("Statut : ${message.status.uppercase()}", color = AccentRose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    message.readAt?.let {
                        Text("Lu à : ${dateFormat.format(Date(it))}", color = StatusBlue, fontSize = 13.sp)
                    }
                    if (message.editedAt != null) {
                        Text("Dernière modification : ${dateFormat.format(Date(message.editedAt))}", color = AccentGold, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fermer", color = Color.White)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardModalSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderSubtleWhite)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Quick Emojis Row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite),
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
                            }
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Option: Répondre
            OptionItem(
                icon = Icons.Rounded.Reply,
                title = "Répondre 💬",
                subtitle = "Citer ce message dans le fil de discussion",
                iconColor = AccentViolet,
                onClick = {
                    onReply()
                    onDismiss()
                }
            )

            // Option: Copier
            if (message.content.isNotBlank()) {
                OptionItem(
                    icon = Icons.Rounded.ContentCopy,
                    title = "Copier le texte 📋",
                    subtitle = "Copie dans le presse-papier système",
                    iconColor = TextPrimary,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Mikayala Message", message.content)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Texte copié ! 📋", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }

            // Option: Modifier (si expéditeur)
            if (message.senderId == "me" && message.type == "text") {
                OptionItem(
                    icon = Icons.Rounded.Edit,
                    title = "Modifier le message ✏️",
                    subtitle = "Corriger le texte (mentionné modifié)",
                    iconColor = AccentRose,
                    onClick = {
                        onEdit()
                        onDismiss()
                    }
                )
            }

            // Option: Étoile / Favoris
            OptionItem(
                icon = if (message.isStarred) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                title = if (message.isStarred) "Retirer des favoris ⭐" else "Marquer d'une étoile ⭐",
                subtitle = "Sauvegarder parmi les messages précieux",
                iconColor = AccentGold,
                onClick = {
                    onToggleStar()
                    onDismiss()
                }
            )

            // Option: Épingler
            OptionItem(
                icon = Icons.Rounded.PushPin,
                title = if (message.isPinned) "Détacher du haut 📌" else "Épingler en haut 📌",
                subtitle = "Affichage en bannière permanente du chat",
                iconColor = AccentViolet,
                onClick = {
                    onTogglePin()
                    onDismiss()
                }
            )

            // Option: Sauvegarder dans le Coffre
            OptionItem(
                icon = Icons.Rounded.Lock,
                title = "Sauvegarder dans le Coffre-Fort 🔒",
                subtitle = "Transférer dans l'album secret partagé",
                iconColor = AccentRose,
                onClick = {
                    onSaveToVault()
                    Toast.makeText(context, "Ajouté au Coffre-Fort ! 🔒", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // Option: Partager natif
            OptionItem(
                icon = Icons.Rounded.Share,
                title = "Partager / Transférer 🔗",
                subtitle = "Partager via les applications du téléphone",
                iconColor = TextSecondary,
                onClick = {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Mikayala Souvenir")
                            putExtra(Intent.EXTRA_TEXT, message.content)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Partager ce souvenir"))
                    } catch (e: Exception) {
                        // fallback
                    }
                    onDismiss()
                }
            )

            // Option: Infos du message
            OptionItem(
                icon = Icons.Rounded.Info,
                title = "Infos du message ℹ️",
                subtitle = "Horodatages d'expédition et de lecture",
                iconColor = TextSecondary,
                onClick = {
                    showInfoDialog = true
                }
            )

            // Option: Supprimer pour moi
            OptionItem(
                icon = Icons.Rounded.DeleteOutline,
                title = "Supprimer pour moi 🗑️",
                subtitle = "Masque le message sur cet appareil uniquement",
                iconColor = TextMuted,
                onClick = {
                    onDeleteForMe()
                    onDismiss()
                }
            )

            // Option: Supprimer pour tout le monde (expéditeur)
            if (message.senderId == "me") {
                OptionItem(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Supprimer pour tout le monde 💣",
                    subtitle = "Supprime définitivement chez les deux partenaires",
                    iconColor = Color(0xFFFF5252),
                    onClick = {
                        onDeleteForEveryone()
                        onDismiss()
                    }
                )
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HoverStateWhite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
