package org.example.project

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Вместо поиска объекта body, мы просто говорим Compose:
    // "Рисуй внутри HTML-элемента с id='ComposeTarget'"
    ComposeViewport(viewportContainerId = "ComposeTarget") {
        App()
    }
}