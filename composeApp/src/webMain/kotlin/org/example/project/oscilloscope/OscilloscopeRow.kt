package org.example.project.oscilloscope

import org.example.project.oscilloscope.OscilloscopeRightWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OscilloscopeRow(
    code: String,
    name: String,
    hex: String,
    physical: String, // Оставляем для совместимости
    unit: String,
    weights: List<Float>,
    isSelected: Boolean,
    onRowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (isSelected) Color(0xFFE2EDF8) else Color.White)
            .clickable { onRowClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Колонка 1: Name
        Box(
            modifier = Modifier.weight(weights[0]).fillMaxHeight().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = name, color = Color.Black, fontSize = 12.sp, maxLines = 1)
        }

        // Колонка 2: Hex
        Box(
            modifier = Modifier.weight(weights[1]).fillMaxHeight().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = hex, color = Color.DarkGray, fontSize = 12.sp, maxLines = 1)
        }

        // Колонка 3: Physical (Убрали ломающий derivedStateOf, теперь текст будет обновляться напрямую)
        Box(
            modifier = Modifier.weight(weights[2]).fillMaxHeight().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = physical, color = Color.Black, fontSize = 12.sp, maxLines = 1)
        }

        // Колонка 4: Unit
        Box(
            modifier = Modifier.weight(weights[3]).fillMaxHeight().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = unit, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }

        // Колонка 5: НАШ НАСТОЯЩИЙ ЖИВОЙ КАНВАС-САМОПИСЕЦ
        Box(
            modifier = Modifier.weight(weights[4]).fillMaxHeight()
        ) {
            OscilloscopeRightWindow(
                paramCode = code,
                isSelected = isSelected,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}