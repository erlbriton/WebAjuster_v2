package org.example.project.utils

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Универсальная кнопка-иконка с тултипом
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableIconButton(
    icon: ImageVector? = null,
    text: String? = null,
    tooltipText: String,
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltipText, fontSize = 12.sp) } },
        state = rememberTooltipState()
    ) {
        Surface(
            modifier = Modifier
                .padding(start = 4.dp)
                .clickable { onClick() }
                .height(24.dp),
            color = backgroundColor,
            border = if (backgroundColor != Color.Transparent) BorderStroke(1.dp, Color.Gray) else null
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                if (text != null) {
                    Text(
                        text = text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Простой пункт меню
 */
@Composable
fun UniversalMenuItem(
    label: String,
    itemHeight: Dp = 24.dp,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.height(itemHeight),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    )
}

/**
 * Пункт меню с иконкой
 */
//@Composable
//fun iconsMenu(
//    label: String,
//    itemHeight: Dp = 24.dp,
//    icon: ImageVector? = null,
//    iconColor: Color = Color.Black,
//    onClick: () -> Unit
//) {
//    DropdownMenuItem(
//        text = {
//            Row(verticalAlignment = Alignment.CenterVertically) {
//                if (icon != null) {
//                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = iconColor)
//                    Spacer(Modifier.width(8.dp))
//                }
//                Text(label, fontSize = 12.sp)
//            }
//        },
//        modifier = Modifier.height(itemHeight),
//        onClick = onClick,
//        contentPadding = PaddingValues(horizontal = 8.dp)
//    )
//}

/**
 * Селектор (выпадающий список) для выбора области памяти и т.д.
 */
//@Composable
//fun UniversalSelector(
//    label: String,
//    selectedOption: String,
//    options: List<String>,
//    tooltipText: String,
//    minWidth: Dp = 60.dp,
//    onOptionSelected: (String) -> Unit
//) {
//    var expanded by remember { mutableStateOf(false) }
//
//    Box(modifier = Modifier.padding(start = 4.dp)) {
//        Row(
//            modifier = Modifier
//                .border(1.dp, Color.Blue)
//                .background(Color.White)
//                .height(24.dp)
//                .widthIn(min = minWidth)
//                .clickable { expanded = true }
//                .padding(horizontal = 4.dp),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.SpaceBetween
//        ) {
//            Text(selectedOption, fontSize = 11.sp)
//            Icon(androidx.compose.material.icons.Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
//        }
//
//        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
//            options.forEach { option ->
//                DropdownMenuItem(
//                    text = { Text(option, fontSize = 12.sp) },
//                    onClick = {
//                        onOptionSelected(option)
//                        expanded = false
//                    }
//                )
//            }
//        }
//    }
//}