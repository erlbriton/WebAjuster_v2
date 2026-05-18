package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.TableConfig
import org.example.project.utils.ManualAndAutoInputField
import org.example.project.utils.UniversalSelector
import org.example.project.viewmodels.LocalMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineTwoTable(
    thickness: Dp = TableConfig.lineThickness,
    color: Color = TableConfig.lineColor
) {
    val vm = LocalMainViewModel.current // Подключаем ViewModel

    var selectedProtocol by remember { mutableStateOf("Modbus RTU") }
    val protocolOptions = listOf("Modbus RTU", "Modbus TCP")

    var chosenSpeed by remember { mutableStateOf("115200") }
    val speedOptions = listOf("921600", "460800", "230400", "115200", "57600", "38400", "19200", "9600")

    var frameEndInput by remember { mutableStateOf("20") }
    var addressInput by remember { mutableStateOf("x01") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
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

                    Spacer(modifier = Modifier.width(3.dp))

                    // === БЛОК ОТОБРАЖЕНИЯ ИМЕНИ ПЕРЕХОДНИКА ===
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Лейбл "COM" теперь снаружи, как у остальных полей
                        Text(
                            text = "COM",
                            color = Color.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(end = 3.dp)
                        )

                        // Нередактируемое окошко — внутри ТОЛЬКО имя чипа
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .widthIn(min = 55.dp) // Минимальная ширина для красивого выравнивания
                                .border(1.dp, Color.Gray, RoundedCornerShape(2.dp))
                                .background(Color(0xFFE0E0E0)) // Серый фон нередактируемого поля
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = vm.selectedComPort.ifEmpty { "—" },
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(3.dp))

                    UniversalSelector(
                        label = "BPS",
                        selectedOption = chosenSpeed,
                        options = speedOptions,
                        tooltipText = "Скорость обмена (бит/с)",
                        minWidth = 60.dp,
                        onOptionSelected = { chosenSpeed = it }
                    )

                    Spacer(modifier = Modifier.width(3.dp))

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
        HorizontalDivider(thickness = thickness, color = color)
    }
}