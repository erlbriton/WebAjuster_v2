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
import kotlinx.coroutines.delay

@Composable
fun OscilloscopeWindow(
    state: org.example.project.oscilloscope.OscilloscopeState,
    modifier: Modifier = Modifier
) {
    // Получаем доступ к нашей глобальной ViewModel
    val vm = org.example.project.viewmodels.MainViewModel.instance

    // ЕДИНЫЙ ЛОКАЛЬНЫЙ МАССИВ ИЗ 5 ВЕСОВ:
    // Индексы 0, 1, 2, 3 — колонки таблицы параметров RAM (name, hex, physical, unit)
    // Индекс 4 — колонка для графиков-самописцев
    val localWeights = remember { mutableStateListOf(0.20f, 0.10f, 0.15f, 0.10f, 0.45f) }

    // Запускаем единый Modbus-таймер опроса для всего окна осциллографа
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            val device = vm.currentDeviceState.value
            if (device != null) {
                org.example.project.logic.ModbusRepository.performModbusOpros(device, vm.currentVarsMap) { portName ->
                    vm.setConnectedPort(portName)
                }
            }
        }
    }

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

            // 2. Интерактивная шапка таблицы колонок (задает общую сетку для всего экрана, включая Graph)
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

                        val newWeightCurrent = (oldWeightCurrent + deltaWeight).coerceAtLeast(0.05f)
                        val actualDelta = newWeightCurrent - oldWeightCurrent
                        val newWeightNext = (oldWeightNext - actualDelta).coerceAtLeast(0.05f)

                        localWeights[index] = newWeightCurrent
                        localWeights[nextIndex] = newWeightNext
                    }
                }
            )

            // 3. Основная рабочая область (Единый скроллируемый контейнер таблицы и графиков)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                val device = vm.currentDeviceState.value

                if (device != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        device.ramParameters.forEach { param ->
                            val isSelected = (vm.selectedCode == param.code)

                            // Мы передаем веса и флаг выбора.
                            // Внутри OscilloscopeRow теперь встроен Canvas для 5-й колонки!
                            OscilloscopeRow(
                                name = param.idName,
                                hex = param.hexBase,
                                physical = param.physBase,
                                unit = param.unit,
                                weights = localWeights,
                                isSelected = isSelected,
                                onRowClick = {
                                    vm.selectRow(param.code)
                                }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Прибор не выбран. Загрузите INI файл.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }

            // 4. Нижний статус-бар
            Box(
                modifier = Modifier.fillMaxWidth().height(22.dp).background(Color(0xFF1A2332)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = " Мониторинг параметров секции [RAM] в реальном времени. Многоканальный самописец.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}