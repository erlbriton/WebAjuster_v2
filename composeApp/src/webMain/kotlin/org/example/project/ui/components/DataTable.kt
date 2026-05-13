package org.example.project.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.models.ParameterData
import org.example.project.viewmodels.LocalMainViewModel

private val FIXED_WIDTH = 50.dp
private val ARROW_SEP_WIDTH = 28.dp
private val ColorBorder = Color(0xFF9E9E9E)

@Composable
fun DataTable(modifier: Modifier = Modifier) {
    val vm = LocalMainViewModel.current
    val weights = vm.colWeights

    Column(modifier = modifier.fillMaxSize().border(0.5.dp, ColorBorder)) {
        HeaderSection(weights)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(vm.parameters) { _, param ->
                ParameterRow(param, weights) { vm.selectRow(param.code) }
            }
        }
    }
}

@Composable
private fun HeaderSection(weights: List<Float>) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0))) {
        // УРОВЕНЬ 1: Группы
        Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            // ПАРАМЕТРЫ: объединяем №, Имя, Описание, Ед.изм
            Box(
                modifier = Modifier
                    .width(FIXED_WIDTH * 2)
                    .weight(weights[0] + weights[1])
                    .fillMaxHeight()
                    .border(0.5.dp, ColorBorder),
                contentAlignment = Alignment.Center
            ) { Text("ПАРАМЕТРЫ", fontSize = 11.sp, fontWeight = FontWeight.Bold) }

            // БАЗА: hex + phys
            Box(
                modifier = Modifier.weight(weights[2] + weights[3]).fillMaxHeight().border(0.5.dp, ColorBorder),
                contentAlignment = Alignment.Center
            ) { Text("БАЗА", fontSize = 11.sp, fontWeight = FontWeight.Bold) }

            // КОНТРОЛЛЕР: hex + phys
            Box(
                modifier = Modifier.weight(weights[4] + weights[5]).fillMaxHeight().border(0.5.dp, ColorBorder),
                contentAlignment = Alignment.Center
            ) { Text("КОНТРОЛЛЕР", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }

        // УРОВЕНЬ 2: Имена столбцов
        Row(modifier = Modifier.fillMaxWidth().height(26.dp).background(Color(0xFFF5F5F5))) {
            HeaderCell(width = FIXED_WIDTH, text = "№")
            DynamicHeaderCell(weight = weights[0], text = "Имя")
            DynamicHeaderCell(weight = weights[1], text = "Описание")
            HeaderCell(width = FIXED_WIDTH, text = "Ед.изм")
            DynamicHeaderCell(weight = weights[2], text = "hex")
            DynamicHeaderCell(weight = weights[3], text = "Physical")
            DynamicHeaderCell(weight = weights[4], text = "hex")
            DynamicHeaderCell(weight = weights[5], text = "Physical")
        }
    }
}
@Composable
private fun ParameterRow(param: ParameterData, weights: List<Float>, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .background(if (param.isSelected) Color(0xFFB3E5FC) else Color.White)
            .clickable { onClick() }
    ) {
        ReadOnlyCell(text = param.code, width = FIXED_WIDTH, align = TextAlign.Center)
        DynamicReadOnlyCell(text = param.idName, weight = weights[0])
        DynamicReadOnlyCell(text = param.description, weight = weights[1])
        ReadOnlyCell(text = param.unit, width = FIXED_WIDTH, align = TextAlign.Center)
        EditableCell(weight = weights[2], value = param.hexBase) { param.hexBase = it }
        EditableCell(weight = weights[3], value = param.physBase) { param.physBase = it }
        EditableCell(weight = weights[4], value = param.hexCtrl) { param.hexCtrl = it }
        EditableCell(weight = weights[5], value = param.physCtrl) { param.physCtrl = it }
    }
}

// Унифицированные методы отрисовки с явными границами
@Composable
private fun HeaderCell(width: androidx.compose.ui.unit.Dp, text: String) {
    Box(modifier = Modifier.width(width).fillMaxHeight().border(0.5.dp, ColorBorder), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RowScope.DynamicHeaderCell(weight: Float, text: String) {
    Box(modifier = Modifier.weight(weight).fillMaxHeight().border(0.5.dp, ColorBorder), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReadOnlyCell(text: String, width: androidx.compose.ui.unit.Dp, align: TextAlign) {
    Box(modifier = Modifier.width(width).fillMaxHeight().border(0.5.dp, ColorBorder).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, fontSize = 10.sp, textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.DynamicReadOnlyCell(text: String, weight: Float) {
    Box(modifier = Modifier.weight(weight).fillMaxHeight().border(0.5.dp, ColorBorder).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.EditableCell(weight: Float, value: String, onValueChange: (String) -> Unit) {
    Box(modifier = Modifier.weight(weight).fillMaxHeight().border(0.5.dp, ColorBorder).padding(horizontal = 2.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 10.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}