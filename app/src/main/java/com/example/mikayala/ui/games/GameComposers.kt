package com.example.mikayala.ui.games

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.MikayalaColors
import org.json.JSONArray

/** Questions / lots préparés à l'avance, enregistrés sur le téléphone. */
object GameDrafts {
    private const val PREFS = "mikayala_game_drafts"

    fun load(context: Context, key: String): List<String> = try {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]"))
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    fun save(context: Context, key: String, items: List<String>) {
        val arr = JSONArray()
        items.distinct().take(50).forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, arr.toString()).apply()
    }
}

@Composable
private fun SuggestionRow(text: String, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val c = MikayalaColors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) GameViolet.copy(alpha = 0.18f) else c.surfaceElevated,
        border = BorderStroke(1.dp, if (selected) GameViolet else c.border),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
            Text(text, fontSize = 13.sp, color = c.textPrimary, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Retirer", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Choisir ou écrire soi-même le contenu d'un jeu (carte à gratter, bon d'amour, qui de nous deux…).
 * « Mes idées » : ce qu'on écrit est gardé pour la prochaine fois.
 */
@Composable
fun TextGameComposer(
    title: String,
    hint: String,
    draftKey: String,
    suggestions: List<String>,
    sendLabel: String = "Envoyer 💜",
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val context = LocalContext.current
    val c = MikayalaColors
    val saved = remember { mutableStateListOf<String>().apply { addAll(GameDrafts.load(context, draftKey)) } }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.modal,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.textPrimary) },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200).replace("§", "").replace("|", "") },
                    label = { Text(hint) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { text = (saved + suggestions).randomOrNull().orEmpty() }) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Au hasard", tint = GameViolet)
                        }
                    }
                )
                if (text.isNotBlank() && text.trim() !in saved) {
                    TextButton(onClick = { saved.add(0, text.trim()); GameDrafts.save(context, draftKey, saved) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = GameGreen, modifier = Modifier.size(16.dp))
                        Text(" Garder dans mes idées", color = GameGreen, fontSize = 13.sp)
                    }
                }
                if (saved.isNotEmpty()) {
                    Text("Mes idées", fontSize = 12.sp, color = GameGreen, fontWeight = FontWeight.SemiBold)
                    saved.toList().forEach { s ->
                        SuggestionRow(s, selected = text == s, onClick = { text = s }, onDelete = {
                            saved.remove(s); GameDrafts.save(context, draftKey, saved)
                        })
                    }
                }
                Text("Idées", fontSize = 12.sp, color = GameViolet, fontWeight = FontWeight.SemiBold)
                suggestions.forEach { s -> SuggestionRow(s, selected = text == s, onClick = { text = s }) }
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = { onSend(text.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = GameViolet)
            ) { Text(sendLabel, color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = c.textSecondary) } }
    )
}

/** Action ou vérité : on choisit le type, puis sa propre question (ou une idée). */
@Composable
fun TruthOrDareComposer(
    truths: List<String>,
    dares: List<String>,
    onDismiss: () -> Unit,
    onSend: (isDare: Boolean, text: String) -> Unit
) {
    var isDare by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(0) }
    val c = MikayalaColors
    if (step == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = c.modal,
            title = { Text("Action ou vérité ?", fontWeight = FontWeight.Bold, color = c.textPrimary) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { isDare = false; step = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = GameViolet),
                        modifier = Modifier.weight(1f).height(64.dp)
                    ) { Text("✨ Vérité", color = Color.White, fontSize = 15.sp) }
                    Button(
                        onClick = { isDare = true; step = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = GameGreen),
                        modifier = Modifier.weight(1f).height(64.dp)
                    ) { Text("🔥 Action", color = Color.White, fontSize = 15.sp) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = c.textSecondary) } }
        )
    } else {
        TextGameComposer(
            title = if (isDare) "🔥 Ton action" else "✨ Ta question vérité",
            hint = if (isDare) "Écris le défi…" else "Écris ta question…",
            draftKey = if (isDare) "dares" else "truths",
            suggestions = if (isDare) dares else truths,
            onDismiss = onDismiss,
            onSend = { onSend(isDare, it) }
        )
    }
}

/** Roue : les cases sont modifiables avant de lancer (2 à 8 cases), et gardées pour la prochaine fois. */
@Composable
fun WheelComposer(defaults: List<String>, onDismiss: () -> Unit, onSend: (List<String>) -> Unit) {
    val context = LocalContext.current
    val c = MikayalaColors
    val items = remember {
        mutableStateListOf<String>().apply { addAll(GameDrafts.load(context, "wheel").ifEmpty { defaults }) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.modal,
        title = { Text("🎡 Ta roue", fontWeight = FontWeight.Bold, color = c.textPrimary) },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Modifie les cases, puis lance : le résultat sera le même sur vos deux téléphones.", fontSize = 12.sp, color = c.textSecondary)
                items.forEachIndexed { i, v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = v,
                            onValueChange = { items[i] = it.take(40).replace("|", "").replace("§", "") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (items.size > 2) {
                            IconButton(onClick = { items.removeAt(i) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Retirer", tint = c.textSecondary)
                            }
                        }
                    }
                }
                if (items.size < 8) {
                    TextButton(onClick = { items.add("") }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = GameGreen)
                        Text(" Ajouter une case", color = GameGreen)
                    }
                }
                TextButton(onClick = { items.clear(); items.addAll(defaults) }) {
                    Text("Revenir aux cases d'origine", color = c.textSecondary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            val clean = items.map { it.trim() }.filter { it.isNotBlank() }
            Button(
                enabled = clean.size >= 2,
                onClick = { GameDrafts.save(context, "wheel", clean); onSend(clean) },
                colors = ButtonDefaults.buttonColors(containerColor = GameViolet)
            ) { Text("Envoyer la roue", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = c.textSecondary) } }
    )
}
