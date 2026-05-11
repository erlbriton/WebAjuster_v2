package org.example.project.models

/**
 * Класс данных для хранения информации об устройстве из INI-файла
 */
data class DeviceInfoIni(
    val id: String = "",       // Тот самый ID, который выводится в 3-й строке
    val name: String = "",     // Имя устройства
    val path: String = "",     // Путь к файлу настроек
    val location: String = ""  // Место установки
)