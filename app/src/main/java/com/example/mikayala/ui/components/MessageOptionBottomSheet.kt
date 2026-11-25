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
                    Text(
                        "Statut : " + when (message.status) {
                            "read" -> "Lu ✓✓"
                            "delivered" -> "Reçu ✓✓"
                            "sent" -> "Envoyé ✓"
                            "pending" -> "En attente 🕓"
                            "failed" -> "Échec d'envoi"
                            else -> message.status
                        },
                        color = c.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    message.deliveredAt?.let {
                        Text("Reçu à : ${dateFormat.format(Date(it))}", color = c.textSecondary, fontSize = 13.sp)
                    }
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

    // Choix de suppression façon WhatsApp : un seul bouton « Supprimer », puis « pour moi / pour tous »
    var showDeleteChoice by remember { mutableStateOf(false) }
    if (showDeleteChoice) {
        AlertDialog(
            onDismissRequest = { showDeleteChoice = false },
            containerColor = c.modal,
            title = { Text("Supprimer le message ?", color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = null,
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (isFromMe) {
                        TextButton(onClick = { showDeleteChoice = false; onDeleteForEveryone(); onDismiss() }) {
                            Text("Supprimer pour tous", color = c.danger)
                        }
                    }
                    TextButton(onClick = { showDeleteChoice = false; onDeleteForMe(); onDismiss() }) {
                        Text("Supprimer pour moi", color = c.danger)
                    }
                    TextButton(onClick = { showDeleteChoice = false }) {
                        Text("Annuler", color = c.textSecondary)
                    }
                }
            }
        )
    }

    // Menu simplifié façon WhatsApp : réactions, 4 actions rapides, puis une courte liste.
    // Avant : 12 lignes avec sous-titres, trop long à parcourir.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.modal,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(36.dp)
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
                .padding(horizontal = 14.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Réactions rapides (un appui = réagir, un 2e appui sur la même = retirer)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = c.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("❤️", "😂", "😮", "😢", "🙏", "👍", "🔥").forEach { emoji ->
                        IconButton(
                            onClick = {
                                onToggleReaction(emoji)
                                onDismiss()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            // 4 actions rapides en icônes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickAction(Icons.Rounded.Reply, "Répondre", Modifier.weight(1f)) { onReply(); onDismiss() }
                if (message.content.isNotBlank()) {
                    QuickAction(Icons.Rounded.ContentCopy, "Copier", Modifier.weight(1f)) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Mikayala Message", message.content))
                        Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
                QuickAction(
                    if (message.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (message.isStarred) "Retirer" else "Favori",
                    Modifier.weight(1f)
                ) { onToggleStar(); onDismiss() }
                QuickAction(Icons.Rounded.DeleteOutline, "Supprimer", Modifier.weight(1f), danger = true) {
                    showDeleteChoice = true
                }
            }

            // Liste courte : une ligne par action, sans sous-titre
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isFromMe && message.type == "text") {
                        OptionItem(Icons.Rounded.Edit, "Modifier", c.textPrimary) { onEdit(); onDismiss() }
                        HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    }
                    OptionItem(Icons.Rounded.PushPin, if (message.isPinned) "Détacher" else "Épingler", c.textPrimary) {
                        onTogglePin(); onDismiss()
                    }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    OptionItem(Icons.Rounded.Lock, "Mettre dans le coffre", c.textPrimary) { onSaveToVault(); onDismiss() }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    OptionItem(Icons.Rounded.Checklist, "Sélectionner", c.textPrimary) { onSelect(); onDismiss() }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    OptionItem(Icons.Rounded.Share, "Partager", c.textPrimary) {
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
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    OptionItem(Icons.Rounded.Info, "Infos", c.textPrimary) { showInfoDialog = true }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val c = MikayalaColors
    val tint = if (danger) c.danger else c.accent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = c.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = if (danger) c.danger else c.textPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    title: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    val c = MikayalaColors
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = title, fontSize = 15.sp, color = iconColor)
        }
    }
}
