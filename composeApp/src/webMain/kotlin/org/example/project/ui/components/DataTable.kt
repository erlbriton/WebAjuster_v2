package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.models.ParameterData
import org.example.project.viewmodel.LocalMainViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Цвета
// ─────────────────────────────────────────────────────────────────────────────
private val ColorHeaderGroup = Color(0xFFBDBDBD)
private val ColorHeaderCol   = Color(0xFFE0E0E0)
private val ColorBorder      = Color(0xFF9E9E9E)
private val ColorRowNormal   = Color.White
private val ColorRowSelected = Color(0xFFB3E5FC)
private val ColorRedText     = Color(0xFFD32F2F)
private val ColorNormalText  = Color(0xFF212121)
private val ColorArrow       = Color(0xFF1565C0)

// ─────────────────────────────────────────────────────────────────────────────
// Индексы
// ─────────────────────────────────────────────────────────────────────────────
private const val COL_CODE  = 0   // №         фиксированный
private const val COL_NAME  = 1   // Имя       фиксированный
private const val COL_DESC  = 2   // Описание  weight(1f) — растягивается
private const val COL_UNIT  = 3   // Ед.изм    фиксированный
private const val COL_HBASE = 4   // hex  БАЗА
private const val COL_PBASE = 5   // Phys БАЗА
private const val COL_HCTRL = 6   // hex  КОНТРОЛЛЕР
private const val COL_PCTRL = 7   // Phys КОНТРОЛЛЕР

// Ширина разделителя со стрелками (между ПАРАМЕТРЫ и БАЗА)
private val ARROW_SEP_WIDTH = 28.dp

// ─────────────────────────────────────────────────────────────────────────────
// DataTable
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DataTable(modifier: Modifier = Modifier) {
    val vm = LocalMainViewModel.current

    LaunchedEffect(Unit) {
        if (vm.parameters.isEmpty()) vm.loadSampleData()
    }

    val colWidths  = vm.colWidths
    val parameters = vm.parameters

    Column(modifier = modifier.fillMaxSize()) {
        GroupHeaderRow(
            colWidths        = colWidths,
            onCopyBaseToCtrl = { vm.copyBaseToController() },
            onCopyCtrlToBase = { vm.copyControllerToBase() }
        )
        ColumnHeaderRow(colWidths = colWidths)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(parameters, key = { _, p -> p.code }) { _, param ->
                ParameterRowItem(
                    param     = param,
                    colWidths = colWidths,
                    onClick   = { vm.selectRow(param.code) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Шапка групп
// Структура: [ПАРАМЕТРЫ — weight(1f)] [sep+arrows 28dp] [БАЗА fixed] [КОНТРОЛЛЕР fixed]
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GroupHeaderRow(
    colWidths: List<Dp>,
    onCopyBaseToCtrl: () -> Unit,
    onCopyCtrlToBase: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        // ПАРАМЕТРЫ — занимает всё место кроме фиксированных правых групп
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(ColorHeaderGroup)
                .border(0.5.dp, ColorBorder),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                "ПАРАМЕТРЫ",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = ColorNormalText
            )
        }

        // Разделитель со стрелками → ←
        Column(
            modifier = Modifier
                .width(ARROW_SEP_WIDTH)
                .fillMaxHeight()
                .background(ColorHeaderGroup)
                .border(0.5.dp, ColorBorder),
            verticalArrangement   = Arrangement.SpaceEvenly,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                "→",
                modifier   = Modifier.clickable { onCopyBaseToCtrl() },
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = ColorArrow
            )
            androidx.compose.material3.Text(
                "←",
                modifier   = Modifier.clickable { onCopyCtrlToBase() },
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = ColorArrow
            )
        }

        // БАЗА
        Box(
            modifier = Modifier
                .width(colWidths[COL_HBASE] + colWidths[COL_PBASE])
                .fillMaxHeight()
                .background(ColorHeaderGroup)
                .border(0.5.dp, ColorBorder),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                "БАЗА",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = ColorNormalText
            )
        }

        // КОНТРОЛЛЕР
        Box(
            modifier = Modifier
                .width(colWidths[COL_HCTRL] + colWidths[COL_PCTRL])
                .fillMaxHeight()
                .background(ColorHeaderGroup)
                .border(0.5.dp, ColorBorder),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                "КОНТРОЛЛЕР",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = ColorNormalText
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Заголовки столбцов
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ColumnHeaderRow(
    colWidths: androidx.compose.runtime.snapshots.SnapshotStateList<Dp>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(ColorHeaderCol)
    ) {
        // №
        HeaderCell(colWidths[COL_CODE], "№")
        ColDragger(COL_CODE, colWidths)
        // Имя
        HeaderCell(colWidths[COL_NAME], "Имя")
        ColDragger(COL_NAME, colWidths)
        // Описание — растягивается
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(0.5.dp, ColorBorder),
            contentAlignment = Alignment.Center
        ) { HeaderLabel("Описание") }
        // Ед.изм
        HeaderCell(colWidths[COL_UNIT], "Ед.изм")
        ColDragger(COL_UNIT, colWidths)
        // Разделитель (стрелки — пустой в строке заголовков)
        Box(
            modifier = Modifier
                .width(ARROW_SEP_WIDTH)
                .fillMaxHeight()
                .background(ColorHeaderCol)
                .border(0.5.dp, ColorBorder)
        )
        // hex БАЗА
        HeaderCell(colWidths[COL_HBASE], "hex")
        ColDragger(COL_HBASE, colWidths)
        // Physical БАЗА
        HeaderCell(colWidths[COL_PBASE], "Physical")
        ColDragger(COL_PBASE, colWidths)
        // hex КОНТРОЛЛЕР
        HeaderCell(colWidths[COL_HCTRL], "hex")
        ColDragger(COL_HCTRL, colWidths)
        // Physical КОНТРОЛЛЕР
        HeaderCell(colWidths[COL_PCTRL], "Physical")
    }
}

@Composable
private fun HeaderCell(width: Dp, text: String) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.5.dp, ColorBorder),
        contentAlignment = Alignment.Center
    ) { HeaderLabel(text) }
}

@Composable
private fun HeaderLabel(text: String) {
    androidx.compose.material3.Text(
        text       = text,
        fontSize   = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign  = TextAlign.Center,
        color      = ColorNormalText,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Строка параметра
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ParameterRowItem(
    param: ParameterData,
    colWidths: List<Dp>,
    onClick: () -> Unit
) {
    val snapshotWidths = colWidths as androidx.compose.runtime.snapshots.SnapshotStateList<Dp>
    val bgColor        = if (param.isSelected) ColorRowSelected else ColorRowNormal
    val hexMismatch    = param.hexBase.trim()  != param.hexCtrl.trim()
    val physMismatch   = param.physBase.trim() != param.physCtrl.trim()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(bgColor)
            .clickable { onClick() }
    ) {
        // №
        ReadOnlyCell(param.code,        colWidths[COL_CODE],  TextAlign.Center)
        ColDragger(COL_CODE, snapshotWidths)
        // Имя
        ReadOnlyCell(param.idName,      colWidths[COL_NAME])
        ColDragger(COL_NAME, snapshotWidths)
        // Описание — растягивается
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(0.5.dp, ColorBorder)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.material3.Text(
                text     = param.description,
                fontSize = 11.sp,
                color    = ColorNormalText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Ед.изм
        ReadOnlyCell(param.unit,        colWidths[COL_UNIT],  TextAlign.Center)
        ColDragger(COL_UNIT, snapshotWidths)

        // Разделитель (пустой, соответствует разделителю со стрелками в шапке)
        Box(
            modifier = Modifier
                .width(ARROW_SEP_WIDTH)
                .fillMaxHeight()
                .background(bgColor)
                .border(0.5.dp, ColorBorder)
        )

        // БАЗА hex
        EditableCell(param.hexBase,  colWidths[COL_HBASE], hexMismatch)  { param.hexBase  = it }
        ColDragger(COL_HBASE, snapshotWidths)
        // БАЗА Physical
        EditableCell(param.physBase, colWidths[COL_PBASE], physMismatch) { param.physBase = it }
        ColDragger(COL_PBASE, snapshotWidths)
        // КОНТРОЛЛЕР hex
        EditableCell(param.hexCtrl,  colWidths[COL_HCTRL], hexMismatch)  { param.hexCtrl  = it }
        ColDragger(COL_HCTRL, snapshotWidths)
        // КОНТРОЛЛЕР Physical
        EditableCell(param.physCtrl, colWidths[COL_PCTRL], physMismatch) { param.physCtrl = it }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ячейки
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ReadOnlyCell(
    text: String,
    width: Dp,
    align: TextAlign = TextAlign.Start
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.5.dp, ColorBorder)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.material3.Text(
            text      = text,
            fontSize  = 11.sp,
            color     = ColorNormalText,
            textAlign = align,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EditableCell(
    value: String,
    width: Dp,
    redText: Boolean,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.5.dp, ColorBorder)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value         = value,
            onValueChange = onValueChange,
            singleLine    = true,
            textStyle     = TextStyle(
                fontSize  = 11.sp,
                color     = if (redText) ColorRedText else ColorNormalText
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Драггер (изменение ширины столбца)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ColDragger(
    colIndex: Int,
    colWidths: androidx.compose.runtime.snapshots.SnapshotStateList<Dp>
) {
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(if (dragging) ColorArrow else ColorBorder)
            .pointerInput(colIndex) {
                detectHorizontalDragGestures(
                    onDragStart  = { dragging = true },
                    onDragEnd    = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    colWidths[colIndex] = (colWidths[colIndex] + dragAmount.toDp()).coerceAtLeast(30.dp)
                }
            }
    )
}