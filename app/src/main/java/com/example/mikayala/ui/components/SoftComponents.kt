package com.example.mikayala.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.MikayalaColors
import com.example.mikayala.theme.Radius
import com.example.mikayala.theme.Spacing

// Composants de base du système Soft : à utiliser pour tout nouvel écran.

@Composable
fun SoftPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val c = MikayalaColors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = Radius.md,
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = if (c.isDark) c.textPrimary else androidx.compose.ui.graphics.Color.White),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
        modifier = modifier
    ) {
        if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = c.textPrimary)
        else Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SoftSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = MikayalaColors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Radius.md,
        border = BorderStroke(1.dp, c.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
        modifier = modifier
    ) { Text(text, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
}

@Composable
fun SoftTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val c = MikayalaColors
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = if (danger) c.danger else c.accent, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true
) {
    val c = MikayalaColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it, color = if (isError) c.danger else c.textMuted) } },
        isError = isError,
        singleLine = singleLine,
        shape = Radius.md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.border,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textSecondary,
            cursorColor = c.accent,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            errorBorderColor = c.danger
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** En-tête de section discret (petites capitales, couleur secondaire). */
@Composable
fun SoftSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MikayalaColors.textMuted,
        modifier = modifier.padding(start = Spacing.xs, top = Spacing.lg, bottom = Spacing.xs)
    )
}

/** État vide : icône, titre, explication, action facultative. */
@Composable
fun SoftEmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val c = MikayalaColors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Surface(shape = Radius.pill, color = c.accentSoft) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.padding(Spacing.lg).size(28.dp))
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(message, fontSize = 14.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.sm))
            SoftPrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun SoftLoading(message: String = "Chargement…", modifier: Modifier = Modifier) {
    val c = MikayalaColors
    Row(modifier = modifier.fillMaxWidth().padding(Spacing.xl), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = c.accent)
        Spacer(Modifier.size(Spacing.md))
        Text(message, color = c.textSecondary, fontSize = 14.sp)
    }
}

/** Bandeau d'erreur ou d'information, sans fenêtre. */
@Composable
fun SoftBanner(message: String, modifier: Modifier = Modifier, isError: Boolean = true, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val c = MikayalaColors
    Surface(shape = Radius.md, color = if (isError) c.danger.copy(alpha = 0.12f) else c.accentSoft, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = if (isError) c.danger else c.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) SoftTextButton(actionLabel, onAction, danger = isError)
        }
    }
}

/** Limite la largeur du contenu sur tablette, centré. */
@Composable
fun SoftContentWidth(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = Spacing.maxContentWidth)) { content() }
    }
}
