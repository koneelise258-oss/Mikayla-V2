package com.example.mikayala.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DecoyCalculatorScreen(
    onExitDecoy: () -> Unit
) {
    var displayValue by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var operator by remember { mutableStateOf<String?>(null) }
    var isNewEntry by remember { mutableStateOf(true) }

    fun onDigit(d: String) {
        if (isNewEntry || displayValue == "0") {
            displayValue = d
            isNewEntry = false
        } else {
            if (displayValue.length < 10) {
                displayValue += d
            }
        }
    }

    fun onOp(op: String) {
        operand1 = displayValue.toDoubleOrNull()
        operator = op
        isNewEntry = true
    }

    fun onCalculate() {
        val op2 = displayValue.toDoubleOrNull()
        if (operand1 != null && operator != null && op2 != null) {
            val result = when (operator) {
                "+" -> operand1!! + op2
                "-" -> operand1!! - op2
                "×" -> operand1!! * op2
                "÷" -> if (op2 != 0.0) operand1!! / op2 else Double.NaN
                else -> op2
            }
            displayValue = if (result.isNaN()) "Erreur" else if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
            operand1 = null
            operator = null
            isNewEntry = true
        }
    }

    fun onClear() {
        displayValue = "0"
        operand1 = null
        operator = null
        isNewEntry = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Exit subtle back icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onExitDecoy) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Quitter",
                    tint = Color.Gray
                )
            }
        }

        // Display Screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = displayValue,
                fontSize = 54.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                maxLines = 1
            )
        }

        // Calculator Buttons Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val buttons = listOf(
                listOf("C", "±", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            for (row in buttons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (btn in row) {
                        val isOrange = btn in listOf("÷", "×", "-", "+", "=")
                        val isLightGray = btn in listOf("C", "±", "%")
                        val btnColor = if (isOrange) Color(0xFFFF9F0A) else if (isLightGray) Color(0xFFA5A5A5) else Color(0xFF333333)
                        val textColor = if (isLightGray) Color.Black else Color.White

                        val widthModifier = if (btn == "0") Modifier.weight(2.1f) else Modifier.weight(1f)

                        Box(
                            modifier = widthModifier
                                .padding(horizontal = 4.dp)
                                .height(72.dp)
                                .clip(CircleShape)
                                .background(btnColor)
                                .clickable {
                                    when (btn) {
                                        "C" -> onClear()
                                        "±" -> {
                                            displayValue = if (displayValue.startsWith("-")) displayValue.drop(1) else "-$displayValue"
                                        }
                                        "%" -> {
                                            val num = displayValue.toDoubleOrNull() ?: 0.0
                                            displayValue = (num / 100.0).toString()
                                        }
                                        "÷", "×", "-", "+" -> onOp(btn)
                                        "=" -> onCalculate()
                                        "." -> {
                                            if (!displayValue.contains(".")) displayValue += "."
                                        }
                                        else -> onDigit(btn)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = btn,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}
