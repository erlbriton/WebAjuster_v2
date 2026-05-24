
//OscilloscopeState.kt
package org.example.project.oscilloscope

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

data class RamDataPoint(
    val timestampMs: Long,
    val value: Double
)

class SignalDisplayConfig(
    val code: String,
    val name: String,
    val description: String,
    val dataType: String,
    var isVisible: Boolean = true,
    var scaleY: Float = 1.0f,
    var offsetY: Float = 0f,
    var colorHex: Long = 0xFF00FF00
) {
    val points = mutableListOf<RamDataPoint>()
}

class OscilloscopeState {
    var isWindowOpen by mutableStateOf(false)     // Управляет показом окна
    var isPaused by mutableStateOf(false)         // Пауза графика
    var viewDurationMs by mutableStateOf(10000L)  // Развертка X (10 сек)
    var viewStartTimeMs by mutableStateOf(0L)     // Сдвиг по времени

    val activeSignals = mutableStateListOf<SignalDisplayConfig>()
}