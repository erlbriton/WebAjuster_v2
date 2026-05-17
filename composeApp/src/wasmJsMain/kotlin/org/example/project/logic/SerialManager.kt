package org.example.project.logic

// Вспомогательное расширение для красивого логирования HEX в стиле Kotlin
private fun ByteArray.toHexString() =
    joinToString(" ") { it.toInt().and(0xFF).toString(16).padStart(2, '0').uppercase() }

actual suspend fun findSerialPort(data: ByteArray) {}

actual suspend fun readDeviceIdentification() {
    println("=== [Kotlin Architecture] Запуск команды 0x11 ===")

    // 1. Формируем сырое тело команды (Адрес 0x01, Функция 0x11)
    val rawPacket = byteArrayOf(0x01, 0x11)

    // 2. Считаем и прикрепляем правильный Modbus CRC16 через наш синглтон
    val fullPacket = ModbusUtils.appendCRC(rawPacket)
    println("--> Отправка пакета (Kotlin): ${fullPacket.toHexString()}")

    // 3. Отправляем в порт через изолированный движок (ожидаем паспорт на 39 байт)
    val response = SerialEngine.transceive(fullPacket, 39)

    // 4. Обрабатываем результат на чистом Kotlin
    if (response != null && response.isNotEmpty()) {
        println("🔥 ОТВЕТ ПОЛУЧЕН В KOTLIN (HEX): ${response.toHexString()}")

        // Декодируем текстовый паспорт устройства (пропускаем первые 3 байта заголовка и 2 байта CRC)
        val asciiResult = StringBuilder()
        for (i in 3 until response.size - 2) {
            val byteValue = response[i].toInt() and 0xFF
            if (byteValue in 32..126) {
                asciiResult.append(byteValue.toChar())
            } else {
                asciiResult.append(".")
            }
        }
        println("📝 Расшифровка паспорта ASCII: $asciiResult")
    } else {
        println("❌ Ошибка: Буфер пуст или превышен таймаут ответа.")
    }
}