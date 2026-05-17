@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.example.project.utils

import org.example.project.models.DeviceInfoIni

// Низкоуровневый JS-движок для вызова системного окна выбора файла
@JsFun("""
    () => {
        return new Promise((resolve) => {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = '.ini'; 

            input.onchange = (event) => {
                const file = event.target.files[0];
                if (!file) {
                    resolve(null);
                    return;
                }

                const fileName = file.name;

                const reader = new FileReader();
                reader.onload = (e) => {
                    const text = e.target.result;
                    
                    let description = "Неизвестно";
                    const lines = text.split(/\r?\n/);
                    for (let line of lines) {
                        if (line.toLowerCase().startsWith("description=")) {
                            description = line.split("=")[1].trim();
                            break;
                        }
                    }

                    resolve({
                        FileName: fileName,
                        Description: description
                    });
                };
                reader.readAsText(file);
            };

            window.addEventListener('focus', () => {
                setTimeout(() => {
                    if (!input.files.length) resolve(null);
                }, 300);
            }, { once: true });

            input.click();
        });
    }
""")
private external fun jsPickSingleFile(): kotlin.js.Promise<kotlin.js.JsAny?>?

// JS-хелперы для безопасного извлечения данных из JsAny
@JsFun("(obj) => obj.Description")
private external fun getDescriptionFromJs(obj: kotlin.js.JsAny): String?

@JsFun("(obj) => obj.FileName")
private external fun getFileNameFromJs(obj: kotlin.js.JsAny): String?

// Адаптер, который связывает JS Promise с корутинами Kotlin
actual suspend fun pickSingleFile(): DeviceInfoIni? {
    val promise = jsPickSingleFile() ?: return null

    val jsResult = kotlinx.coroutines.suspendCancellableCoroutine<kotlin.js.JsAny?> { continuation ->
        promise.then { valData ->
            continuation.resumeWith(Result.success(valData))
            null
        }.catch { err ->
            continuation.resumeWith(Result.failure(Exception("File picker error")))
            null
        }
    } ?: return null

    // Извлекаем переданные из JS свойства
    val description = getDescriptionFromJs(jsResult) ?: "Неизвестно"
    val fileName = getFileNameFromJs(jsResult) ?: "unknown.ini"

    // Кормим компилятор ВСЕМИ тремя полями, которые он поочередно просил: id, fileName, location
    return DeviceInfoIni(
        id = "",
        fileName = fileName,
        location = ""
    ).apply {
        Description = description
    }
}

// Заглушка для папки
actual suspend fun pickDirectory(): List<DeviceInfoIni>? {
    println("Выбор папки пока не реализован для Web")
    return null
}