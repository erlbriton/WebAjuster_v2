package org.example.project.logic

import org.example.project.utils.SerialEngine
import org.example.project.jsinterop.jsOscilloPush

// === ШАГ 1: ИСПРАВЛЕННЫЙ ВАРИАНТ JS ДЛЯ WEBPACK (Используем самовызывающуюся функцию) ===
fun getBrowserCurrentPortName(): String =
    js("(() => { if (typeof currentPort !== 'undefined' && currentPort && currentPort.getInfo) { const info = currentPort.getInfo(); return info.usbProductId ? 'COM' + (info.usbProductId % 10 + 1) : ''; } return ''; })()")

// Вспомогательное расширение для красивого логирования HEX в стиле Kotlin
private fun ByteArray.toHexString() =
    joinToString(" ") { it.toInt().and(0xFF).toString(16).padStart(2, '0').uppercase() }

actual suspend fun findSerialPort(data: ByteArray) {}

actual suspend fun readDeviceIdentification() {

    // 1. Формируем сырое тело команды (Адрес 0x01, Функция 0x11)
    val rawPacket = byteArrayOf(0x01, 0x11)

    // 2. Считаем и прикрепляем правильный Modbus CRC16 через наш синглтон
    val fullPacket = ModbusUtils.appendCRC(rawPacket)

    // 3. Отправляем в порт через изолированный движок (ожидаем паспорт на 39 байт)
    val response = SerialEngine.transceive(fullPacket, 39)

    // 4. Обрабатываем результат на чистом Kotlin
    if (response != null && response.isNotEmpty()) {

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

        val resultText = asciiResult.toString()

        // === 1. ОТКРЫВАЕМ КРАСИВОЕ COMPOSE-ОКНО ===
        org.example.project.viewmodels.MainViewModel.instance.openHardwareDialog(resultText)

        // === 2. ОБНОВЛЯЕМ ИМЯ ПОРТА НА ЭКРАНЕ В LINE_TWO_TABLE ===
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            val vm = org.example.project.viewmodels.MainViewModel.instance

            // Вызываем нашу безопасную JS функцию
            val realBrowserPort: String = try {
                val jsPortName = getBrowserCurrentPortName()
                if (jsPortName.isNotEmpty()) jsPortName else vm.selectedComPort
            } catch (e: Exception) {
                vm.selectedComPort
            }

            // Если имя пустое, ставим статус "Connected", чтобы пользователь видел, что связь есть
            val connectedPort = if (realBrowserPort.isNotEmpty()) realBrowserPort else "Connected"

            // Добавляем этот реальный порт в список доступных
            if (!vm.availableComPorts.contains(connectedPort)) {
                vm.availableComPorts.add(connectedPort)
            }

            // Записываем его как выбранный, чтобы он мгновенно отобразился в таблице настроек связи
            vm.selectedComPort = connectedPort
        }

    } else {
        println("❌ Ошибка: Буфер пуст или превышен таймаут ответа.")
    }
    // Вызываем чтение регистра 0x002D и отправку в осциллограф
   // readRegister002D()
}//////////////////////////////////////////////////////////////////////


actual suspend fun readRegister002D() {
    println("🔴 readRegister002D() вызвана!")

    try {
        val rawPacket = byteArrayOf(0x01, 0x03, 0x00, 0x2D, 0x00, 0x02)
        val fullPacket = ModbusUtils.appendCRC(rawPacket)

        println("🔴 Запускаю цикл опроса...")

        while (true) {
            println("🔴 Отправляю запрос...")
            val response = SerialEngine.transceive(fullPacket, 9)

            if (response != null && response.size >= 7) {
                val reg1 = ((response[3].toInt() and 0xFF) shl 8) or (response[4].toInt() and 0xFF)
                val reg2 = ((response[5].toInt() and 0xFF) shl 8) or (response[6].toInt() and 0xFF)

                println("📊 Регистр 0x002D: reg1=$reg1, reg2=$reg2")

                jsOscilloPush("testId", reg1.toDouble().coerceIn(0.0, 1000.0), 0.0, 1000.0)
                jsOscilloPush("testId2", reg2.toDouble().coerceIn(0.0, 1000.0), 0.0, 1000.0)
            } else {
                println("❌ Ошибка чтения регистра 0x002D")
            }

            kotlinx.coroutines.delay(40)
        }
    } catch (e: Exception) {
        println("❌ КРИТИЧЕСКАЯ ОШИБКА В readRegister002D: ${e.message}")
    }
}