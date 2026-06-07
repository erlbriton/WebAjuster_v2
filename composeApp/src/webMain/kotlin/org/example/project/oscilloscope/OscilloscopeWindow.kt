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

        // ❌ ТЕСТОВЫЕ ДАННЫЕ ОТКЛЮЧЕНЫ
        // val job = kotlinx.browser.window.setInterval({
        //     viewModel.sendTestValuesToJS()
        //     null as JsAny?
        // }, 20)

        val job: Int? = null // job объявлена, но равна null

        onDispose {
            viewModel.stopJsOscilloscope()
            // Не вызываем clearInterval, так как job = null
            // if (job != null) kotlinx.browser.window.clearInterval(job)
        }
    }

    // Просто отображаем наше новое окно, внутри которого развернется HTML-контейнер для PixiJS
    OscilloscopeRightWindow(
        paramCode = viewModel.selectedCode ?: "",
        isSelected = true,
        modifier = modifier.fillMaxSize()
    )
}