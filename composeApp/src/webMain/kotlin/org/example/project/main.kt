package org.example.project

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // ComposeViewport — это точка входа для Wasm
    ComposeViewport(document.body!!) {
        App() // Вызывает функцию App() из файла App.kt
    }
}