package org.example.project.oscilloscope

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
//import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import org.example.project.logic.ModbusRepository
import kotlin.js.JsAny

// ─────────────────────────────────────────────────────────────────────────────
// JS-функции для работы с PixiJS через window.*
// ─────────────────────────────────────────────────────────────────────────────

@JsFun("(code, canvasId, minVal, maxVal) => { if (window.oscilloInit) window.oscilloInit(code, canvasId, minVal, maxVal); }")
private external fun jsOscilloInit(code: String, canvasId: String, minVal: Double, maxVal: Double)

@JsFun("(code, value, minVal, maxVal) => { if (window.oscilloPush) window.oscilloPush(code, value, minVal, maxVal); }")
private external fun jsOscilloPush(code: String, value: Double, minVal: Double, maxVal: Double)

@JsFun("(canvasId, containerId) => { if (window.oscilloCreate) window.oscilloCreate(canvasId, containerId); }")
private external fun jsOscilloCreate(canvasId: String, containerId: String)

@JsFun("(canvasId, code) => { if (window.oscilloRemove) window.oscilloRemove(canvasId, code); }")
private external fun jsOscilloRemove(canvasId: String, code: String)

// ─────────────────────────────────────────────────────────────────────────────
// Composable — заменяет OscilloscopeRightWindow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PixiOscilloscopeCell(
    paramCode: String,
    modbusAddress: Int,     // адрес регистра Modbus для чтения
    minVal: Double = 0.0,
    maxVal: Double = 1100.0,
    modifier: Modifier = Modifier
) {
    val canvasId = remember(paramCode) { "oscillo_${paramCode.replace(Regex("[^a-zA-Z0-9]"), "_")}" }

    // Создаём canvas в DOM при появлении composable
    DisposableEffect(canvasId) {
        jsOscilloCreate(canvasId, canvasId + "_container")
        jsOscilloInit(paramCode, canvasId, minVal, maxVal)
        onDispose {
            jsOscilloRemove(canvasId, paramCode)
        }
    }

    // Читаем данные по Modbus и передаём в PixiJS
    LaunchedEffect(paramCode, modbusAddress) {
        var lastValue = 0.0
        var consecutiveErrors = 0

        while (isActive) {
            val readStart = jsPerformanceNow()
            val value = ModbusRepository.readRegisterFast(modbusAddress)
            val elapsed = (jsPerformanceNow() - readStart).toLong()

            when {
                value != null -> {
                    lastValue = value.toDouble()
                    consecutiveErrors = 0
                    // Передаём значение в PixiJS — он сам перерисует
                    jsOscilloPush(canvasId, lastValue, minVal, maxVal)
                    val remaining = 20L - elapsed
                    if (remaining > 0) delay(remaining)
                }
                else -> {
                    consecutiveErrors++
                    if (consecutiveErrors >= 3) {
                        delay(200)
                        consecutiveErrors = 0
                    } else {
                        // Повторяем последнее значение чтобы не было разрывов
                        jsOscilloPush(canvasId, lastValue, minVal, maxVal)
                        delay(20)
                    }
                }
            }
        }
    }

    // Compose рендерит пустой Box — реальный рендер делает PixiJS поверх
    androidx.compose.foundation.layout.Box(
        modifier = modifier
    )
}

@JsFun("() => performance.now()")
private external fun jsPerformanceNow(): Double