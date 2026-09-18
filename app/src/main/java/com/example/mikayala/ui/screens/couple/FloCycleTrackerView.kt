package com.example.mikayala.ui.screens.couple

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.data.model.CycleDailyLogEntity
import com.example.mikayala.data.model.MenstrualCycleInfo
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.GlowingGradientButton
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FloCycleTrackerView(
    repository: MikayalaRepository,
    cycleInfo: MenstrualCycleInfo
) {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLogDayDialog by remember { mutableStateOf(false) }

    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date()) }
    val todayLog = cycleInfo.dailyLogs[todayKey] ?: CycleDailyLogEntity(dateKey = todayKey)

    val dateFormat = remember { SimpleDateFormat("dd MMMM", Locale.FRANCE) }
    val now = System.currentTimeMillis()

    val daysSinceStart = ((now - cycleInfo.lastPeriodStartDate) / 86400000L).toInt().coerceAtLeast(0)
    val cycleDay = (daysSinceStart % cycleInfo.cycleLengthDays) + 1
    val daysUntilNext = (cycleInfo.cycleLengthDays - cycleDay).coerceAtLeast(0)

    val ovulationDay = cycleInfo.cycleLengthDays - 14
    val isFertile = cycleDay in (ovulationDay - 5)..(ovulationDay + 1)
    val isPeriod = cycleDay in 1..cycleInfo.periodDurationDays
    val isLuteal = cycleDay in (ovulationDay + 2)..(cycleInfo.cycleLengthDays - 4)
    val isPMS = cycleDay > (cycleInfo.cycleLengthDays - 4)

    val pregnancyChance = when {
        cycleDay == ovulationDay -> "Chance très élevée 🔥 (Pic d'ovulation)"
        isFertile -> "Chance élevée ✨ (Fenêtre fertile)"
        isPeriod -> "Chance très faible"
        else -> "Chance faible"
    }

    val primaryRingColor = when {
        isPeriod -> AccentRose
        cycleDay == ovulationDay -> VibrantCyan
        isFertile -> Color(0xFF00E5FF)
        isPMS -> WarningGold
        else -> Color(0xFF9C27B0)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // 1. FLO DIAL HERO CARD
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(primaryRingColor.copy(alpha = 0.16f), Color.Transparent)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Top Header Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = primaryRingColor.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, primaryRingColor.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Spa,
                                        contentDescription = null,
                                        tint = primaryRingColor,
                                        modifier = Modifier.padding(6.dp).size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CYCLE & FERTILITÉ (STYLE FLO)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = primaryRingColor
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardDarkElevated,
                                border = BorderStroke(1.dp, BorderSubtleWhite),
                                modifier = Modifier.clickable { showSettingsDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Paramètres du cycle",
                                        tint = VibrantCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Paramètres",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantCyan
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Large Interactive Flo Dial Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(200.dp)
                        ) {
                            Canvas(modifier = Modifier.size(190.dp)) {
                                // Background Track
                                drawArc(
                                    color = Color(0xFF2A2D3A),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // Progress Arc
                                val progressSweep = (cycleDay.toFloat() / cycleInfo.cycleLengthDays.toFloat()) * 360f
                                drawArc(
                                    color = primaryRingColor,
                                    startAngle = -90f,
                                    sweepAngle = progressSweep,
                                    useCenter = false,
                                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Jour $cycleDay",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "sur ${cycleInfo.cycleLengthDays} jours",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = primaryRingColor.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, primaryRingColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = if (isPeriod) "Règles" else if (isFertile) "Ovulation" else "Phase stable",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryRingColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Current Phase & Pregnancy Probability Pills
                        Text(
                            text = cycleInfo.currentPhase,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BabyChangingStation,
                                contentDescription = null,
                                tint = if (isFertile) VibrantCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pregnancyChance,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFertile) VibrantCyan else TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Next Period & Ovulation Forecast Cards
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardDarkElevated,
                                border = BorderStroke(1.dp, BorderSubtleWhite),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "Prochaines Règles", fontSize = 11.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Dans $daysUntilNext jours",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentRose
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardDarkElevated,
                                border = BorderStroke(1.dp, BorderSubtleWhite),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "Pic Ovulation", fontSize = 11.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Jour $ovulationDay du cycle",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantCyan
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Button: Log Symptoms & Mood Today (Flo style)
                        GlowingGradientButton(
                            text = "Enregistrer mes symptômes & humeur ✍️",
                            icon = Icons.Rounded.AddCircleOutline,
                            onClick = { showLogDayDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 2. TODAY'S LOGGED SUMMARY (IF ANY)
        item {
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
                        Text(
                            text = "Journal du Jour (Aujourd'hui)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = todayKey,
                            fontSize = 11.sp,
                            color = VibrantCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (todayLog.moods.isEmpty() && todayLog.symptoms.isEmpty() && todayLog.note.isBlank()) {
                        Text(
                            text = "Aucun symptôme enregistré aujourd'hui. Clique sur le bouton ci-dessus pour renseigner ton humeur, tes ressentis ou ton hydratation !",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    } else {
                        // Moods row
                        if (todayLog.moods.isNotEmpty()) {
                            Text(text = "Humeur :", fontSize = 11.sp, color = TextSecondary)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                todayLog.moods.forEach { mood ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VibrantCyan.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, VibrantCyan.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = mood,
                                            fontSize = 11.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Symptoms row
                        if (todayLog.symptoms.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Symptômes :", fontSize = 11.sp, color = TextSecondary)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                todayLog.symptoms.forEach { symptom ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentRose.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = symptom,
                                            fontSize = 11.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Hydration & intimacy
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(text = "💧 ${todayLog.waterGlasses} verres d'eau", fontSize = 12.sp, color = VibrantCyan)
                            if (todayLog.hadIntimacy) {
                                Text(text = "❤️ Intimité partagée", fontSize = 12.sp, color = AccentRose)
                            }
                        }

                        if (todayLog.note.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Note : « ${todayLog.note} »",
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. PARTNER CARE GUIDE (CONSEILS POUR MIKEY)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(AccentRose.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💡", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Conseils d'Attention pour Mikey",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = cycleInfo.partnerCareAdvice,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick action suggestion chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            val actionSuggestions = if (isPeriod) {
                                listOf("💆 Massage du dos", "🫖 Tisane chaude", "🍫 Chocolat noir", "🛋️ Soirée plaid")
                            } else if (isFertile) {
                                listOf("🍷 Dîner romantique", "💐 Fleurs surprise", "✨ Sortie nocturne", "💋 Mots doux")
                            } else {
                                listOf("🍿 Soirée film", "👩‍🍳 Cuisiner à deux", "🛁 Bain relaxant", "🎧 Écoute attentive")
                            }

                            actionSuggestions.forEach { suggestion ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CardDarkElevated,
                                    border = BorderStroke(1.dp, BorderSubtleWhite),
                                    modifier = Modifier.clickable {
                                        Toast.makeText(context, "Super idée d'attention : $suggestion ! ❤️", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG: FLO CYCLE PARAMETERS
    if (showSettingsDialog) {
        var tempStartDateMillis by remember { mutableLongStateOf(cycleInfo.lastPeriodStartDate) }
        var tempCycleLength by remember { mutableIntStateOf(cycleInfo.cycleLengthDays) }
        var tempPeriodDuration by remember { mutableIntStateOf(cycleInfo.periodDurationDays) }

        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Paramètres du Cycle (Flo) 🌸",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Ajustez la durée moyenne du cycle et des règles pour des prédictions exactes.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Start date picker
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardDarkElevated,
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = tempStartDateMillis }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val newCal = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                                        tempStartDateMillis = newCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Début des dernières règles :", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = dateFormat.format(Date(tempStartDateMillis)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRose
                                )
                            }
                            Icon(imageVector = Icons.Rounded.CalendarMonth, contentDescription = null, tint = AccentRose)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Cycle Length Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Durée du cycle", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "Moyenne : 28 jours", fontSize = 11.sp, color = TextSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (tempCycleLength > 20) tempCycleLength-- }) {
                                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "-", tint = VibrantCyan)
                            }
                            Text(text = "$tempCycleLength j", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            IconButton(onClick = { if (tempCycleLength < 45) tempCycleLength++ }) {
                                Icon(Icons.Rounded.AddCircleOutline, contentDescription = "+", tint = VibrantCyan)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Period Duration Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Durée des règles", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "Moyenne : 5 jours", fontSize = 11.sp, color = TextSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (tempPeriodDuration > 2) tempPeriodDuration-- }) {
                                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "-", tint = AccentRose)
                            }
                            Text(text = "$tempPeriodDuration j", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            IconButton(onClick = { if (tempPeriodDuration < 12) tempPeriodDuration++ }) {
                                Icon(Icons.Rounded.AddCircleOutline, contentDescription = "+", tint = AccentRose)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSettingsDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Annuler", color = TextSecondary)
                        }

                        GlowingGradientButton(
                            text = "Enregistrer",
                            icon = Icons.Rounded.Check,
                            onClick = {
                                repository.updateFloCycleSettings(
                                    startDate = tempStartDateMillis,
                                    cycleLength = tempCycleLength,
                                    periodDuration = tempPeriodDuration
                                )
                                showSettingsDialog = false
                                Toast.makeText(context, "Cycle mis à jour !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    }

    // DIALOG: LOG DAILY SYMPTOMS & MOOD
    if (showLogDayDialog) {
        var selectedMoods by remember { mutableStateOf(todayLog.moods.toSet()) }
        var selectedSymptoms by remember { mutableStateOf(todayLog.symptoms.toSet()) }
        var waterCount by remember { mutableIntStateOf(todayLog.waterGlasses) }
        var hadIntimacy by remember { mutableStateOf(todayLog.hadIntimacy) }
        var tempNote by remember { mutableStateOf(todayLog.note) }

        val moodOptions = listOf("Heureuse 🥰", "Amoureuse ❤️", "Sensible 🥺", "Fatiguée 😴", "Irritable ⚡", "Sereine 🧘‍♀️", "Passionnée 🔥")
        val symptomOptions = listOf("Crampes", "Maux de tête", "Fatigue", "Ballonnements", "Seins sensibles", "Acné", "Mal de dos", "Douceur")

        Dialog(onDismissRequest = { showLogDayDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Journal du Jour 📝",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Suivi quotidien pour Flo et pour votre complicité",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(text = "Humeur du jour :", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    FlowRowLayout(
                        items = moodOptions,
                        selectedItems = selectedMoods,
                        onToggle = { item ->
                            selectedMoods = if (selectedMoods.contains(item)) selectedMoods - item else selectedMoods + item
                        },
                        activeColor = VibrantCyan
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Symptômes physiques :", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    FlowRowLayout(
                        items = symptomOptions,
                        selectedItems = selectedSymptoms,
                        onToggle = { item ->
                            selectedSymptoms = if (selectedSymptoms.contains(item)) selectedSymptoms - item else selectedSymptoms + item
                        },
                        activeColor = AccentRose
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Hydration counter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Hydratation (verres d'eau) 💧", fontSize = 13.sp, color = TextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (waterCount > 0) waterCount-- }) {
                                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null, tint = VibrantCyan)
                            }
                            Text(text = "$waterCount", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = VibrantCyan)
                            IconButton(onClick = { if (waterCount < 15) waterCount++ }) {
                                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = VibrantCyan)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Intimacy toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Intimité / Câlins aujourd'hui ❤️", fontSize = 13.sp, color = TextPrimary)
                        Switch(
                            checked = hadIntimacy,
                            onCheckedChange = { hadIntimacy = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentRose
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tempNote,
                        onValueChange = { tempNote = it },
                        label = { Text("Note personnelle intime") },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showLogDayDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Fermer", color = TextSecondary)
                        }

                        GlowingGradientButton(
                            text = "Sauvegarder",
                            icon = Icons.Rounded.Save,
                            onClick = {
                                val newLog = CycleDailyLogEntity(
                                    dateKey = todayKey,
                                    moods = selectedMoods.toList(),
                                    symptoms = selectedSymptoms.toList(),
                                    waterGlasses = waterCount,
                                    hadIntimacy = hadIntimacy,
                                    note = tempNote
                                )
                                repository.saveCycleDailyLog(newLog)
                                showLogDayDialog = false
                                Toast.makeText(context, "Journal enregistré ! 🌸", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowRowLayout(
    items: List<String>,
    selectedItems: Set<String>,
    onToggle: (String) -> Unit,
    activeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val isSelected = selectedItems.contains(item)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) activeColor.copy(alpha = 0.25f) else CardDarkElevated,
                border = BorderStroke(1.dp, if (isSelected) activeColor else BorderSubtleWhite),
                modifier = Modifier.clickable { onToggle(item) }
            ) {
                Text(
                    text = item,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
