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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

private val modbusMutex = Mutex()

object ModbusRepository {
    // Высокоскоростная труба для трендов осциллографа
    private val _oscilloscopeStream = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Float>>(
        replay = 600,
        extraBufferCapacity = 200,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val oscilloscopeStream = _oscilloscopeStream.asSharedFlow()

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
                if (param.dataType.equals("TFloat",  ignoreCase = true) ||
                    param.dataType.equals("TIPAddr", ignoreCase = true)) {
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
                        addressToValueMap[currentRegAddress] = (highByte shl 8) or lowByte
                    }

                    val paramsInThisChunk = chunk.flatMap { regToParamsMap[it] ?: emptyList() }.distinctBy { it.code }

                    paramsInThisChunk.forEach { param ->
                        val isTFloat  = param.dataType.equals("TFloat",  ignoreCase = true)
                        val isTIPAddr = param.dataType.equals("TIPAddr", ignoreCase = true)
                        val baseAddress = parseModbusRegister(param.modbusReg)!!

                        val rawValue: Long = if (isTFloat || isTIPAddr) {
                            val r1 = addressToValueMap[baseAddress] ?: 0
                            val r2 = addressToValueMap[baseAddress + 1] ?: 0
                            ((r2 and 0xFFFF).toLong() shl 16) or (r1 and 0xFFFF).toLong()
                        } else {
                            (addressToValueMap[baseAddress] ?: 0).toLong()
                        }

                        val bitNum = parseModbusBit(param.modbusReg)
                        if (bitNum != null) {
                            val bitValue = (rawValue shr bitNum) and 1
                            param.hexCtrl = bitValue.toString()
                            param.physCtrl = bitValue.toString()
                        } else if (isTFloat || isTIPAddr) {
                            param.hexCtrl = "x" + rawValue.toString(16).uppercase().padStart(8, '0')
                        } else {
                            val ext = if (param.modbusReg.contains("."))
                                param.modbusReg.substringAfter(".").uppercase() else ""
                            val displayValue = when (ext) {
                                "H" -> (rawValue shr 8) and 0xFF
                                "L" -> rawValue and 0xFF
                                else -> rawValue and 0xFFFF
                            }
                            val padLen = if (ext == "H" || ext == "L") 2 else 4
                            param.hexCtrl = "x" + displayValue.toString(16).uppercase().padStart(padLen, '0')
                        }

                        val cleanScaleName = param.scaleName.trim()
                        val scaleValue = if (cleanScaleName.isEmpty()) {
                            1.0
                        } else {
                            val directNumber = cleanScaleName.replace(",", ".").toDoubleOrNull()
                            if (directNumber != null) {
                                directNumber
                            } else {
                                val exactKey = varsMap.keys.firstOrNull { it.trim().equals(cleanScaleName, ignoreCase = true) }
                                if (exactKey != null) varsMap[exactKey] ?: 1.0 else 1.0
                            }
                        }

                        val finalPhysValue = if (bitNum != null) {
                            ((rawValue shr bitNum) and 1).toDouble()
                        } else if (param.dataType.equals("TFloat", ignoreCase = true)) {
                            val floatVal = Float.fromBits(rawValue.toInt()).toDouble()
                            val rawCalculated = floatVal * scaleValue
                            kotlin.math.round(rawCalculated * 10000.0) / 10000.0
                        } else {
                            val ext = if (param.modbusReg.contains("."))
                                param.modbusReg.substringAfter(".").uppercase() else ""
                            val byteValue = when (ext) {
                                "H" -> (rawValue shr 8) and 0xFF
                                "L" -> rawValue and 0xFF
                                else -> rawValue and 0xFFFF
                            }
                            byteValue * scaleValue
                        }

                        if (param.dataType.equals("TPrmList", ignoreCase = true)) {
                            val currentHexKey = param.hexCtrl.trim().lowercase()
                            val prmMap = ParamConverter.parsePrmList(param.scaleName)
                            param.physCtrl = prmMap[currentHexKey] ?: param.hexCtrl
                        } else {
                            param.physCtrl = if (finalPhysValue % 1.0 == 0.0) {
                                finalPhysValue.toLong().toString()
                            } else {
                                finalPhysValue.toString()
                            }
                        }

                        _oscilloscopeStream.tryEmit(Pair(param.code, finalPhysValue.toFloat()))
                        delay(1)
                    }

                    try {
                        val portName = getWasmPortName().toString()
                        if (portName.isNotEmpty()) onPortDetected(portName)
                    } catch (e: Exception) {}

                } else {
                    println("❌ Ошибка Modbus: Устройство не ответило или размер пакета не совпал.")
                }

                delay(15)
            }
        }
    }

    suspend fun writeSingleParameter(param: ParameterData, varsMap: Map<String, Double>): Boolean {
        return modbusMutex.withLock {
            val address = parseModbusRegister(param.modbusReg) ?: return@withLock false
            val isTFloat  = param.dataType.equals("TFloat",  ignoreCase = true)
            val isTIPAddr = param.dataType.equals("TIPAddr", ignoreCase = true)
            val hasExtension = param.modbusReg.contains(".")

            val currentHexInCell = param.hexCtrl.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

            val rawPacket = if (isTFloat || isTIPAddr) {
                val raw32BitValue = currentHexInCell.toLongOrNull(16) ?: 0L
                val (reg1, reg2) = split32BitValue(raw32BitValue)

                val reg1Hex = reg1.toString(16).padStart(4, '0')
                val reg2Hex = reg2.toString(16).padStart(4, '0')

                "0110" + address.toString(16).padStart(4, '0') + "000204" + reg1Hex + reg2Hex
            } else {
                val raw16BitValue = currentHexInCell.toIntOrNull(16) ?: 0
                val finalHexValue = if (hasExtension) {
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
                "0110" + address.toString(16).padStart(4, '0') + "000102" + hexWord
            }

            val packetWithCrc = WebModbusConverter.appendCRCToHex(rawPacket)
            val writeResult = safeTransceiveAwait(packetWithCrc, 8)
            delay(40)

            return@withLock writeResult != null
        }
    }

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

    private suspend fun readSingleRegisterDirectly(regAddress: Int): Int? {
        return modbusMutex.withLock {
            readSingleRegisterDirectlyInternal(regAddress)
        }
    }

    suspend fun writeByteToRegister(address: Int, modbusReg: String, newValueHex: String): Boolean {
        return modbusMutex.withLock {
            val currentRegValue = readSingleRegisterDirectlyInternal(address) ?: return@withLock false
            val newByteValue = newValueHex.toIntOrNull(16) ?: 0

            val updatedValue = if (modbusReg.contains(".H", ignoreCase = true)) {
                (currentRegValue and 0x00FF) or ((newByteValue and 0xFF) shl 8)
            } else {
                (currentRegValue and 0xFF00) or (newByteValue and 0xFF)
            }

            val hexValue = updatedValue.toString(16).padStart(4, '0')
            val rawPacket = "0110" + address.toString(16).padStart(4, '0') + "000102" + hexValue
            val packetWithCrc = WebModbusConverter.appendCRCToHex(rawPacket)

            val writeResult = safeTransceiveAwait(packetWithCrc, 8)
            delay(20)

            return@withLock writeResult != null
        }
    }

    fun build32BitValue(reg1: Int, reg2: Int): Long {
        return ((reg2 and 0xFFFF).toLong() shl 16) or (reg1 and 0xFFFF).toLong()
    }

    fun split32BitValue(value32: Long): Pair<Int, Int> {
        val reg2 = ((value32 shr 16) and 0xFFFF).toInt()
        val reg1 = (value32 and 0xFFFF).toInt()
        return Pair(reg1, reg2)
    }

    // 🔥 НАСТОЯЩИЙ ВЫСОКОСКОРОСТНОЙ ОПРОС ОДНОГО РЕГИСТРА ИЗ ПОРТА
    suspend fun readRegisterFast(regAddress: Int): Float {
        return modbusMutex.withLock {
            val startAddrHex = regAddress.toString(16).padStart(4, '0')
            val regCountHex = "0001" // Читаем ровно 1 регистр (16 бит)
            val rawPacketHex = "0103" + startAddrHex + regCountHex

            val hexPacketString = WebModbusConverter.appendCRCToHex(rawPacketHex)
            // Ожидаемый размер ответа функции 03 для 1 регистра:
            // 1 байт (ID) + 1 байт (Функция) + 1 байт (Кол-во байт данных = 2) + 2 байта (Данные) + 2 байта (CRC) = 7 байт
            val expectedSize = 7

            try {
                val hexResult = safeTransceiveAwait(hexPacketString, expectedSize)

                if (!hexResult.isNullOrEmpty() && hexResult.length == (expectedSize * 2)) {
                    // Байт ответа с данными начинаются с индекса 6 (после ID, функции и счетчика байт)
                    val highByte = hexResult.substring(6, 8).toInt(16) and 0xFF
                    val lowByte = hexResult.substring(8, 10).toInt(16) and 0xFF
                    val rawValue = (highByte shl 8) or lowByte

                    // Переводим в Float (если регистр знаковый 16-битный, преобразуем short)
                    return rawValue.toShort().toFloat()
                }
            } catch (e: Exception) {
                println("❌ Ошибка быстрого опроса регистра 0x${regAddress.toString(16)}: ${e.message}")
            }
            return 0f // Если устройство не ответило
        }
    }

    fun emitOscilloscopeData(code: String, value: Float) {
        _oscilloscopeStream.tryEmit(Pair(code, value))
    }
}