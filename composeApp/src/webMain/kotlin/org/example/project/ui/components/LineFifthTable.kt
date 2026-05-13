package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import org.example.project.viewmodels.LocalMainViewModel

@Composable
fun LineFifthTable(
    thickness: Dp = TableConfig.lineThickness,
    color: Color = TableConfig.lineColor
) {
    val vm = LocalMainViewModel.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp) // Немного увеличил высоту для удобства клика по кнопкам
                .background(TableConfig.headerBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))

            // Поле "Место установки"
            ManualAndAutoInputField(
                label = "Место установки",
                value = vm.installationLocation,
                tooltipText = "Место установки устройства",
                windowColor = Color.White,
                width = 120.dp,
                onValueChange = { vm.installationLocation = it }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Кнопка "Сохранить"
            TableIconButton(
                text = "Сохранить изменения",
                backgroundColor = Color.LightGray,
                tooltipText = "Сохранить параметры в локальной базе",
                onClick = {
                    println("DEBUG: Сохранение для ${vm.installationLocation}")
                }
            )

            // "Пружина", чтобы прижать кнопку Обновить к правому краю
            Spacer(modifier = Modifier.weight(1f))

            // Кнопка "Обновить"
            TableIconButton(
                icon = Icons.Default.Refresh,
                text = "Обновить",
                iconColor = Color(0xFF00AA00),
                tooltipText = "Обновить параметры из контроллера",
                backgroundColor = Color.LightGray,
                onClick = {
                    println("DEBUG: Запрос данных из контроллера...")
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = thickness,
            color = color
        )
    }
}