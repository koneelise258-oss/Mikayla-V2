package com.example.mikayala.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.mikayala.data.model.VaultItemEntity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.icons.AppleSvgIcons
import java.util.*

@Composable
fun VaultScreen(
    repository: MikayalaRepository
) {
    val context = LocalContext.current
    val vaultItems by repository.allVaultItems.collectAsState()
    val vaultScreenScope = rememberCoroutineScope()
    // À l'ouverture : rechargement depuis le serveur (ajouts et suppressions faits sur l'autre téléphone)
    LaunchedEffect(Unit) { repository.refreshVault() }

    var selectedCategory by remember { mutableStateOf("intime") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedItemForPreview by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "intime" to "Intime 🔒",
        "souvenirs" to "Souvenirs 📸",
        "projets" to "Projets ✈️",
        "sauvegardes" to "Sauvegardes 🎙️"
    )

    val filteredItems = remember(vaultItems, selectedCategory, searchQuery) {
        vaultItems.filter { item ->
            item.category == selectedCategory &&
                    (searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true) || item.caption.contains(searchQuery, ignoreCase = true))
        }
    }

    val palette = MikayalaColors

    // Full screen / Dialog Preview
    selectedItemForPreview?.let { item ->
        var previewUrl by remember(item.id, item.mediaUrl) { mutableStateOf<String?>(null) }
        LaunchedEffect(item.id, item.mediaUrl) {
            if (item.type == "photo" && item.mediaUrl.isNotBlank()) {
                previewUrl = repository.resolveVaultMediaUrl(item.mediaUrl)
            }
        }
        Dialog(onDismissRequest = { selectedItemForPreview = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = palette.modal,
                border = BorderStroke(0.5.dp, palette.borderStrong),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        val resolved = previewUrl
                        if (resolved != null) {
                            AsyncImage(
                                model = resolved,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = when (item.type) {
                                    "photo" -> "📸"
                                    "video" -> "🎬"
                                    "audio" -> "🎙️"
                                    "letter" -> "💌"
                                    else -> "📝"
                                },
                                fontSize = 64.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.caption,
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                vaultScreenScope.launch {
                                    val ok = repository.deleteVaultItem(item.id)
                                    if (ok) {
                                        selectedItemForPreview = null
                                        Toast.makeText(context, "Élément supprimé du coffre", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Suppression impossible : vérifiez la connexion", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.danger.copy(alpha = 0.16f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(AppleSvgIcons.Trash, contentDescription = null, tint = palette.danger, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Supprimer", color = palette.danger)
                        }

                        Button(
                            onClick = { selectedItemForPreview = null },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Fermer", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Add Item Dialog
    if (showAddDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newCaption by remember { mutableStateOf("") }
        var selectedTextType by remember { mutableStateOf("note") }
        var pickedPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
        var saving by remember { mutableStateOf(false) }
        val vaultScope = rememberCoroutineScope()
        val photoPicker = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri -> if (uri != null) pickedPhoto = uri }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = palette.modal,
                border = BorderStroke(0.5.dp, palette.borderStrong),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Nouveau souvenir", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Choix de la photo, avec aperçu
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(com.example.mikayala.theme.MikayalaColors.surfaceSunken)
                            .clickable {
                                photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val uri = pickedPhoto
                        if (uri != null) {
                            coil.compose.AsyncImage(
                                model = uri,
                                contentDescription = "Photo choisie",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(AppleSvgIcons.Camera, contentDescription = null, tint = palette.accent, modifier = Modifier.size(30.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Ajouter une photo (facultatif)", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                    }

                    if (pickedPhoto == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("note" to "📝 Note", "letter" to "💌 Lettre d'amour").forEach { (typeKey, label) ->
                                val selected = selectedTextType == typeKey
                                Surface(
                                    onClick = { selectedTextType = typeKey },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) palette.secondarySoft else palette.surfaceSunken,
                                    border = BorderStroke(1.dp, if (selected) palette.secondary else palette.border),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) palette.secondary else TextSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Titre du souvenir") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border,
                            cursorColor = palette.accent
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newCaption,
                        onValueChange = { newCaption = it },
                        label = { Text("Note d'amour / Légende") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border,
                            cursorColor = palette.accent
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        enabled = !saving && (newTitle.isNotBlank() || pickedPhoto != null),
                        onClick = {
                            val photo = pickedPhoto
                            if (photo == null) {
                                saving = true
                                vaultScope.launch {
                                    val ok = repository.addVaultItem(newTitle, selectedTextType, selectedCategory, newCaption)
                                    saving = false
                                    if (ok) {
                                        showAddDialog = false
                                        Toast.makeText(context, "Enregistré dans le coffre", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Non enregistré : vérifiez la connexion", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                saving = true
                                vaultScope.launch {
                                    val ok = repository.addVaultPhoto(photo, newTitle, newCaption, selectedCategory)
                                    saving = false
                                    if (ok) {
                                        showAddDialog = false
                                        Toast.makeText(context, "Photo enregistrée dans le coffre", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Envoi impossible : vérifiez la connexion", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("vault_save_button")
                    ) {
                        Text(if (saving) "Envoi…" else "Enregistrer", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = Spacing.maxContentWidth)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // iOS 18 Pill Search Bar & Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher dans le coffre...", color = palette.textMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(AppleSvgIcons.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border,
                    focusedContainerColor = palette.surfaceSunken,
                    unfocusedContainerColor = palette.surfaceSunken,
                    cursorColor = palette.accent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("vault_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(palette.accent, palette.secondary)))
                    .testTag("vault_add_button")
            ) {
                Icon(AppleSvgIcons.Plus, contentDescription = "Ajouter", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // iOS 18 Segmented Categories Track
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.surfaceSunken,
            border = BorderStroke(0.5.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { (catKey, catLabel) ->
                    val isSelected = selectedCategory == catKey
                    val cleanLabel = catLabel.substringBefore(" ")
                    Surface(
                        onClick = { selectedCategory = catKey },
                        shape = RoundedCornerShape(11.dp),
                        color = if (isSelected) palette.surfaceElevated else Color.Transparent,
                        border = if (isSelected) BorderStroke(0.5.dp, palette.borderStrong) else null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .testTag("vault_cat_$catKey")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cleanLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) palette.textPrimary else palette.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Vault Grid
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                com.example.mikayala.ui.components.SoftEmptyState(
                    icon = AppleSvgIcons.Lock,
                    title = "Rien ici pour l'instant",
                    message = "Les souvenirs que vous ajoutez au coffre dans cette catégorie apparaîtront ici, pour vous deux."
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    VaultGridItem(
                        item = item,
                        onClick = { selectedItemForPreview = item },
                        resolveMedia = { ref -> repository.resolveVaultMediaUrl(ref) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultGridItem(
    item: VaultItemEntity,
    onClick: () -> Unit,
    resolveMedia: suspend (String) -> String? = { null }
) {
    val palette = MikayalaColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                // Vraie photo si l'élément en contient une, sinon pictogramme du type
                var shownUrl by remember(item.mediaUrl) { mutableStateOf<String?>(null) }
                if (item.type == "photo" && item.mediaUrl.isNotBlank()) {
                    LaunchedEffect(item.mediaUrl) { shownUrl = resolveMedia(item.mediaUrl) }
                }
                val url = shownUrl
                if (url != null) {
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = item.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = when (item.type) {
                            "photo" -> "📸"
                            "video" -> "🎬"
                            "audio" -> "🎙️"
                            "letter" -> "💌"
                            else -> "📝"
                        },
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.caption,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 2
            )
        }
    }
}
