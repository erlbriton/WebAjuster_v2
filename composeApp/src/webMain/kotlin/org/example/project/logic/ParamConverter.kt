package org.example.project.logic

import org.example.project.models.ParameterData

object ParamConverter {

    fun updateHexValue(param: ParameterData, newHex: String, varsMap: Map<String, Double>, isBase: Boolean) {
        val targetHex = if (newHex.isEmpty()) "x" else {
            val upper = newHex.uppercase()
            val clean = if (upper.startsWith("X")) upper.removePrefix("X") else upper
            if (clean.length > 4) return
            "x$clean"
        }

        if (isBase) param.hexBase = targetHex else param.hexCtrl = targetHex
        if (targetHex == "x") return

        val cleanHex = targetHex.removePrefix("x")
        val rawInt = cleanHex.toIntOrNull(16)
        if (rawInt != null) {
            val scaleValue = varsMap[param.scaleName] ?: 1.0
            val calculated = rawInt * scaleValue

            // --- ИСПРАВЛЕНИЕ: Округляем до 5 знаков после запятой, чтобы убрать двоичный хвост ---
            val rounded = kotlin.math.round(calculated * 100000.0) / 100000.0

            // Если число целое — убираем точку (например, "48.0" -> "48"), иначе выводим как есть
            val finalStr = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()

            if (isBase) param.physBase = finalStr else param.physCtrl = finalStr
        }
    }

    fun updatePhysValue(param: ParameterData, newPhys: String, varsMap: Map<String, Double>, isBase: Boolean) {
        // 1. Сохраняем текст в физическое поле
        if (isBase) param.physBase = newPhys else param.physCtrl = newPhys

        // 2. Ищем коэффициент шкалы (числовой или из vars)
        val cleanScaleName = param.scaleName.trim()
        val scaleValue = if (cleanScaleName.isEmpty()) {
            1.0
        } else {
            val directNumber = cleanScaleName.replace(",", ".").toDoubleOrNull()
            if (directNumber != null) {
                directNumber
            } else {
                val exactKey = varsMap.keys.firstOrNull { it.equals(cleanScaleName, ignoreCase = true) }
                if (exactKey != null) varsMap[exactKey] ?: 1.0 else 1.0
            }
        }

        // 3. Парсим введенное пользователем число
        val inputDouble = newPhys.replace(",", ".").toDoubleOrNull() ?: 0.0
        val isTFloat = param.dataType.equals("TFloat", ignoreCase = true)

        // 4. ГЕНЕРАЦИЯ HEX СТРОГО ПО ТИПУ ДАННЫХ
        val hexString = if (isTFloat) {
            // Си-логика: Физика / Шкала -> Float -> Биты -> 32-битный HEX
            val rawFloat = (inputDouble / scaleValue).toFloat()
            val bits = rawFloat.toBits()
            "x" + (bits.toLong() and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
        } else {
            // Для обычных 16-битных параметров
            val rawValue = (inputDouble / scaleValue).toInt() and 0xFFFF
            "x" + rawValue.toString(16).uppercase().padStart(4, '0')
        }

        // 5. Записываем правильный HEX обратно в модель
        if (isBase) {
            param.hexBase = hexString
        } else {
            param.hexCtrl = hexString
        }
    }

    /**
     * Парсит строку TPrmList вида "BPS//x06#115200/x05#57600/"
     * Возвращает карту, где ключ — это HEX (например, "x06"), а значение — текст ("115200")
     */
    fun parsePrmList(scaleName: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!scaleName.contains("//")) return result

        // Отрезаем заголовок (все что до //)
        val itemsRaw = scaleName.substringAfter("//").trim()

        // Бьем строку по разделителю "/"
        val tokens = itemsRaw.split("/")
        for (token in tokens) {
            val cleanToken = token.trim()
            // Ищем элементы, содержащие знак решетки "#" (например, x06#115200)
            if (cleanToken.contains("#")) {
                val hexKey = cleanToken.substringBefore("#").trim().lowercase() // "x06"
                val textValue = cleanToken.substringAfter("#").trim()          // "115200"
                result[hexKey] = textValue
            }
        }
        return result
    }
}