// Файл: DeviceSidebar.kt
package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.viewmodels.LocalMainViewModel

@Composable
fun DeviceSidebar(modifier: Modifier = Modifier) {
    val vm = LocalMainViewModel.current
    val expandedGroups = remember { mutableStateListOf<String>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(4.dp)
    ) {
        vm.devicesMap.forEach { (location, devices) ->
            val isExpanded = expandedGroups.contains(location)
            val groupName = if (location.isEmpty()) "Unknown" else location

            // Строка группы (Location) с раскрывающимся плюсиком/стрелочкой
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isExpanded) expandedGroups.remove(location)
                        else expandedGroups.add(location)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )
                Text(
                    text = groupName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Счетчик файлов в папке
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = devices.size.toString(),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }

            // Список ID внутри раскрытой группы
            if (isExpanded) {
                devices.forEach { device ->
                    val isSelected = vm.selectedDeviceId == device.id
                    Text(
                        text = device.id,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 4.dp)
                            .background(
                                if (isSelected) Color(0xFFA6E594) else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                            .clickable {
                                // Вызываем логику активации параметров устройства во ViewModel
                                vm.selectDevice(device)
                            }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}