package org.example.project.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.logic.HeaderActions
import org.example.project.ui.TableConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderTable(
    actions: HeaderActions
) {
    var expanded by remember { mutableStateOf(false) }
    val menuItems = listOf("Обновить", "Серийные номера", "Место установки")

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = TableConfig.lineThickness, color = TableConfig.lineColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp) // Чуть увеличил для кликабельности в вебе
                .background(TableConfig.headerBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // КНОПКА ОБНОВИТЬ (Упрощенная версия для запуска)
            Box(modifier = Modifier.padding(start = 8.dp)) {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Build, null, tint = Color(0xFF04C104))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    menuItems.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 12.sp) },
                            onClick = { expanded = false }
                        )
                    }
                }
            }

            // Стандартные кнопки-иконки (пока без кастомных TableIconButton)
            HeaderIconButton(Icons.Default.Search, "Поиск") { actions.onSearch() }
            HeaderIconButton(Icons.AutoMirrored.Filled.ListAlt, "Exel") { actions.onExel() }
            HeaderIconButton(Icons.AutoMirrored.Filled.ShowChart, "Осциллограф", Color.Red) { }
            HeaderIconButton(Icons.Default.Terminal, "Терминал") { actions.onTerminalOpen() }

            Spacer(modifier = Modifier.width(20.dp))

            // Кнопка ПОДКЛЮЧИТЬСЯ
            Button(
                onClick = { actions.onFileOration() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2B7B7)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text("ПОДКЛЮЧИТЬСЯ", color = Color.Black, fontSize = 10.sp)
            }

            Spacer(modifier = Modifier.weight(1f))
        }
        HorizontalDivider(thickness = TableConfig.lineThickness, color = TableConfig.lineColor)
    }
}

@Composable
fun HeaderIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tooltip: String, tint: Color = Color.Black, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp).padding(4.dp)) {
        Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(18.dp))
    }
}