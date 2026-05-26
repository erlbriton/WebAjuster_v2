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
    val maxVal = 1100f
    val range = maxVal - minVal

    val stepX = 3.5f
    val maxPoints = 600
    val windowDurationMs = 3_000.0 // 10 секунд

    // Буфер для сглаживания (скользящее среднее)
    val smoothingBuffer = remember { mutableStateListOf<Float>() }
    val smoothingWindow = 1 // ← ИЗМЕНЕНО: отключаем сглаживание
    val timedPoints = remember { mutableStateListOf<TimedValue>() }

    LaunchedEffect(paramCode) {
        var lastTime = 0.0
        val targetAddress = 0x002D // Адрес регистра p04500 (r002D)

        while (true) {
            val startTime = performanceNow()

            // 🔥 Прямой запрос к порту, минуя весь репозиторий
            val value = ModbusRepository.readRegisterFast(targetAddress)

            val now = performanceNow()
            val delta = now - lastTime
            val execTime = now - startTime

            if (lastTime > 0) {
                println("⚡ Delta: ${delta.toInt()}ms | Exec: ${execTime.toInt()}ms | Val: $value")
            }
            lastTime = now

            // Добавляем в график
            timedPoints.add(TimedValue(now, value))
            val cutoff = now - windowDurationMs
            while (timedPoints.isNotEmpty() && timedPoints[0].timeMs < cutoff) {
                timedPoints.removeAt(0)
            }
            if (timedPoints.size > maxPoints) timedPoints.removeAt(0)

            delay(20) // 50 Гц
        }
    }

    // Отрисовка
    Box(
        modifier = modifier.background(
            if (isSelected) Color(0xFFE2EDF8) else Color(0xFFFAFAFA)
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Сетка
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

            // 🔥 ОТРИСОВКА С ФИКСИРОВАННЫМ МАСШТАБОМ ВРЕМЕНИ
            // 🔥 ОТРИСОВКА С ФИКСИРОВАННЫМ МАСШТАБОМ ВРЕМЕНИ (как в обычном осциллографе)
            if (timedPoints.size > 1) {
                // МАСШТАБ: 1 см экрана ≈ 1 секунда сигнала
                // На стандартном экране (96 DPI): 1 см ≈ 38-40 пикселей
                // 40 пикселей / 1000 мс = 0.04f пикселей на миллисекунду
                val PIXELS_PER_MS = 0.04f

                // Привязываем правый край графика к самой свежей точке
                val anchorTime = timedPoints.last().timeMs
                val path = androidx.compose.ui.graphics.Path()
                var pathStarted = false

                for (i in timedPoints.indices) {
                    val p = timedPoints[i]

                    // X считается от правого края: чем старее точка, тем левее
                    val x = width - ((anchorTime - p.timeMs) * PIXELS_PER_MS).toFloat()

                    // Оптимизация: пропускаем точки, ушедшие за левый край экрана
                    if (x < -20f) continue

                    val y = height - (((p.value - minVal) / range) * height).coerceIn(0f, height)

                    if (!pathStarted) {
                        path.moveTo(x, y)
                        pathStarted = true
                    } else {
                        path.lineTo(x, y) // ← Только прямые линии для острых углов пилы
                    }
                }

                if (pathStarted) {
                    drawPath(
                        path = path,
                        color = if (isSelected) Color(0xFF00AA00) else Color(0xFF4A90E2),
                        style = Stroke(width = 2f)
                    )
                }
            }//////////////////////////

                }
            }
        }

