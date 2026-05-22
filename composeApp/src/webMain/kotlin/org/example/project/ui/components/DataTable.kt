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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.example.project.models.ParameterData
import org.example.project.viewmodels.LocalMainViewModel
import org.example.project.viewmodels.MainViewModel
import kotlin.coroutines.ContinuationInterceptor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent

private val ColorBorder     = Color(0xFF000000)
private val ColorHeaderGroup = Color(0xFFBDBDBD)
private val ColorHeaderCol   = Color(0xFFE0E0E0)

// Рисует правую и нижнюю границу ячейки
fun Modifier.drawTableBorder(right: Boolean = true, bottom: Boolean = true): Modifier =
    this.drawBehind {
        val sw = 0.5.dp.toPx()
        if (right)  drawLine(ColorBorder, Offset(size.width, 0f),    Offset(size.width, size.height), sw)
        if (bottom) drawLine(ColorBorder, Offset(0f, size.height),   Offset(size.width, size.height), sw)
    }

// ─────────────────────────────────────────────────────────────────────────────
// DataTable
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DataTable(modifier: Modifier = Modifier) {
    val vm      = LocalMainViewModel.current
    val weights = vm.colWeights

    var headerHeight by remember { mutableStateOf(0f) }

    // 1. Создаем состояние прокрутки таблицы
    val tableScrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    // ВНЕШНИЙ изолированный Box. Скроллбар живет здесь и НЕ жмет таблицу!
    Box(modifier = modifier.fillMaxSize()) {

        // ВНУТРЕННИЙ Box, где мы используем BoxWithConstraints вместо onGloballyPositioned.
        // Это железно защищает от бесконечных циклов изменения размеров!
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidthPx = constraints.maxWidth.toFloat()

            // Вычисляем X-позиции вертикальных линий БЕЗ использования side-эффектов стейта
            val lineXPositions = remember(weights.toList(), totalWidthPx) {
                val total = weights.sum()
                val positions = mutableListOf<Float>()
                var acc = 0f
                for (i in 0 until weights.size - 1) {
                    acc += weights[i] / total
                    positions.add(acc * totalWidthPx)
                }
                positions
            }

            // Рисуем сетку линий
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val sw = 1.5.dp.toPx()
                        lineXPositions.forEach { x ->
                            drawLine(ColorBorder, Offset(x, headerHeight), Offset(x, size.height), sw)
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Шапка
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HeaderSection(weights, totalWidthPx, vm, onGroupRowHeight = { headerHeight = it })
                    }

                    // Тело таблицы
                    LazyColumn(
                        state = tableScrollState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ВРЕМЕННО УБИРАЕМ КЛЮЧИ ВООБЩЕ
                        itemsIndexed(vm.parameters) { _, param ->
                            Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                                ParameterRow(
                                    param = param,
                                    weights = weights,
                                    modifier = Modifier.background(
                                        if (param.isSelected) Color(0xFFA6E594) else Color.White
                                    ),
                                    onClick = { vm.selectRow(param.code) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Скроллбар накладывается ПОВЕРХ, как независимый слой, не сдвигая границы таблицы
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(tableScrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 54.dp), // строго под шапкой
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 5.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = Color.Green.copy(alpha = 0.5f),
                hoverColor = Color.Cyan
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Шапка
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeaderSection(
    weights: List<Float>,
    totalWidth: Float,
    vm: MainViewModel,
    onGroupRowHeight: (Float) -> Unit
) {
    // Стейт для отображения окна подтверждения переноса
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Уровень 1: Группы
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(ColorHeaderGroup)
            .onGloballyPositioned { onGroupRowHeight(it.size.height.toFloat()) }
            .drawWithContent {
                drawContent()
                val sw = 1.5.dp.toPx()
                val total = weights.sum()
                val x1 = (weights[0] + weights[1] + weights[2] + weights[3]) / total * size.width
                drawLine(ColorBorder, Offset(x1, 0f), Offset(x1, size.height), sw)
                val x2 = (weights[0] + weights[1] + weights[2] + weights[3] + weights[4] + weights[5]) / total * size.width
                drawLine(ColorBorder, Offset(x2, 0f), Offset(x2, size.height), sw)
            }
        ) {
            Box(modifier = Modifier.weight(weights[0] + weights[1] + weights[2] + weights[3]).fillMaxHeight()) {
                GroupCell("ПАРАМЕТРЫ")
                VerticalResizer { delta -> vm.updateWeights(3, delta, totalWidth) }
            }

            // --- СДЕЛАЛИ КЛИКАБЕЛЬНОЙ ГРУППУ "БАЗА" ---
            Box(
                modifier = Modifier
                    .weight(weights[4] + weights[5])
                    .fillMaxHeight()
                    .clickable { showConfirmDialog = true } // Открываем диалог по клику
                    .pointerHoverIcon(PointerIcon.Hand) // Меняем курсор на руку при наведении
            ) {
                GroupCell("БАЗА (Записать всё ⚡)")
                VerticalResizer { delta -> vm.updateWeights(5, delta, totalWidth) }
            }

            GroupCell("КОНТРОЛЛЕР", Modifier.weight(weights[6] + weights[7]).fillMaxHeight())
        }

        // Уровень 2: Заголовки столбцов
        Row(modifier = Modifier.fillMaxWidth().height(26.dp).background(ColorHeaderCol)) {
            val titles = listOf("№", "Имя", "Описание", "Ед.изм", "hex", "Physical", "hex", "Physical")
            titles.forEachIndexed { index, title ->
                Box(modifier = Modifier.weight(weights[index]).fillMaxHeight()) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .drawBehind {
                                val sw = 0.5.dp.toPx()
                                drawLine(ColorBorder, Offset(0f, size.height), Offset(size.width, size.height), sw)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            title,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                    if (index < titles.size - 1) {
                        VerticalResizer { delta -> vm.updateWeights(index, delta, totalWidth) }
                    }
                }
            }
        }
    }

    // --- ОКНО ПОДТВЕРЖДЕНИЯ ДЛЯ ПЕРЕЗАПИСИ ---
    if (showConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(text = "Подтверждение записи", fontWeight = FontWeight.Bold) },
            text = { Text("Вы уверены, что хотите переписать ВСЕ параметры из БАЗЫ в КОНТРОЛЛЕР по Modbus?") },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showConfirmDialog = false
                        vm.writeAllBaseToControllerDevice() // Запускаем массовую запись
                    }
                ) {
                    Text("Да, записать")
                }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Строка параметра
// ─────────────────────────────────────────────────────────────────────────────

@Composable // Убедитесь, что над функцией стоит @Composable
private fun ParameterRow(
    param: ParameterData,
    weights: List<Float>,
    modifier: Modifier = Modifier, // 1. Добавили параметр
    onClick: () -> Unit
) {
    val vm = LocalMainViewModel.current
    // Очищаем hexBase от "x" и лишних нулей (парсим как hex),
    // а hexCtrl парсим как обычное целое (так как там теперь 0 или 1)
    val baseVal = param.hexBase.replace("x", "").toLongOrNull(16) ?: 0L
    val ctrlVal = param.hexCtrl.toLongOrNull() ?: 0L

    // 1. Сравниваем HEX: убираем 'x', приводим к числу (Long), если там просто 0/1 — тоже сработает
    val baseHexStr = (param.hexBase.toLongOrNull(16) ?: 0L).toString()
    val ctrlHexStr = (param.hexCtrl.toLongOrNull(16) ?: 0L).toString()
    val hexMismatch = baseHexStr != ctrlHexStr

    // 2. Сравниваем PHYS: приводим к Double, чтобы 1.0 == 1
    val basePhysNum = param.physBase.toDoubleOrNull() ?: 0.0
    val ctrlPhysNum = param.physCtrl.toDoubleOrNull() ?: 0.0
    val physMismatch = basePhysNum != ctrlPhysNum
    val isTBit = param.dataType.equals("TBit", ignoreCase = true)

    Row(
        modifier = modifier // 2. Применяем его ПЕРВЫМ (он принесет цвет фона из LazyColumn)
            .fillMaxWidth()
            .height(24.dp)
            .clickable { onClick() }
    ) {
        ReadOnlyCell(param.code,        weights[0], TextAlign.Center)
        ReadOnlyCell(param.idName,      weights[1], TextAlign.Center)
        ReadOnlyCell(param.description, weights[2], TextAlign.Center)
        ReadOnlyCell(param.unit,        weights[3], TextAlign.Center)

        val redColor  = Color(0xFFD32F2F)
        val normColor = Color(0xFF212121)

        // Объединяем флаги: если есть несовпадение в HEX ИЛИ в Physical — красим всё
        val hasAnyMismatch = hexMismatch || physMismatch

        // 4-й и 5-й столбцы (БАЗА)
        EditableCell(
            weight = weights[4],
            value = if (isTBit) {
                val clean = param.hexBase.replace("x", "").replace("0x", "")
                if (clean.isEmpty()) "" else clean.toLongOrNull(16)?.toString() ?: clean
            } else param.hexBase,
            textColor = if (hasAnyMismatch) redColor else normColor,
            onValueChange = { vm.updateHexBase(param, it) }
        )
        EditableCell(
            weight = weights[5],
            value = if (isTBit) {
                val clean = param.physBase.replace("x", "").replace("0x", "")
                if (clean.isEmpty()) "" else clean.toLongOrNull()?.toString() ?: clean
            } else param.physBase,
            textColor = if (hasAnyMismatch) redColor else normColor,
            onValueChange = { vm.updatePhysBase(param, it) }
        )

        // 6-й и 7-й столбцы (КОНТРОЛЛЕР)
        EditableCell(
            weight = weights[6],
            value = if (isTBit) {
                val clean = param.hexCtrl.replace("x", "").replace("0x", "")
                if (clean.isEmpty()) "" else clean.toLongOrNull(16)?.toString() ?: clean
            } else param.hexCtrl,
            textColor = if (hasAnyMismatch) redColor else normColor,
            onValueChange = { vm.updateHexCtrl(param, it) },
            onEnterPressed = { vm.writeParameterToDevice(param) }
        )
        EditableCell(
            weight = weights[7],
            value = if (isTBit) {
                val clean = param.physCtrl.replace("x", "").replace("0x", "")
                if (clean.isEmpty()) "" else clean.toLongOrNull()?.toString() ?: clean
            } else param.physCtrl,
            textColor = if (hasAnyMismatch) redColor else normColor,
            onValueChange = { newValue ->
                if (param.type == org.example.project.models.ParameterType.TBit) {
                    if (newValue == "0" || newValue == "1" || newValue.isEmpty()) {
                        vm.updatePhysCtrl(param, newValue)
                    }
                } else {
                    vm.updatePhysCtrl(param, newValue)
                }
            },
            onEnterPressed = { vm.writeParameterToDevice(param) }
        )///////////////////////////////////////////////////////////////////////////////////////////
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Вспомогательные composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GroupCell(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val sw = 0.5.dp.toPx()
                drawLine(ColorBorder, Offset(0f, size.height), Offset(size.width, size.height), sw)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.ReadOnlyCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Center // Установили центр по умолчанию
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center // Центрирует контент внутри Box
    ) {
        Text(
            text,
            fontSize   = 12.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = align, // Центрирует текст внутри границ Text
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

// --- ОБНОВЛЕННАЯ РЕДАКТИРУЕМАЯ ЯЧЕЙКА (с центрированием) ---
@Composable
private fun RowScope.EditableCell(
    weight: Float,
    value: String,
    textColor: Color,
    onValueChange: (String) -> Unit,
    onEnterPressed: (() -> Unit)? = null // 1. Новый параметр
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value         = value,
            onValueChange = onValueChange,
            singleLine    = true,
            textStyle     = TextStyle(
                fontSize  = 12.sp,
                color     = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier      = Modifier
                .fillMaxWidth()
                // 2. Врезаем перехват клавиши Enter, если передан коллбек
                .then(
                    if (onEnterPressed != null) {
                        Modifier.onKeyEvent { keyEvent ->
                            // Проверяем, что нажата кнопка Enter ИЛИ NumPadEnter
                            val isEnter = keyEvent.key == androidx.compose.ui.input.key.Key.Enter ||
                                    keyEvent.key == androidx.compose.ui.input.key.Key.NumPadEnter

                            if (isEnter && keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                onEnterPressed()
                                true // Событие обработано, фокус не улетит
                            } else {
                                false
                            }
                        }
                    } else Modifier
                )
        )
    }
}

// Драггер — прозрачная зона справа от ячейки, перехватывает горизонтальный drag
@Composable
private fun BoxScope.VerticalResizer(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(8.dp)
            .align(Alignment.CenterEnd)
            .zIndex(1f)
            .pointerHoverIcon(PointerIcon.Hand)
            .draggable(
                state       = rememberDraggableState { delta -> onDrag(delta) },
                orientation = Orientation.Horizontal
            )
    )
}
