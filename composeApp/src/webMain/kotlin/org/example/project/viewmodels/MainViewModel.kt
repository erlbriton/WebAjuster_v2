// MainViewModel.kt

package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.logic.ModbusRepository
import org.example.project.logic.ParamConverter

// ====================================================================
// ИНТЕРФЕЙСНЫЙ МОСТ МЕЖДУ KOTLIN И JAVASCRIPT (Wasm-совместимый)
// ====================================================================
// Объявляем внешние JS-функции. Передаем массив в виде строки,
// так как Kotlin/Wasm interop напрямую не поддерживает IntArray.

@JsFun("(registersStr, baudRate) => { if (window.oscilloStart) window.oscilloStart(registersStr, baudRate); }")
external fun callJsOscilloStart(registersStr: String, baudRate: Int)

@JsFun("() => { if (window.oscilloStop) window.oscilloStop(); }")
external fun callJsOscilloStop()


class MainViewModel {
    var currentVarsMap = mapOf<String, Double>()
    var typeMechanism        by mutableStateOf("Не указан")
    var dateSet              by mutableStateOf("29.01.1964")
    var installationLocation by mutableStateOf("")
    val currentDeviceState = mutableStateOf<DeviceInfoIni?>(null)

    val currentDevice: DeviceInfoIni?
        get() = currentDeviceState.value

    var selectedMemoryArea by mutableStateOf("Flash")
    val parameters = mutableStateListOf<ParameterData>()

    // Веса колонок таблицы
    val colWeights = mutableStateListOf(0.1f, 0.1f, 0.24f, 0.06f, 0.1125f, 0.1125f, 0.1125f, 0.1125f)

    val devicesMap = mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<DeviceInfoIni>>()
    var selectedDeviceId by mutableStateOf("")

    var showHardwareDialog by mutableStateOf(false)
    var hardwareDialogText by mutableStateOf("")

    val availableComPorts = mutableStateListOf<String>()
    var selectedComPort by mutableStateOf("")

    private val viewModelScope = MainScope()

    companion object {
        lateinit var instance: MainViewModel
    }

    // Флаг, который управляет тем, открыто ли сейчас окно осциллографа
    var isOscilloscopeWindowOpen by mutableStateOf(false)

    init {
        instance = this
        // СТАРАЯ КОРУТИНА ВЫСОКОСКОРОСТНОГО ОПРОСА readRegisterFast(0) УДАЛЕНА.
        // Теперь запуск опроса контролируется осознанно через интерфейс моста.
    }

    // ====================================================================
    // ФУНКЦИИ УПРАВЛЕНИЯ НОВЫМ JS-ОСЦИЛЛОГРАФОМ
    // ====================================================================

    /**
     * Запускает высокоскоростной опрос выбранных регистров в JS.
     * Принимает список адресов (например, [0x002D, 0x002E]) и скорость порта.
     */
    fun startJsOscilloscope(registers: IntArray, baudRate: Int = 115200) {
        try {
            // Преобразуем IntArray в строку вида "0,1,2,3,4" для безопасной передачи в Wasm interop
            val registersStr = registers.joinToString(separator = ",")

            callJsOscilloStart(registersStr, baudRate)
            println("🚀 Сигнал на старт JS-осциллографа отправлен для регистров: $registersStr")
        } catch (e: Exception) {
            println("❌ Не удалось вызвать window.oscilloStart: ${e.message}")
        }
    }

    /**
     * Полностью останавливает поток опроса и анимацию в JS.
     */
    fun stopJsOscilloscope() {
        try {
            callJsOscilloStop()
            println("🛑 Сигнал на остановку JS-осциллографа отправлен.")
        } catch (e: Exception) {
            println("❌ Не удалось вызвать window.oscilloStop: ${e.message}")
        }
    }

    // ====================================================================

    fun openHardwareDialog(text: String) {
        hardwareDialogText = text
        showHardwareDialog = true
    }

    fun setConnectedPort(portName: String) {
        if (portName.isEmpty()) return
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            if (!availableComPorts.contains(portName)) availableComPorts.add(portName)
            selectedComPort = portName
        }
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

    fun selectDevice(device: DeviceInfoIni) {
        selectedDeviceId = device.id
        currentDeviceState.value = device
        currentVarsMap = device.varsMap
        installationLocation = device.location
        typeMechanism = device.Description.ifEmpty { "" }
        refreshParametersList()
    }

    fun updateFromDevice(info: DeviceInfoIni) {
        currentDeviceState.value = info
        typeMechanism = info.Description
        currentVarsMap = info.varsMap
        installationLocation = info.location
        if (info.LastDateTime.isNotEmpty()) dateSet = info.LastDateTime
        refreshParametersList()
    }

    fun updateWeights(index: Int, delta: Float, containerWidth: Float) {
        if (containerWidth <= 0 || delta == 0f || index >= colWeights.size - 1) return
        val weightDelta = delta / containerWidth
        val minWeight = 0.02f
        val newCur = (colWeights[index] + weightDelta).coerceAtLeast(minWeight)
        val newNext = (colWeights[index + 1] - weightDelta).coerceAtLeast(minWeight)
        colWeights[index] = newCur
        colWeights[index + 1] = newNext
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

    fun copyBaseToController() = parameters.forEach { it.hexCtrl = it.hexBase; it.physCtrl = it.physBase }
    fun copyControllerToBase() = parameters.forEach { it.hexBase = it.hexCtrl; it.physBase = it.physCtrl }

    fun updateHexBase(param: ParameterData, newHex: String) = ParamConverter.updateHexValue(param, newHex, currentVarsMap, true)
    fun updatePhysBase(param: ParameterData, newPhys: String) = ParamConverter.updatePhysValue(param, newPhys, currentVarsMap, true)
    fun updateHexCtrl(param: ParameterData, newHex: String) = ParamConverter.updateHexValue(param, newHex, currentVarsMap, false)
    fun updatePhysCtrl(param: ParameterData, newPhys: String) = ParamConverter.updatePhysValue(param, newPhys, currentVarsMap, false)

    fun refreshControllerData() {
        val device = currentDeviceState.value ?: return
        viewModelScope.launch {
            ModbusRepository.performModbusOpros(device, currentVarsMap) { portName ->
                setConnectedPort(portName)
            }
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                val currentArea = selectedMemoryArea
                selectedMemoryArea = ""
                selectedMemoryArea = currentArea
            }
        }
    }

    fun writeParameterToDevice(param: ParameterData) {
        viewModelScope.launch {
            if (currentDeviceState.value == null) return@launch
            val success = ModbusRepository.writeSingleParameter(param, currentVarsMap)
            if (!success) {
                openHardwareDialog("Ошибка записи параметра ${param.code} в контроллер.")
            }
        }
    }

    fun writeAllBaseToControllerDevice() {
        copyBaseToController()

        viewModelScope.launch {
            var hasError = false
            var errorCount = 0

            val byteParams = parameters.filter { it.modbusReg.contains(".H") || it.modbusReg.contains(".L") }
            val regularParams = parameters.filter { !it.modbusReg.contains(".H") && !it.modbusReg.contains(".L") }

            regularParams.forEach { param ->
                if (!ModbusRepository.writeSingleParameter(param, currentVarsMap)) {
                    hasError = true
                    errorCount++
                }
            }

            val groupedBytes = byteParams.groupBy { it.modbusReg.substringBefore(".") }

            for ((_, paramsInGroup) in groupedBytes) {
                paramsInGroup.forEach { param ->
                    if (!ModbusRepository.writeSingleParameter(param, currentVarsMap)) {
                        hasError = true
                        errorCount++
                    }
                    delay(100)
                }
            }

            if (hasError) {
                openHardwareDialog("При записи возникло ошибок: $errorCount")
            }
        }
    }
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")
}