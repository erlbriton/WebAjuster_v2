package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

    // Переменная для управления шириной правой панели
    // По умолчанию установим широкую — например, 600.dp (или можно будет вычислить % от экрана)
    var tableWidth by remember { mutableStateOf(600.dp) }

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
        Row(modifier = Modifier.fillMaxSize()) {

            // 1. ЛЕВАЯ ЧАСТЬ: Основная рабочая область (занимает всё свободное место)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFECECEC)), // Светло-серый фон для контраста
                contentAlignment = Alignment.Center
            ) {
                // Здесь будет основной контент, графики и т.д.
            }

            // 2. РАЗДЕЛИТЕЛЬ (Сплиттер): теперь он СЛЕВА от таблицы
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Когда тянем влево, dragAmount.x отрицательный.
                            // Вычитаем его, чтобы ширина увеличивалась при движении влево.
                            tableWidth -= dragAmount.x.toDp()
                        }
                    }
            )

            // 3. ПРАВАЯ ЧАСТЬ: Твоя таблица
            Column(
                modifier = Modifier
                    .width(tableWidth)
                    .fillMaxHeight()
                    .background(Color.White)
            ) {
                HeaderTable(actions = headerActions)
                LineTwoTable()
                LineThirdTable(selectedDevice = null)
                LineFourthTable()
                LineFifthTable()

                // Место для будущей таблицы параметров (CreatorColumn)
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {
                    // Тут будет список параметров
                }
            }
        }
    }
}