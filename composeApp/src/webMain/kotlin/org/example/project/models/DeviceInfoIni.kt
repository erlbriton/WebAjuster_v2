//DeviceInfoIni.kt

package org.example.project.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Перечисление всех типов данных, поддерживаемых контроллером.
 */
enum class ParameterType {
    TBit, TByte, TWord, TInteger, TPrmList, TUnknown
}

/**
 * Модель данных для отдельного параметра.
 */
data class ParameterData(
    val code: String,         // №
    val idName: String,       // Имя
    val description: String,  // Описание
    val dataType: String,     // Тип (строка из INI: "Byte", "Word" и т.д.)
    val modbusReg: String,    // Номер регистра (например, "0x2064.H")
    val unit: String,         // Ед. изм.
    val vars: Double = 1.0,

    val initialHexBase: String = "",
    val initialPhysBase: String = "",
    val initialHexCtrl: String = "",
    val initialPhysCtrl: String = "",
    val scaleName: String = ""
) {
    // Вычисляемое свойство: автоматически определяет тип при обращении к параметру
    val type: ParameterType
        get() = when (dataType.trim().uppercase()) {
            "BIT" -> ParameterType.TBit
            "BYTE" -> ParameterType.TByte
            "WORD" -> ParameterType.TWord
            "INTEGER", "INT" -> ParameterType.TInteger
            "PRMLIST" -> ParameterType.TPrmList
            else -> ParameterType.TUnknown
        }

    // Состояние, которое будет отслеживать Compose для обновления UI
    var hexBase by mutableStateOf(initialHexBase)
    var physBase by mutableStateOf(initialPhysBase)
    var hexCtrl by mutableStateOf(initialHexCtrl)
    var physCtrl by mutableStateOf(initialPhysCtrl)

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