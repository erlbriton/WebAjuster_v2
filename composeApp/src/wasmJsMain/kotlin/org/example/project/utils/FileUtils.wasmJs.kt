//FileUtils.wasmJs.kt

package org.example.project.utils

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.example.project.models.DeviceInfoIni
import kotlinx.browser.window
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.example.project.models.ParameterData

// --- СИСТЕМНЫЙ МОСТ ДЛЯ WASM ---

external interface FileSystemHandle : JsAny {
    val kind: String
    val name: String
}
external interface FileSystemFileHandle : FileSystemHandle {
    fun getFile(): Promise<JsAny>
}
@JsFun("() => window.showDirectoryPicker()")
private external fun jsShowDirectoryPicker(): Promise<JsAny>
@JsFun("(parent, name) => parent.getDirectoryHandle(name)")
private external fun jsGetDirectoryHandle(parent: JsAny, name: String): Promise<FileSystemHandle>
@JsFun("(handle) => handle.values()")
private external fun jsValues(handle: JsAny): JsAny
@JsFun("(iterator) => iterator.next()")
private external fun jsIteratorNext(iterator: JsAny): Promise<JsAny>
@JsFun("(obj) => !!obj.done")
private external fun asDynamicGetDone(obj: JsAny): Boolean
@JsFun("(obj) => obj.value")
private external fun asDynamicGetValue(obj: JsAny): JsAny?
@JsFun("(file) => file.text()")
private external fun jsFileText(file: JsAny): Promise<JsString>

// --- ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ---
private suspend fun getDirectoryEntries(directoryHandle: JsAny): List<JsAny> {
    val entries = mutableListOf<JsAny>()
    val iterator = jsValues(directoryHandle)
    var safetyCounter = 0
    while (safetyCounter < 1000) {
        val result: JsAny = jsIteratorNext(iterator).await<JsAny>()
        if (asDynamicGetDone(result)) break

        val value: JsAny? = asDynamicGetValue(result)
        if (value != null) entries.add(value)
        safetyCounter++
    }
    return entries
}
private fun isDirectory(handle: JsAny): Boolean =
    handle.unsafeCast<FileSystemHandle>().kind == "directory"
private fun getFileName(handle: JsAny): String =
    handle.unsafeCast<FileSystemHandle>().name
private suspend fun readFileContent(fileHandle: JsAny): String {
    val h = fileHandle.unsafeCast<FileSystemFileHandle>()
    val file = h.getFile().await<JsAny>()
    return jsFileText(file).await<JsString>().toString()
}
private fun parseIniContent(content: String, fileName: String): DeviceInfoIni? {
    val lines = content.split("\n", "\r").map { it.trim() }

    val varsMap = mutableMapOf<String, Double>()
    var idValue = ""
    var locationValue = ""
    var descriptionValue = ""
    var lastDateTimeValue = ""

    // ПРАВИЛЬНО: Создаем три разных списка
    val flashParams = mutableListOf<ParameterData>()
    val ramParams = mutableListOf<ParameterData>()
    val cdParams = mutableListOf<ParameterData>()

    var currentSection = ""

    // --- ПРОХОД 1: Собираем шкалы [vars] (оставляем как есть) ---
    for (line in lines) {
        if (line.startsWith("[") && line.endsWith("]")) {
            currentSection = line.uppercase()
            continue
        }
        if (currentSection == "[VARS]" && line.contains("=")) {
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim().replace(",", ".").toDoubleOrNull() ?: 1.0
            varsMap[key] = value
        }
    }

    // --- ПРОХОД 2: Парсим всё остальное ---
    currentSection = ""
    for (line in lines) {
        if (line.isEmpty()) continue
        if (line.startsWith("[") && line.endsWith("]")) {
            currentSection = line.uppercase()
            continue
        }

        when (currentSection) {
            "[DEVICE]" -> {
                when {
                    line.startsWith("ID=", true) -> idValue = line.substringAfter("=")
                    line.startsWith("Location=", true) -> locationValue = line.substringAfter("=")
                    line.startsWith("Description=", true) -> descriptionValue = line.substringAfter("=")
                    line.startsWith("LastDateTime=", true) -> lastDateTimeValue = line.substringAfter("=")
                }
            }
            // ПРАВИЛЬНО: Добавляем [CD] в список прослушивания
            "[FLASH]", "[RAM]", "[CD]" -> {
                if (line.contains("=")) {
                    val pCode = line.substringBefore("=").trim()
                    val rawData = line.substringAfter("=").trim()
                    val parts = rawData.split("/")

                    if (parts.size >= 2) {
                        // ... (код расчета HEX и физики оставляем без изменений) ...
                        val hexRaw = if (parts.last().isEmpty()) parts[parts.size - 2].trim() else parts.last().trim()

                        // (Ваша логика расчета physicalValue...)
                        var physicalValue = ""
                        val enumEntry = parts.find { it.contains(hexRaw + "#") }
                        if (enumEntry != null) {
                            physicalValue = enumEntry.substringAfter("#")
                        } else {
                            val scaleName = parts.getOrNull(6)?.trim() ?: ""
                            val scaleValue = varsMap[scaleName] ?: 1.0
                            val rawInt = hexRaw.removePrefix("x").toIntOrNull(16) ?: 0
                            val calculated = rawInt * scaleValue
                            physicalValue = if (calculated % 1.0 == 0.0) calculated.toInt().toString() else calculated.toString()
                        }

                        val parameter = ParameterData(
                            code = pCode,
                            idName = parts.getOrNull(0) ?: "",
                            description = parts.getOrNull(1) ?: "",
                            dataType = parts.getOrNull(2) ?: "",
                            modbusReg = parts.getOrNull(4) ?: "",
                            unit = parts.getOrNull(5) ?: "",
                            initialHexBase = hexRaw,
                            initialPhysBase = physicalValue,
                            initialHexCtrl = "x0000",
                            initialPhysCtrl = "0"
                        )

                        // ПРАВИЛЬНО: Раскладываем по нужным корзинам
                        when (currentSection) {
                            "[FLASH]" -> flashParams.add(parameter)
                            "[RAM]"   -> ramParams.add(parameter)
                            "[CD]"    -> cdParams.add(parameter)
                        }
                    }
                }
            }
        }
    }

    if (idValue.isEmpty()) return null

    // ПРАВИЛЬНО: Возвращаем объект со всеми тремя наполненными списками
    return DeviceInfoIni(
        fileName = fileName,
        id = idValue,
        location = locationValue,
        Description = descriptionValue,
        LastDateTime = lastDateTimeValue,
        flashParameters = flashParams,
        ramParameters = ramParams,
        cdParameters = cdParams
    )
}

// --- РЕАЛИЗАЦИЯ ACTUAL ---
actual suspend fun pickDirectory(): List<DeviceInfoIni>? {
    val results = mutableListOf<DeviceInfoIni>()
    try {
        println("DEBUG: Ожидание выбора папки...")
        val rootHandle: JsAny = jsShowDirectoryPicker().await<JsAny>()
        val rootName = getFileName(rootHandle)

        // ЖЕСТКОЕ УСЛОВИЕ: Только папка Devices
        if (!rootName.equals("Devices", ignoreCase = true)) {
            println("ОШИБКА: Выбрана папка '$rootName'. Необходимо выбрать именно 'Devices'!")
            return null
        }
        println("DEBUG: Начинаю сканирование содержимого Devices...")
        val deviceEntries = getDirectoryEntries(rootHandle)
        for (entry in deviceEntries) {
            if (isDirectory(entry)) {
                val subDirName = getFileName(entry)
                println("DEBUG: Сканирую подпапку '$subDirName'")

                val files = getDirectoryEntries(entry)
                for (fileHandle in files) {
                    val name = getFileName(fileHandle)
                    if (name.lowercase().endsWith(".txt")) {
                        try {
                            val content = readFileContent(fileHandle)
                            val info = parseIniContent(content, name)
                            if (info != null) {
                                results.add(info)
                            }
                        } catch (e: Exception) {
                            println("DEBUG: Пропущен файл $name (ошибка чтения)")
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        println("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}")
        return null
    }
    println("DEBUG: Успешно загружено объектов: ${results.size}")
    return if (results.isEmpty()) null else results
}
actual suspend fun pickSingleFile(): DeviceInfoIni? {
    val handle = suspendCoroutine<JsAny?> { cont ->
        showFilePickerNative { res -> cont.resume(res) }
    } ?: return null
    return parseIniFile(handle)
}
private fun parseIniFile(handle: JsAny?): DeviceInfoIni? {
    if (handle == null) return null
    return try {
        val result = handle.unsafeCast<JsFileResult>()
        if (!result.isAlreadyTxt) saveFileAsTxt(result.name, result.content)
        parseIniContent(result.content, result.name)
    } catch (e: Throwable) { null }
}
private fun List<String>.findValue(prefix: String): String {
    return this.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
        ?.substringAfter("=")?.trim() ?: ""
}
external interface JsFileResult : JsAny {
    val name: String
    val content: String
    val isAlreadyTxt: Boolean
}
external fun showFilePickerNative(callback: (JsAny?) -> Unit)
external fun saveFileAsTxt(originalName: String, content: String)