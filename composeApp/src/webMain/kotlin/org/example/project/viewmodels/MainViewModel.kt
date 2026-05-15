package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni

class MainViewModel {
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
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")
}