package org.example.project.logic

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni
import org.example.project.models.ParameterType

private val modbusMutex = Mutex()

object ModbusRepository {

    fun parseModbusRegister(rawReg: String): Int? {
        var clean = rawReg.lowercase().removePrefix("r").trim()
        if (clean.contains(".")) {
            clean = clean.substringBefore(".")
        }
        return clean.toIntOrNull(16)
    }

    fun parseModbusBit(modbusRegStr: String): Int? {
        if (!modbusRegStr.contains(".")) return null
        val bitStr = modbusRegStr.substringAfter(".").trim()
        return bitStr.toIntOrNull(16)
    }

    /**
     * Вспомогательная функция, которая преобразует асинхронный JS Promise
     * в честную приостановку Kotlin-корутины. Удерживает Mutex до полного ответа JS.
     */
    private suspend fun safeTransceiveAwait(packetHex: String, expectedSize: Int): String? {
        val jsPromise = executeRealTransceiveJsAsync(packetHex, expectedSize) ?: return null

        return suspendCancellableCoroutine { continuation ->
            jsPromise.then { jsStr ->
                val result = jsStr?.toString()
                if (continuation.isActive) continuation.resume(result)
                null
            }.catch { err ->
                println("❌ Ошибка на стороне WebSerial JS: ${err.toString()}")
                if (continuation.isActive) continuation.resume(null)
                null
            }
        }
    }

    suspend fun performModbusOpros(device: DeviceInfoIni, varsMap: Map<String, Double>, onPortDetected: (String) -> Unit) {
        // Опрос тоже должен монопольно владеть портом
        modbusMutex.withLock {
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

                val startAddrHex = startAddress.toString(16).padStart(4, '0')
                val regCountHex = regCount.toString(16).padStart(4, '0')
                val rawPacketHex = "0103" + startAddrHex + regCountHex

                val hexPacketString = WebModbusConverter.appendCRCToHex(rawPacketHex)
                val expectedSize = 5 + (regCount * 2)

                println("--> Отправка 0x03 (Адрес: $startAddress, Кол-во: $regCount), Ожидаем байт: $expectedSize")

                val responseList: List<Int>? = try {
                    // Используем безопасное ожидание ответа JS
                    val hexResult = safeTransceiveAwait(hexPacketString, expectedSize)

                    if (!hexResult.isNullOrEmpty()) {
                        val list = mutableListOf<Int>()
                        var idx = 0
                        while (idx < hexResult.length) {
                            list.add(hexResult.substring(idx, idx + 2).toInt(16) and 0xFF)
                            idx += 2
                        }
                        list
                    } else null
                } catch (e: Exception) {
                    println("❌ Ошибка при выполнении разбора ответа: ${e.message}")
                    null
                }

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

                            val cleanScaleName = param.scaleName.trim()
                            val scaleValue = if (cleanScaleName.isEmpty()) {
                                1.0
                            } else {
                                // 1. Пробуем распарсить как прямое число (заменяя запятую на точку для Kotlin)
                                val directNumber = cleanScaleName.replace(",", ".").toDoubleOrNull()

                                if (directNumber != null) {
                                    // Если это число (например, 1 или 0.001) — берем его напрямую!
                                    directNumber
                                } else {
                                    // 2. Если это текст (например, CINScale) — ищем в карте [vars], как и раньше
                                    val exactKey = varsMap.keys.firstOrNull { it.equals(cleanScaleName, ignoreCase = true) }
                                    if (exactKey != null) varsMap[exactKey] ?: 1.0 else 1.0
                                }
                            }
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

                // Небольшой тайм-аут между чанками опроса
                delay(15)
            }
            println("DEBUG: === СЕТЕВОЙ ОПРОС ЗАВЕРШЕН ===")
        }
    }

    /**
     * Единственная точка входа для записи параметров.
     * Полностью синхронизирована на уровне корутин.
     */
    suspend fun writeSingleParameter(param: ParameterData, varsMap: Map<String, Double>): Boolean {
        return modbusMutex.withLock {
            val address = parseModbusRegister(param.modbusReg) ?: return@withLock false
            val hasExtension = param.modbusReg.contains(".")

            // ЧИТАЕМ СЫРОЙ HEX ИЗ ЯЧЕЙКИ КОНТРОЛЛЕРА (ТАМ УЖЕ ВСЕ РАЗДЕЛЕНО НА VARS)
            val currentHexInCell = param.hexCtrl.replace("0x", "").replace("x", "").trim()
            val raw16BitValue = currentHexInCell.toIntOrNull(16) ?: 0

            val finalHexValue = if (hasExtension) {
                // Логика записи в отдельные биты/байты (Read-Modify-Write)
                val current = readSingleRegisterDirectlyInternal(address) ?: return@withLock false
                val ext = param.modbusReg.substringAfter(".").uppercase()

                when (ext) {
                    "H" -> {
                        val rawVal = raw16BitValue and 0xFF
                        (current and 0x00FF) or (rawVal shl 8)
                    }
                    "L" -> {
                        val rawVal = raw16BitValue and 0xFF
                        (current and 0xFF00) or rawVal
                    }
                    else -> {
                        val bitPos = ext.toIntOrNull() ?: 0
                        val bitState = if (param.physCtrl == "1" || param.physCtrl.lowercase() == "true") 1 else 0
                        if (bitState == 1) current or (1 shl bitPos) else current and (1 shl bitPos).inv()
                    }
                }.toString(16).padStart(4, '0')
            } else {
                // ДЛЯ ОБЫЧНОГО РЕГИСТРА (как p10900 или CINZ):
                // Просто отправляем исходное HEX-число. Из 3000 улетит строго "0BB8"
                raw16BitValue.toString(16).padStart(4, '0')
            }

            // Формирование стандартного пакета Modbus (Функция 0x10) и отправка в порт
            val rawPacket = "0110" + address.toString(16).padStart(4, '0') + "000102" + finalHexValue
            val packetWithCrc = WebModbusConverter.appendCRCToHex(rawPacket)

            val writeResult = safeTransceiveAwait(packetWithCrc, 8)
            delay(40)

            return@withLock writeResult != null
        }
    }

    private fun getScaleValue(scaleName: String, varsMap: Map<String, Double>): Double {
        val cleanName = scaleName.trim()
        if (cleanName.isEmpty()) return 1.0
        if (varsMap.containsKey(cleanName)) return varsMap[cleanName] ?: 1.0
        val foundKey = varsMap.keys.firstOrNull { it.equals(cleanName, ignoreCase = true) }
        if (foundKey != null) return varsMap[foundKey] ?: 1.0
        return cleanName.replace(",", ".").toDoubleOrNull() ?: 1.0
    }

    /**
     * Внутреннее чтение, вызываемое строго внутри существующего lock-а writeSingleParameter
     */
    private suspend fun readSingleRegisterDirectlyInternal(regAddress: Int): Int? {
        val startAddrHex = regAddress.toString(16).padStart(4, '0')
        val rawPacketHex = "0103" + startAddrHex + "0001"
        val hexPacketString = WebModbusConverter.appendCRCToHex(rawPacketHex)
        val expectedSize = 7

        val hexResult = safeTransceiveAwait(hexPacketString, expectedSize) ?: return null

        if (hexResult.length == (expectedSize * 2)) {
            val high = hexResult.substring(6, 8).toInt(16)
            val low = hexResult.substring(8, 10).toInt(16)
            return (high shl 8) or low
        }
        return null
    }

    /**
     * Публичный метод для одиночного чтения (например, из UI)
     */
    private suspend fun readSingleRegisterDirectly(regAddress: Int): Int? {
        return modbusMutex.withLock {
            readSingleRegisterDirectlyInternal(regAddress)
        }
    }

    /**
     * Безопасная запись байта в регистр с автоблокировкой
     */
    suspend fun writeByteToRegister(address: Int, modbusReg: String, newValueHex: String): Boolean {
        return modbusMutex.withLock {
            // 1. Читаем текущее значение регистра используя внутренний метод без лока
            val currentRegValue = readSingleRegisterDirectlyInternal(address) ?: return@withLock false

            // 2. Парсим новое значение байта
            val newByteValue = newValueHex.toIntOrNull(16) ?: 0

            // 3. Формируем новое 16-битное значение
            val updatedValue = if (modbusReg.contains(".H", ignoreCase = true)) {
                (currentRegValue and 0x00FF) or ((newByteValue and 0xFF) shl 8)
            } else {
                (currentRegValue and 0xFF00) or (newByteValue and 0xFF)
            }

            // 4. Пишем готовое 16-битное слово обратно
            val hexValue = updatedValue.toString(16).padStart(4, '0')
            val rawPacket = "0110" + address.toString(16).padStart(4, '0') + "000102" + hexValue
            val packetWithCrc = WebModbusConverter.appendCRCToHex(rawPacket)

            val writeResult = safeTransceiveAwait(packetWithCrc, 8)

            delay(20)

            return@withLock writeResult != null
        }
    }
}