package org.example.project.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni
import org.example.project.models.ParameterType

private val modbusMutex = Mutex()
object ModbusRepository {

    fun parseModbusRegister(rawReg: String): Int? {
        // 1. Принудительно в нижний регистр и убираем "r"
        var clean = rawReg.lowercase().removePrefix("r").trim()
        // 2. Отрезаем точку и всё, что после неё
        if (clean.contains(".")) {
            clean = clean.substringBefore(".")
        }
        // 3. ИСПРАВЛЕНО: Парсим строку как HEX (основание 16)!
        // Теперь строка "2000" превратится в число 8192 (0x2000).
        // В логе ты увидишь "Адрес: 8192", но в порт улетит правильный HEX "2000"!
        return clean.toIntOrNull(16)
    }

    fun parseModbusBit(modbusRegStr: String): Int? {
        if (!modbusRegStr.contains(".")) return null
        val bitStr = modbusRegStr.substringAfter(".").trim()
        return bitStr.toIntOrNull(16)
    }

    suspend fun performModbusOpros(device: DeviceInfoIni, varsMap: Map<String, Double>, onPortDetected: (String) -> Unit) {
        println("DEBUG: === ЗАПУСК ФИЗИЧЕСКОГО ОПРОСА КОНТРОЛЛЕРА ПО МОДБАС (0x03) ===")

        val allValidParams = mutableListOf<ParameterData>().apply {
            addAll(device.flashParameters.filter { parseModbusRegister(it.modbusReg) != null })
            addAll(device.cdParameters.filter { parseModbusRegister(it.modbusReg) != null })
            addAll(device.ramParameters.filter { parseModbusRegister(it.modbusReg) != null })
        }

        val regToParamsMap = mutableMapOf<Int, MutableList<ParameterData>>()
        allValidParams.forEach { param ->
            val regAddress = parseModbusRegister(param.modbusReg)!!
            regToParamsMap.getOrPut(regAddress) { mutableListOf() }.add(param)
        }

        val sortedAddresses = regToParamsMap.keys.sorted()
        val chunks = mutableListOf<MutableList<Int>>()
        var currentChunk = mutableListOf<Int>()

        for (addr in sortedAddresses) {
            if (currentChunk.isEmpty()) {
                currentChunk.add(addr)
            } else {
                // ИСПРАВЛЕНО: Склеиваем, только если регистры идут подряд И в текущем куске МЕНЬШЕ 10 регистров
                if (addr - currentChunk.last() == 1 && currentChunk.size < 10 && (addr - currentChunk.first() < 60)) {
                    currentChunk.add(addr)
                } else {
                    chunks.add(currentChunk)
                    currentChunk = mutableListOf(addr)
                }
            }
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)

        chunks.forEach { chunk ->
            val startAddress = chunk.first()
            val endAddress = chunk.last()
            val regCount = endAddress - startAddress + 1

            // Собираем HEX строку запроса
            val startAddrHex = startAddress.toString(16).padStart(4, '0')
            val regCountHex = regCount.toString(16).padStart(4, '0')
            val rawPacketHex = "0103" + startAddrHex + regCountHex

            val hexPacketString = WebModbusConverter.appendCRCToHex(rawPacketHex)
            val expectedSize = 5 + (regCount * 2)

            println("--> Отправка 0x03 (Адрес: $startAddress, Кол-во: $regCount), Ожидаем байт: $expectedSize")

            // Парсим ответ сразу в список List<Int> вместо ByteArray!
            val responseList: List<Int>? = try {
                val jsPromise = executeRealTransceiveJsAsync(hexPacketString, expectedSize)
                var hexResult: String? = null

                if (jsPromise != null) {
                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                        jsPromise.then { jsStr ->
                            if (jsStr != null) hexResult = jsStr.toString()
                            continuation.resumeWith(Result.success(Unit))
                            null
                        }.catch { err ->
                            println("❌ Ошибка промиса JS при опросе: ${err.toString()}")
                            continuation.resumeWith(Result.success(Unit))
                            null
                        }
                    }
                }

                if (!hexResult.isNullOrEmpty()) {
                    val list = mutableListOf<Int>()
                    var idx = 0
                    while (idx < hexResult!!.length) {
                        list.add(hexResult!!.substring(idx, idx + 2).toInt(16) and 0xFF)
                        idx += 2
                    }
                    list
                } else null
            } catch (e: Exception) {
                println("❌ Ошибка при выполнении разбора ответа: ${e.message}")
                null
            }

            // Обрабатываем ответ из List<Int>
            if (responseList != null && responseList.size == expectedSize) {
                val addressToValueMap = mutableMapOf<Int, Int>()
                val startReg = chunk.first()

                for (i in 0 until regCount) {
                    val currentRegAddress = startReg + i
                    val highByte = responseList[3 + i * 2]
                    val lowByte = responseList[3 + i * 2 + 1]
                    val combinedValue = (highByte shl 8) or lowByte
                    addressToValueMap[currentRegAddress] = combinedValue
                }

                chunk.forEach { addr ->
                    val raw16BitValue = addressToValueMap[addr] ?: 0

                    regToParamsMap[addr]?.forEach { param ->
                        param.hexCtrl = "x" + raw16BitValue.toString(16).uppercase().padStart(4, '0')

                        val scaleValue = varsMap[param.scaleName] ?: 1.0
                        val bitNum = parseModbusBit(param.modbusReg)

                        val finalPhysValue = if (bitNum != null) {
                            ((raw16BitValue shr bitNum) and 1).toDouble()
                        } else {
                            raw16BitValue * scaleValue
                        }

                        param.physCtrl = if (finalPhysValue % 1.0 == 0.0) {
                            finalPhysValue.toInt().toString()
                        } else {
                            finalPhysValue.toString()
                        }
                    }
                }

                try {
                    val portName = getWasmPortName().toString()
                    if (portName.isNotEmpty()) onPortDetected(portName)
                } catch (e: Exception) {}

            } else {
                println("❌ Ошибка Modbus: Устройство не ответило или размер пакета не совпал.")
            }
        }
        println("DEBUG: === СЕТЕВОЙ ОПРОС ЗАВЕРШЕН ===")
    }

    /**
     * Единственная точка входа для записи параметров.
     * Теперь сама определяет, нужно ли читать регистр перед записью (RMW).
     */
    suspend fun writeSingleParameter(param: ParameterData): Boolean = modbusMutex.withLock {
        val address = parseModbusRegister(param.modbusReg) ?: return false

        // 1. Подготовка значения (RMW)
        val hasExtension = param.modbusReg.contains(".")
        val finalHexValue = if (hasExtension) {
            val current = readSingleRegisterDirectly(address) ?: return false
            val newValue = param.hexCtrl.removePrefix("x").removePrefix("X").toIntOrNull(16) ?: 0
            val ext = param.modbusReg.substringAfter(".").uppercase()

            when (ext) {
                "H" -> (current and 0x00FF) or ((newValue and 0xFF) shl 8)
                "L" -> (current and 0xFF00) or (newValue and 0xFF)
                else -> {
                    val bitPos = ext.toIntOrNull() ?: 0
                    val bitState = if (param.hexCtrl.contains("1") || param.hexCtrl.lowercase() == "true") 1 else 0
                    if (bitState == 1) current or (1 shl bitPos) else current and (1 shl bitPos).inv()
                }
            }.toString(16).padStart(4, '0')
        } else {
            param.hexCtrl.removePrefix("x").removePrefix("X").padStart(4, '0')
        }

        // 2. Отправка записи
        val rawPacket = "0110" + address.toString(16).padStart(4, '0') + "000102" + finalHexValue
        val packetWithCrc = WebModbusConverter.appendCRCToHex(rawPacket)

        return@withLock executeRealTransceiveJsAsync(packetWithCrc, 8) != null
    }

    /**
     * Вспомогательная функция для чтения (ваша существующая)
     */
    private suspend fun readSingleRegisterDirectly(regAddress: Int): Int? {
        val startAddrHex = regAddress.toString(16).padStart(4, '0')
        val rawPacketHex = "0103" + startAddrHex + "0001"
        val hexPacketString = WebModbusConverter.appendCRCToHex(rawPacketHex)
        val expectedSize = 7

        val jsPromise = executeRealTransceiveJsAsync(hexPacketString, expectedSize) ?: return null
        var hexResult: String? = null

        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            jsPromise.then { jsStr ->
                if (jsStr != null) hexResult = jsStr.toString()
                continuation.resumeWith(Result.success(Unit))
                null
            }.catch {
                continuation.resumeWith(Result.success(Unit))
                null
            }
        }

        if (!hexResult.isNullOrEmpty() && hexResult!!.length == (expectedSize * 2)) {
            val high = hexResult!!.substring(6, 8).toInt(16)
            val low = hexResult!!.substring(8, 10).toInt(16)
            return (high shl 8) or low
        }
        return null
    }

    suspend fun writeByteToRegister(address: Int, modbusReg: String, newValueHex: String): Boolean {
        // 1. Читаем текущее значение регистра (16 бит)
        val currentRegValue = readSingleRegisterDirectly(address) ?: return false

        // 2. Парсим новое значение байта из строки
        val newByteValue = newValueHex.toIntOrNull(16) ?: 0

        // 3. Формируем новое 16-битное значение
        // Если modbusReg содержит ".H", меняем старший байт, если ".L" - младший
        val updatedValue = if (modbusReg.contains(".H", ignoreCase = true)) {
            (currentRegValue and 0x00FF) or ((newByteValue and 0xFF) shl 8)
        } else {
            (currentRegValue and 0xFF00) or (newByteValue and 0xFF)
        }

        // 4. Пишем готовое 16-битное слово обратно
        val hexValue = updatedValue.toString(16).padStart(4, '0')
        val rawPacket = "0110" + address.toString(16).padStart(4, '0') + "000102" + hexValue
        val packetWithCrc = WebModbusConverter.appendCRCToHex(rawPacket)

        // Используем ваш существующий метод выполнения команды
        return executeRealTransceiveJsAsync(packetWithCrc, 8) != null
    }

}