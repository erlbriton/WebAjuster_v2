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
    state: org.example.project.oscilloscope.OscilloscopeState,
    modifier: Modifier = Modifier
) {
    // Получаем доступ к нашей глобальной ViewModel
    val vm = org.example.project.viewmodels.MainViewModel.instance

    // ЕДИНЫЙ ЛОКАЛЬНЫЙ МАССИВ ИЗ 5 ВЕСОВ:
    // Индексы 0, 1, 2, 3 — колонки таблицы параметров RAM (name, hex, physical, unit)
    // Индекс 4 — колонка для окна осциллограммы
    val localWeights = remember { mutableStateListOf(0.20f, 0.10f, 0.15f, 0.10f, 0.45f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color(0xFFEFEFEF))) {
        val totalWidthPx = constraints.maxWidth.toFloat()

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Панель инструментов верхняя
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).background(Color(0xFFEFEFEF)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentPort = if (vm.selectedComPort.isEmpty()) "Не выбран" else vm.selectedComPort
                Text(
                    text = "📁 Секция: [RAM]  |  🔌 COM Порт: $currentPort  |  ⏱ Цикл опроса: 200 мс",
                    color = Color.Black,
                    fontSize = 12.sp
                )
            }

            // 2. Интерактивная шапка таблицы колонок (задает общую сетку для всего экрана)
            OscilloscopeHeaderColumns(
                weights = localWeights,
                totalWidthPx = totalWidthPx,
                onWeightChange = { index, delta ->
                    val totalSum = localWeights.sum()
                    val deltaWeight = (delta / totalWidthPx) * totalSum
                    val nextIndex = index + 1

                    if (index in localWeights.indices && nextIndex in localWeights.indices) {
                        val oldWeightCurrent = localWeights[index]
                        val oldWeightNext = localWeights[nextIndex]

                        // Меняем пропорции соседних колонок, обеспечивая минимальную ширину в 5%
                        val newWeightCurrent = (oldWeightCurrent + deltaWeight).coerceAtLeast(0.05f)
                        val actualDelta = newWeightCurrent - oldWeightCurrent
                        val newWeightNext = (oldWeightNext - actualDelta).coerceAtLeast(0.05f)

                        localWeights[index] = newWeightCurrent
                        localWeights[nextIndex] = newWeightNext
                    }
                }
            )

            // 3. Основная рабочая область (Скроллируемый контейнер)
            // Мы избавляемся от деления на левый и правый блоки, делая прокрутку только для данных таблицы.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                val device = vm.currentDeviceState.value

                if (device != null) {
                    // Вертикальный список строк параметров
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        device.ramParameters.forEach { param ->
                            val isSelected = (vm.selectedCode == param.code)

                            // Каждая строка — это Row на всю ширину экрана, где первые 4 ячейки — текст,
                            // а вместо 5-й ячейки — пустое место (Spacer), чтобы не перекрывать график!
                            OscilloscopeRow(
                                name = param.idName,
                                hex = param.hexBase,
                                physical = param.physBase,
                                unit = param.unit,
                                weights = localWeights, // Строка идеально выравнивается по тем же 5 весам
                                isSelected = isSelected,
                                onRowClick = {
                                    vm.selectRow(param.code)
                                }
                            )
                        }
                    }
                } else {
                    // Заглушка, если прибор не выбран (занимает только область таблицы, не заходя на график)
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(localWeights[0] + localWeights[1] + localWeights[2] + localWeights[3])
                                .fillMaxHeight()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Прибор не выбран. Загрузите INI файл.", color = Color.Gray, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.weight(localWeights[4]))
                    }
                }

                // Окно с графиком накладывается поверх общего слоя СТРОГО в правую часть экрана,
                // используя 5-й вес. Его левая граница теперь железно совпадает с разделителем 'unit'.
                Row(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.weight(localWeights[0] + localWeights[1] + localWeights[2] + localWeights[3]))
                    OscilloscopeRightWindow(
                        modifier = Modifier
                            .weight(localWeights[4])
                            .fillMaxHeight()
                    )
                }
            }

            // 4. Нижний статус-бар
            Box(
                modifier = Modifier.fillMaxWidth().height(22.dp).background(Color(0xFF1A2332)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = " Мониторинг параметров секции [RAM] в реальном времени через Modbus",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}