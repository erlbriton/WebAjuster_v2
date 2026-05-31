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
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import org.example.project.logic.ModbusRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.project.logic.WebModbusConverter



@JsFun("() => performance.now()")
external fun performanceNow(): Double

// Простая пара время-значение (время в миллисекундах)
private data class TimedValue(
    val timeMs: Double,  // ✅ Теперь Double
    val value: Float
)

@Composable
fun OscilloscopeRightWindow(
    paramCode: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val minVal = 0f
    val maxVal = 40f
    val range = maxVal - minVal

    val stepX = 3.5f
    val maxPoints = 600
    val windowDurationMs = 3_000.0 // 10 секунд

    // Буфер для сглаживания (скользящее среднее)
    val smoothingBuffer = remember { mutableStateListOf<Float>() }
    val smoothingWindow = 1 // ← ИЗМЕНЕНО: отключаем сглаживание
    val timedPoints = remember { mutableStateListOf<TimedValue>() }

    // ==========================================
    // ШАГ 3: ЖИВОЙ СБОРЩИК ТОЧЕК ДЛЯ КАНВАСА
    // ==========================================
    // ==========================================
    // ШАГ 52: ПЛАВНЫЙ ГЕНЕРАТОР ПИЛЫ 1 ГЦ НА ЧАСТОТЕ 50 ГЦ
    // ==========================================
    LaunchedEffect(paramCode) {
        val vm = org.example.project.viewmodels.MainViewModel.instance

        while (true) {
            val now = performanceNow()

            // 1. Считаем прогресс в текущей секунде (0.0 до 999.999... мс)
            val progressInSecond = now % 200.0

            // 2. Генерируем амплитуду пилы от 0 до 35 (под maxVal = 40f)
            val maxAmplitude = 35f
            val calculatedWaveValue = ((progressInSecond / 200.0) * maxAmplitude).toFloat()

            // 3. Синхронно закидываем это значение в текстовое поле таблицы RAM (чтобы цифры бежали плавно)
            val device = vm.currentDeviceState.value
            val myParam = device?.ramParameters?.find { it.code == paramCode }
            if (myParam != null) {
                myParam.physCtrl = (kotlin.math.round(calculatedWaveValue * 100.0) / 100.0).toString()
            }

            // 4. Добавляем ИДЕАЛЬНО ПЛАВНУЮ точку в график
            timedPoints.add(TimedValue(now, calculatedWaveValue))

            val cutoff = now - windowDurationMs
            while (timedPoints.isNotEmpty() && timedPoints[0].timeMs < cutoff) {
                timedPoints.removeAt(0)
            }
            if (timedPoints.size > maxPoints) timedPoints.removeAt(0)

            // Четкий шаг 20 мс обеспечивает 50 обновлений в секунду
            delay(20)
        }
    }
    // ==========================================

    // Отрисовка
    Box(
        modifier = modifier.background(
            if (isSelected) Color(0xFFE2EDF8) else Color(0xFFFAFAFA)
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Рисуем сетку
            val gridStep = 40f
            var xGrid = width
            while (xGrid > 0) {
                drawLine(
                    color = Color(0xFFEBEBEB),
                    start = Offset(xGrid, 0f),
                    end = Offset(xGrid, height),
                    strokeWidth = 1f
                )
                xGrid -= gridStep
            }

            // 2. БЕЗОПАСНАЯ ОТРИСОВКА ПО ИНДЕКСАМ (Шаг влево на 4 пикселя для каждой точки)
            if (timedPoints.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path()
                var pathStarted = false

                val pointStepX = 4f // Расстояние в пикселях между точками графиков

                // Перебираем точки с конца (от самых свежих к старым)
                for (i in timedPoints.indices.reversed()) {
                    val p = timedPoints[i]

                    // Самая свежая точка — на правом краю (width), каждая следующая — левее на pointStepX
                    val indexFromLast = timedPoints.size - 1 - i
                    val x = width - (indexFromLast * pointStepX)

                    // Если график ушел за левый край экрана — прекращаем рисовать старые точки
                    if (x < 0f) break

                    // Рассчитываем Y с учетом вашего масштаба (maxVal = 40f)
                    val y = height - (((p.value - minVal) / range) * height).coerceIn(0f, height)

                    if (!pathStarted) {
                        path.moveTo(x, y)
                        pathStarted = true
                    } else {
                        path.lineTo(x, y)
                    }
                }

                if (pathStarted) {
                    drawPath(
                        path = path,
                        color = if (isSelected) Color(0xFF00AA00) else Color(0xFF4A90E2),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }
}

