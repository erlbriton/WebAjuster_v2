//MainScreen.kt

package org.example.project.ui.screens

import org.example.project.oscilloscope.OscilloscopeWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import org.example.project.ui.components.*
import org.example.project.viewmodels.MainViewModel
import org.example.project.viewmodels.LocalMainViewModel
import org.example.project.logic.HeaderActionsButtons
import org.example.project.components.HeaderTable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun MainScreen() {
    val viewModel = remember { MainViewModel() }
    val scope = rememberCoroutineScope()

    // Состояния для размеров
    var screenWidth by remember { mutableStateOf(0f) }
    var sidebarWidth by remember { mutableStateOf(200.dp) }

    // Начальное смещение для левого сплиттера, чтобы таблица была 0.4 от окна
    // Мы вычислим это через weight, чтобы не гадать с пикселями
    var leftWeight by remember { mutableStateOf(0.5f) }

    var errorMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }

    val headerActions = remember(scope, viewModel) {
        HeaderActionsButtons(
            mainViewModel = viewModel,
            scope = scope,
            onDeviceLoaded = { info ->
                // 1. Чистим старые дубликаты этого файла, если они были загружены ранее
                viewModel.devicesMap.values.forEach { list ->
                    list.removeAll { it.fileName == info.fileName }
                }

                // Исправлено: Удаляем пустые группы кроссплатформенным способом через итератор
                val iterator = viewModel.devicesMap.entries.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value.isEmpty()) {
                        iterator.remove()
                    }
                }

                // 2. Кладем файл в нужную папку-локацию дерева
                viewModel.devicesMap.getOrPut(info.location) {
                    androidx.compose.runtime.mutableStateListOf()
                }.add(info)

                // 3. Автоматически активируем только что загруженное устройство в таблице
                viewModel.selectDevice(info)
            },
            ShowError = { message ->
                errorMessage = message
                showErrorDialog = true
            }
        )
    }

    CompositionLocalProvider(LocalMainViewModel provides viewModel) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                // Запоминаем общую ширину окна для расчетов драга
                .onGloballyPositioned { screenWidth = it.size.width.toFloat() }
        ) {

            // ЛЕВАЯ ЧАСТЬ: Пустота (0.6 от экрана по умолчанию)
            if (viewModel.oscilloscopeState.isWindowOpen) {
                OscilloscopeWindow(
                    state = viewModel.oscilloscopeState,
                    modifier = Modifier
                        .weight(leftWeight)
                        .fillMaxHeight()
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(leftWeight)
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }

            // ЛЕВЫЙ СПЛИТТЕР (Граница всей таблицы)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .pointerInput(screenWidth) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (screenWidth > 0) {
                                // Пересчитываем weight в зависимости от движения мыши
                                val deltaWeight = dragAmount.x / screenWidth
                                leftWeight = (leftWeight + deltaWeight).coerceIn(0.1f, 0.5f)
                            }
                        }
                    }
            )

            // ПРАВАЯ ЧАСТЬ: Приложение (0.4 от экрана по умолчанию)
            Column(
                modifier = Modifier
                    .weight(1f - leftWeight)
                    .fillMaxHeight()
                    .background(Color.White)
            ) {
                HeaderTable(actions = headerActions)

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                    // САЙДБАР (Теперь здесь живое интерактивное дерево файлов)
                    DeviceSidebar(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .background(Color(0xFFF5F5F5))
                    )

                    // СПЛИТТЕР САЙДБАРА
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    sidebarWidth = (sidebarWidth + dragAmount.x.toDp())
                                        .coerceAtLeast(80.dp)
                                        .coerceAtMost(400.dp)
                                }
                            }
                    )

                    // ТАБЛИЦЫ
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        LineTwoTable()

                        // Читаем значение напрямую из стейта, исключая любые двусмысленности компилятора
                        val activeDevice = viewModel.currentDeviceState.value
                        LineThirdTable(selectedDevice = activeDevice)

                        LineFourthTable()
                        LineFifthTable()

                        DataTable(modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showErrorDialog) {
        println("ERROR: $errorMessage")
    }
}