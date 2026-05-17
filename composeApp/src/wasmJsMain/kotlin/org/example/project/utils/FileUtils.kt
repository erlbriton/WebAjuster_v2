@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.example.project.utils

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.example.project.models.DeviceInfoIni
import org.example.project.models.ParameterData
import kotlinx.browser.window
import kotlin.js.Promise
import kotlinx.coroutines.await

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

@JsFun("() => window.showOpenFilePicker({ types: [{ description: 'INI Files', accept: { 'text/plain': ['.ini', '.txt'] } }] }).then(handles => handles[0])")
private external fun jsShowOpenFilePicker(): Promise<JsAny>

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

// ЖЕСТКИЙ ДЕКОДЕР: Принудительно передаем кодировку из Kotlin
@JsFun("(file, encoding) => file.arrayBuffer().then(buf => new TextDecoder(encoding).decode(buf))")
private external fun jsFileText(file: JsAny, encoding: String): Promise<JsString>

// Функция сохранения: Теперь она создает чистый UTF-8 файл из правильной Kotlin-строки
@JsFun("""
    (name, content) => {
        const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        
        let newName = name;
        if (name.toLowerCase().endsWith('.ini')) {
            newName = name.slice(0, -4) + '.txt';
        } else if (!name.toLowerCase().endsWith('.txt')) {
            newName = name + '.txt';
        }
        
        a.href = url;
        a.download = newName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }
""")
private external fun jsSaveFileAsTxt(name: String, content: String)

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

// ИСПРАВЛЕНО: Читаем строго по расширению. Исходный старый .ini — ВСЕГДА в 1251. А уже скопированный новый .txt — ВСЕГДА в utf-8.
private suspend fun readFileContent(fileHandle: JsAny, fileName: String): String {
    val h = fileHandle.unsafeCast<FileSystemFileHandle>()
    val file = h.getFile().await<JsAny>()
    val encoding = if (fileName.lowercase().endsWith(".ini")) "windows-1251" else "utf-8"
    return jsFileText(file, encoding).await<JsString>().toString()
}

private fun parseIniContent(content: String, fileName: String): DeviceInfoIni? {
    val lines = content.split("\n", "\r").map { it.trim() }

    val varsMap = mutableMapOf<String, Double>()
    var idValue = ""
    var locationValue = ""
    var descriptionValue = ""
    var lastDateTimeValue = ""

    val flashParams = mutableListOf<ParameterData>()
    val ramParams = mutableListOf<ParameterData>()
    val cdParams = mutableListOf<ParameterData>()

    var currentSection = ""

    // --- ПРОХОД 1: Собираем шкалы [vars] ---
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
                if (line.contains("=")) {
                    val key = line.substringBefore("=").trim().lowercase()
                    val value = line.substringAfter("=").trim()

                    when (key) {
                        "id"           -> idValue = value
                        "location"     -> locationValue = value
                        "description"  -> descriptionValue = value
                        "lastdatetime" -> lastDateTimeValue = value
                    }
                }
            }
            "[FLASH]", "[RAM]", "[CD]" -> {
                if (line.contains("=")) {
                    val pCode = line.substringBefore("=").trim()
                    val rawData = line.substringAfter("=").trim()
                    val parts = rawData.split("/")

                    if (parts.size >= 2) {
                        val fileHex = if (parts.last().isEmpty()) parts[parts.size - 2].trim() else parts.last().trim()
                        val rawInt = fileHex.removePrefix("x").toIntOrNull(16) ?: 0
                        val hexString = rawInt.toString(16).uppercase()
                        val hexRaw = "x" + hexString.padStart(4, '0')
                        val scaleName = parts.getOrNull(6)?.trim() ?: ""
                        val scaleValue = varsMap[scaleName] ?: 1.0
                        val calculated = rawInt * scaleValue
                        val physicalValue = if (calculated % 1.0 == 0.0) calculated.toInt().toString() else calculated.toString()

                        val finalUnit = if (pCode == "4") "29.01.1964" else (parts.getOrNull(5) ?: "")
                        val parameter = ParameterData(
                            code = pCode,
                            idName = parts.getOrNull(0) ?: "",
                            description = parts.getOrNull(1) ?: "",
                            dataType = parts.getOrNull(2) ?: "",
                            modbusReg = parts.getOrNull(4) ?: "",
                            unit = parts.getOrNull(5) ?: "",
                            scaleName = scaleName,
                            initialHexBase = hexRaw,
                            initialPhysBase = physicalValue,
                            initialHexCtrl = "x0000",
                            initialPhysCtrl = "0"
                        )

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

    return DeviceInfoIni(
        fileName = fileName,
        id = idValue,
        location = locationValue,
        Description = descriptionValue,
        LastDateTime = lastDateTimeValue,
        flashParameters = flashParams,
        ramParameters = ramParams,
        cdParameters = cdParams,
        varsMap = varsMap
    )
}

// --- РЕАЛИЗАЦИЯ ACTUAL ---

actual suspend fun pickDirectory(): List<DeviceInfoIni>? {
    val results = mutableListOf<DeviceInfoIni>()
    try {
        println("DEBUG: Ожидание выбора папки...")
        val rootHandle: JsAny = jsShowDirectoryPicker().await<JsAny>()
        val rootName = getFileName(rootHandle)

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
                    if (name.lowercase().endsWith(".txt") || name.lowercase().endsWith(".ini")) {
                        try {
                            // Передаем имя файла, чтобы правильно выбрать кодировку чтения
                            val content = readFileContent(fileHandle, name)
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
    return try {
        println("DEBUG: Ожидание выбора одиночного файла...")
        val fileHandle: JsAny = jsShowOpenFilePicker().await<JsAny>()
        val name = getFileName(fileHandle)

        // Передаем имя файла, чтобы правильно декодировать оригинал
        val content = readFileContent(fileHandle, name)

        // Теперь, когда оригинальный .ini прочитан правильно (в windows-1251),
        // мы сохраняем его копию .txt в ЧИСТОМ Юникоде с уже исправленным русским текстом.
        if (name.lowercase().endsWith(".ini")) {
            println("DEBUG: Обнаружен .ini файл, инициирую копирование в .txt")
            jsSaveFileAsTxt(name, content)
        }

        val parsedInfo = parseIniContent(content, name)
        parsedInfo
    } catch (e: Exception) {
        println("DEBUG: Выбор файла отменен или произошла ошибка: ${e.message}")
        null
    }
}