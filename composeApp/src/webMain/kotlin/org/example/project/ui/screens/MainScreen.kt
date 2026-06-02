// MainScreen.kt

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
import org.example.project.worker.ModbusWorkerManager

@Composable
fun MainScreen() {
    val viewModel = remember { MainViewModel() }
    val scope = rememberCoroutineScope()

    // ==========================================
    // ШАГ 51: ОСТАВЛЯЕМ ВОРКЕР КАК ПРОСТОЙ ТРИГГЕР
    // ==========================================
    LaunchedEffect(Unit) {
        ModbusWorkerManager.init { value ->
            val device = viewModel.currentDeviceState.value
            if (device != null) {
                // Просто обновляем hex, чтобы видеть, что связь идет
                device.ramParameters.forEach { param ->
                    param.hexCtrl = "x" + value.toInt().toString(16).uppercase()
                }
            }
        }
    }
    // ==========================================

    // Состояния для размеров
    var screenWidth by remember { mutableStateOf(0f) }
    var sidebarWidth by remember { mutableStateOf(200.dp) }

    // Начальное смещение для левого сплиттера, чтобы таблица была 0.5 от окна
    var leftWeight by remember { mutableStateOf(0.5f) }

    var errorMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }

    val headerActions = remember(scope, viewModel) {
        HeaderActionsButtons(
            mainViewModel = viewModel,
            scope = scope,
            onDeviceLoaded = { info ->
                viewModel.devicesMap.values.forEach { list ->
                    list.removeAll { it.fileName == info.fileName }
                }

                val iterator = viewModel.devicesMap.entries.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value.isEmpty()) {
                        iterator.remove()
                    }
                }

                viewModel.devicesMap.getOrPut(info.location) {
                    androidx.compose.runtime.mutableStateListOf()
                }.add(info)

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
                .onGloballyPositioned { screenWidth = it.size.width.toFloat() }
        ) {

            // ЛЕВАЯ ЧАСТЬ: Строго ваше оригинальное расположение (Осциллограф или Белая Пустота)
            if (viewModel.isOscilloscopeWindowOpen) {
                OscilloscopeWindow(
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(leftWeight)
                        .fillMaxHeight()
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(leftWeight)
                        .fillMaxHeight()
                        .background(Color.White) // При закрытии гарантированно возвращаем белый фон
                )
            }

            // ЛЕВЫЙ СПЛИТТЕР (Граница всей таблицы — строго на своем месте)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .pointerInput(screenWidth) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (screenWidth > 0) {
                                val deltaWeight = dragAmount.x / screenWidth
                                leftWeight = (leftWeight + deltaWeight).coerceIn(0.1f, 0.5f)
                            }
                        }
                    }
            )

            // ПРАВАЯ ЧАСТЬ: Полностью нетронутое приложение (Ваши таблицы, сайдбары и кнопки)
            Column(
                modifier = Modifier
                    .weight(1f - leftWeight)
                    .fillMaxHeight()
                    .background(Color.White)
            ) {
                HeaderTable(actions = headerActions)

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                    // САЙДБАР
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

                    // ТАБЛИЦЫ (Сюда мы вообще не прикасаемся)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        LineTwoTable()

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