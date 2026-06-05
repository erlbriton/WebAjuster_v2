// OscilloscopeRightWindow.kt

package org.example.project.oscilloscope

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

// ====================================================================
// JS-МОСТ ДЛЯ УПРАВЛЕНИЯ ЛЕВОЙ ПАНЕЛЬЮ
// ====================================================================

@JsFun("() => { if (window.showLeftPanel) window.showLeftPanel(); }")
external fun callJsShowLeftPanel()

@JsFun("() => { if (window.hideLeftPanel) window.hideLeftPanel(); }")
external fun callJsHideLeftPanel()

// ====================================================================

@Composable
fun OscilloscopeRightWindow(
    paramCode: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    // Вызываем JS для показа новой HTML-панели
    DisposableEffect(Unit) {
        callJsShowLeftPanel()

        // Передаём параметры из Kotlin в JS для построения таблицы
        viewModel.sendParametersToJS()

        onDispose {
            callJsHideLeftPanel()
        }
    }

    // Пустой Box (занимает место, но невидим)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    )
}