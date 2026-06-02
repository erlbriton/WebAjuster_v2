// OscilloscopeRightWindow.kt

package org.example.project.oscilloscope

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun OscilloscopeRightWindow(
    paramCode: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isSelected) Color(0xFFE2EDF8) else Color(0xFFFAFAFA))
    ) {
        // Создаем пустой Box, который Compose отрендерит на экране.
        // Задаем ему фоновый цвет Pixi-осциллографа.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
        )

        // Как только окно появляется, Kotlin-код напрямую создает div в DOM-дереве страницы
        LaunchedEffect(Unit) {
            try {
                val document = kotlinx.browser.document

                // Проверяем, вдруг контейнер уже был создан ранее
                var container = document.getElementById("oscilloscope-container") as? org.w3c.dom.HTMLElement

                if (container == null) {
                    container = document.createElement("div") as org.w3c.dom.HTMLElement
                    container.id = "oscilloscope-container"
                    container.style.width = "100%"
                    container.style.height = "100%"
                    container.style.position = "absolute"
                    container.style.top = "0"
                    container.style.left = "0"

                    // Прикрепляем контейнер прямо к body страницы поверх или внутрь нужной области
                    document.body?.appendChild(container)
                }
            } catch (e: Exception) {
                println("Ошибка инициализации DOM контейнера: ${e.message}")
            }
        }
    }
}