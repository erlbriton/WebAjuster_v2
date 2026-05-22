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
        if (isBase) param.physBase = newPhys else param.physCtrl = newPhys

        val isPrmList = param.dataType.equals("TPrmList", ignoreCase = true)
        var targetValue = newPhys.replace(",", ".").toDoubleOrNull() ?: 0.0

        // Если это список параметров и ввели/выбрали текст, а не число
        if (isPrmList && newPhys.toDoubleOrNull() == null) {
            val prmMap = parsePrmList(param.scaleName)
            val matchedHex = prmMap.entries.firstOrNull { it.value.trim().equals(newPhys.trim(), ignoreCase = true) }?.key
            if (matchedHex != null) {
                targetValue = matchedHex.replace("x", "").toIntOrNull(16)?.toDouble() ?: 0.0
            }
        }

        // Вычисляем чистое 16-битное значение для параметра
        val cleanScaleName = param.scaleName.trim()
        val scaleValue = if (cleanScaleName.isEmpty() || isPrmList) 1.0 else {
            val directNumber = cleanScaleName.replace(",", ".").toDoubleOrNull()
            directNumber ?: varsMap[cleanScaleName] ?: 1.0
        }
        val calculatedRaw = (targetValue / scaleValue).toInt() and 0xFFFF

        // --- НАЧАЛО БЛОКА ЗАЩИТЫ БАЙТОВЫХ РЕГИСТРОВ (.L / .H) ---
        val currentHex = if (isBase) param.hexBase else param.hexCtrl
        val cleanCurrentHex = currentHex.replace("x", "").padStart(4, '0')
        val currentFullInt = cleanCurrentHex.toIntOrNull(16) ?: 0

        val hexString = when {
            param.dataType.equals("TFloat", ignoreCase = true) -> {
                val bits = (targetValue / scaleValue).toFloat().toBits()
                "x" + (bits.toLong() and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
            }
            param.modbusReg.contains(".L", ignoreCase = true) -> {
                // Оставляем старший байт нетронутым, меняем только младший
                val highByte = currentFullInt and 0xFF00
                val newLowByte = calculatedRaw and 0x00FF
                "x" + (highByte or newLowByte).toString(16).uppercase().padStart(4, '0')
            }
            param.modbusReg.contains(".H", ignoreCase = true) -> {
                // Оставляем младший байт нетронутым, меняем только старший
                val lowByte = currentFullInt and 0x00FF
                val newHighByte = (calculatedRaw and 0x00FF) shl 8
                "x" + (newHighByte or lowByte).toString(16).uppercase().padStart(4, '0')
            }
            else -> {
                // Для обычных 16-битных регистров
                "x" + calculatedRaw.toString(16).uppercase().padStart(4, '0')
            }
        }

        if (isBase) param.hexBase = hexString else param.hexCtrl = hexString
    }

    /**
     * Парсит строку TPrmList вида "BPS//x06#115200/x05#57600/"
     * Возвращает карту, где ключ — это HEX (например, "x06"), а значение — текст ("115200")
     */
    fun parsePrmList(scaleName: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!scaleName.contains("//")) return result

        val itemsRaw = scaleName.substringAfter("//").trim()
        val tokens = itemsRaw.split("/")

        for (token in tokens) {
            val cleanToken = token.trim()

            // Если токен не содержит решётку или символ 'x' — это пустой слэш или мусор, пропускаем
            if (cleanToken.isEmpty() || !cleanToken.contains("#") || !cleanToken.contains("x")) {
                continue
            }

            // Извлекаем HEX-ключ: всё, что между началом токена и первой решёткой
            // Из куска "x01" или "  x01 " получим "x01"
            val hexKey = cleanToken.substringBefore("#").trim().lowercase()

            // Извлекаем чистый текст до второй решётки (отсекаем мусор заказчика)
            val remainder = cleanToken.substringAfter("#")
            val textValue = if (remainder.contains("#")) {
                remainder.substringBefore("#").trim()
            } else {
                remainder.trim()
            }

            if (hexKey.isNotEmpty() && textValue.isNotEmpty()) {
                result[hexKey] = textValue
            }
        }
        return result
    }
}