package org.example.project.models

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class ParameterData(
    val code: String,                // Например, p11200
    var idName: String,              // Обозначение
    var description: String,         // Описание
    var unit: String,                // Ед. Изм.

    // Свойства из твоего окна "Свойства параметра"
    var scale: String = "0,1",
    var dependency: String = "",
    var factor: String = "1",
    var correction: String = "1",

    // Значения для таблицы
    initialHexBase: String = "0000",
    initialPhysBase: String = "0.0",
    initialHexCtrl: String = "0000",
    initialPhysCtrl: String = "0.0"
) {
    // Делаем значения реактивными, чтобы таблица обновлялась при вводе
    var hexBase by mutableStateOf(initialHexBase)
    var physBase by mutableStateOf(initialPhysBase)
    var hexCtrl by mutableStateOf(initialHexCtrl)
    var physCtrl by mutableStateOf(initialPhysCtrl)
}