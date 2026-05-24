package org.example.project.oscilloscope

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.js.js

// Правило Kotlin/Wasm: функция js() должна быть выражением на самом верхнем уровне
private fun getBrowserTime(): Double = js("Date.now()")

@Composable
fun OscilloscopeRightWindow(
    modifier: Modifier = Modifier
) {
    // История точек для отрисовки графиков на Canvas
    val graphPoints = remember { mutableStateListOf<Float>() }

    // ФОНОВЫЙ ТАЙМЕР ОПРОСА (Пока стоит 200 мс для подготовки к Modbus)
    // ФОНОВЫЙ ТАЙМЕР ОПРОСА (Каждые 200 мс)
    LaunchedEffect(Unit) {
        val vm = org.example.project.viewmodels.MainViewModel.instance

        while (true) {
            delay(200) // Период опроса по умолчанию

            // 1. Проверяем, загружен ли прибор в систему
            val device = vm.currentDeviceState.value
            if (device != null) {

                // 2. Вызываем стандартный Modbus-опрос для текущего устройства
                // Он автоматически обновит hexBase/physBase у параметров внутри ramParameters
                org.example.project.logic.ModbusRepository.performModbusOpros(
                    device,
                    vm.currentVarsMap
                ) { portName ->
                    vm.setConnectedPort(portName)
                }

                // 3. Берем значение выделенного (выбранного пользователем) или первого параметра из секции RAM
                // Для теста берем первый доступный параметр, у которого есть физическое значение
                val targetParam = device.ramParameters.firstOrNull()
                if (targetParam != null) {
                    // Просто парсим чистое физическое значение из Modbus в Float
                    val realValue = targetParam.physBase.toFloatOrNull() ?: 0f
                    graphPoints.add(realValue)
                }
            } else {
                graphPoints.add(0f) // Если прибора нет, пишем 0
            }

            // Ограничиваем историю точек на экране
            if (graphPoints.size > 300) {
                graphPoints.removeAt(0)
            }
        }
    }

    // Область высокопроизводительного Canvas осциллографа
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF151921)) // Глубокий темно-синий/черный цвет сетки
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Рисуем сетку (8 горизонтальных делений)
            val rows = 8
            val rowHeight = size.height / rows
            for (i in 1 until rows) {
                drawLine(
                    color = Color(0xFF1E2530).copy(alpha = 0.6f),
                    start = Offset(0f, i * rowHeight),
                    end = Offset(size.width, i * rowHeight),
                    strokeWidth = 1f
                )
            }

            // Отрисовка луча осциллографа по точкам из LaunchedEffect
            if (graphPoints.size > 1) {
                val stepX = 3f // Каждая точка жестко занимает 3 пикселя по горизонтали
                val maxExpectedValue = 100f // Максимальное физическое значение для масштаба
                val scaleY = size.height / maxExpectedValue

                for (i in 0 until graphPoints.size - 1) {
                    val x1 = stepX * i
                    val y1 = size.height - (graphPoints[i] * scaleY).coerceIn(0f, size.height)

                    val x2 = stepX * (i + 1)
                    val y2 = size.height - (graphPoints[i + 1] * scaleY).coerceIn(0f, size.height)

                    // Рисуем линию, если она попадает в видимую область холста
                    if (x1 <= size.width) {
                        drawLine(
                            color = Color(0xFF00FF66), // Ярко-зеленый луч
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