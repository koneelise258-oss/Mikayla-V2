package com.example.mikayala.ui.screens

import android.widget.Toast
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
                        color = Color.White
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
                                repository.deleteVaultItem(item.id)
                                selectedItemForPreview = null
                                Toast.makeText(context, "Élément supprimé du coffre", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5252)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Supprimer 🗑️", color = Color(0xFFFF5252))
                        }

                        Button(
                            onClick = { selectedItemForPreview = null },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
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
        var newType by remember { mutableStateOf("photo") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardModalSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Nouveau Souvenir Secret 🔒", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(modifier = Modifier.height(14.dp))

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
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                repository.addVaultItem(newTitle, newType, selectedCategory, newCaption)
                                showAddDialog = false
                                Toast.makeText(context, "Souvenir sauvegardé dans le Coffre ! 🔒", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enregistrer dans le Coffre ✨", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNight)
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
                Icon(Icons.Rounded.Add, contentDescription = "Ajouter", tint = Color.White)
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
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Coffre vide dans cette catégorie", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    VaultGridItem(
                        item = item,
                        onClick = { selectedItemForPreview = item }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultGridItem(
    item: VaultItemEntity,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = CardDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite),
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
