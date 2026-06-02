//package org.example.project.oscilloscope
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.gestures.Orientation
//import androidx.compose.foundation.gestures.draggable
//import androidx.compose.foundation.gestures.rememberDraggableState
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.input.pointer.PointerIcon
//import androidx.compose.ui.input.pointer.pointerHoverIcon
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.zIndex
//
//@Composable
//fun OscilloscopeHeaderColumns(
//    weights: List<Float>,
//    totalWidthPx: Float,
//    onWeightChange: (index: Int, delta: Float) -> Unit
//) {
//    Row(
//        modifier = Modifier.fillMaxWidth().height(24.dp).background(Color(0xFFE0E0E0)),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        val titles = listOf("name", "Hex", "physical", "unit")
//
//        titles.forEachIndexed { index, title ->
//            Box(
//                modifier = Modifier.weight(weights[index]).fillMaxHeight(),
//                contentAlignment = Alignment.Center
//            ) {
//                Text(
//                    text = title,
//                    color = Color.DarkGray,
//                    fontSize = 11.sp,
//                    textAlign = TextAlign.Center
//                )
//
//                // Драггер-ресайзер ставится на правый край каждой ячейки
//                VerticalResizer(
//                    onDrag = { delta -> onWeightChange(index, delta) }
//                )
//            }
//        }
//
//        // Правая часть под Canvas графиков
//        Box(modifier = Modifier.weight(weights.getOrElse(4) { 0.25f }).fillMaxHeight().background(Color(0xFFEFEFEF)))
//    }
//}
//
//@Composable
//private fun BoxScope.VerticalResizer(onDrag: (Float) -> Unit) {
//    Box(
//        modifier = Modifier
//            .fillMaxHeight()
//            .width(8.dp) // Зона легкого перехвата мыши
//            .align(Alignment.CenterEnd)
//            .zIndex(1f)
//            .pointerHoverIcon(PointerIcon.Hand)
//            .draggable(
//                state = rememberDraggableState { delta -> onDrag(delta) },
//                orientation = Orientation.Horizontal
//            ),
//        contentAlignment = Alignment.Center
//    ) {
//        // Та самая разделительная линия в 2.dp, которую вы настроили
//        Box(
//            modifier = Modifier
//                .width(2.dp)
//                .fillMaxHeight()
//                .background(Color.Gray)
//        )
//    }
//}