package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// ЧИСТАЯ TOP-LEVEL ФУНКЦИЯ ДЛЯ KOTLIN/WASM:
// Только здесь компилятор разрешает использовать встроенный блок js(...)
fun executeSerialTransceiveJs(hexPacket: String, expectedSize: Int): kotlin.js.JsString? = js(
    """
    (function() {
        try {
            var hexIn = hexPacket;
            var sizeIn = expectedSize;
            
            var bytesIn = new Int8Array(hexIn.length / 2);
            for (var i = 0; i < hexIn.length; i += 2) {
                bytesIn[i / 2] = parseInt(hexIn.substr(i, 2), 16);
            }
            
            var engine = null;
            if (typeof org !== 'undefined' && org.example && org.example.project && org.example.project.utils && org.example.project.utils.SerialEngine) {
                engine = org.example.project.utils.SerialEngine;
            } else if (typeof SerialEngine !== 'undefined') {
                engine = SerialEngine;
            }
            
            if (engine) {
                var respBytes = null;
                if (typeof engine.transceive === 'function') {
                    respBytes = engine.transceive(bytesIn, sizeIn);
                } else if (engine.prototype && typeof engine.prototype.transceive === 'function') {
                    respBytes = engine.prototype.transceive(bytesIn, sizeIn);
                }
                
                if (respBytes) {
                    var hexOut = "";
                    for (var j = 0; j < respBytes.length; j++) {
                        var b = respBytes[j] & 0xFF;
                        hexOut += b.toString(16).padStart(2, '0');
                    }
                    return hexOut;
                }
            }
        } catch(e) {}
        return null;
    })()
    """
)

class MainViewModel {
    var currentVarsMap = mapOf<String, Double>()
    var typeMechanism        by mutableStateOf("Не указан")
    var dateSet              by mutableStateOf("29.01.1964")
    var installationLocation by mutableStateOf("")
    val currentDeviceState = mutableStateOf<org.example.project.models.DeviceInfoIni?>(null)

    val currentDevice: org.example.project.models.DeviceInfoIni?
        get() = currentDeviceState.value

    var selectedMemoryArea by mutableStateOf("Flash")
    val parameters = mutableStateListOf<ParameterData>()

    val colWeights = mutableStateListOf<Float>(
        0.1f, 0.1f, 0.24f, 0.06f, 0.1125f, 0.1125f, 0.1125f, 0.1125f
    )

    val devicesMap = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<org.example.project.models.DeviceInfoIni>>()
    var selectedDeviceId by androidx.compose.runtime.mutableStateOf("")

    var showHardwareDialog by mutableStateOf(false)
    var hardwareDialogText by mutableStateOf("")

    val availableComPorts = mutableStateListOf<String>()
    var selectedComPort by mutableStateOf("")

    private val viewModelScope = MainScope()

    fun openHardwareDialog(text: String) {
        hardwareDialogText = text
        showHardwareDialog = true
    }

    fun setConnectedPort(portName: String) {
        if (portName.isNotEmpty()) {
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                if (!availableComPorts.contains(portName)) {
                    availableComPorts.add(portName)
                }
                selectedComPort = portName
            }
        }
    }

    companion object {
        lateinit var instance: MainViewModel
    }

    init {
        instance = this
        loadSampleData()
    }

    fun changeMemoryArea(newArea: String) {
        selectedMemoryArea = newArea
        refreshParametersList()
    }

    private fun refreshParametersList() {
        val device = currentDeviceState.value ?: return
        parameters.clear()

        val targetList = when (selectedMemoryArea) {
            "Flash" -> device.flashParameters
            "CD"    -> device.cdParameters
            "RAM"   -> device.ramParameters
            else    -> device.flashParameters
        }

        parameters.addAll(targetList)
    }

    fun selectDevice(device: org.example.project.models.DeviceInfoIni) {
        selectedDeviceId = device.id
        currentDeviceState.value = device
        installationLocation = device.location
        typeMechanism = if (device.Description.isNotEmpty()) device.Description else ""
        refreshParametersList()
    }

    fun updateFromDevice(info: DeviceInfoIni) {
        currentDeviceState.value = info
        typeMechanism = info.Description
        currentVarsMap = info.varsMap
        installationLocation = info.location
        if (info.LastDateTime.isNotEmpty()) {
            dateSet = info.LastDateTime
        }
        refreshParametersList()
        println("DEBUG: Загружено параметров для области $selectedMemoryArea: ${parameters.size}")
    }

    fun updateWeights(index: Int, delta: Float, containerWidth: Float) {
        if (containerWidth <= 0 || delta == 0f) return
        val weightDelta = delta / containerWidth
        val minWeight = 0.02f
        if (index < colWeights.size - 1) {
            val newCur = (colWeights[index] + weightDelta).coerceAtLeast(minWeight)
            val newNext = (colWeights[index + 1] - weightDelta).coerceAtLeast(minWeight)
            colWeights[index] = newCur
            colWeights[index + 1] = newNext
        }
    }

    var selectedCode by mutableStateOf<String?>(null)
        private set

    fun selectRow(code: String) {
        selectedCode = code
        for (i in 0 until parameters.size) {
            val param = parameters[i]
            val shouldBeSelected = (param.code == code)
            if (param.isSelected != shouldBeSelected) {
                param.isSelected = shouldBeSelected
                parameters[i] = param
            }
        }
    }

    fun copyBaseToController() {
        parameters.forEach { p ->
            p.hexCtrl  = p.hexBase
            p.physCtrl = p.physBase
        }
    }

    fun copyControllerToBase() {
        parameters.forEach { p ->
            p.hexBase  = p.hexCtrl
            p.physBase = p.physCtrl
        }
    }

    fun loadSampleData() {
        parameters.clear()
    }

    fun updateHexBase(param: ParameterData, newHex: String) {
        if (newHex.isEmpty()) {
            param.hexBase = "x"
            return
        }
        val upperInput = newHex.uppercase()
        val cleanHex = if (upperInput.startsWith("X")) upperInput.removePrefix("X") else upperInput
        if (cleanHex.length > 4) return
        param.hexBase = "x" + cleanHex
        val rawInt = cleanHex.toIntOrNull(16)
        if (rawInt != null) {
            val scaleValue = currentVarsMap[param.scaleName] ?: 1.0
            val calculated = rawInt * scaleValue
            param.physBase = if (calculated % 1.0 == 0.0) calculated.toInt().toString() else calculated.toString()
        }
    }

    fun updatePhysBase(param: ParameterData, newPhys: String) {
        param.physBase = newPhys
        val physDouble = newPhys.replace(",", ".").toDoubleOrNull() ?: 0.0
        val scaleValue = currentVarsMap[param.scaleName] ?: 1.0
        val rawInt = if (scaleValue != 0.0) (physDouble / scaleValue).toInt() else 0
        param.hexBase = "x" + rawInt.toString(16).uppercase().padStart(4, '0')
    }

    fun updateHexCtrl(param: ParameterData, newHex: String) {
        if (newHex.isEmpty()) {
            param.hexCtrl = "x"
            return
        }
        val upperInput = newHex.uppercase()
        val cleanHex = if (upperInput.startsWith("X")) upperInput.removePrefix("X") else upperInput
        if (cleanHex.length > 4) return
        param.hexCtrl = "x" + cleanHex
        val rawInt = cleanHex.toIntOrNull(16)
        if (rawInt != null) {
            val scaleValue = currentVarsMap[param.scaleName] ?: 1.0
            val calculated = rawInt * scaleValue
            param.physCtrl = if (calculated % 1.0 == 0.0) calculated.toInt().toString() else calculated.toString()
        }
    }

    fun updatePhysCtrl(param: ParameterData, newPhys: String) {
        param.physCtrl = newPhys
        val physDouble = newPhys.replace(",", ".").toDoubleOrNull() ?: 0.0
        val scaleValue = currentVarsMap[param.scaleName] ?: 1.0
        val rawInt = if (scaleValue != 0.0) (physDouble / scaleValue).toInt() else 0
        param.hexCtrl = "x" + rawInt.toString(16).uppercase().padStart(4, '0')
    }

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

    fun refreshControllerData() {
        val device = currentDeviceState.value
        if (device == null) return

        fun appendModbusCRC(bytes: ByteArray): ByteArray {
            var crc = 0xFFFF
            for (byte in bytes) {
                crc = crc xor (byte.toInt() and 0xFF)
                for (j in 0 until 8) {
                    if ((crc and 1) != 0) {
                        crc = (crc ushr 1) xor 0xA001
                    } else {
                        crc = crc ushr 1
                    }
                }
            }
            val result = ByteArray(bytes.size + 2)
            bytes.copyInto(result)
            result[bytes.size] = (crc and 0xFF).toByte()
            result[bytes.size + 1] = ((crc ushr 8) and 0xFF).toByte()
            return result
        }

        viewModelScope.launch {
            println("DEBUG: === ЗАПУСК ФИЗИЧЕСКОГО ОПРОСА КОНТРОЛЛЕРА ПО МОДБАС (0x03) ===")

            val allValidParams = mutableListOf<ParameterData>()
            allValidParams.addAll(device.flashParameters.filter { parseModbusRegister(it.modbusReg) != null })
            allValidParams.addAll(device.cdParameters.filter { parseModbusRegister(it.modbusReg) != null })
            allValidParams.addAll(device.ramParameters.filter { parseModbusRegister(it.modbusReg) != null })

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

                val rawPacket = byteArrayOf(
                    0x01,
                    0x03,
                    (startAddress shr 8).toByte(),
                    (startAddress and 0xFF).toByte(),
                    (regCount shr 8).toByte(),
                    (regCount and 0xFF).toByte()
                )

                val fullPacket = appendModbusCRC(rawPacket)
                val expectedSize = 5 + (regCount * 2)

                println("--> Отправка 0x03 (Адрес: $startAddress, Кол-во: $regCount), Ожидаем байт: $expectedSize")

                val hexPacketString = fullPacket.joinToString("") { it.toInt().and(0xFF).toString(16).padStart(2, '0') }

                val response: ByteArray? = try {
                    // ИСПРАВЛЕНИЕ: Вызываем разрешенную изолированную top-level функцию
                    val returnedJsAny = executeSerialTransceiveJs(hexPacketString, expectedSize)
                    val hexResult = returnedJsAny?.toString()

                    if (!hexResult.isNullOrEmpty()) {
                        ByteArray(hexResult.length / 2) { i ->
                            hexResult.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                        }
                    } else null
                } catch (e: Exception) {
                    println("❌ Ошибка при выполнении Wasm-интеропа: ${e.message}")
                    null
                }

                if (response != null && response.size == expectedSize) {
                    val responseValues = IntArray(regCount)
                    for (i in 0 until regCount) {
                        val highByte = response[3 + i * 2].toInt() and 0xFF
                        val lowByte = response[3 + i * 2 + 1].toInt() and 0xFF
                        responseValues[i] = (highByte shl 8) or lowByte
                    }

                    chunk.forEach { addr ->
                        val offset = addr - startAddress
                        val raw16BitValue = responseValues[offset]

                        regToParamsMap[addr]?.forEach { param ->
                            param.hexCtrl = "x" + raw16BitValue.toString(16).uppercase().padStart(4, '0')

                            val scaleValue = currentVarsMap[param.scaleName] ?: 1.0
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
                } else {
                    println("❌ Ошибка Modbus: Устройство не ответило на диапазон $startAddress или размер пакета не совпал.")
                }
            }

            val currentArea = selectedMemoryArea
            selectedMemoryArea = ""
            selectedMemoryArea = currentArea

            println("DEBUG: === СЕТЕВОЙ ОПРОС ЗАВЕРШЕН ===")
        }
    }
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")
}