package com.example.mikayala.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Sondage rédigé par l'utilisateur (avant : question et choix imposés, identiques pour tout le monde).
 * Les votes sont partagés entre les deux téléphones (même mécanisme que « Tu préfères »).
 */
@Composable
fun PollComposerDialog(onDismiss: () -> Unit, onSend: (question: String, options: List<String>) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    val valid = question.isNotBlank() && options.count { it.isNotBlank() } >= 2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau sondage") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = question, onValueChange = { question = it.take(120) },
                    label = { Text("Question") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                options.forEachIndexed { i, opt ->
                    OutlinedTextField(
                        // « | » sépare les choix dans le message : interdit dans un choix
                        value = opt, onValueChange = { options[i] = it.replace("|", "/").replace("§", "").take(60) },
                        label = { Text("Choix ${i + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                if (options.size < 4) {
                    TextButton(onClick = { options.add("") }) { Text("Ajouter un choix") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSend(question.trim(), options.map { it.trim() }.filter { it.isNotEmpty() })
            }) { Text("Envoyer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Événement rédigé par l'utilisateur (avant : « Escapade Weekend Spa… Ce Samedi à 18h » imposé). */
@Composable
fun EventComposerDialog(onDismiss: () -> Unit, onSend: (title: String, whenText: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Proposer un moment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(100) },
                    label = { Text("Quoi ?") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = whenText, onValueChange = { whenText = it.take(60) },
                    label = { Text("Quand ? (ex. samedi 18 h)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSend(title.trim(), whenText.trim()) }) { Text("Envoyer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
