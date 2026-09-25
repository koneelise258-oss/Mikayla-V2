package com.example.mikayala.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.mikayala.theme.*

@Composable
fun MessageStatusIndicator(
    status: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(15.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            "pending", "sending", "queued_offline" -> {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = "En attente / Envoi",
                    tint = TextMuted,
                    modifier = Modifier.size(11.dp)
                )
            }
            "sent" -> {
                // Simple coche grise
                SingleCheck(color = StatusSent)
            }
            "delivered" -> {
                // Double coche grise
                DoubleCheck(color = StatusGrey)
            }
            "read" -> {
                // Double coche bleue
                DoubleCheck(color = StatusBlue)
            }
            else -> {
                // Unknown server status: fallback to sent (SingleCheck), never assume read
                SingleCheck(color = StatusSent)
            }
        }
    }
}

@Composable
fun SingleCheck(color: Color) {
    Canvas(modifier = Modifier.size(13.dp)) {
        val stroke = 1.6.dp.toPx()
        val start = Offset(size.width * 0.15f, size.height * 0.52f)
        val mid = Offset(size.width * 0.42f, size.height * 0.80f)
        val end = Offset(size.width * 0.88f, size.height * 0.25f)

        drawLine(color, start, mid, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, mid, end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun DoubleCheck(color: Color) {
    Canvas(modifier = Modifier.size(15.dp)) {
        val stroke = 1.6.dp.toPx()
        // First tick
        val start1 = Offset(size.width * 0.08f, size.height * 0.52f)
        val mid1 = Offset(size.width * 0.35f, size.height * 0.80f)
        val end1 = Offset(size.width * 0.70f, size.height * 0.25f)

        drawLine(color, start1, mid1, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, mid1, end1, strokeWidth = stroke, cap = StrokeCap.Round)

        // Second tick shifted right
        val start2 = Offset(size.width * 0.28f, size.height * 0.52f)
        val mid2 = Offset(size.width * 0.55f, size.height * 0.80f)
        val end2 = Offset(size.width * 0.90f, size.height * 0.25f)

        drawLine(color, start2, mid2, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, mid2, end2, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
