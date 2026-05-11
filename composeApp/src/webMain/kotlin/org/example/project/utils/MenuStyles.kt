package org.example.project.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единый шаблон для всех пунктов меню
 */
@Composable
fun UniversalMenuItem(
    label: String,
    itemHeight: Dp,
    fontFamily: FontFamily = FontFamily.Monospace,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                softWrap = false,
                maxLines = 1
            )
        },
        modifier = Modifier
            .height(itemHeight)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        onClick = onClick
    )
}

/**
 * Функция для окна с селектором (например, выбор области памяти Flash/RAM)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSelector(
    label: String,
    selectedOption: String,
    options: List<String>,
    tooltipText: String,
    minWidth: Dp = 40.dp,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isEnabled = options.isNotEmpty()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltipText, fontSize = 12.sp) } },
        state = rememberTooltipState()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = Color.Black, // Исправил на черный для видимости на сером фоне
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }
            Box {
                Row(
                    modifier = Modifier
                        .border(1.dp, if (isEnabled) Color.Gray else Color.DarkGray)
                        .background(Color.White)
                        .height(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .background(if (isEnabled) Color(0xFF0066CC) else Color.Gray)
                            .padding(horizontal = 4.dp)
                            .widthIn(min = minWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedOption.uppercase(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(22.dp)
                            .background(if (isEnabled) Color(0xFFE0E0E0) else Color(0xFFB0B0B0))
                            .clickable(enabled = isEnabled) { expanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isEnabled) Color.Black else Color.White
                        )
                    }
                }

                if (isEnabled) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .background(Color.White)
                            .border(1.dp, Color.Black)
                            .widthIn(min = 120.dp)
                    ) {
                        options.forEach { option ->
                            UniversalMenuItem(
                                label = option,
                                itemHeight = 20.dp,
                                onClick = {
                                    onOptionSelected(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Поле ввода значения вручную
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAndAutoInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    windowColor: Color,
    tooltipText: String,
    minWidth: Dp = 20.dp,
    width: Dp? = null,
    labelColor: Color = Color.Black
) {
    val textStyle = TextStyle(
        color = Color.Black,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltipText, fontSize = 12.sp) } },
        state = rememberTooltipState()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth()
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 4.dp)
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .then(
                        if (width != null) Modifier.width(width)
                        else Modifier.width(IntrinsicSize.Min).widthIn(min = minWidth)
                    )
                    .height(24.dp)
                    .border(1.dp, Color.Gray)
                    .background(windowColor),
                textStyle = textStyle,
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        innerTextField()
                    }
                }
            )
        }
    }
}

/**
 * Универсальная кнопка с иконкой и/или текстом в рамке
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableIconButton(
    icon: ImageVector? = null,
    text: String? = null,
    tooltipText: String,
    borderColor: Color = Color.Blue,
    backgroundColor: Color = Color.White,
    contentColor: Color = Color.Black,
    iconColor: Color = contentColor,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltipText, fontSize = 14.sp) } },
        state = rememberTooltipState()
    ) {
        Box(modifier = Modifier.padding(start = 4.dp)) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .clickable { onClick() }
                    .border(1.dp, borderColor)
                    .background(backgroundColor)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = iconColor
                    )
                    if (text != null) Spacer(modifier = Modifier.width(6.dp))
                }

                if (text != null) {
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Пункт меню с иконкой
 */
@Composable
fun iconsMenu(
    label: String,
    icon: ImageVector? = null,
    itemHeight: Dp = 24.dp,
    onClick: () -> Unit,
    iconColor: Color = Color.Black
) {
    DropdownMenuItem(
        modifier = Modifier.height(itemHeight),
        onClick = onClick,
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        tint = iconColor
                    )
                }
                Text(
                    text = label,
                    fontSize = 11.sp,
                    softWrap = false
                )
            }
        }
    )
}