package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.example.project.ui.components.*
import org.example.project.viewmodel.MainViewModel
import org.example.project.viewmodel.LocalMainViewModel
import org.example.project.logic.HeaderActions

@Composable
fun MainScreen() {
    val viewModel = remember { MainViewModel() }

    // 1. ОБЩАЯ ШИРИНА таблицы (меняется, когда тянешь за левый край)
    var totalContentWidth by remember { mutableStateOf(900.dp) }

    // 2. ШИРИНА САЙДБАРА (внутри таблицы)
    var sidebarWidth by remember { mutableStateOf(200.dp) }

    val headerActions = remember {
        object : HeaderActions {
            override fun onSearch() {}
            override fun onExel() {}
            override fun onTerminalOpen() {}
            override fun onFileOration() {}
            override fun onBlackBox() {}
            override fun onPickFileRequest() {}
            override fun onPickDirectoryRequest() {}
        }
    }

    CompositionLocalProvider(LocalMainViewModel provides viewModel) {
        Row(modifier = Modifier.fillMaxSize().background(Color.White)) {

            // Распорка слева (пустое место браузера)
            Spacer(modifier = Modifier.weight(1f))

            // --- ЛЕВАЯ ГРАНИЦА ВСЕЙ ТАБЛИЦЫ (АКТИВНАЯ) ---
            Box(
                modifier = Modifier
                    .width(6.dp) // Чуть шире для удобства захвата
                    .fillMaxHeight()
                    .background(Color.Gray) // Видимая линия
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Когда тянем ВЛЕВО (минус), ширина всей таблицы должна УВЕЛИЧИТЬСЯ
                            totalContentWidth -= dragAmount.x.toDp()
                        }
                    }
            )

            // ВЕСЬ РАБОЧИЙ БЛОК
            Column(
                modifier = Modifier
                    .width(totalContentWidth)
                    .fillMaxHeight()
            ) {

                // ШАПКА
                HeaderTable(actions = headerActions)

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                    // САЙДБАР (Устройства)
                    Box(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .background(Color(0xFFF0F0F0))
                    )

                    // ВНУТРЕННИЙ РАЗДЕЛИТЕЛЬ (Сплиттер)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(Color.LightGray)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // Меняет пропорции внутри таблицы
                                    sidebarWidth += dragAmount.x.toDp()
                                }
                            }
                    )

                    // САМА ТАБЛИЦА (Данные)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        LineTwoTable()
                        LineThirdTable(selectedDevice = null)
                        LineFourthTable()
                        LineFifthTable()

                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)))
                    }
                }
            }
        }
    }
}