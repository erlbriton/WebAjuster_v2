package org.example.project.oscilloscope

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    // Высота строки — 32.dp, чтобы и текст читался отлично, и для мини-графика хватало места
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(if (isSelected) Color(0xFFE2EDF8) else Color.White) // Подсветка всей строки при клике
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

        // Колонка 3: Physical
        Box(
            modifier = Modifier.weight(weights[2]).fillMaxHeight().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Используем derivedStateOf, чтобы Compose следил за изменением значения
            val displayValue by remember(physical) { derivedStateOf { physical } }

            Text(text = displayValue, color = Color.Black, fontSize = 12.sp, maxLines = 1)
        }

        // Колонка 4: Unit
        Box(
            modifier = Modifier.weight(weights[3]).fillMaxHeight().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = unit, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }

        // Колонка 5: НАШ НАСТОЯЩИЙ ЖИВОЙ КАНВАС-САМОПИСЕЦ
        // Занимает строго 5-й вес, склеен с unit и скроллируется вместе со всей строкой!
        OscilloscopeRightWindow(
            physValueString = physical,
            isSelected = isSelected,
            modifier = Modifier
                .weight(weights[4])
                .fillMaxHeight()
        )
    }
}