// OscilloscopeWindow.kt

package org.example.project.oscilloscope

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import org.example.project.viewmodels.MainViewModel

@Composable
fun OscilloscopeWindow(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // При открытии окна осциллографа даем команду JS запустить высокоскоростной опрос.
    // Передаем массив нужных регистров (например, первые 5 регистров) и скорость порта.
    DisposableEffect(Unit) {
        // 1. Запуск опроса регистров
        val targetRegisters = intArrayOf(0, 1, 2, 3, 4)
        val baudRate = 115200
        viewModel.startJsOscilloscope(targetRegisters, baudRate)

        // 2. НОВОЕ: Передаём параметры в JS для построения левой панели
        viewModel.sendParametersToJS()

        onDispose {
            viewModel.stopJsOscilloscope()
        }
    }

    // Просто отображаем наше новое окно, внутри которого развернется HTML-контейнер для PixiJS
    OscilloscopeRightWindow(
        paramCode = viewModel.selectedCode ?: "",
        isSelected = true,
        modifier = modifier.fillMaxSize()
    )
}