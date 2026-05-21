package org.example.project.logic

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import org.example.project.logic.ModbusRepository.parseModbusRegister
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

                // ЖЕЛЕЗНО ВКЛЮЧАЕМ ВТОРОЙ РЕГИСТР В СПИСОК ОПРОСА ДЛЯ 32-БИТНЫХ ТИПОВ
                if (param.dataType.equals("TFloat", ignoreCase = true)) {
                    regToParamsMap.getOrPut(regAddress + 1) { mutableListOf() }
                }
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
                    // 1. Набиваем карту сырых регистров из буфера ответа
                    val addressToValueMap = mutableMapOf<Int, Int>()
                    val startReg = chunk.first()

                    for (i in 0 until regCount) {
                        val currentRegAddress = startReg + i
                        val highByte = responseList[3 + i * 2]
                        val lowByte = responseList[3 + i * 2 + 1]
                        addressToValueMap[currentRegAddress] = (highByte shl 8) or lowByte
                    }

                    // 2. СТРОИМ КАРТУ ПО УНИКАЛЬНЫМ КОДАМ ПАРАМЕТРОВ (p16600, p10900)
                    // Собираем все параметры, которые входят в текущую пачку (chunk) регистров
                    val paramsInThisChunk = chunk.flatMap { regToParamsMap[it] ?: emptyList() }.distinctBy { it.code }

                    // 3. ОБРАБАТЫВАЕМ КАЖДЫЙ ПАРАМЕТР РОВНО ОДИН РАЗ
                    paramsInThisChunk.forEach { param ->
                        val isTFloat = param.dataType.equals("TFloat", ignoreCase = true)
                        val baseAddress = parseModbusRegister(param.modbusReg)!!

                        // Читаем из карты ровно столько, сколько требует тип
                        val rawValue: Long = if (isTFloat) {
                            val r1 = addressToValueMap[baseAddress] ?: 0       // Младшее слово (Low Word)
                            val r2 = addressToValueMap[baseAddress + 1] ?: 0   // Старшее слово (High Word)

                            // Склеиваем 32-битное число ((r2 << 16) | r1)
                            ((r2 and 0xFFFF).toLong() shl 16) or (r1 and 0xFFFF).toLong()
                        } else {
                            (addressToValueMap[baseAddress] ?: 0).toLong()     // Обычные 16-битные
                        }

                        // Записываем HEX (8 символов для TFloat, 4 символа для 16-бит)
                        if (isTFloat) {
                            param.hexCtrl = "x" + rawValue.toString(16).uppercase().padStart(8, '0')
                        } else {
                            param.hexCtrl = "x" + (rawValue and 0xFFFF).toString(16).uppercase().padStart(4, '0')
                        }

                        // 1. УМНЫЙ ПОИСК КОЭФФИЦИЕНТА ШКАЛЫ С ОЧИСТКОЙ КЛЮЧЕЙ КАРТЫ
                        val cleanScaleName = param.scaleName.trim()
                        val scaleValue = if (cleanScaleName.isEmpty()) {
                            1.0
                        } else {
                            val directNumber = cleanScaleName.replace(",", ".").toDoubleOrNull()
                            if (directNumber != null) {
                                directNumber
                            } else {
                                // Ищем ключ в varsMap, обрезая пробелы у каждого ключа карты
                                val exactKey = varsMap.keys.firstOrNull { it.trim().equals(cleanScaleName, ignoreCase = true) }
                                if (exactKey != null) varsMap[exactKey] ?: 1.0 else 1.0
                            }
                        }
                        if (param.code.equals("p19100", ignoreCase = true)) {
                            println("🔍 ЛОГ ДЛЯ p19100 -> сырое число: $rawValue, имя шкалы в INI: '${param.scaleName}', scaleValue: $scaleValue")
                            println("📋 СОДЕРЖИМОЕ КАРТЫ varsMap В МОМЕНТ ОПРОСА:")
                            if (varsMap.isEmpty()) {
                                println("   [!] КАРТА varsMap СОВЕРШЕННО ПУСТАЯ!")
                            } else {
                                varsMap.forEach { (key, value) -> println("   Ключ: '$key' -> Значение: $value") }
                            }
                        }
// 2. ВЫЧИСЛЕНИЕ ФИЗИЧЕСКОГО ЗНАЧЕНИЯ С УЧЕТОМ НАЙДЕННОЙ ШКАЛЫ
                        val bitNum = parseModbusBit(param.modbusReg)
                        val finalPhysValue = if (bitNum != null) {
                            ((rawValue shr bitNum) and 1).toDouble()
                        } else if (param.dataType.equals("TFloat", ignoreCase = true)) {
                            // Ветка для 32-битных Float параметров
                            val floatVal = Float.fromBits(rawValue.toInt()).toDouble()
                            val rawCalculated = floatVal * scaleValue
                            kotlin.math.round(rawCalculated * 10000.0) / 10000.0
                        } else {
                            // Ветка для ВСЕХ ОСТАЛЬНЫХ ТИПОВ (включая TWORD p19100)
                            // Берем сырое значение (16) и умножаем на scaleValue (0.1) -> получим 1.6
                            (rawValue and 0xFFFF) * scaleValue
                        }

// 3. ВЫВОД НА ЭКРАН (физическое значение контроллера с поддержкой TPrmList)
                        if (param.dataType.equals("TPrmList", ignoreCase = true)) {
                            // Приводим текущий HEX к нижнему регистру для поиска (например, "x06")
                            val currentHexKey = param.hexCtrl.trim().lowercase()
                            // Парсим карту вариантов из строки scaleName
                            val prmMap = ParamConverter.parsePrmList(param.scaleName)
                            // Ищем текст по HEX-ключу. Если не нашли — выводим исходный HEX
                            param.physCtrl = prmMap[currentHexKey] ?: param.hexCtrl
                        } else {
                            // Стандартный вывод для всех остальных числовых типов
                            param.physCtrl = if (finalPhysValue % 1.0 == 0.0) {
                                finalPhysValue.toLong().toString()
                            } else {
                                finalPhysValue.toString()
                            }
                        }
                    }

                    // Обновление имени порта в UI
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
            val isTFloat = param.dataType.equals("TFloat", ignoreCase = true)
            val hasExtension = param.modbusReg.contains(".")

            val currentHexInCell = param.hexCtrl.replace("0x", "").replace("x", "").trim()

            val rawPacket = if (isTFloat) {
                // ЗАПИСЬ 32-БИТНОГО ПАРАМЕТРА (TFloat)
                val raw32BitValue = currentHexInCell.toLongOrNull(16) ?: 0L

                // Распиливаем одно 32-битное число на два регистра по нашей Си-логике
                val (reg1, reg2) = split32BitValue(raw32BitValue)

                val reg1Hex = reg1.toString(16).padStart(4, '0')
                val reg2Hex = reg2.toString(16).padStart(4, '0')

                // Формируем пакет функции 0x10: Записать 2 регистра (0002), длина данных 4 байта (04)
                // Передаем сначала reg1 (Low), затем reg2 (High)
                "0110" + address.toString(16).padStart(4, '0') + "000204" + reg1Hex + reg2Hex
            } else {
                // ЗАПИСЬ ОБЫЧНОГО 16-БИТНОГО ПАРАМЕТРА
                val raw16BitValue = currentHexInCell.toIntOrNull(16) ?: 0
                val finalHexValue = if (hasExtension) {
                    // Логика побитовой маски (Read-Modify-Write)
                    val current = readSingleRegisterDirectlyInternal(address) ?: return@withLock false
                    val ext = param.modbusReg.substringAfter(".").uppercase()
                    when (ext) {
                        "H" -> (current and 0x00FF) or ((raw16BitValue and 0xFF) shl 8)
                        "L" -> (current and 0xFF00) or (raw16BitValue and 0xFF)
                        else -> {
                            val bitPos = ext.toIntOrNull() ?: 0
                            val bitState = if (param.physCtrl == "1" || param.physCtrl.lowercase() == "true") 1 else 0
                            if (bitState == 1) current or (1 shl bitPos) else current and (1 shl bitPos).inv()
                        }
                    }
                } else {
                    raw16BitValue
                }

                val hexWord = finalHexValue.toString(16).padStart(4, '0')
                // Функция 0x10: Записать 1 регистр (0001), длина данных 2 байта (02)
                "0110" + address.toString(16).padStart(4, '0') + "000102" + hexWord
            }

            val packetWithCrc = WebModbusConverter.appendCRCToHex(rawPacket)
            val writeResult = safeTransceiveAwait(packetWithCrc, 8)
            delay(40)

            return@withLock writeResult != null
        }
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

    /**
     * Собирает 32-битное число из двух 16-битных регистров (Low Word / High Word Swap)
     */
    fun build32BitValue(reg1: Int, reg2: Int): Long {
        return ((reg2 and 0xFFFF).toLong() shl 16) or (reg1 and 0xFFFF).toLong()
    }

    /**
     * Распиливает 32-битное число на два 16-битных регистра для отправки по Modbus
     */
    fun split32BitValue(value32: Long): Pair<Int, Int> {
        val reg2 = ((value32 shr 16) and 0xFFFF).toInt() // High Word
        val reg1 = (value32 and 0xFFFF).toInt()         // Low Word
        return Pair(reg1, reg2)
    }
}
