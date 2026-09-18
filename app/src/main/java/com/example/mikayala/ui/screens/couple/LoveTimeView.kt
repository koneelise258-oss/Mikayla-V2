package com.example.mikayala.ui.screens.couple

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.data.model.CoupleSpaceEntity
import com.example.mikayala.data.model.LoveMilestoneEntity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.GlowingGradientButton
import com.example.mikayala.ui.components.NeumorphicSquircleButton
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun LoveTimeView(
    repository: MikayalaRepository,
    coupleSpace: CoupleSpaceEntity,
    milestones: List<LoveMilestoneEntity>,
    currentMillis: Long
) {
    val context = LocalContext.current
    var showEditSettingsDialog by remember { mutableStateOf(false) }
    var showAddMilestoneDialog by remember { mutableStateOf(false) }

    val totalDurationMillis = (currentMillis - coupleSpace.anniversaryDate).coerceAtLeast(0)
    val daysTogether = TimeUnit.MILLISECONDS.toDays(totalDurationMillis)
    val hoursRemainder = TimeUnit.MILLISECONDS.toHours(totalDurationMillis) % 24
    val minutesRemainder = TimeUnit.MILLISECONDS.toMinutes(totalDurationMillis) % 60
    val secondsRemainder = TimeUnit.MILLISECONDS.toSeconds(totalDurationMillis) % 60

    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Love Counter Card
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
                                listOf(
                                    AccentRose.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Top Header with Edit Customization Action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = AccentRose.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = AccentRose,
                                        modifier = Modifier.padding(6.dp).size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "NOTRE HISTOIRE D'AMOUR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = AccentRose
                                )
                            }

                            // 100% Personnalisable Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardDarkElevated,
                                border = BorderStroke(1.dp, BorderSubtleWhite),
                                modifier = Modifier.clickable { showEditSettingsDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Personnaliser",
                                        tint = VibrantCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Personnaliser",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantCyan
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Couple Avatars & Nicknames
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(VibrantCyan.copy(alpha = 0.15f))
                                        .border(2.dp, VibrantCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "👨‍🦱", fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = coupleSpace.partner1Name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 18.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AccentRose.copy(alpha = 0.25f))
                                    .border(1.dp, AccentRose, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "❤️", fontSize = 16.sp)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(AccentRose.copy(alpha = 0.15f))
                                        .border(2.dp, AccentRose, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "👩‍🦰", fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = coupleSpace.partner2Name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Days Counter
                        Text(
                            text = "$daysTogether",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "JOURS D'AMOUR PARTAGÉS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                            color = AccentRose
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Live Time Remainder Units (Hours : Mins : Secs)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TimeUnitBox(label = "Heures", value = String.format("%02d", hoursRemainder), modifier = Modifier.weight(1f))
                            TimeUnitBox(label = "Minutes", value = String.format("%02d", minutesRemainder), modifier = Modifier.weight(1f))
                            TimeUnitBox(label = "Secondes", value = String.format("%02d", secondsRemainder), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Start Date & Quote
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardDarkElevated.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, BorderSubtleWhite),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Depuis le :",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = dateFormat.format(Date(coupleSpace.anniversaryDate)),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "« ${coupleSpace.loveQuote} »",
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Milestones & Steps of Love
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Étapes & Jalons Marquants ✨",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.clickable { showAddMilestoneDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Ajouter",
                            tint = VibrantCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Ajouter", fontSize = 11.sp, color = VibrantCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(milestones) { milestone ->
            val isPassed = daysTogether >= milestone.daysTarget
            val progress = (daysTogether.toFloat() / milestone.daysTarget.toFloat()).coerceIn(0f, 1f)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MatteCardDark,
                border = BorderStroke(
                    1.dp,
                    if (isPassed) AccentRose.copy(alpha = 0.5f) else BorderSubtleWhite
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPassed) AccentRose.copy(alpha = 0.2f) else CardDarkElevated
                            )
                            .border(
                                1.dp,
                                if (isPassed) AccentRose else BorderSubtleWhite,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = milestone.icon, fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = milestone.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (isPassed) {
                                Text(
                                    text = "Accompli ! 🎉",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnlinePresenceGreen
                                )
                            } else {
                                val remaining = milestone.daysTarget - daysTogether
                                Text(
                                    text = "Dans $remaining j",
                                    fontSize = 11.sp,
                                    color = VibrantCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (isPassed) AccentRose else VibrantCyan,
                            trackColor = CardDarkElevated
                        )
                    }

                    IconButton(
                        onClick = { repository.deleteMilestone(milestone.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Supprimer",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialog: Customize Love Time 100%
    if (showEditSettingsDialog) {
        var tempPartner1 by remember { mutableStateOf(coupleSpace.partner1Name) }
        var tempPartner2 by remember { mutableStateOf(coupleSpace.partner2Name) }
        var tempQuote by remember { mutableStateOf(coupleSpace.loveQuote) }
        var tempDateMillis by remember { mutableLongStateOf(coupleSpace.anniversaryDate) }

        Dialog(onDismissRequest = { showEditSettingsDialog = false }) {
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
                        text = "Personnaliser Love Time 💖",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Adaptez les noms, la date exacte de début et votre phrase d'amour.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = tempPartner1,
                        onValueChange = { tempPartner1 = it },
                        label = { Text("Mon Nom / Surnom") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tempPartner2,
                        onValueChange = { tempPartner2 = it },
                        label = { Text("Nom / Surnom de Mikayala") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentRose,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Date Picker Trigger
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardDarkElevated,
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = tempDateMillis }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val newCal = Calendar.getInstance().apply {
                                            set(year, month, dayOfMonth, 0, 0, 0)
                                        }
                                        tempDateMillis = newCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Date de notre rencontre :", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = dateFormat.format(Date(tempDateMillis)),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantCyan
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = VibrantCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tempQuote,
                        onValueChange = { tempQuote = it },
                        label = { Text("Citation ou Message d'amour") },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditSettingsDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Annuler", color = TextSecondary)
                        }

                        GlowingGradientButton(
                            text = "Sauvegarder",
                            icon = Icons.Rounded.Check,
                            onClick = {
                                repository.updateLoveTimeSettings(
                                    anniversaryDate = tempDateMillis,
                                    partner1Name = tempPartner1.ifBlank { "Moi" },
                                    partner2Name = tempPartner2.ifBlank { "Mikayala" },
                                    loveQuote = tempQuote.ifBlank { "Pour toujours et à jamais." }
                                )
                                showEditSettingsDialog = false
                                Toast.makeText(context, "Love Time mis à jour ! ❤️", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    }

    // Dialog: Add Custom Milestone
    if (showAddMilestoneDialog) {
        var milestoneTitle by remember { mutableStateOf("") }
        var targetDaysStr by remember { mutableStateOf("1000") }
        var selectedIcon by remember { mutableStateOf("🏆") }

        Dialog(onDismissRequest = { showAddMilestoneDialog = false }) {
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
                        text = "Nouveau Jalon d'Amour ✨",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = milestoneTitle,
                        onValueChange = { milestoneTitle = it },
                        label = { Text("Titre du jalon (ex: 1500 Jours)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = targetDaysStr,
                        onValueChange = { targetDaysStr = it },
                        label = { Text("Nombre de jours cible") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = BorderSubtleWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Icône :", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        listOf("🏆", "👑", "💍", "🏰", "🌟", "🥂", "💖").forEach { icon ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedIcon == icon) VibrantCyan.copy(alpha = 0.25f) else CardDarkElevated)
                                    .border(1.dp, if (selectedIcon == icon) VibrantCyan else Color.Transparent, CircleShape)
                                    .clickable { selectedIcon = icon },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = icon, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddMilestoneDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Annuler", color = TextSecondary)
                        }

                        GlowingGradientButton(
                            text = "Ajouter",
                            icon = Icons.Rounded.Add,
                            onClick = {
                                val target = targetDaysStr.toLongOrNull() ?: 1000L
                                if (milestoneTitle.isNotBlank()) {
                                    repository.addMilestone(milestoneTitle, target, selectedIcon)
                                    showAddMilestoneDialog = false
                                }
                            },
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeUnitBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardDarkElevated,
        border = BorderStroke(1.dp, BorderSubtleWhite),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}
