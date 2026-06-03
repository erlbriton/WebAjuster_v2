package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.example.project.ui.screens.MainScreen

@Composable
fun App() {
    MaterialTheme {
        // Теперь здесь живет твой основной экран
        MainScreen()

    }
}