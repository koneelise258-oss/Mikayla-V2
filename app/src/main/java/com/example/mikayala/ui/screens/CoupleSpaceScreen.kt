package com.example.mikayala.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.screens.couple.CoupleCalendarView
import com.example.mikayala.ui.screens.couple.CoupleGamesView
import com.example.mikayala.ui.screens.couple.FloCycleTrackerView
import com.example.mikayala.ui.screens.couple.LoveTimeView
import kotlinx.coroutines.delay

@Composable
fun CoupleSpaceScreen(
    repository: MikayalaRepository
) {
    var selectedSubSection by remember { mutableIntStateOf(0) } // 0: Love Time, 1: Jeux & Complices, 2: Calendrier, 3: Cycle & Soins (Flo)

    val coupleSpace by repository.activeCoupleSpace.collectAsState()
    val events by repository.allEvents.collectAsState()
    val quizItems by repository.allQuizItems.collectAsState()
    val milestones by repository.allMilestones.collectAsState()
    val coupons by repository.allCoupons.collectAsState()
    val bucketList by repository.allBucketList.collectAsState()
    val truthOrDareList by repository.allTruthOrDare.collectAsState()
    val dilemmas by repository.allDilemmas.collectAsState()
    val loveCapsules by repository.allLoveCapsules.collectAsState()
    val cycleInfo by repository.cycleInfo.collectAsState()

    // Real-time ticking timer for Love Time live precision
    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentMillis = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MatteSlateBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Modern Capsule Segmented Sub-Navbar for the 4 Couple Hubs
        ScrollableTabRow(
            selectedTabIndex = selectedSubSection,
            containerColor = Color.Transparent,
            contentColor = VibrantCyan,
            edgePadding = 0.dp,
            divider = {},
            indicator = {}
        ) {
            val hubs = listOf(
                Pair("Love Time", Icons.Rounded.Favorite),
                Pair("Jeux & Complices", Icons.Rounded.SportsEsports),
                Pair("Calendrier Annuel", Icons.Rounded.CalendarMonth),
                Pair("Cycle & Soins", Icons.Rounded.Spa)
            )
            hubs.forEachIndexed { index, (title, icon) ->
                val isSelected = selectedSubSection == index
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) CardDarkElevated else Color.Transparent,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f)))) else null,
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Tab(
                        selected = isSelected,
                        onClick = { selectedSubSection = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) AccentRose else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = selectedSubSection,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(220)))
                } else {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { width -> width / 3 } + fadeOut(tween(220)))
                }
            },
            label = "CoupleSpaceAnimatedContent"
        ) { section ->
            when (section) {
                0 -> {
                    // 1. LOVE TIME 100% PERSONNALISABLE
                    LoveTimeView(
                        repository = repository,
                        coupleSpace = coupleSpace,
                        milestones = milestones,
                        currentMillis = currentMillis
                    )
                }
                1 -> {
                    // 2. SUITE DE JEUX DE COUPLE RICHES (8 Jeux)
                    CoupleGamesView(
                        repository = repository,
                        quizItems = quizItems,
                        coupons = coupons,
                        bucketList = bucketList,
                        truthOrDareList = truthOrDareList,
                        dilemmas = dilemmas,
                        loveCapsules = loveCapsules
                    )
                }
                2 -> {
                    // 3. VRAI CALENDRIER ANNUEL & MENSUEL INTERACTIF
                    CoupleCalendarView(
                        repository = repository,
                        events = events
                    )
                }
                3 -> {
                    // 4. CYCLE MENSTRUEL & SOINS STYLE FLO
                    FloCycleTrackerView(
                        repository = repository,
                        cycleInfo = cycleInfo
                    )
                }
            }
        }
    }
}
