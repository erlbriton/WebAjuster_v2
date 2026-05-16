//MainViewModel.kt

package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData
import org.example.project.models.DeviceInfoIni
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

class MainViewModel {
    var currentVarsMap = mapOf<String, Double>()
    var typeMechanism        by mutableStateOf("Не указан")
    var dateSet              by mutableStateOf("29.01.1964")
    var installationLocation by mutableStateOf("")
    val currentDeviceState = mutableStateOf<org.example.project.models.DeviceInfoIni?>(null)

    // Для совместимости с остальным кодом делаем геттер
    val currentDevice: org.example.project.models.DeviceInfoIni?
        get() = currentDeviceState.value
    val parameters = mutableStateListOf<ParameterData>()
    val colWeights = mutableStateListOf<Float>(
        0.1f, 0.1f, 0.24f, 0.06f, 0.1125f, 0.1125f, 0.1125f, 0.1125f
    )

    // Карта загруженных устройств
    val devicesMap = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<org.example.project.models.DeviceInfoIni>>()

    // ID выбранного устройства для подсветки
    var selectedDeviceId by androidx.compose.runtime.mutableStateOf("")

    // Объект текущего выбранного устройства для передачи в шапки/линии таблиц
   // var currentDevice by androidx.compose.runtime.mutableStateOf<org.example.project.models.DeviceInfoIni?>(null)

//    fun selectDevice(device: org.example.project.models.DeviceInfoIni) {
//        selectedDeviceId = device.id
//        currentDeviceState.value = device
//
//        // СИНХРОНИЗАЦИЯ: Копируем данные из файла в переменные экрана
//        installationLocation = device.location
//        // Если в DeviceInfoIni есть дата и тип, их можно скопировать так же:
//        // dateSet = device.date
//        // typeMechanism = device.type
//
//        parameters.clear()
//        parameters.addAll(device.flashParameters)
//    }

    fun selectDevice(device: org.example.project.models.DeviceInfoIni) {
        selectedDeviceId = device.id
        currentDeviceState.value = device

        // Синхронизируем данные с экраном
        installationLocation = device.location

        // Железно прописываем значение (эта логика заставляет Compose вовремя обновлять UI)
        typeMechanism = if (device.Description.isNotEmpty()) device.Description else ""

        parameters.clear()
        parameters.addAll(device.flashParameters)
    }

    /**
     * Обновленная функция загрузки с детектором дубликатов
     * @param info Данные из парсера
     * @param onDuplicatesFound Коллбэк для вывода сообщения в ваше стандартное окно
     */
    fun updateFromDevice(info: DeviceInfoIni) {
        // Сохраняем общую информацию
        currentDeviceState.value = info
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
        // 1. Сохраняем выбранный код, как и раньше
        selectedCode = code

        // 2. Пробегаемся по индексам списка параметров
        for (i in 0 until parameters.size) {
            val param = parameters[i]
            val shouldBeSelected = (param.code == code)

            // Перерисовываем только то, что реально изменилось (старую выделенную строку и новую)
            if (param.isSelected != shouldBeSelected) {
                param.isSelected = shouldBeSelected

                // Заменяем элемент в списке на самого себя — это триггер для Compose!
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

    init {
        loadSampleData()
    }

    fun loadSampleData() {
        parameters.clear()
    }

    // --- ФУНКЦИИ АВТОПЕРЕСЧЕТА ДАННЫХ ---

    fun updateHexBase(param: ParameterData, newHex: String) {
        // 1. Если пользователь стёр вообще всё (пустая строка), оставляем префикс "x"
        if (newHex.isEmpty()) {
            param.hexBase = "x"
            return
        }

        // 2. Приводим к верхнему регистру для проверки, но префикс сделаем маленькой "x"
        val upperInput = newHex.uppercase()
        val cleanHex = if (upperInput.startsWith("X")) upperInput.removePrefix("X") else upperInput

        // Ограничиваем длину чистых HEX-символов до 4 штук (например: FFFF)
        if (cleanHex.length > 4) return

        // Сохраняем обратно в модель с маленькой "x" на конце
        param.hexBase = "x" + cleanHex

        // 3. Пересчитываем физическое значение, только если введено валидное число
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
        // Заменили .lowercase() на .uppercase() для перевода букв в верхний регистр
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
        // Заменили .lowercase() на .uppercase() для перевода букв в верхний регистр
        param.hexCtrl = "x" + rawInt.toString(16).uppercase().padStart(4, '0')
    }
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")
}