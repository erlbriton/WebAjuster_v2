//MainViewModel.kt

package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni

class MainViewModel {
    var currentVarsMap = mapOf<String, Double>()
    var typeMechanism        by mutableStateOf("Не указан")
    var dateSet              by mutableStateOf("29.01.1964")
    var installationLocation by mutableStateOf("Цех №1")
    var currentDevice by mutableStateOf<DeviceInfoIni?>(null)
    val parameters = mutableStateListOf<ParameterData>()
    val colWeights = mutableStateListOf<Float>(
        0.05f, 0.15f, 0.29f, 0.06f, 0.1125f, 0.1125f, 0.1125f, 0.1125f
    )
    /**
     * Обновленная функция загрузки с детектором дубликатов
     * @param info Данные из парсера
     * @param onDuplicatesFound Коллбэк для вывода сообщения в ваше стандартное окно
     */
    fun updateFromDevice(info: DeviceInfoIni) {
        // Сохраняем общую информацию
        currentDevice = info
        typeMechanism = info.Description
        currentVarsMap = info.varsMap
        installationLocation = info.location
        if (info.LastDateTime.isNotEmpty()) {
            dateSet = info.LastDateTime
        }

        // 1. Очищаем список
        parameters.clear()

        // 2. Наполняем его ТОЛЬКО из списка flashParameters
        // Если здесь пусто — значит в парсере flashParameters не заполнились
        parameters.addAll(info.flashParameters)

        println("DEBUG: Загружено параметров во FLASH: ${parameters.size}")
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
        parameters.forEach { it.isSelected = (it.code == code) }
        selectedCode = code
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

    init {
        loadSampleData()
    }

    fun loadSampleData() {
        parameters.clear()
    }

    // --- ФУНКЦИИ АВТОПЕРЕСЧЕТА ДАННЫХ ---

    fun updateHexBase(param: ParameterData, newHex: String) {
        val cleanHex = newHex.removePrefix("x").trim()
        val rawInt = cleanHex.toIntOrNull(16) ?: 0
        param.hexBase = "x" + rawInt.toString(16).lowercase().padStart(4, '0')

        val scaleValue = currentVarsMap[param.scaleName] ?: 1.0
        val calculated = rawInt * scaleValue
        param.physBase = if (calculated % 1.0 == 0.0) calculated.toInt().toString() else calculated.toString()
    }

    fun updatePhysBase(param: ParameterData, newPhys: String) {
        param.physBase = newPhys
        val physDouble = newPhys.replace(",", ".").toDoubleOrNull() ?: 0.0
        val scaleValue = currentVarsMap[param.scaleName] ?: 1.0

        val rawInt = if (scaleValue != 0.0) (physDouble / scaleValue).toInt() else 0
        param.hexBase = "x" + rawInt.toString(16).lowercase().padStart(4, '0')
    }

    fun updateHexCtrl(param: ParameterData, newHex: String) {
        val cleanHex = newHex.removePrefix("x").trim()
        val rawInt = cleanHex.toIntOrNull(16) ?: 0
        param.hexCtrl = "x" + rawInt.toString(16).lowercase().padStart(4, '0')

        val scaleValue = currentVarsMap[param.scaleName] ?: 1.0
        val calculated = rawInt * scaleValue
        param.physCtrl = if (calculated % 1.0 == 0.0) calculated.toInt().toString() else calculated.toString()
    }

    fun updatePhysCtrl(param: ParameterData, newPhys: String) {
        param.physCtrl = newPhys
        val physDouble = newPhys.replace(",", ".").toDoubleOrNull() ?: 0.0
        val scaleValue = currentVarsMap[param.scaleName] ?: 1.0

        val rawInt = if (scaleValue != 0.0) (physDouble / scaleValue).toInt() else 0
        param.hexCtrl = "x" + rawInt.toString(16).lowercase().padStart(4, '0')
    }
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")
}