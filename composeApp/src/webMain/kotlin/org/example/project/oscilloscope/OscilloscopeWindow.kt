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
        val targetRegisters = intArrayOf(0, 1, 2, 3, 4)
        val baudRate = 115200
        viewModel.startJsOscilloscope(targetRegisters, baudRate)

        viewModel.sendParametersToJS()

        // Запускаем периодическую отправку тестовых значений
        val job = kotlinx.browser.window.setInterval({
            viewModel.sendTestValuesToJS()
            null as JsAny? // Возвращаем null, чтобы удовлетворить требование JsAny?
        }, 1000) // Каждую секунду

        onDispose {
            viewModel.stopJsOscilloscope()
            kotlinx.browser.window.clearInterval(job) // Останавливаем таймер
        }
    }

    // Просто отображаем наше новое окно, внутри которого развернется HTML-контейнер для PixiJS
    OscilloscopeRightWindow(
        paramCode = viewModel.selectedCode ?: "",
        isSelected = true,
        modifier = modifier.fillMaxSize()
    )
}