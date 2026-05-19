package org.example.project.logic

import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni

object ModbusRepository {

    fun parseModbusRegister(modbusRegStr: String): Int? {
        if (!modbusRegStr.contains("r", ignoreCase = true)) return null
        var clean = modbusRegStr.replace("r", "", ignoreCase = true).trim()
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
                if (addr - currentChunk.last() <= 5 && (addr - currentChunk.first() < 60)) {
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
                println("❌ Ошибка при выполнении: ${e.message}")
                null
            }

            // Обрабатываем ответ из List<Int>
            if (responseList != null && responseList.size == expectedSize) {
                val responseValues = mutableListOf<Int>()

                for (i in 0 until regCount) {
                    val highByte = responseList[3 + i * 2]
                    val lowByte = responseList[3 + i * 2 + 1]
                    responseValues.add((highByte shl 8) or lowByte)
                }

                chunk.forEachIndexed { index, addr ->
                    val raw16BitValue = responseValues[index]

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
                } catch(e: Exception) {}

            } else {
                println("❌ Ошибка Modbus: Устройство не ответило или размер пакета не совпал.")
            }
        }
        println("DEBUG: === СЕТЕВОЙ ОПРОС ЗАВЕРШЕН ===")
    }
}