package com.example.mikayala.ui.screens.couple

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.data.model.*
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.GlowingGradientButton
import kotlin.random.Random

@Composable
fun CoupleGamesView(
    repository: MikayalaRepository,
    quizItems: List<QuizItemEntity>,
    coupons: List<LoveCouponEntity>,
    bucketList: List<CoupleBucketItemEntity>,
    truthOrDareList: List<TruthOrDareEntity>,
    dilemmas: List<CoupleDilemmaEntity>,
    loveCapsules: List<LoveCapsuleEntity>
) {
    val context = LocalContext.current
    var selectedGameTab by remember { mutableIntStateOf(0) }
    // 0: Cartes à Gratter, 1: Action/Vérité, 2: Roue des Désirs, 3: Quiz Complicité, 4: Capsules Temporelles, 5: Bons d'Amour, 6: Bucket List, 7: Tu Préfères

    val gameTabs = remember {
        listOf(
            Pair("À Gratter", Icons.Rounded.Style),
            Pair("Action / Vérité", Icons.Rounded.LocalFireDepartment),
            Pair("Roue des Désirs", Icons.Rounded.Casino),
            Pair("Quiz", Icons.Rounded.Psychology),
            Pair("Capsules", Icons.Rounded.MarkunreadMailbox),
            Pair("Bons d'Amour", Icons.Rounded.ConfirmationNumber),
            Pair("Bucket List", Icons.Rounded.Checklist),
            Pair("Tu Préfères", Icons.Rounded.CompareArrows)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
    ) {
        // Game Category Selector Pills
        ScrollableTabRow(
            selectedTabIndex = selectedGameTab,
            containerColor = Color.Transparent,
            contentColor = VibrantCyan,
            edgePadding = 0.dp,
            divider = {}
        ) {
            gameTabs.forEachIndexed { index, (title, icon) ->
                val isSelected = selectedGameTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedGameTab = index },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) VibrantCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) VibrantCyan else TextSecondary
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = selectedGameTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally { width -> width / 3 } + fadeOut(tween(200)))
                }
            },
            label = "GamesAnimatedContent"
        ) { tab ->
            when (tab) {
                0 -> ScratchCardGameSection(repository)
                1 -> TruthOrDareGameSection(repository, truthOrDareList)
                2 -> WheelOfDesiresSection(repository)
                3 -> QuizGameSection(repository, quizItems)
                4 -> LoveCapsulesSection(repository, loveCapsules)
                5 -> LoveCouponsGameSection(repository, coupons)
                6 -> BucketListGameSection(repository, bucketList)
                7 -> WouldYouRatherGameSection(repository, dilemmas)
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 1. SCRATCH CARD GAME
// ---------------------------------------------------------------------------------
@Composable
private fun ScratchCardGameSection(repository: MikayalaRepository) {
    val context = LocalContext.current
    val prizes = remember {
        listOf(
            "Massage du dos sensuel aux huiles chaudes (20 min) 💆‍♂️",
            "Dîner aux chandelles cuisiné avec tout mon amour 🕯️🍝",
            "Joker Câlin Infini sans condition 🫂❤️",
            "Petit-déjeuner royal servi au lit demain matin 🥐☕",
            "Soirée cinéma sous le plaid avec tous tes caprices 🎬🍿",
            "Baiser passionné de 60 secondes les yeux fermés 💋"
        )
    }
    var currentPrizeIndex by remember { mutableIntStateOf(0) }
    var scratchedPoints by remember { mutableStateOf(listOf<Offset>()) }
    var isRevealed by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Carte à Gratter Magique ✨",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Frotte avec ton doigt sur la surface pour découvrir ton cadeau romantique !",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Scratch Card Surface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardDarkElevated)
                            .border(2.dp, if (isRevealed) AccentRose else VibrantCyan, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Hidden Prize underneath
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(text = "🎉 SURPRISE GAGNÉE 🎉", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarningGold)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = prizes[currentPrizeIndex],
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Scratch layer on top
                        if (!isRevealed) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, _ ->
                                            change.consume()
                                            scratchedPoints = scratchedPoints + change.position
                                            if (scratchedPoints.size > 80) {
                                                isRevealed = true
                                                Toast.makeText(context, "Surprise révélée ! Félicitations ! ❤️", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                            ) {
                                // Silver glitter background
                                drawRect(
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF616161), Color(0xFF9E9E9E), Color(0xFF424242))
                                    )
                                )

                                // Draw scratched transparent holes
                                scratchedPoints.forEach { pt ->
                                    drawCircle(
                                        color = Color.Transparent,
                                        radius = 35.dp.toPx(),
                                        center = pt,
                                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                                    )
                                }
                            }

                            // Scratch prompt text
                            if (scratchedPoints.isEmpty()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "GRATTE ICI AVEC LE DOIGT",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isRevealed) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AccentRose.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "👑", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Gage prêt à être honoré !", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                                    Text(text = "Partagez la surprise directement dans votre fil de discussion !", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                repository.sendMessage(
                                    content = "🎁 Carte Mystère : ${prizes[currentPrizeIndex]}",
                                    type = "scratch_card"
                                )
                                Toast.makeText(context, "Carte envoyée dans le chat ! 💬❤️", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Envoyer cette carte dans le Chat 💬", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    GlowingGradientButton(
                        text = "Nouvelle Carte à Gratter 🔄",
                        icon = Icons.Rounded.Refresh,
                        onClick = {
                            currentPrizeIndex = (currentPrizeIndex + 1) % prizes.size
                            scratchedPoints = emptyList()
                            isRevealed = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 2. TRUTH OR DARE GAME
// ---------------------------------------------------------------------------------
@Composable
private fun TruthOrDareGameSection(
    repository: MikayalaRepository,
    cards: List<TruthOrDareEntity>
) {
    val context = LocalContext.current
    var selectedIntensity by remember { mutableStateOf("Tous") }
    var currentCard by remember { mutableStateOf<TruthOrDareEntity?>(null) }
    var showAddCardDialog by remember { mutableStateOf(false) }

    val filteredCards = remember(cards, selectedIntensity) {
        if (selectedIntensity == "Tous") cards else cards.filter { it.intensity.contains(selectedIntensity) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Action ou Vérité de Couple 🔥",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Pimentez votre complicité avec des confidences et des gages intimes.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Intensity Filter Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Tous", "Doux", "Romantique", "Piquant", "Intime").forEach { intensity ->
                            val isSelected = selectedIntensity == intensity
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentRose.copy(alpha = 0.25f) else CardDarkElevated,
                                border = BorderStroke(1.dp, if (isSelected) AccentRose else BorderSubtleWhite),
                                modifier = Modifier.clickable { selectedIntensity = intensity }
                            ) {
                                Text(
                                    text = intensity,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentRose else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Active Card Display
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CardDarkElevated,
                        border = BorderStroke(
                            1.5.dp,
                            if (currentCard?.type == "dare") AccentRose else VibrantCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentCard == null) {
                                Text(
                                    text = "Choisis « Vérité » ou « Action » ci-dessous pour tirer une carte !",
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (currentCard?.type == "dare") AccentRose.copy(alpha = 0.2f) else VibrantCyan.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (currentCard?.type == "dare") "🎯 ACTION (${currentCard?.intensity})" else "💬 VÉRITÉ (${currentCard?.intensity})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentCard?.type == "dare") AccentRose else VibrantCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = currentCard?.prompt ?: "",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Draw Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val truths = filteredCards.filter { it.type == "truth" }
                                if (truths.isNotEmpty()) {
                                    currentCard = truths.random()
                                } else {
                                    Toast.makeText(context, "Pas de carte correspondante", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, VibrantCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("💬 Tirer Vérité", color = VibrantCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val dares = filteredCards.filter { it.type == "dare" }
                                if (dares.isNotEmpty()) {
                                    currentCard = dares.random()
                                } else {
                                    Toast.makeText(context, "Pas de carte correspondante", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, AccentRose),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🎯 Tirer Action", color = AccentRose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showAddCardDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ajouter notre propre gage ou question", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Dialog: Add Custom Truth or Dare
    if (showAddCardDialog) {
        var cardType by remember { mutableStateOf("truth") }
        var cardPrompt by remember { mutableStateOf("") }
        var cardIntensity by remember { mutableStateOf("Romantique") }

        Dialog(onDismissRequest = { showAddCardDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Créer une Carte Personnalisée ✨", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = cardType == "truth",
                            onClick = { cardType = "truth" },
                            label = { Text("💬 Vérité") }
                        )
                        FilterChip(
                            selected = cardType == "dare",
                            onClick = { cardType = "dare" },
                            label = { Text("🎯 Action") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = cardPrompt,
                        onValueChange = { cardPrompt = it },
                        label = { Text("Question ou Défi") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    GlowingGradientButton(
                        text = "Ajouter la carte",
                        icon = Icons.Rounded.Check,
                        onClick = {
                            if (cardPrompt.isNotBlank()) {
                                repository.addTruthOrDare(cardType, cardPrompt, cardIntensity)
                                showAddCardDialog = false
                                Toast.makeText(context, "Carte ajoutée avec succès !", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 3. WHEEL OF DESIRES (ROUE DES DÉSIRS)
// ---------------------------------------------------------------------------------
@Composable
private fun WheelOfDesiresSection(repository: MikayalaRepository) {
    val context = LocalContext.current
    val items = remember {
        listOf(
            "Massage aux huiles 💆‍♀️",
            "Dîner romantique 🍷",
            "Bain aux chandelles 🛁",
            "Nuit hôtel surprise 🏨",
            "Câlin 30 min sans fin 🫂",
            "Joker désir au choix 👑",
            "Soirée cinéma plaid 🍿",
            "Petit-déj royal 🥐"
        )
    }

    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var selectedPrize by remember { mutableStateOf<String?>(null) }

    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
        finishedListener = {
            isSpinning = false
            val normalized = (rotationAngle % 360 + 360) % 360
            val sectorDegrees = 360f / items.size
            val winningIndex = (((360 - normalized) % 360) / sectorDegrees).toInt() % items.size
            selectedPrize = items[winningIndex]
        },
        label = "WheelRotation"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Roue des Désirs & Décisions 🎡",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Laissez le destin choisir votre prochaine surprise romantique !",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Wheel Graphic with pointer
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(220.dp)
                    ) {
                        // The rotating Wheel Canvas
                        Canvas(
                            modifier = Modifier
                                .size(210.dp)
                                .rotate(animatedRotation)
                        ) {
                            val colors = listOf(
                                Color(0xFFFF4081),
                                Color(0xFF00E5FF),
                                Color(0xFFFFB300),
                                Color(0xFF7C4DFF),
                                Color(0xFF00B0FF),
                                Color(0xFFE91E63),
                                Color(0xFF26A69A),
                                Color(0xFFAB47BC)
                            )
                            val sweepAngle = 360f / items.size

                            items.forEachIndexed { index, _ ->
                                drawArc(
                                    color = colors[index % colors.size],
                                    startAngle = index * sweepAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true
                                )
                            }

                            // Center Hub
                            drawCircle(color = Color(0xFF1E202A), radius = 26.dp.toPx())
                            drawCircle(color = Color.White, radius = 6.dp.toPx())
                        }

                        // Top Pointer Arrow
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(44.dp)
                                .align(Alignment.TopCenter)
                                .offset(y = (-14).dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Winner Display
                    selectedPrize?.let { prize ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AccentRose.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AccentRose),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "RÉSULTAT DE LA ROUE :", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = prize, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    GlowingGradientButton(
                        text = if (isSpinning) "La roue tourne..." else "Tourner la Roue ! 🎡",
                        icon = Icons.Rounded.Casino,
                        onClick = {
                            if (!isSpinning) {
                                isSpinning = true
                                selectedPrize = null
                                val extraSpins = Random.nextInt(4, 8) * 360
                                val randomAngle = Random.nextFloat() * 360
                                rotationAngle += extraSpins + randomAngle
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 4. QUIZ DE COMPLICITÉ MUTUELLE
// ---------------------------------------------------------------------------------
@Composable
private fun QuizGameSection(
    repository: MikayalaRepository,
    quizItems: List<QuizItemEntity>
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Quiz de Complicité Intime 🧠", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Répondez chacun en secret pour débloquer les réponses !", fontSize = 11.sp, color = TextSecondary)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.clickable { showAddDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Créer", fontSize = 11.sp, color = VibrantCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(quizItems) { quiz ->
            var tempAnswer by remember { mutableStateOf(quiz.answerMe ?: "") }
            var isAnswering by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VibrantCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = quiz.category,
                                fontSize = 11.sp,
                                color = VibrantCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        IconButton(onClick = { repository.deleteQuizItem(quiz.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = quiz.question, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(modifier = Modifier.height(10.dp))

                    // My Answer
                    if (quiz.answerMe != null) {
                        Text(text = "Ma réponse : « ${quiz.answerMe} »", fontSize = 12.sp, color = VibrantCyan)
                    } else {
                        if (isAnswering) {
                            OutlinedTextField(
                                value = tempAnswer,
                                onValueChange = { tempAnswer = it },
                                label = { Text("Écris ta réponse secrète") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    if (tempAnswer.isNotBlank()) {
                                        repository.answerQuiz(quiz.id, tempAnswer)
                                        isAnswering = false
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Valider ma réponse")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { isAnswering = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Répondre à cette question", fontSize = 12.sp)
                            }
                        }
                    }

                    // Partner's Answer
                    Spacer(modifier = Modifier.height(6.dp))
                    if (quiz.answerPartner != null) {
                        Text(text = "Réponse de Mikayala : « ${quiz.answerPartner} »", fontSize = 12.sp, color = AccentRose)
                    } else {
                        Text(text = "Mikayala n'a pas encore répondu 🔒", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var question by remember { mutableStateOf("") }
        var myAnswer by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Nouvelle Question de Quiz ❓", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Question intime ou complice") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = myAnswer,
                        onValueChange = { myAnswer = it },
                        label = { Text("Ma réponse secrète") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GlowingGradientButton(
                        text = "Ajouter la question",
                        icon = Icons.Rounded.Check,
                        onClick = {
                            if (question.isNotBlank() && myAnswer.isNotBlank()) {
                                repository.addQuizItem(question, myAnswer)
                                showAddDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 5. BONS D'AMOUR (LOVE COUPONS)
// ---------------------------------------------------------------------------------
@Composable
private fun LoveCouponsGameSection(
    repository: MikayalaRepository,
    coupons: List<LoveCouponEntity>
) {
    val context = LocalContext.current
    var showAddCouponDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Bons d'Amour Échangeables 🎟️", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Des privilèges romantiques utilisables à tout moment !", fontSize = 11.sp, color = TextSecondary)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.clickable { showAddCouponDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Créer", fontSize = 11.sp, color = VibrantCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(coupons) { coupon ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MatteCardDark,
                border = BorderStroke(
                    1.dp,
                    if (coupon.isRedeemed) BorderSubtleWhite else AccentRose.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (coupon.isRedeemed) CardDarkElevated else AccentRose.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = coupon.icon, fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = coupon.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (coupon.isRedeemed) TextMuted else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = coupon.description,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    if (coupon.isRedeemed) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CardDarkElevated
                        ) {
                            Text(
                                text = "Utilisé ✔️",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                repository.redeemCoupon(coupon.id)
                                Toast.makeText(context, "Bon « ${coupon.title} » utilisé avec succès ! ❤️", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Utiliser", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(onClick = { repository.deleteCoupon(coupon.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    if (showAddCouponDialog) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedIcon by remember { mutableStateOf("🎟️") }

        Dialog(onDismissRequest = { showAddCouponDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Nouveau Bon d'Amour 🎟️", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titre du bon") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description & conditions") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GlowingGradientButton(
                        text = "Créer ce bon",
                        icon = Icons.Rounded.Check,
                        onClick = {
                            if (title.isNotBlank()) {
                                repository.addCoupon(title, description, selectedIcon)
                                showAddCouponDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 6. BUCKET LIST & DÉFIS 30 JOURS
// ---------------------------------------------------------------------------------
@Composable
private fun BucketListGameSection(
    repository: MikayalaRepository,
    bucketList: List<CoupleBucketItemEntity>
) {
    val completedCount = bucketList.count { it.isCompleted }
    val progress = if (bucketList.isNotEmpty()) completedCount.toFloat() / bucketList.size else 0f
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Notre Bucket List de Couple 📜", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "$completedCount / ${bucketList.size} faits", fontSize = 12.sp, color = VibrantCyan, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = VibrantCyan,
                        trackColor = CardDarkElevated
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Défis & Rêves à Réaliser", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.clickable { showAddDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Ajouter", fontSize = 11.sp, color = VibrantCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(bucketList) { item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, if (item.isCompleted) VibrantCyan.copy(alpha = 0.4f) else BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isCompleted,
                        onCheckedChange = { repository.toggleBucketItem(item.id) },
                        colors = CheckboxDefaults.colors(checkedColor = VibrantCyan)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = if (item.isCompleted) FontWeight.Normal else FontWeight.Medium,
                        color = if (item.isCompleted) TextMuted else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { repository.deleteBucketItem(item.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Nouveau Rêve de Couple 🌟", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Ex: Aller voir les aurores boréales") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GlowingGradientButton(
                        text = "Ajouter à la liste",
                        icon = Icons.Rounded.Check,
                        onClick = {
                            if (title.isNotBlank()) {
                                repository.addBucketItem(title)
                                showAddDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 7. TU PRÉFÈRES ? (WOULD YOU RATHER)
// ---------------------------------------------------------------------------------
@Composable
private fun WouldYouRatherGameSection(
    repository: MikayalaRepository,
    dilemmas: List<CoupleDilemmaEntity>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(text = "Tu Préfères ? — Spécial Couple ⚖️", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = "Choisissez chacun votre option préférée et comparez vos réponses !", fontSize = 11.sp, color = TextSecondary)
        }

        items(dilemmas) { dilemma ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "TU PRÉFÈRES :", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WarningGold)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option A
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (dilemma.myChoice == "A") VibrantCyan.copy(alpha = 0.25f) else CardDarkElevated,
                        border = BorderStroke(1.dp, if (dilemma.myChoice == "A") VibrantCyan else BorderSubtleWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repository.voteDilemma(dilemma.id, "A") }
                    ) {
                        Text(
                            text = "A) ${dilemma.optionA}",
                            fontSize = 13.sp,
                            fontWeight = if (dilemma.myChoice == "A") FontWeight.Bold else FontWeight.Normal,
                            color = TextPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option B
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (dilemma.myChoice == "B") AccentRose.copy(alpha = 0.25f) else CardDarkElevated,
                        border = BorderStroke(1.dp, if (dilemma.myChoice == "B") AccentRose else BorderSubtleWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repository.voteDilemma(dilemma.id, "B") }
                    ) {
                        Text(
                            text = "B) ${dilemma.optionB}",
                            fontSize = 13.sp,
                            fontWeight = if (dilemma.myChoice == "B") FontWeight.Bold else FontWeight.Normal,
                            color = TextPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 8. LOVE CAPSULES SECTION (Capsules Temporelles)
// ---------------------------------------------------------------------------------
@Composable
private fun LoveCapsulesSection(
    repository: MikayalaRepository,
    capsules: List<LoveCapsuleEntity>
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedCapsuleForView by remember { mutableStateOf<LoveCapsuleEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Capsules Temporelles d'Amour 💌",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scellez des souvenirs et messages secrets à déverrouiller dans le futur !",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    GlowingGradientButton(
                        text = "Sceller une Lettre pour le Futur 📜✨",
                        icon = Icons.Rounded.Lock,
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (capsules.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MatteCardDark,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📜", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Aucune capsule temporelle",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Créez votre première lettre d'amour scellée pour une occasion future !",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(capsules, key = { it.id }) { capsule ->
                val now = System.currentTimeMillis()
                val isTimeUnlocked = now >= capsule.unlockDate || capsule.isUnlocked
                val daysRemaining = if (!isTimeUnlocked) {
                    ((capsule.unlockDate - now) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
                } else 0

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isTimeUnlocked) CardDarkElevated else MatteCardDark,
                    border = BorderStroke(
                        1.dp,
                        if (isTimeUnlocked) AccentRose.copy(alpha = 0.5f) else BorderSubtleWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isTimeUnlocked) {
                                if (!capsule.isUnlocked) {
                                    repository.unlockLoveCapsule(capsule.id)
                                }
                                selectedCapsuleForView = capsule
                            } else {
                                Toast.makeText(
                                    context,
                                    "🔒 Capsule scellée ! Revenez dans $daysRemaining jour(s).",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isTimeUnlocked) AccentRose.copy(alpha = 0.2f) else DeepSurfaceBlack,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isTimeUnlocked) "🔓" else "🔒",
                                        fontSize = 22.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = capsule.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Sceau : ${capsule.sealTheme} • De ${capsule.senderName}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            IconButton(
                                onClick = { repository.deleteLoveCapsule(capsule.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Supprimer",
                                    tint = TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isTimeUnlocked) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentRose.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("✨", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Capsule Déverrouillée ! Appuyez pour lire la lettre 💌",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentRose
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DeepSurfaceBlack,
                                border = BorderStroke(1.dp, BorderSubtleWhite),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = VibrantCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Déverrouillage dans ~$daysRemaining jour(s)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VibrantCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal view capsule
    selectedCapsuleForView?.let { capsule ->
        Dialog(onDismissRequest = { selectedCapsuleForView = null }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, AccentRose),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("💌", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = capsule.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Écrit par ${capsule.senderName} • ${capsule.sealTheme}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DeepSurfaceBlack,
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "« ${capsule.message} »",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            repository.sendMessage(
                                content = "💌 Capsule Temporelle [${capsule.title}] : « ${capsule.message} »",
                                type = "love_capsule"
                            )
                            Toast.makeText(context, "Lettre partagée dans le Chat ! 💬❤️", Toast.LENGTH_SHORT).show()
                            selectedCapsuleForView = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Partager dans le Chat 💬", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { selectedCapsuleForView = null }) {
                        Text("Fermer", color = TextSecondary)
                    }
                }
            }
        }
    }

    // Modal Create Capsule
    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var daysOption by remember { mutableIntStateOf(30) } // 7, 30, 90, 365
        var sealTheme by remember { mutableStateOf("Cœur d'Or 💛") }

        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, VibrantCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sceller une Capsule 📜",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titre de la capsule", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Votre lettre secrète / promesse...", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite
                        ),
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Déverrouiller dans :",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            7 to "7 jours",
                            30 to "1 mois",
                            90 to "3 mois",
                            365 to "1 an"
                        ).forEach { (days, label) ->
                            FilterChip(
                                selected = daysOption == days,
                                onClick = { daysOption = days },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VibrantCyan,
                                    selectedLabelColor = Color.Black,
                                    containerColor = DeepSurfaceBlack,
                                    labelColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (title.isBlank() || message.isBlank()) {
                                Toast.makeText(context, "Veuillez remplir le titre et le message.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val unlockTime = System.currentTimeMillis() + (daysOption * 24L * 60 * 60 * 1000)
                            repository.addLoveCapsule(
                                title = title.trim(),
                                message = message.trim(),
                                unlockDate = unlockTime,
                                sealTheme = sealTheme
                            )
                            Toast.makeText(context, "Capsule scellée avec succès ! 🔒✨", Toast.LENGTH_SHORT).show()
                            showCreateDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sceller la Capsule 🔒", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Annuler", color = TextSecondary)
                    }
                }
            }
        }
    }
}
