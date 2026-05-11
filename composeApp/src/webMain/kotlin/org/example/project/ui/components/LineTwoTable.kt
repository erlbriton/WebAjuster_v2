package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.TableConfig
import org.example.project.utils.ManualAndAutoInputField
import org.example.project.utils.UniversalSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineTwoTable(
    thickness: Dp = TableConfig.lineThickness,
    color: Color = TableConfig.lineColor
) {
    var selectedProtocol by remember { mutableStateOf("Modbus RTU") }
    val protocolOptions = listOf("Modbus RTU", "Modbus TCP")

    var selectedCom by remember { mutableStateOf("COM1") } // Добавил заглушку для наглядности
    var comOptions by remember { mutableStateOf(listOf("COM1", "COM2", "COM3")) }

    var chosenSpeed by remember { mutableStateOf("115200") }
    val speedOptions = listOf("921600", "460800", "230400", "115200", "57600", "38400", "19200", "9600")

    var frameEndInput by remember { mutableStateOf("20") }
    var addressInput by remember { mutableStateOf("x01") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp) // Чуть увеличил высоту для комфорта
                .background(TableConfig.TwoBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(start = 6.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Настройки связи",
                    color = Color.Black,
                    fontSize = 10.sp,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    UniversalSelector(
                        label = "BUS",
                        selectedOption = selectedProtocol,
                        options = protocolOptions,
                        tooltipText = "Протокол передачи данных",
                        onOptionSelected = { selectedProtocol = it }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    UniversalSelector(
                        label = "COM",
                        selectedOption = selectedCom,
                        options = comOptions,
                        tooltipText = if (comOptions.isEmpty()) "Порты не найдены" else "Выберите доступный COM-порт",
                        minWidth = 45.dp,
                        onOptionSelected = { selectedCom = it }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    UniversalSelector(
                        label = "BPS",
                        selectedOption = chosenSpeed,
                        options = speedOptions,
                        tooltipText = "Скорость обмена (бит/с)",
                        minWidth = 60.dp,
                        onOptionSelected = { chosenSpeed = it }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    ManualAndAutoInputField(
                        label = "FrameEnd",
                        value = frameEndInput,
                        tooltipText = "Время ожидания окончания пакета[mc]",
                        windowColor = Color.White,
                        width = 35.dp,
                        onValueChange = { frameEndInput = it }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ManualAndAutoInputField(
                        label = "Адрес",
                        value = addressInput,
                        tooltipText = "Адрес устройства в сети",
                        windowColor = Color.White,
                        width = 35.dp,
                        onValueChange = { addressInput = it }
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        // Линия-разделитель между строками
        HorizontalDivider(thickness = thickness, color = color)
    }
}