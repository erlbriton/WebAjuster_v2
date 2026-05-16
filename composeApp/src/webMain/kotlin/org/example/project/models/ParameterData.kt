package org.example.project.models

//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.setValue
//
//data class ParameterData(
//    val code: String,
//    val idName: String,
//    val description: String,
//    val dataType: String,
//    val modbusReg: String,
//    val unit: String,
//    val scaleName: String = "",       // Имя шкалы для связи с varsMap (например, AINK)
//    var hexBase: String = "x0000",    // Значение HEX в Базе (var для изменения)
//    var physBase: String = "0",       // Физическое значение в Базе (var для изменения)
//    var hexCtrl: String = "x0000",    // Значение HEX в Контроллере (var для изменения)
//    var physCtrl: String = "0",       // Физическое значение в Контроллере (var для изменения)
//    var isSelected: Boolean = false   // Флаг выбора строки в таблице
//)
//{
//    var hexBase  by mutableStateOf(initialHexBase)
//    var physBase by mutableStateOf(initialPhysBase)
//    var hexCtrl  by mutableStateOf(initialHexCtrl)
//    var physCtrl by mutableStateOf(initialPhysCtrl)
//
//    // Строка выделена кликом
//    var isSelected by mutableStateOf(false)
//}