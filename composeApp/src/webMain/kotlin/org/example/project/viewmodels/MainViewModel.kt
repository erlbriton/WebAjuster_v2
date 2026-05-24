package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.example.project.logic.ModbusRepository
import org.example.project.logic.ParamConverter
import org.example.project.oscilloscope.OscilloscopeState

class MainViewModel {
    val oscilloscopeState = OscilloscopeState()
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

    init {
        instance = this
    }

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
        currentVarsMap = device.varsMap // ЖЕЛЕЗНО ПЕРЕДАЕМ ШКАЛЫ ПРИ ВЫБОРЕ ПРИБОРА
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
                // Перезаписываем элемент по индексу, чтобы Compose увидел изменения
                parameters[i] = param
            }
        }
    }

    fun copyBaseToController() = parameters.forEach { it.hexCtrl = it.hexBase; it.physCtrl = it.physBase }
    fun copyControllerToBase() = parameters.forEach { it.hexBase = it.hexCtrl; it.physBase = it.physCtrl }

    // Перенаправляем вызовы в ParamConverter
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
            // Форсированный триггер Compose
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
            val success = ModbusRepository.writeSingleParameter(param, currentVarsMap) // Берем согласованную карту шкал
            if (!success) {
                openHardwareDialog("Ошибка записи параметра ${param.code} в контроллер.")
            }
        }
    }

    /**
     * Переносит все значения из БАЗЫ в КОНТРОЛЛЕР
     * и физически записывает их в устройство по Modbus.
     */
    fun writeAllBaseToControllerDevice() {
        copyBaseToController()

        viewModelScope.launch {
            var hasError = false
            var errorCount = 0

            // 1. Разбиваем параметры на две группы: обычные и побайтовые (.H/.L)
            val byteParams = parameters.filter { it.modbusReg.contains(".H") || it.modbusReg.contains(".L") }
            val regularParams = parameters.filter { !it.modbusReg.contains(".H") && !it.modbusReg.contains(".L") }

            // 2. Отправляем обычные параметры (как раньше)
            regularParams.forEach { param ->
                if (!ModbusRepository.writeSingleParameter(param, currentVarsMap)) {
                    hasError = true
                    errorCount++
                }
            }

            // 3. Группируем байтовые параметры по номеру регистра (например, r2064)
            val groupedBytes = byteParams.groupBy { it.modbusReg.substringBefore(".") }

            // 4. Отправляем сгруппированные регистры
            for ((_, paramsInGroup) in groupedBytes) {
                // Берем первый параметр из группы, чтобы узнать адрес регистра (он у них общий)
                val baseParam = paramsInGroup.first()

                // Вместо того чтобы вызывать writeSingleParameter много раз,
                // мы вызываем его только один раз для "базового" регистра,
                // НО нам нужно убедиться, что внутри ModbusRepository
                // логика чтения/записи учитывает оба байта.

                // ВАРИАНТ: Если вы оставите логику внутри writeSingleParameter (как мы обсуждали),
                // то для групповой записи вам нужно передать туда "собранное" значение.

                // Если вы не хотите менять архитектуру репозитория, самый простой "костыль" здесь — delay
                paramsInGroup.forEach { param ->
                    if (!ModbusRepository.writeSingleParameter(param, currentVarsMap)) {
                        hasError = true
                        errorCount++
                    }
                    delay(100) // ДАЕМ КОНТРОЛЛЕРУ ВРЕМЯ НА ПЕРЕЗАПИСЬ ПАМЯТИ
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