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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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

    // « Tout » par défaut : avant, un ajout du partenaire dans une autre catégorie semblait avoir disparu
    var selectedCategory by remember { mutableStateOf("tout") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedItemForPreview by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "tout" to "Tout 💜",
        "intime" to "Intime 🔒",
        "souvenirs" to "Souvenirs 📸",
        "projets" to "Projets ✈️",
        "sauvegardes" to "Sauvegardes 🎙️"
    )

    val filteredItems = remember(vaultItems, selectedCategory, searchQuery) {
        vaultItems.filter { item ->
            (selectedCategory == "tout" || item.category == selectedCategory) &&
                    (searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true) || item.caption.contains(searchQuery, ignoreCase = true))
        }
    }

    val palette = MikayalaColors

    // Aperçu plein écran (avant : petite fenêtre recadrée, et « Supprimer » sans confirmation :
    // un appui de trop effaçait la photo pour les deux)
    val settingsForVault by repository.userSettings.collectAsState()
    selectedItemForPreview?.let { item ->
        var previewUrl by remember(item.id, item.mediaUrl) { mutableStateOf<String?>(null) }
        var loadingPreview by remember(item.id) { mutableStateOf(item.type == "photo" && item.mediaUrl.isNotBlank()) }
        var confirmDelete by remember(item.id) { mutableStateOf(false) }
        var scale by remember(item.id) { mutableStateOf(1f) }
        var offset by remember(item.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        LaunchedEffect(item.id, item.mediaUrl) {
            if (item.type == "photo" && item.mediaUrl.isNotBlank()) {
                previewUrl = repository.resolveVaultMediaUrl(item.mediaUrl)
            }
            loadingPreview = false
        }
        val addedByMe = item.addedBy.isNotBlank() && item.addedBy == repository.getCurrentUserId()
        val whoAdded = when {
            addedByMe -> "Ajouté par toi"
            item.addedBy.isNotBlank() -> "Ajouté par ${settingsForVault.partnerNickname.ifBlank { "ton partenaire" }}"
            else -> ""
        }
        val whenAdded = remember(item.dateAdded) {
            if (item.dateAdded > 0) java.text.SimpleDateFormat("d MMM yyyy 'à' HH'h'mm", java.util.Locale.FRANCE).format(java.util.Date(item.dateAdded)) else ""
        }
        Dialog(
            onDismissRequest = { selectedItemForPreview = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val resolved = previewUrl
                if (resolved != null) {
                    AsyncImage(
                        model = resolved,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(item.id) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                                }
                            }
                            .pointerInput(item.id) {
                                detectTapGestures(onDoubleTap = {
                                    scale = if (scale > 1f) 1f else 2.5f
                                    offset = androidx.compose.ui.geometry.Offset.Zero
                                })
                            }
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                    )
                } else if (loadingPreview) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
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
                        if (item.type == "photo") {
                            Text("Photo indisponible (vérifie la connexion)", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }

                // Barre du haut : fermer / supprimer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedItemForPreview = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(AppleSvgIcons.Trash, contentDescription = "Supprimer", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // Légende en bas
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(item.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (item.caption.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(item.caption, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    }
                    val meta = listOf(whoAdded, whenAdded).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(meta, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }

                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text("Supprimer ce souvenir ?") },
                        text = { Text("Il sera supprimé du coffre pour vous deux. Cette action est définitive.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmDelete = false
                                vaultScreenScope.launch {
                                    val ok = repository.deleteVaultItem(item.id)
                                    if (ok) {
                                        selectedItemForPreview = null
                                        Toast.makeText(context, "Élément supprimé du coffre", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Suppression impossible : vérifiez la connexion", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }) { Text("Supprimer", color = palette.danger) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
                        }
                    )
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
                                    val ok = repository.addVaultItem(newTitle, selectedTextType, selectedCategory.takeIf { it != "tout" } ?: "souvenirs", newCaption)
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
                                    val ok = repository.addVaultPhoto(photo, newTitle, newCaption, selectedCategory.takeIf { it != "tout" } ?: "souvenirs")
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
