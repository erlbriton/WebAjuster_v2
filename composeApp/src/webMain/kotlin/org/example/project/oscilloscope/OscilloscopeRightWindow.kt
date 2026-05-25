package org.example.project.oscilloscope

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@Composable
fun OscilloscopeRightWindow(
    physValueString: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val points = remember { mutableStateListOf<Float>() }

    // Таймер, который принудительно берет текущее значение и обновляет график
    LaunchedEffect(Unit) {
        while (true) {
            val numeric = physValueString.replace(Regex("[^0-9.-]"), "")
            val value = numeric.toFloatOrNull() ?: 0f

            points.add(value)
            if (points.size > 200) {
                points.removeAt(0)
            }
            delay(200) // Частота отрисовки совпадает с циклом опроса Modbus
        }
    }

    Box(modifier = modifier.background(if (isSelected) Color(0xFFE2EDF8) else Color(0xFFFAFAFA))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // ... (Ваш код отрисовки остается прежним) ...
            val width = size.width
            val height = size.height

            // Рисуем сетку
            for (i in 1 until 15) {
                drawLine(Color(0xFFEBEBEB), Offset(i * width / 15, 0f), Offset(i * width / 15, height), 1f)
            }
            drawLine(Color(0xFFF0F0F0), Offset(0f, height / 2f), Offset(width, height / 2f), 1f)

            if (points.size > 1) {
                val stepX = width / 200f
                val maxVal = points.maxOrNull() ?: 100f
                val minVal = points.minOrNull() ?: 0f
                val delta = (maxVal - minVal).coerceAtLeast(1f)
                val displayDelta = (maxVal + delta * 0.1f) - (minVal - delta * 0.1f)
                val displayMin = minVal - delta * 0.1f

                for (i in 0 until points.size - 1) {
                    val x1 = stepX * i
                    val y1 = height - (((points[i] - displayMin) / displayDelta) * height)
                    val x2 = stepX * (i + 1)
                    val y2 = height - (((points[i + 1] - displayMin) / displayDelta) * height)

                    if (x1 <= width) {
                        drawLine(
                            color = if (isSelected) Color(0xFF00AA00) else Color(0xFF4A90E2),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }
    }
}