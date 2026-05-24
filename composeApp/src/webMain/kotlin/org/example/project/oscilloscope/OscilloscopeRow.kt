package org.example.project.oscilloscope

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OscilloscopeRow(
    name: String,
    hex: String,
    physical: String,
    unit: String,
    weights: List<Float>,
    isSelected: Boolean,
    onRowClick: () -> Unit
) {
    val rowBgColor = if (isSelected) Color(0xFF00FF00) else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(rowBgColor)
            .clickable { onRowClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. name
        Box(modifier = Modifier.weight(weights[0]).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(name, color = if (isSelected) Color.Black else Color(0xFF0000C8), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
        }

        // 2. Hex
        Box(modifier = Modifier.weight(weights[1]).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(hex, color = Color.Black, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
        }

        // 3. physical
        Box(modifier = Modifier.weight(weights[2]).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(physical, color = Color.Black, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
        }

        // 4. unit
        Box(modifier = Modifier.weight(weights[3]).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(unit, color = Color.Black, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
        }

        Box(
            modifier = Modifier
                .weight(weights.getOrElse(4) { 0.25f })
                .fillMaxHeight()
                .background(rowBgColor)
        )
    }
}