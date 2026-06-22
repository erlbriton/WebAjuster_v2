// MainScreen.kt

package org.example.project.ui.screens

import org.example.project.oscilloscope.OscilloscopeWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.example.project.ui.components.*
import org.example.project.viewmodels.MainViewModel
import org.example.project.viewmodels.LocalMainViewModel
import org.example.project.logic.HeaderActionsButtons
import org.example.project.components.HeaderTable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.example.project.jsinterop.checkJsClasses
import org.example.project.jsinterop.getModbusValue
import org.example.project.jsinterop.jsOscilloCreate
import org.example.project.jsinterop.jsOscilloInit
import org.example.project.jsinterop.jsOscilloPush
import org.example.project.jsinterop.testJsBridge
import org.example.project.worker.ModbusWorkerManager

@Composable
fun MainScreen() {
    val viewModel = remember { MainViewModel() }
    val scope = rememberCoroutineScope()

    var screenWidth by remember { mutableStateOf(0f) }
    var sidebarWidth by remember { mutableStateOf(200.dp) }
    var leftWeight by remember { mutableStateOf(0.5f) }

    var errorMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }

    // 🔥 Получаем density ОДИН РАЗ в начале - используется для конвертации Float -> Dp
    val density = LocalDensity.current

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
                        .background(Color.White)
                )
            }

            // 🔥 ЛЕВЫЙ СПЛИТТЕР
            var leftSplitterOffset by remember { mutableStateOf(0f) }
            var isLeftSplitterDragging by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .zIndex(1f)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .background(if (isLeftSplitterDragging) Color(0xFF2196F3).copy(alpha = 0.3f) else Color.Transparent)
                    .pointerInput(screenWidth) {
                        detectDragGestures(
                            onDragStart = {
                                isLeftSplitterDragging = true
                                leftSplitterOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                leftSplitterOffset += dragAmount.x
                            },
                            onDragEnd = {
                                isLeftSplitterDragging = false
                                if (screenWidth > 0 && leftSplitterOffset != 0f) {
                                    val deltaWeight = leftSplitterOffset / screenWidth
                                    leftWeight = (leftWeight + deltaWeight).coerceIn(0.1f, 0.5f)
                                }
                                leftSplitterOffset = 0f
                            },
                            onDragCancel = {
                                isLeftSplitterDragging = false
                                leftSplitterOffset = 0f
                            }
                        )
                    }
                    .drawWithContent {
                        drawContent()
                        // Чёрная линия по центру
                        drawLine(
                            color = Color.Black,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 4.dp.toPx()
                        )
                    }
            ) {
                // 🔥 Индикатор — конвертация через with(density) { ...toDp() }
                if (isLeftSplitterDragging) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .offset(x = with(density) { leftSplitterOffset.toDp() })
                            .background(Color(0xFF2196F3))
                            .zIndex(2f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f - leftWeight)
                    .fillMaxHeight()
                    .background(Color.White)
            ) {
                HeaderTable(actions = headerActions)

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                    DeviceSidebar(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .background(Color(0xFFF5F5F5))
                    )

                    // 🔥 СПЛИТТЕР САЙДБАРА
                    var sidebarSplitterOffset by remember { mutableStateOf(0f) }
                    var isSidebarSplitterDragging by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .zIndex(1f)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .background(if (isSidebarSplitterDragging) Color(0xFF2196F3).copy(alpha = 0.3f) else Color.Transparent)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        isSidebarSplitterDragging = true
                                        sidebarSplitterOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        sidebarSplitterOffset += dragAmount.x
                                    },
                                    onDragEnd = {
                                        isSidebarSplitterDragging = false
                                        if (sidebarSplitterOffset != 0f) {
                                            val deltaDp = with(density) { sidebarSplitterOffset.toDp() }
                                            sidebarWidth = (sidebarWidth + deltaDp)
                                                .coerceAtLeast(80.dp)
                                                .coerceAtMost(400.dp)
                                        }
                                        sidebarSplitterOffset = 0f
                                    },
                                    onDragCancel = {
                                        isSidebarSplitterDragging = false
                                        sidebarSplitterOffset = 0f
                                    }
                                )
                            }
                            .drawWithContent {
                                drawContent()
                                // Чёрная линия по центру
                                drawLine(
                                    color = Color.Black,
                                    start = Offset(size.width / 2, 0f),
                                    end = Offset(size.width / 2, size.height),
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                    ) {
                        // 🔥 Индикатор — конвертация через with(density) { ...toDp() }
                        if (isSidebarSplitterDragging) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .offset(x = with(density) { sidebarSplitterOffset.toDp() })
                                    .background(Color(0xFF2196F3))
                                    .zIndex(2f)
                            )
                        }
                    }

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