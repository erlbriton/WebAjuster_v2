//MainScreen.kt

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
import org.example.project.viewmodels.MainViewModel
import org.example.project.viewmodels.LocalMainViewModel
import org.example.project.logic.HeaderActions
import androidx.compose.material3.Text

@Composable
fun MainScreen() {
    val viewModel = remember { MainViewModel() }

    var totalContentWidth by remember { mutableStateOf(900.dp) }
    var sidebarWidth      by remember { mutableStateOf(200.dp) }

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

            // Распорка слева
            Spacer(modifier = Modifier.weight(1f))

            // Левая граница (активная, меняет ширину всей таблицы)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color.Gray)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            totalContentWidth -= dragAmount.x.toDp()
                        }
                    }
            )

            // Весь рабочий блок
            Column(
                modifier = Modifier
                    .width(totalContentWidth)
                    .fillMaxHeight()
            ) {
                // Шапка
                HeaderTable(actions = headerActions)

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                    // Сайдбар (список устройств)
                    Box(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .background(Color(0xFFF0F0F0))
                    )

                    // Сплиттер между сайдбаром и таблицей
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(Color.LightGray)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    sidebarWidth = (sidebarWidth + dragAmount.x.toDp())
                                        .coerceAtLeast(80.dp)
                                        .coerceAtMost(400.dp)
                                }
                            }
                    )

                    // Таблица параметров
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

                        // ── ТАБЛИЦА ДАННЫХ (DataTable) ──────────────────
                        DataTable(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}