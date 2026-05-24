package org.example.project.oscilloscope

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OscilloscopeWindow(
    state: OscilloscopeState,
    modifier: Modifier = Modifier
) {
    // Используем веса (Float) в точности как в правом окне таблицы параметров
    val colWeights = remember { mutableStateListOf(0.2f, 0.1f, 0.08f, 0.08f, 0.54f) }

    var selectedRowIndex by remember { mutableStateOf(0) }

    val dummySignals = remember {
        listOf(
            "F1.NotValidReg" to "0102",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "F1.NotValidReg" to "0xFF0000C8",
            "HnowalidReg"    to "0x00000000"
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color(0xFFEFEFEF))) {
        val totalWidthPx = constraints.maxWidth.toFloat()

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Панель инструментов
            OscilloscopeTopBar(state = state)

            // 2. Интерактивная шапка на весах с компенсацией соседней колонки
            // 2. Интерактивная шапка на весах с компенсацией соседней колонки
            OscilloscopeHeaderColumns(
                weights = colWeights,
                totalWidthPx = totalWidthPx,
                onWeightChange = { index, delta ->
                    // Убираем тормоза: переводим пиксели напрямую в пропорцию от общей ширины экрана
                    val totalSum = colWeights.sum()
                    val deltaWeight = (delta / totalWidthPx) * totalSum

                    val nextIndex = index + 1
                    if (index in colWeights.indices && nextIndex in colWeights.indices) {
                        val oldWeightCurrent = colWeights[index]
                        val oldWeightNext = colWeights[nextIndex]

                        val newWeightCurrent = (oldWeightCurrent + deltaWeight).coerceAtLeast(0.02f)
                        val actualDelta = newWeightCurrent - oldWeightCurrent
                        val newWeightNext = (oldWeightNext - actualDelta).coerceAtLeast(0.02f)

                        colWeights[index] = newWeightCurrent
                        colWeights[nextIndex] = newWeightNext
                    }
                }
            )/////////////////////////////////\\\\\\\\\\\\\\\\\\\\\\\

            // 3. Таблица строк параметров (Чистый белый фон, без линий)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
            ) {
                dummySignals.forEachIndexed { index, pair ->
                    val isSelected = index == selectedRowIndex
                    OscilloscopeRow(
                        name = pair.first,
                        hex = pair.second,
                        physical = if (index == 0) "true" else if (index == 3) "40" else "25.0",
                        unit = if (index == 0 || index == 1) "FLAG" else "V",
                        weights = colWeights,
                        isSelected = isSelected,
                        onRowClick = { selectedRowIndex = index }
                    )
                }
            }

            // 4. Зарезервированный пустой статус-бар внизу
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(Color(0xFF1A2332)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = " COM3 | MODBUS | STATUS: READY | Parameter: ${dummySignals.getOrNull(selectedRowIndex)?.first ?: ""} | Connected",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun OscilloscopeTopBar(state: OscilloscopeState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFFEFEFEF))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "📁 💾  ▶ 🛑   [ Настройки развертки / Пауза / Запись ]", color = Color.Black, fontSize = 12.sp)
    }
}