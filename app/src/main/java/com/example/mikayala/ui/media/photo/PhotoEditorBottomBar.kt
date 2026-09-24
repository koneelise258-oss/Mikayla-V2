package com.example.mikayala.ui.media.photo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.VibrantCyan

@Composable
fun PhotoEditorBottomBar(
    state: PhotoEditorState,
    viewModel: PhotoEditorViewModel,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Active Sub-tool Panel (if open)
        AnimatedVisibility(visible = state.activeTab != PhotoEditorActiveTab.NONE) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xDD1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                when (state.activeTab) {
                    PhotoEditorActiveTab.CROP -> CropToolPanel(state, viewModel)
                    PhotoEditorActiveTab.ADJUST -> AdjustToolPanel(state, viewModel)
                    PhotoEditorActiveTab.FILTERS -> FiltersToolPanel(state, viewModel)
                    PhotoEditorActiveTab.DRAW -> DrawToolPanel(state, viewModel)
                    PhotoEditorActiveTab.BLUR -> BlurToolPanel(state, viewModel)
                    PhotoEditorActiveTab.TEXT -> TextToolPanel(viewModel)
                    PhotoEditorActiveTab.STICKERS -> StickersToolPanel(viewModel)
                    PhotoEditorActiveTab.NONE -> {}
                }
            }
        }

        // Caption Field + View-Once & Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.caption,
                onValueChange = { viewModel.updateCaption(it) },
                placeholder = { Text("Ajouter une légende...", color = Color.White.copy(alpha = 0.6f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = VibrantCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color(0x66000000),
                    unfocusedContainerColor = Color(0x44000000)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            // View Once toggle
            IconButton(
                onClick = { viewModel.toggleViewOnce() },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (state.isViewOnce) VibrantCyan.copy(alpha = 0.25f) else Color(0x44000000),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (state.isViewOnce) VibrantCyan else Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
            ) {
                Text(
                    text = "1",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (state.isViewOnce) VibrantCyan else Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Send Button
            FilledIconButton(
                onClick = onSend,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = VibrantCyan),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = "Envoyer",
                    tint = Color.Black
                )
            }
        }

        // Main Tools Scrollable Bar
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                ToolTabButton(
                    icon = Icons.Rounded.Crop,
                    label = "Recadrer",
                    isSelected = state.activeTab == PhotoEditorActiveTab.CROP,
                    onClick = { viewModel.setActiveTab(PhotoEditorActiveTab.CROP) }
                )
            }
            item {
                ToolTabButton(
                    icon = Icons.Rounded.Tune,
                    label = "Ajuster",
                    isSelected = state.activeTab == PhotoEditorActiveTab.ADJUST,
                    onClick = { viewModel.setActiveTab(PhotoEditorActiveTab.ADJUST) }
                )
            }
            item {
                ToolTabButton(
                    icon = Icons.Rounded.PhotoFilter,
                    label = "Filtres",
                    isSelected = state.activeTab == PhotoEditorActiveTab.FILTERS,
                    onClick = { viewModel.setActiveTab(PhotoEditorActiveTab.FILTERS) }
                )
            }
            item {
                ToolTabButton(
                    icon = Icons.Rounded.Brush,
                    label = "Dessin",
                    isSelected = state.activeTab == PhotoEditorActiveTab.DRAW,
                    onClick = { viewModel.setActiveTab(PhotoEditorActiveTab.DRAW) }
                )
            }
            item {
                ToolTabButton(
                    icon = Icons.Rounded.BlurOn,
                    label = "Flou",
                    isSelected = state.activeTab == PhotoEditorActiveTab.BLUR,
                    onClick = { viewModel.setActiveTab(PhotoEditorActiveTab.BLUR) }
                )
            }
            item {
                ToolTabButton(
                    icon = Icons.Rounded.TextFields,
                    label = "Texte",
                    isSelected = state.activeTab == PhotoEditorActiveTab.TEXT,
                    onClick = { viewModel.setActiveTab(PhotoEditorActiveTab.TEXT) }
                )
            }
            item {
                ToolTabButton(
                    icon = Icons.Rounded.EmojiEmotions,
                    label = "Stickers",
                    isSelected = state.activeTab == PhotoEditorActiveTab.STICKERS,
                    onClick = { viewModel.setActiveTab(PhotoEditorActiveTab.STICKERS) }
                )
            }
        }
    }
}

@Composable
fun ToolTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) VibrantCyan.copy(alpha = 0.2f) else Color(0x331E293B),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) VibrantCyan else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) VibrantCyan else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) VibrantCyan else Color.White
            )
        }
    }
}

// ---------------- Tool Panels ----------------

@Composable
fun CropToolPanel(state: PhotoEditorState, viewModel: PhotoEditorViewModel) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Rapports de recadrage & Transformations", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhotoCropRatio.values().forEach { ratio ->
                FilterChip(
                    selected = state.cropRatio == ratio,
                    onClick = { viewModel.setCropRatio(ratio) },
                    label = { Text(ratio.label, fontSize = 11.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.rotate90() }) {
                Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pivoter 90°", fontSize = 11.sp)
            }
            OutlinedButton(onClick = { viewModel.flipHorizontal() }) {
                Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Miroir", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun AdjustToolPanel(state: PhotoEditorState, viewModel: PhotoEditorViewModel) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Luminosité (${state.adjustments.brightness.toInt()})", color = Color.White, fontSize = 12.sp)
        Slider(
            value = state.adjustments.brightness,
            onValueChange = { viewModel.updateBrightness(it) },
            valueRange = -100f..100f
        )
        Text("Contraste (${String.format("%.1f", state.adjustments.contrast)})", color = Color.White, fontSize = 12.sp)
        Slider(
            value = state.adjustments.contrast,
            onValueChange = { viewModel.updateContrast(it) },
            valueRange = 0.5f..2.0f
        )
        Text("Saturation (${String.format("%.1f", state.adjustments.saturation)})", color = Color.White, fontSize = 12.sp)
        Slider(
            value = state.adjustments.saturation,
            onValueChange = { viewModel.updateSaturation(it) },
            valueRange = 0.0f..2.0f
        )
    }
}

@Composable
fun FiltersToolPanel(state: PhotoEditorState, viewModel: PhotoEditorViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        PhotoFilterType.values().forEach { filter ->
            FilterChip(
                selected = state.activeFilter == filter,
                onClick = { viewModel.setFilter(filter) },
                label = { Text(filter.label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun DrawToolPanel(state: PhotoEditorState, viewModel: PhotoEditorViewModel) {
    val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.White, Color.Black)
    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(colors) { col ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(
                                2.dp,
                                if (state.currentBrushColor == col && !state.isEraserActive) Color.White else Color.Transparent,
                                CircleShape
                            )
                            .clickable { viewModel.setBrushColor(col) }
                    )
                }
            }

            IconButton(
                onClick = { viewModel.toggleEraser() },
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (state.isEraserActive) VibrantCyan.copy(alpha = 0.3f) else Color.Transparent,
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Rounded.CleaningServices,
                    contentDescription = "Gomme",
                    tint = if (state.isEraserActive) VibrantCyan else Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Épaisseur du trait (${state.currentBrushWidth.toInt()}px)", color = Color.White, fontSize = 11.sp)
        Slider(
            value = state.currentBrushWidth,
            onValueChange = { viewModel.setBrushWidth(it) },
            valueRange = 4f..40f
        )
    }
}

@Composable
fun BlurToolPanel(state: PhotoEditorState, viewModel: PhotoEditorViewModel) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Touche l'image pour flouter une zone", color = VibrantCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Taille du pinceau flou (${state.currentBlurRadius.toInt()}px)", color = Color.White, fontSize = 11.sp)
        Slider(
            value = state.currentBlurRadius,
            onValueChange = { viewModel.setBlurRadius(it) },
            valueRange = 20f..100f
        )
        Text("Intensité du flou (${state.currentBlurIntensity.toInt()})", color = Color.White, fontSize = 11.sp)
        Slider(
            value = state.currentBlurIntensity,
            onValueChange = { viewModel.setBlurIntensity(it) },
            valueRange = 5f..35f
        )
    }
}

@Composable
fun TextToolPanel(viewModel: PhotoEditorViewModel) {
    var textInput by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color.White) }
    val colors = listOf(Color.White, Color.Yellow, Color.Red, Color.Cyan, Color.Green, Color.Black)

    Column(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Texte à superposer...", color = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.addTextOverlay(textInput, selectedColor)
                        textInput = ""
                    }
                }
            ) {
                Text("Ajouter")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colors) { col ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(col)
                        .border(
                            2.dp,
                            if (selectedColor == col) VibrantCyan else Color.Transparent,
                            CircleShape
                        )
                        .clickable { selectedColor = col }
                )
            }
        }
    }
}

@Composable
fun StickersToolPanel(viewModel: PhotoEditorViewModel) {
    val stickers = listOf(
        "❤️", "💖", "🔥", "✨", "😍", "💋", "👑", "🌹", "🦋", "💍", "🥰", "💌", "🎉", "🌟", "🌙", "🥂"
    )
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Appuie sur un sticker pour l'ajouter", color = Color.White, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(stickers) { emoji ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.clickable { viewModel.addSticker(emoji) }
                ) {
                    Text(
                        text = emoji,
                        fontSize = 28.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
