package org.example.project.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.models.ParameterData
import org.example.project.viewmodels.LocalMainViewModel
import org.example.project.viewmodels.MainViewModel

private val ColorBorder = Color(0xFF9E9E9E)

// Отрисовка границ ячейки
fun Modifier.drawTableBorder(right: Boolean = true, bottom: Boolean = true): Modifier = this.drawBehind {
    val strokeWidth = 0.5.dp.toPx()
    if (right) drawLine(ColorBorder, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth)
    if (bottom) drawLine(ColorBorder, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth)
}

@Composable
fun DataTable(modifier: Modifier = Modifier) {
    val vm = LocalMainViewModel.current
    val weights = vm.colWeights
    var totalWidth by remember { mutableStateOf(0f) }

    Column(modifier = modifier
        .fillMaxSize()
        .onGloballyPositioned { totalWidth = it.size.width.toFloat() }
        .drawBehind {
            val sw = 0.5.dp.toPx()
            drawLine(ColorBorder, Offset(0f, 0f), Offset(size.width, 0f), sw)
            drawLine(ColorBorder, Offset(0f, 0f), Offset(0f, size.height), sw)
        }
    ) {
        HeaderSection(weights, totalWidth, vm)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(vm.parameters) { _, param ->
                ParameterRow(param, weights) { vm.selectRow(param.code) }
            }
        }
    }
}

@Composable
private fun HeaderSection(weights: List<Float>, totalWidth: Float, vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0))) {
        // УРОВЕНЬ 1: Группы
        Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            GroupCell("ПАРАМЕТРЫ", weights[0] + weights[1] + weights[2] + weights[3])
            GroupCell("БАЗА", weights[4] + weights[5])
            GroupCell("КОНТРОЛЛЕР", weights[6] + weights[7])
        }

        // УРОВЕНЬ 2: Имена столбцов + Ресайзеры
        Row(modifier = Modifier.fillMaxWidth().height(26.dp).background(Color(0xFFF5F5F5))) {
            val titles = listOf("№", "Имя", "Описание", "Ед.изм", "hex", "Physical", "hex", "Physical")

            titles.forEachIndexed { index, title ->
                Box(modifier = Modifier.weight(weights[index]).fillMaxHeight()) {
                    DynamicHeaderCell(text = title)

                    // Ресайзер для изменения ширины (кроме последнего столбца)
                    if (index < titles.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(8.dp)
                                .align(Alignment.CenterEnd)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .draggable(
                                    state = rememberDraggableState { delta ->
                                        vm.updateWeights(index, delta, totalWidth)
                                    },
                                    orientation = Orientation.Horizontal
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.GroupCell(text: String, weight: Float) {
    Box(
        modifier = Modifier.weight(weight).fillMaxHeight().drawTableBorder(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxScope.DynamicHeaderCell(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().drawTableBorder(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ParameterRow(param: ParameterData, weights: List<Float>, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .background(if (param.isSelected) Color(0xFFB3E5FC) else Color.White)
            .clickable { onClick() }
    ) {
        DynamicReadOnlyCell(param.code, weights[0], align = TextAlign.Center)
        DynamicReadOnlyCell(param.idName, weights[1])
        DynamicReadOnlyCell(param.description, weights[2])
        DynamicReadOnlyCell(param.unit, weights[3], align = TextAlign.Center)

        EditableCell(weights[4], param.hexBase) { param.hexBase = it }
        EditableCell(weights[5], param.physBase) { param.physBase = it }
        EditableCell(weights[6], param.hexCtrl) { param.hexCtrl = it }
        EditableCell(weights[7], param.physCtrl) { param.physCtrl = it }
    }
}

@Composable
private fun RowScope.DynamicReadOnlyCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Box(
        modifier = Modifier.weight(weight).fillMaxHeight().drawTableBorder().padding(horizontal = 4.dp),
        contentAlignment = if (align == TextAlign.Center) Alignment.Center else Alignment.CenterStart
    ) {
        Text(text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RowScope.EditableCell(weight: Float, value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier.weight(weight).fillMaxHeight().drawTableBorder().padding(horizontal = 2.dp),
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