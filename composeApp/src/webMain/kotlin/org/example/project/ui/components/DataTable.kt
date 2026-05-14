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

        // УРОВЕНЬ 1: Группы (ПАРАМЕТРЫ, БАЗА, КОНТРОЛЛЕР)
        Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            // ПАРАМЕТРЫ: Индексы 0, 1, 2, 3
            Box(
                modifier = Modifier
                    .weight(weights[0] + weights[1] + weights[2] + weights[3])
                    .fillMaxHeight()
                    .border(0.5.dp, ColorBorder),
                contentAlignment = Alignment.Center
            ) {
                Text("ПАРАМЕТРЫ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // БАЗА: Индексы 4, 5
            Box(
                modifier = Modifier
                    .weight(weights[4] + weights[5])
                    .fillMaxHeight()
                    .border(0.5.dp, ColorBorder),
                contentAlignment = Alignment.Center
            ) {
                Text("БАЗА", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // КОНТРОЛЛЕР: Индексы 6, 7
            Box(
                modifier = Modifier
                    .weight(weights[6] + weights[7])
                    .fillMaxHeight()
                    .border(0.5.dp, ColorBorder),
                contentAlignment = Alignment.Center
            ) {
                Text("КОНТРОЛЛЕР", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // УРОВЕНЬ 2: Имена столбцов (Строго по порядку 0-7)
        Row(modifier = Modifier.fillMaxWidth().height(26.dp).background(Color(0xFFF5F5F5))) {
            DynamicHeaderCell(text = "№", weight = weights[0])
            DynamicHeaderCell(text = "Имя", weight = weights[1])
            DynamicHeaderCell(text = "Описание", weight = weights[2])
            DynamicHeaderCell(text = "Ед.изм", weight = weights[3])

            DynamicHeaderCell(text = "hex", weight = weights[4])
            DynamicHeaderCell(text = "Physical", weight = weights[5])

            DynamicHeaderCell(text = "hex", weight = weights[6])
            DynamicHeaderCell(text = "Physical", weight = weights[7])
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
        DynamicReadOnlyCell(text = param.code, weight = weights[0], align = TextAlign.Center)
        DynamicReadOnlyCell(text = param.idName, weight = weights[1])
        DynamicReadOnlyCell(text = param.description, weight = weights[2])
        DynamicReadOnlyCell(text = param.unit, weight = weights[3], align = TextAlign.Center)

        EditableCell(weight = weights[4], value = param.hexBase) { param.hexBase = it }
        EditableCell(weight = weights[5], value = param.physBase) { param.physBase = it }

        EditableCell(weight = weights[6], value = param.hexCtrl) { param.hexCtrl = it }
        EditableCell(weight = weights[7], value = param.physCtrl) { param.physCtrl = it }
    }
}

@Composable
private fun RowScope.DynamicReadOnlyCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .border(0.5.dp, ColorBorder)
            .padding(horizontal = 4.dp),
        contentAlignment = if (align == TextAlign.Center) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RowScope.EditableCell(weight: Float, value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier.weight(weight).fillMaxHeight().border(0.5.dp, ColorBorder).padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 10.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RowScope.DynamicHeaderCell(text: String, weight: Float) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .border(0.5.dp, ColorBorder)
            .background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}