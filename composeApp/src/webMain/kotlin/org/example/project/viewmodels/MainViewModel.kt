package org.example.project.viewmodels

import androidx.compose.runtime.*
import org.example.project.models.ParameterData

class MainViewModel {
    // ── Поля состояния для шапки (используются в LineFifthTable и HeaderActions) ──
    var typeMechanism        by mutableStateOf("Не указан")
    var dateSet              by mutableStateOf("29.01.1964")
    var installationLocation by mutableStateOf("Цех №1")

    // ── Список параметров ──────────────────────────────────────────────────
    val parameters = mutableStateListOf<ParameterData>()

    // ── Веса шести динамических столбцов ──────────────────────────────────
    // [0]Имя, [1]Описание | [2]hex(База), [3]Phys(База) | [4]hex(Контр), [5]Phys(Контр)
    val colWeights = mutableStateListOf<Float>(
        0.05f,   // 0: № (5%)
        0.15f,   // 1: Имя (15%)
        0.30f,   // 2: Описание (30%) - было 0.35
        0.05f,   // 3: Ед.изм (5%)
        0.1125f, // 4: hex База (11.25%)
        0.1125f, // 5: Phys База (11.25%)
        0.1125f, // 6: hex Контр (11.25%)
        0.1125f  // 7: Phys Контр (11.25%)
    )

    // ── Выделенная строка ─────────────────────────────────────────────────
    var selectedCode by mutableStateOf<String?>(null)
        private set

    fun selectRow(code: String) {
        parameters.forEach { it.isSelected = (it.code == code) }
        selectedCode = code
    }

    // ── Массовое копирование ───────────────────────────────────────────────
    /** БАЗА → КОНТРОЛЛЕР */
    fun copyBaseToController() {
        parameters.forEach { p ->
            p.hexCtrl  = p.hexBase
            p.physCtrl = p.physBase
        }
    }

    /** КОНТРОЛЛЕР → БАЗА */
    fun copyControllerToBase() {
        parameters.forEach { p ->
            p.hexBase  = p.hexCtrl
            p.physBase = p.physCtrl
        }
    }

    // ── Загрузка данных ──────────────────────────────────────────────────
    init {
        loadSampleData()
    }

    fun loadSampleData() {
        parameters.clear()
        val sample = listOf(
            ParameterData("p10000","IstStart",        "Пуск по току статора",           "A",   initialHexBase="0014", initialPhysBase="20",   initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10100","IstStop",         "Стоп по току статора",           "A",   initialHexBase="000A", initialPhysBase="10",   initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10200","IstExcEnable",    "Допустимый ток статора",         "A",   initialHexBase="0190", initialPhysBase="400",  initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10300","ExcEnableFreq",   "Минимальная частота скольж.",    "Hz",  initialHexBase="0019", initialPhysBase="25",   initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10400","HsFreq",          "Тяжёлый пуск: максимум",         "Hz",  initialHexBase="0032", initialPhysBase="50",   initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10500","HsTime",          "Время тяжёлого пуска",           "sec", initialHexBase="0014", initialPhysBase="20",   initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10600","IExcRef",         "Ток возбуждения в ручном режиме","A",   initialHexBase="00C8", initialPhysBase="200",  initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10700","IExcTst",         "Ток проверки цепи скольжения",   "A",   initialHexBase="0064", initialPhysBase="100",  initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10800","IExcForce",       "Ток форсировки возбуждения",     "A",   initialHexBase="012C", initialPhysBase="300",  initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p10900","StartForceTime",  "Время пусковой форсировки",      "sec", initialHexBase="0BB8", initialPhysBase="3000", initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p11000","IstOffTime",      "Задержка перед отключением",     "sec", initialHexBase="0BB8", initialPhysBase="3000", initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p11100","TestTime",        "Длительность тестирования",      "sec", initialHexBase="0BB8", initialPhysBase="3000", initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p11200","FieldBlankTime",  "Длительность гашения поля",      "sec", initialHexBase="0BB8", initialPhysBase="3000", initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p11300","NextStartTime",   "Время блокировки повторного пуска","sec",initialHexBase="0005", initialPhysBase="5",    initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p11400","FSAsyncTime",     "Время асинхронного хода 1",      "sec", initialHexBase="001E", initialPhysBase="30",   initialHexCtrl="0000", initialPhysCtrl="0"),
            ParameterData("p11500","QminAsyncTime",   "Время асинхронного хода 2",      "sec", initialHexBase="0023", initialPhysBase="35",   initialHexCtrl="0000", initialPhysCtrl="0"),
        )
        parameters.addAll(sample)
    }
}

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("No MainViewModel provided")
}