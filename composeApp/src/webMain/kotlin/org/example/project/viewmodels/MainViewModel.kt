package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.example.project.logic.ModbusRepository
import org.example.project.logic.ParamConverter

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
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")
}