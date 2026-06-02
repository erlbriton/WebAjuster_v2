// OscilloscopeRightWindow.kt

package org.example.project.oscilloscope

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun OscilloscopeRightWindow(
    paramCode: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    // Чистый Compose Box. Он рендерится внутри того же Canvas, что и всё приложение.
    // Он физически не может вызвать артефакты или перекрыть Хедер,
    // потому что подчиняется правилам отрисовки Compose.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)) // Наш темный фон осциллографа
    )
}