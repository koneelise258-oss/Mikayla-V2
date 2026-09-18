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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mikayala.data.model.CoupleEventEntity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.components.GlowingGradientButton
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun CoupleCalendarView(
    repository: MikayalaRepository,
    events: List<CoupleEventEntity>
) {
    val context = LocalContext.current
    var currentCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CoupleEventEntity?>(null) }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.FRANCE) }
    val dayFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE) }

    // Compute days in month grid
    val displayedCal = remember(currentCalendar.timeInMillis) {
        (currentCalendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val currentMonthDaysCount = displayedCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = (displayedCal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0

    val eventsByDateMap = remember(events) {
        val map = mutableMapOf<String, MutableList<CoupleEventEntity>>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        events.forEach { event ->
            val key = sdf.format(Date(event.date))
            map.getOrPut(key) { mutableListOf() }.add(event)
        }
        map
    }

    val selectedDayKey = remember(selectedDateMillis) {
        SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date(selectedDateMillis))
    }
    val eventsOnSelectedDay = eventsByDateMap[selectedDayKey] ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // 1. CALENDAR CONTROLLER & MONTH GRID
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Month Navigation Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val prev = (currentCalendar.clone() as Calendar).apply {
                                    add(Calendar.MONTH, -1)
                                }
                                currentCalendar = prev
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Mois précédent", tint = TextPrimary)
                        }

                        Text(
                            text = monthYearFormat.format(currentCalendar.time).replaceFirstChar { it.uppercase() },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        IconButton(
                            onClick = {
                                val next = (currentCalendar.clone() as Calendar).apply {
                                    add(Calendar.MONTH, 1)
                                }
                                currentCalendar = next
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Mois suivant", tint = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Days of week header (Lun, Mar, Mer...)
                    val weekDays = listOf("L", "M", "M", "J", "V", "S", "D")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        weekDays.forEach { day ->
                            Text(
                                text = day,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar Grid (6 rows x 7 cols)
                    val totalCells = ((firstDayOfWeek + currentMonthDaysCount + 6) / 7) * 7
                    val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
                    val todayKey = sdfDay.format(Date())

                    for (row in 0 until (totalCells / 7)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (col in 0..6) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - firstDayOfWeek + 1

                                if (dayNum in 1..currentMonthDaysCount) {
                                    val cellCal = (currentCalendar.clone() as Calendar).apply {
                                        set(Calendar.DAY_OF_MONTH, dayNum)
                                    }
                                    val cellDateKey = sdfDay.format(cellCal.time)
                                    val isSelected = cellDateKey == selectedDayKey
                                    val isToday = cellDateKey == todayKey
                                    val hasEvents = eventsByDateMap.containsKey(cellDateKey)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> VibrantCyan.copy(alpha = 0.25f)
                                                    isToday -> AccentRose.copy(alpha = 0.2f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = when {
                                                    isSelected -> VibrantCyan
                                                    isToday -> AccentRose
                                                    else -> Color.Transparent
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                selectedDateMillis = cellCal.timeInMillis
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) VibrantCyan else if (isToday) AccentRose else TextPrimary
                                            )
                                            if (hasEvents) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) VibrantCyan else AccentRose)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Action: Add Event for Selected Day
                    GlowingGradientButton(
                        text = "Ajouter un événement pour le ${dayFormat.format(Date(selectedDateMillis))}",
                        icon = Icons.Rounded.EventAvailable,
                        onClick = { showAddEventDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. EVENTS ON SELECTED DAY
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Événements du ${dayFormat.format(Date(selectedDateMillis))}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "${eventsOnSelectedDay.size} prévu(s)",
                    fontSize = 12.sp,
                    color = VibrantCyan
                )
            }
        }

        if (eventsOnSelectedDay.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MatteCardDark,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Rien de planifié ce jour. Touchez « Ajouter un événement » pour inscrire un souvenir, une surprise ou un resto !",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(eventsOnSelectedDay) { event ->
                EventCardItem(
                    event = event,
                    onEdit = { editingEvent = event },
                    onDelete = { repository.deleteEvent(event.id) }
                )
            }
        }

        // 3. ALL UPCOMING EVENTS TIMELINE
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Tous nos Prochains Rendez-vous & Fêtes ✨",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        val allUpcomingEvents = events.sortedBy { it.date }
        items(allUpcomingEvents) { event ->
            val daysDiff = TimeUnit.MILLISECONDS.toDays(event.date - System.currentTimeMillis())
            val countdownText = when {
                daysDiff == 0L -> "Aujourd'hui ! 🎉"
                daysDiff == 1L -> "Demain !"
                daysDiff > 1L -> "Dans $daysDiff jours"
                else -> "Passé"
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
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
                            .background(getCategoryColor(event.category).copy(alpha = 0.2f))
                            .border(1.dp, getCategoryColor(event.category), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = getCategoryIcon(event.category), fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dayFormat.format(Date(event.date)),
                            fontSize = 12.sp,
                            color = VibrantCyan
                        )
                        if (event.note.isNotBlank()) {
                            Text(
                                text = event.note,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CardDarkElevated,
                        border = BorderStroke(1.dp, BorderSubtleWhite)
                    ) {
                        Text(
                            text = countdownText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (daysDiff <= 3) AccentRose else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = { repository.deleteEvent(event.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // DIALOG: ADD NEW EVENT
    if (showAddEventDialog) {
        EventFormDialog(
            initialDate = selectedDateMillis,
            onDismiss = { showAddEventDialog = false },
            onConfirm = { title, date, category, note ->
                repository.addEvent(title, date, category, note)
                showAddEventDialog = false
                Toast.makeText(context, "Événement ajouté au calendrier ! ❤️", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // DIALOG: EDIT EVENT
    editingEvent?.let { eventToEdit ->
        EventFormDialog(
            initialDate = eventToEdit.date,
            initialTitle = eventToEdit.title,
            initialCategory = eventToEdit.category,
            initialNote = eventToEdit.note,
            isEditing = true,
            onDismiss = { editingEvent = null },
            onConfirm = { title, date, category, note ->
                repository.updateEvent(eventToEdit.id, title, date, category, note)
                editingEvent = null
                Toast.makeText(context, "Événement modifié !", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun EventCardItem(
    event: CoupleEventEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MatteCardDark,
        border = BorderStroke(1.dp, BorderSubtleWhite),
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(getCategoryColor(event.category).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = getCategoryIcon(event.category), fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = event.category, fontSize = 11.sp, color = getCategoryColor(event.category))
                if (event.note.isNotBlank()) {
                    Text(text = "« ${event.note} »", fontSize = 11.sp, color = TextSecondary)
                }
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Modifier", tint = VibrantCyan, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer", tint = AccentRose, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EventFormDialog(
    initialDate: Long,
    initialTitle: String = "",
    initialCategory: String = "Rendez-vous",
    initialNote: String = "",
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var selectedDate by remember { mutableLongStateOf(initialDate) }
    var category by remember { mutableStateOf(initialCategory) }
    var note by remember { mutableStateOf(initialNote) }

    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE) }
    val categories = listOf("Anniversaire", "Rendez-vous", "Voyage", "Surprise", "Date Intime", "Cinéma")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MatteCardDark,
            border = BorderStroke(1.dp, BorderSubtleWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (isEditing) "Modifier l'événement ✏️" else "Nouvel Événement d'Amour 📅",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de l'événement") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VibrantCyan,
                        unfocusedBorderColor = BorderSubtleWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Date Picker
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardDarkElevated,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }
                                    selectedDate = newCal.timeInMillis
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
                            Text(text = "Date :", fontSize = 11.sp, color = TextSecondary)
                            Text(text = dateFormat.format(Date(selectedDate)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VibrantCyan)
                        }
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = VibrantCyan)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Catégorie :", fontSize = 12.sp, color = TextSecondary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = category == cat
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) getCategoryColor(cat).copy(alpha = 0.25f) else CardDarkElevated,
                            border = BorderStroke(1.dp, if (isSelected) getCategoryColor(cat) else BorderSubtleWhite),
                            modifier = Modifier.clickable { category = cat }
                        ) {
                            Text(
                                text = "${getCategoryIcon(cat)} $cat",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notes & détails (optionnel)") },
                    maxLines = 2,
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
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Annuler", color = TextSecondary)
                    }

                    GlowingGradientButton(
                        text = if (isEditing) "Enregistrer" else "Ajouter",
                        icon = Icons.Rounded.Check,
                        onClick = {
                            if (title.isNotBlank()) {
                                onConfirm(title, selectedDate, category, note)
                            }
                        },
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}

private fun getCategoryColor(category: String): Color = when (category) {
    "Anniversaire" -> AccentRose
    "Rendez-vous" -> VibrantCyan
    "Voyage" -> Color(0xFF00E5FF)
    "Surprise" -> WarningGold
    "Date Intime" -> Color(0xFFFF4081)
    "Cinéma" -> Color(0xFFAB47BC)
    else -> VibrantCyan
}

private fun getCategoryIcon(category: String): String = when (category) {
    "Anniversaire" -> "🎂"
    "Rendez-vous" -> "🍷"
    "Voyage" -> "✈️"
    "Surprise" -> "🎁"
    "Date Intime" -> "💋"
    "Cinéma" -> "🎬"
    else -> "📅"
}
