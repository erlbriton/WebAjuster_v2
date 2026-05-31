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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.example.project.logic.ModbusRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.project.logic.WebModbusConverter
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine


@JsFun("() => performance.now()")
external fun performanceNow(): Double

@JsFun("() => new Promise(r => requestAnimationFrame(r))")
external fun jsRequestAnimationFrame(): kotlin.js.JsAny

suspend fun awaitAnimationFrame() {
    suspendCancellableCoroutine { continuation ->
        jsRequestAnimationFrame()
            .unsafeCast<kotlin.js.Promise<kotlin.js.JsAny>>()
            .then  { if (continuation.isActive) continuation.resume(Unit); null }
            .catch { if (continuation.isActive) continuation.resume(Unit); null }
    }
}

@JsFun("() => new Promise(r => requestAnimationFrame(r))")
private external fun jsRequestAnimationFrame(): kotlin.js.JsAny

private suspend fun awaitAnimationFrame() {
    suspendCancellableCoroutine { continuation ->
        jsRequestAnimationFrame()
            .unsafeCast<kotlin.js.Promise<kotlin.js.JsAny>>()
            .then  { if (continuation.isActive) continuation.resume(Unit); null }
            .catch { if (continuation.isActive) continuation.resume(Unit); null }
    }
}

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
    val maxVal = 1000f
    val range = maxVal - minVal

    val stepX = 3.5f
    val maxPoints = 600
    val windowDurationMs = 3_000.0 // 10 секунд

    // Буфер для сглаживания (скользящее среднее)
    val smoothingBuffer = remember { mutableStateListOf<Float>() }
    val smoothingWindow = 1 // ← ИЗМЕНЕНО: отключаем сглаживание
    val timedPoints = remember { mutableStateListOf<TimedValue>() }


    // ==========================================
    // ШАГ 59: АВТОМАТИЧЕСКИЙ ОПРОС ЖЕЛЕЗА 50 ГЦ
    // ==========================================
    LaunchedEffect(paramCode) {
        val targetAddress = 0x002D

        while (isActive) {
            // requestAnimationFrame вместо delay(20) — не throttlится на Windows
            awaitAnimationFrame()

            val realValue = org.example.project.logic.ModbusRepository.readRegisterFast(targetAddress)
            val now = performanceNow()


            timedPoints.add(TimedValue(now, realValue))

            val cutoff = now - windowDurationMs
            while (timedPoints.isNotEmpty() && timedPoints[0].timeMs < cutoff) {
                timedPoints.removeAt(0)
            }
            if (timedPoints.size > maxPoints) timedPoints.removeAt(0)
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

