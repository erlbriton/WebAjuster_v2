package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni

class MainViewModel {
    var typeMechanism        by mutableStateOf("Не указан")
    var dateSet              by mutableStateOf("29.01.1964")
    var installationLocation by mutableStateOf("Цех №1")

    // Состояние текущего устройства (для передачи в LineThirdTable)
    var currentDevice by mutableStateOf<DeviceInfoIni?>(null)

    val parameters = mutableStateListOf<ParameterData>()

    // Веса 8 столбцов. Сумма = 1.0
    val colWeights = mutableStateListOf<Float>(
        0.05f,   // 0: №
        0.15f,   // 1: Имя
        0.29f,   // 2: Описание
        0.06f,   // 3: Ед.изм
        0.1125f, // 4: hex База
        0.1125f, // 5: Phys База
        0.1125f, // 6: hex Контр
        0.1125f  // 7: Phys Контр
    )

    /**
     * Основная функция загрузки данных из парсера
     */
    fun updateFromDevice(info: DeviceInfoIni) {
        currentDevice = info
        typeMechanism = info.Description
        installationLocation = info.location
        dateSet = info.LastDateTime

        // Очищаем старые данные и добавляем новые параметры (RAM + FLASH)
        parameters.clear()
        parameters.addAll(info.flashParameters)
        parameters.addAll(info.ramParameters)
    }

    fun updateWeights(index: Int, delta: Float, containerWidth: Float) {
        if (containerWidth <= 0 || delta == 0f) return
        val weightDelta = delta / containerWidth
        val minWeight = 0.02f

        // Определяем, за какую границу тянем
        when (index) {
            // Тянем границу ГРУППЫ "ПАРАМЕТРЫ" (индексы 0,1,2,3)
            3 -> {
                val currentGroupW = colWeights[0] + colWeights[1] + colWeights[2] + colWeights[3]
                val nextGroupW = colWeights[4] + colWeights[5] + colWeights[6] + colWeights[7]

                if (currentGroupW + weightDelta > minWeight * 4 && nextGroupW - weightDelta > minWeight * 4) {
                    val scaleCurrent = (currentGroupW + weightDelta) / currentGroupW
                    val scaleNext = (nextGroupW - weightDelta) / nextGroupW

                    for (i in 0..3) colWeights[i] *= scaleCurrent
                    for (i in 4..7) colWeights[i] *= scaleNext
                }
            }
            // Тянем границу ГРУППЫ "БАЗА" (индексы 4,5)
            5 -> {
                val currentGroupW = colWeights[4] + colWeights[5]
                val nextGroupW = colWeights[6] + colWeights[7]

                if (currentGroupW + weightDelta > minWeight * 2 && nextGroupW - weightDelta > minWeight * 2) {
                    val scaleCurrent = (currentGroupW + weightDelta) / currentGroupW
                    val scaleNext = (nextGroupW - weightDelta) / nextGroupW

                    for (i in 4..5) colWeights[i] *= scaleCurrent
                    for (i in 6..7) colWeights[i] *= scaleNext
                }
            }
            // Обычное перетягивание одиночного столбца (в нижнем ряду)
            else -> {
                if (index < colWeights.size - 1) {
                    val newCur = (colWeights[index] + weightDelta).coerceAtLeast(minWeight)
                    val newNext = (colWeights[index + 1] - weightDelta).coerceAtLeast(minWeight)
                    colWeights[index] = newCur
                    colWeights[index + 1] = newNext
                }
            }
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
        println("Таблица очищена и готова к загрузке реальных данных.")
    }
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")
}