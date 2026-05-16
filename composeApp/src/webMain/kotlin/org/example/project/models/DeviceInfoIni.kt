//DeviceInfoIni.kt

package org.example.project.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Модель данных для отдельного параметра из секций [RAM], [FLASH] или [CD].
 * Содержит разобранные части строки после знака "=" и разделения символом "/".
 */
/**
 * Расширенная модель параметра для таблицы.
 */
data class ParameterData(
    val code: String,         // №
    val idName: String,       // Имя
    val description: String,  // Описание
    val dataType: String,     // Тип
    val modbusReg: String,    // Номер регистра
    val unit: String,         // Ед. изм.
    val vars: Double = 1.0,

    // Эти поля должны быть VAR, чтобы их можно было изменять при вводе в таблицу
    // Мы задаем им начальные значения через конструктор
    val initialHexBase: String = "",
    val initialPhysBase: String = "",
    val initialHexCtrl: String = "",
    val initialPhysCtrl: String = "",
    val scaleName: String = ""
) {
    // А здесь мы создаем переменные, которые Compose будет "видеть" и перерисовывать
    var hexBase by mutableStateOf(initialHexBase)
    var physBase by mutableStateOf(initialPhysBase)
    var hexCtrl by mutableStateOf(initialHexCtrl)
    var physCtrl by mutableStateOf(initialPhysCtrl)

    // Состояние выделения строки
    var isSelected by mutableStateOf(false)
}

data class DeviceInfoIni(
    val fileName: String,
    val id: String,
    val location: String,
    val Description: String,
    val LastDateTime: String,
    val ramParameters: List<ParameterData> = emptyList(),
    val flashParameters: List<ParameterData> = emptyList(),
    val cdParameters: List<ParameterData> = emptyList(),
    val varsMap: Map<String, Double> = emptyMap()
)

data class PortData(
    val id: String,
    val name: String,
    val status: String
)