package com.example.mikayala.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.data.model.VaultItemEntity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import java.text.SimpleDateFormat
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

    // Full screen / Dialog Preview
    selectedItemForPreview?.let { item ->
        Dialog(onDismissRequest = { selectedItemForPreview = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight),
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
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardElevated),
                        contentAlignment = Alignment.Center
                    ) {
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.caption,
                        fontSize = 13.sp,
                        color = TextSecondary,
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33C4605C)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Supprimer 🗑️", color = Color(0xFFC4605C))
                        }

                        Button(
                            onClick = { selectedItemForPreview = null },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Fermer", color = TextPrimary)
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
        // Photo facultative : sans photo, l'élément est une note (avant, tout était enregistré comme « photo » sans image)
        var pickedPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
        var saving by remember { mutableStateOf(false) }
        val vaultScope = rememberCoroutineScope()
        val photoPicker = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri -> if (uri != null) pickedPhoto = uri }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Nouveau souvenir", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

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
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = AccentRose, modifier = Modifier.size(32.dp))
                                Text("Ajouter une photo (facultatif)", fontSize = 13.sp, color = TextSecondary)
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
                            focusedBorderColor = AccentRose,
                            unfocusedBorderColor = BorderSubtleWhite,
                            cursorColor = AccentRose
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
                            focusedBorderColor = AccentRose,
                            unfocusedBorderColor = BorderSubtleWhite,
                            cursorColor = AccentRose
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
                                    // Confirmation seulement après l'enregistrement réel sur le serveur
                                    val ok = repository.addVaultItem(newTitle, "note", selectedCategory, newCaption)
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
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (saving) "Envoi…" else "Enregistrer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNight)
            .wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally)
            .widthIn(max = com.example.mikayala.theme.Spacing.maxContentWidth)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar & Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher dans le coffre...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentViolet,
                    unfocusedBorderColor = BorderSubtleWhite,
                    cursorColor = AccentRose
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AccentRose, AccentViolet)))
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Ajouter", tint = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Categories selector tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { (catKey, catLabel) ->
                val isSelected = selectedCategory == catKey
                Surface(
                    onClick = { selectedCategory = catKey },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) AccentRose else CardDark,
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtleWhite),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = catLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vault Grid
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                com.example.mikayala.ui.components.SoftEmptyState(
                    icon = Icons.Rounded.Lock,
                    title = "Rien ici pour l'instant",
                    message = "Les souvenirs que vous ajoutez au coffre dans cette catégorie apparaîtront ici, pour vous deux."
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp), // 2 colonnes sur téléphone, plus sur tablette
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = CardDark,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtleWhite),
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
                    .background(CardElevated),
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
