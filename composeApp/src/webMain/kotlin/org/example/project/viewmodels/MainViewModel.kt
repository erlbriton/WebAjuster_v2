package org.example.project.viewmodel

import androidx.compose.runtime.*

class MainViewModel {
    var typeMechanism by mutableStateOf("Не указан")
    var dateSet by mutableStateOf("01.01.2024")
    // НОВОЕ ПОЛЕ:
    var installationLocation by mutableStateOf("Цех №1")

    // Сюда позже добавим логику кнопок "Сохранить" и "Обновить"
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")

}