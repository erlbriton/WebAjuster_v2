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
import org.example.project.logic.HeaderActionsButtons // Импортируем твой класс
import org.example.project.components.HeaderTable
import org.example.project.models.DeviceInfoIni

@Composable
fun MainScreen() {
    // 1. Инициализируем ViewModel и Scope
    val viewModel = remember { MainViewModel() }
    val scope = rememberCoroutineScope()

    // 2. Состояния для размеров и диалогов
    var totalContentWidth by remember { mutableStateOf(900.dp) }
    var sidebarWidth      by remember { mutableStateOf(200.dp) }
    var errorMessage      by remember { mutableStateOf("") }
    var showErrorDialog   by remember { mutableStateOf(false) }

    // 3. Создаем "двигатель" логики. Теперь scope не будет серым!
    val headerActions = remember(scope, viewModel) {
        HeaderActionsButtons(
            mainViewModel = viewModel,
            scope = scope,
            onDeviceLoaded = { info ->
                // Сюда код придет, когда файл выбран и распарсен
                println("DEBUG: Устройство подгружено в UI: ${info.Description}")
            },
            ShowError = { message ->
                errorMessage = message
                showErrorDialog = true
            }
        )
    }

    // 4. Основная верстка
    CompositionLocalProvider(LocalMainViewModel provides viewModel) {
        Row(modifier = Modifier.fillMaxSize().background(Color.White)) {

            // Распорка слева (центрируем таблицу)
            Spacer(modifier = Modifier.weight(1f))

            // Левая граница-сплиттер (меняет общую ширину)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color.Gray.copy(alpha = 0.3f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            totalContentWidth -= dragAmount.x.toDp()
                        }
                    }
            )

            // Весь рабочий блок таблицы
            Column(
                modifier = Modifier
                    .width(totalContentWidth)
                    .fillMaxHeight()
            ) {
                // ШАПКА: Передаем сюда наши активные действия
                HeaderTable(actions = headerActions)

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                    // САЙДБАР (Список устройств слева)
                    Box(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .background(Color(0xFFF0F0F0))
                    ) {
                        // Здесь в будущем будет список устройств из devicesMap
                    }

                    // СПЛИТТЕР (Между сайдбаром и основной частью)
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

                    // ПРАВАЯ ЧАСТЬ: Таблицы параметров
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        LineTwoTable()
                        LineThirdTable(selectedDevice = null)
                        LineFourthTable()
                        // В LineFifthTable тоже можно передать actions, если там кнопка "Обновить"
                        LineFifthTable()

                        // ОСНОВНАЯ ТАБЛИЦА ДАННЫХ
                        DataTable(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    // 5. Диалог ошибки (если файл не подошел)
    if (showErrorDialog) {
        // Здесь можно вставить AlertDialog, если он подключен в проекте
        println("ERROR: $errorMessage")
        // Не забудь сбрасывать: showErrorDialog = false
    }
}