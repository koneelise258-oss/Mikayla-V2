package com.example.mikayala.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.icons.AppleSvgIcons
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
    val palette = MikayalaColors

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
            .background(palette.background)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = Spacing.maxContentWidth)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Apple iOS 18 Segmented Control for Couple Hubs
        val hubs = listOf(
            "Histoire" to AppleSvgIcons.Heart,
            "Jeux" to AppleSvgIcons.Gamepad,
            "Agenda" to AppleSvgIcons.Calendar,
            "Cycle" to AppleSvgIcons.Sparkles
        )
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
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                hubs.forEachIndexed { index, (title, icon) ->
                    val isSelected = selectedSubSection == index
                    Surface(
                        onClick = { selectedSubSection = index },
                        shape = RoundedCornerShape(11.dp),
                        color = if (isSelected) palette.surfaceElevated else Color.Transparent,
                        border = if (isSelected) BorderStroke(0.5.dp, palette.borderStrong) else null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = if (isSelected) palette.accent else palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) palette.textPrimary else palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = selectedSubSection,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(190, easing = FastOutSlowInEasing)) { w -> if (forward) w / 8 else -w / 8 } +
                    fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { w -> if (forward) -w / 10 else w / 10 } +
                        fadeOut(tween(140)))
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
