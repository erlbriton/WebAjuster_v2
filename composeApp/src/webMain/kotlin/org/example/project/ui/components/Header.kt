/*Header.kt
 Файл верстки верхней строки
 */

package org.example.project.components

//import org.example.project.oscilloscope.OscilloscopeState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.project.jsinterop.startModbusConnection
import org.example.project.logic.HeaderActionsInterface
import org.example.project.logic.readDeviceIdentification
import org.example.project.ui.TableConfig
import org.example.project.utils.TableIconButton
import org.example.project.utils.UniversalMenuItem
import org.example.project.utils.UniversalSelector
import org.example.project.utils.iconsMenu
import org.example.project.viewmodels.LocalMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderTable(
    actions: HeaderActionsInterface,
    thickness: Dp = TableConfig.lineThickness,
    color: Color = TableConfig.lineColor
) {
    // Состояния для меню
    var expanded by remember { mutableStateOf(false) }
    val menuItems = listOf(
        "Обновить",
        "Серийные номера",
        "Место установки",
        "Тип механизма",
        "Дата последнего обслуживания",
        "Тип устройства"
    )

    var clue by remember { mutableStateOf(false) }
    val oscilligraphItems = listOf(
        "Открыть осциллогаф подключенного устройства",
        "Открыть осциллограф",
        "Просмотреть осциллогамму",
        "Новый осциллограф"
    )

    var clueHelp by remember { mutableStateOf(false) }
    val helpItems = listOf("Ajuster Help", "About")

    // Состояния для селектора памяти
    var selectedMemory by remember { mutableStateOf("Flash") }
    val memoryOptions = listOf("Flash", "CD", "RAM")

    //Состояние для выбора папки/файла
    var selectFile by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val vm = LocalMainViewModel.current

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = thickness, color = color)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(TableConfig.headerBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --------------------------------КНОПКА ОБНОВИТЬ -------------------------------------
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text("Обновить список устройств", fontSize = 12.sp) } },
                state = rememberTooltipState()
            ) {
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .border(1.dp, Color.Blue)
                            .background(Color.White)
                            .height(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ЛЕВАЯ ЧАСТЬ: Действие по умолчанию (Обновить)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable {
                                    println("Выполнено: ${menuItems[0]}")
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF04C104)
                            )
                        }
                        // РАЗДЕЛИТЕЛЬ
                        Spacer(
                            modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Blue)
                        )
                        // ПРАВАЯ ЧАСТЬ: Открытие меню
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable { expanded = true }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        menuItems.forEachIndexed { index, label ->
                            iconsMenu(
                                label = label,
                                itemHeight = 24.dp,
                                icon = if (index == 0) Icons.Default.Build else null,
                                iconColor = Color(0xFFC7092F),
                                onClick = {
                                    expanded = false
                                    println("Выбрано: $label")
                                }
                            )
                        }
                    }
                }
            }
            //-------------------------------------Поиск устройств в сети Modbus--------------------
            TableIconButton(
                icon = Icons.Default.Search,
                tooltipText = "Поиск устройств в сети Modbus",
                onClick = {
                    actions.onSearch()
                }
            )
            //----------------------------------Отчеты Exel----------------------------------------
            TableIconButton(
                icon = Icons.AutoMirrored.Filled.ListAlt, tooltipText = "Генератор отчетов в Exel",
                onClick = { actions.onExel() }
            )
            // --- 4. ОСЦИЛЛОГРАФ ---
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text("Осциллограф", fontSize = 12.sp) } },
                state = rememberTooltipState()
            ) {
                Box(modifier = Modifier.padding(start = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .border(1.dp, Color.Blue)
                            .background(Color.White)
                            .height(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable {
                                    // Переключаем состояние окна (открыто/закрыто) прямо во ViewModel
                                    vm.isOscilloscopeWindowOpen = !vm.isOscilloscopeWindowOpen
                                    println("Осциллограф открыт: ${vm.isOscilloscopeWindowOpen}")
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.Red
                            )
                        }
                        Spacer(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color.Blue)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable { clue = true }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = clue,
                        onDismissRequest = { clue = false },
                        modifier = Modifier.widthIn(min = 300.dp, max = 600.dp)
                    ) {
                        oscilligraphItems.forEach { label ->
                            UniversalMenuItem(
                                label = label,
                                itemHeight = 16.dp,
                                onClick = {
                                    clue = false
                                }
                            )
                        }
                    }
                }
            }
            // 5. Терминал
            TableIconButton(
                icon = Icons.Default.Terminal,
                tooltipText = "Терминал",
                onClick = {
                    actions.onTerminalOpen()
                }
            )
            //----------------------------Help---------------------------------------------------------------------
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text("Help", fontSize = 12.sp) } },
                state = rememberTooltipState()
            ) {
                Box(modifier = Modifier.padding(start = 4.dp)) {
                    Row(
                        modifier = Modifier.clickable { clueHelp = true }.border(1.dp, Color.Blue)
                            .background(Color.White).padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = clueHelp, onDismissRequest = { clueHelp = false }) {
                        helpItems.forEach { label ->
                            UniversalMenuItem(
                                label = label,
                                itemHeight = 16.dp,
                                onClick = { clueHelp = false }
                            )
                        }
                    }
                }
            }
            // -----------------------------------Файловые операции------------------------------------------------------
            TableIconButton(
                icon = Icons.Default.Save,
                tooltipText = "Файловые операции",
                onClick = {
                    actions.onFileOration()
                }
            )
            // 8. Черный ящик
            TableIconButton(
                icon = Icons.Default.ViewInAr,
                tooltipText = "Черный ящик",
                onClick = {
                    actions.onBlackBox()
                }
            )
            // -------------------------------------Выбор области памяти CPU------------------------
            UniversalSelector(
                label = "",
                selectedOption = vm.selectedMemoryArea, // Читаем текущее состояние из VM
                options = memoryOptions,
                tooltipText = "Выбор области памяти",
                minWidth = 45.dp,
                onOptionSelected = { chosenArea ->
                    // Передаем выбранную область (Flash, CD, RAM) во ViewModel
                    vm.changeMemoryArea(chosenArea)
                }
            )
            // ----------------------------Выбор папки/файла-----------------------------------------

            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text("Выбор файла", fontSize = 12.sp) } },
                state = rememberTooltipState()
            ) {
                Box(modifier = Modifier.padding(start = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .border(1.dp, Color.Blue)
                            .background(Color.White)
                            .height(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ЛЕВАЯ ЧАСТЬ: Иконка папки
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .fillMaxHeight()
                                .clickable {
                                    actions.onPickFileRequest()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF046308)
                            )
                        }

                        // РАЗДЕЛИТЕЛЬ
                        Spacer(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color.Blue)
                        )

                        // ПРАВАЯ ЧАСТЬ: Стрелочка (Просто открывает меню)
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight()
                                .clickable { selectFile = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Выпадающее меню
                    DropdownMenu(expanded = selectFile, onDismissRequest = { selectFile = false }) {
                        UniversalMenuItem(
                            label = "Файл",
                            itemHeight = 16.dp,
                            onClick = {
                                println("DEBUG: Клик по пункту Файл сработал!")
                                selectFile = false
                                // Запускаем через встроенный в браузер планировщик,
                                // чтобы окно открылось СРАЗУ ПОСЛЕ закрытия меню в том же потоке жестов
                                actions.onPickFileRequest()
                            }
                        )
                        UniversalMenuItem(
                            label = "Папка",
                            itemHeight = 16.dp,
                            onClick = {
                                selectFile = false
                                actions.onPickDirectoryRequest()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(5.dp))
            //----------------------Кнопка "Подключиться"-------------------------------------------
            TableIconButton(
                text = "ПОДКЛЮЧИТЬСЯ",
                tooltipText = "Получить ID устройства, найти его в базе и загрузить уставки",
                backgroundColor = Color(0xFFC2B7B7),
                onClick = {
                    actions.onFileOration()
                    startModbusConnection()
                }
            )
            Spacer(modifier = Modifier.width(5.dp))
            //----------------------Кнопка "ID"-----------------------------------------------------
            TableIconButton(
                text = "ID",
                tooltipText = "Получить ID устройства",
                backgroundColor = Color(0xFFC2B7B7),
                onClick = {
                    scope.launch {
                        readDeviceIdentification()
                    }
                }
            )
        } // Конец Row

        // Нижняя сплошная линия-разделитель панели инструментов
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = thickness,
            color = color
        )
    }
}