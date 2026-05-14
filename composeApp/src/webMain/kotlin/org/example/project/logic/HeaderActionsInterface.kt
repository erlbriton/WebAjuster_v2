
package org.example.project.logic

import org.example.project.models.DeviceInfoIni

/**
 * Интерфейс для обработки всех событий заголовка.
 * Вынесен в отдельный пакет 'actions' для удобства масштабирования.
 */
interface HeaderActionsInterface {
    // Операции с устройствами
    fun onUpdate()
    fun onSearch()

    fun onExel()
    fun onOpenOscillograph()

    // Системные операции
    fun onTerminalOpen()
    fun onFileOration()

    fun onBlackBox()
    fun onHelp(topic: String)

    // Выбор памяти
    fun onMemoryChanged(type: String)

    // Работа с файловой системой (вызывается из UI)
    fun onPickFileRequest()
    fun onPickDirectoryRequest()

    // Коллбэк для возврата данных обратно в UI или логику
    fun onDeviceDataLoaded(info: DeviceInfoIni)
}
