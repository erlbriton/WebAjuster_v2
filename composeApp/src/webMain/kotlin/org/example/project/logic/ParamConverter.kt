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
            val finalStr = if (calculated % 1.0 == 0.0) calculated.toInt().toString() else calculated.toString()

            if (isBase) param.physBase = finalStr else param.physCtrl = finalStr
        }
    }

    fun updatePhysValue(param: ParameterData, newPhys: String, varsMap: Map<String, Double>, isBase: Boolean) {
        if (isBase) param.physBase = newPhys else param.physCtrl = newPhys

        val physDouble = newPhys.replace(",", ".").toDoubleOrNull() ?: 0.0
        val scaleValue = varsMap[param.scaleName] ?: 1.0
        val rawInt = if (scaleValue != 0.0) (physDouble / scaleValue).toInt() else 0
        val finalHex = "x" + rawInt.toString(16).uppercase().padStart(4, '0')

        if (isBase) param.hexBase = finalHex else param.hexCtrl = finalHex
    }
}