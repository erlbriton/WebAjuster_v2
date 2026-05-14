package org.example.project.logic

/**
 * Функция для подключения к устройству.
 * Возвращает имя выбранного порта или описание ошибки.
 */

expect suspend fun findSerialPort(data: ByteArray)
expect suspend fun readDeviceIdentification()