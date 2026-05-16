package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.TableConfig
import org.example.project.models.DeviceInfoIni


@Composable
fun LineThirdTable(
    thickness: Dp = TableConfig.lineThickness,
    color: Color = TableConfig.lineColor,
    selectedDevice: DeviceInfoIni? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(25.dp)
                .background(TableConfig.ThirdBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ID:",
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp, // Немного уменьшил, чтобы соответствовать высоте 25.dp
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Окно вывода ID
            Box(
                modifier = Modifier
                    .weight(1f) // Занимает всё доступное пространство
                    .fillMaxHeight(0.8f)
                    .padding(end = 8.dp)
                    .background(Color.Gray, shape = MaterialTheme.shapes.extraSmall)
                    .border(width = 1.dp, color = Color.LightGray, shape = MaterialTheme.shapes.extraSmall),
                contentAlignment = Alignment.CenterStart // Базовое выравнивание для содержимого
            ) {
                BasicTextField(
                    value = selectedDevice?.id ?: "---",
                    onValueChange = {}, // Пустая лямбда, так как поле только для чтения
                    readOnly = true,    // Полностью запрещаем редактирование и вызов клавиатуры
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFAFAFA),
                        // Принудительно заставляем движок Canvas выровнять текст по центру строки
                        platformStyle = null
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = thickness,
            color = color
        )
    }
}