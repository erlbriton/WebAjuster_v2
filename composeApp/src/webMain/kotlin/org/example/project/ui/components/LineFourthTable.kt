package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.project.ui.TableConfig
import org.example.project.utils.ManualAndAutoInputField
import org.example.project.utils.TableIconButton
import org.example.project.viewmodel.LocalMainViewModel

// Правильный способ вызова JS в Kotlin Wasm
@JsFun("(function() { var d = new Date(); var day = ('0' + d.getDate()).slice(-2); var month = ('0' + (d.getMonth() + 1)).slice(-2); var year = d.getFullYear(); return day + '.' + month + '.' + year; })")
external fun getJsDateString(): String

@Composable
fun LineFourthTable(
    thickness: Dp = TableConfig.lineThickness,
    color: Color = TableConfig.lineColor
) {
    // Получаем доступ к ViewModel через CompositionLocal
    val vm = LocalMainViewModel.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp) // Чуть увеличил, чтобы поля ввода (24.dp) не зажимало
                .background(TableConfig.TwoBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))

            // Окно "Тип механизма"
            ManualAndAutoInputField(
                label = "Тип механизма",
                value = vm.typeMechanism,
                tooltipText = "Тип механизма",
                windowColor = Color.White,
                width = 120.dp,
                onValueChange = { vm.typeMechanism = it }
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Окно "Дата"
            ManualAndAutoInputField(
                label = "Дата",
                value = vm.dateSet,
                tooltipText = "Дата",
                windowColor = Color.White,
                width = 80.dp,
                onValueChange = { vm.dateSet = it }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Кнопка "Сегодня"
            TableIconButton(
                text = "Сегодня",
                tooltipText = "Использовать текущую дату",
                backgroundColor = Color(0xFFBBAFAF),
                onClick = {
                    vm.dateSet = getJsDateString()
                }
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = thickness,
            color = color
        )
    }
}