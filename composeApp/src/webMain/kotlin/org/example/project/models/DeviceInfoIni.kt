package org.example.project.models

/**
 * Модель данных для отдельного параметра из секций [RAM], [FLASH] или [CD].
 * Содержит разобранные части строки после знака "=" и разделения символом "/".
 */
/**
 * Расширенная модель параметра для таблицы.
 */
data class ParameterData(
    val code: String,         // Столбец "№" (например, p10000)
    val idName: String,       // Столбец "Имя" (например, IstStart)
    val description: String,  // Столбец "Описание" (бывшее "Значение")
    val dataType: String,     // Тип (TWORD) - пока не выводим
    val modbusReg: String,    //Номер регистра Modbus (например, r2000)м
    val unit: String,         // Столбец "Ед. изм." (A)
    val vars: Double = 1.0,


    // Новые поля для данных
    val hexBase: String = "",    // Столбец "hex" (База)
    val physBase: String = "",   // Столбец "Physical" (База)
    val hexCtrl: String = "",    // Столбец "hex" (Контроллер) - пока пусто
    val physCtrl: String = ""    // Столбец "Physical" (Контроллер) - пока пусто
)

data class DeviceInfoIni(
    val fileName: String,
    val id: String,
    val location: String,
    val Description: String,
    val LastDateTime: String,
    val ramParameters: List<ParameterData> = emptyList(),
    val flashParameters: List<ParameterData> = emptyList(),
    val cdParameters: List<ParameterData> = emptyList()
)

data class PortData(
    val id: String,
    val name: String,
    val status: String
)