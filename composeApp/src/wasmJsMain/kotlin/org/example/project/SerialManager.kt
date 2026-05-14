@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.example.project.logic

import org.khronos.webgl.Int8Array
import org.khronos.webgl.set

/**
 * Старый тест FPS для совместимости
 */
@JsFun("(a, b) => { window.runModbusTest(a, b); }")
external fun jsModbusFpsTest(dataArray: Int8Array, expected: Int)

/**
 * Основная функция чтения ID через Web Serial API
 */
@JsFun("""
    async () => {
        let port;
        try {
            // 1. Запрос порта у пользователя
            port = await navigator.serial.requestPort();
            await port.open({ baudRate: 115200 });
            
            const writer = port.writable.getWriter();
            const reader = port.readable.getReader();

            // 2. Формируем запрос: Читаем 20 регистров начиная с 20-го (0x0014)
            // Пакет: [01] Addr, [03] Func, [00 14] Start, [00 14] Count, [84 0E] CRC
           const request = new Uint8Array([0x01, 0x03, 0x01, 0x00, 0x00, 0x14, 0x45, 0xE5]);

console.log("--> Проверка диапазона 256-276 (0x0100)...");
await writer.write(request);
writer.releaseLock();

            // 3. Читаем ответ (ожидаем 45 байт)
            let receivedData = new Uint8Array(0);
            const expectedLength = 45;
            
            // Читаем, пока не соберем пакет или не выйдет время
            const startTime = Date.now();
            while (receivedData.length < expectedLength && (Date.now() - startTime) < 1000) {
                const { value, done } = await reader.read();
                if (done) break;
                
                let newBuffer = new Uint8Array(receivedData.length + value.length);
                newBuffer.set(receivedData);
                newBuffer.set(value, receivedData.length);
                receivedData = newBuffer;
            }

            if (receivedData.length > 0) {
                console.log("<-- Получено байт: " + receivedData.length);
                
                // Вывод в HEX для отладки
                let hex = "";
                for (let i = 0; i < receivedData.length; i++) {
                    hex += receivedData[i].toString(16).padStart(2, '0').toUpperCase() + " ";
                }
                console.log("RAW HEX: " + hex);

                // Декодирование в текст для поиска "EFI v3.4.0..."
                let ascii = "";
                for (let i = 3; i < receivedData.length - 2; i++) {
                    const char = receivedData[i];
                    if (char >= 32 && char <= 126) {
                        ascii += String.fromCharCode(char);
                    } else {
                        ascii += "."; 
                    }
                }
                console.log("ASCII DECODE: " + ascii);
                
                if (ascii.includes("EFI") || ascii.includes("intmash")) {
                    console.log("✅ СТРОКА ИДЕНТИФИКАЦИИ НАЙДЕНА!");
                } else {
                    console.log("ℹ️ В этом диапазоне текста нет, пробуем дальше...");
                }
            } else {
                console.log("❌ Устройство не ответило на запрос 20-40.");
            }

            reader.releaseLock();
            await port.close();
            console.log("Порт закрыт.");
        } catch (err) {
            console.error("ОШИБКА Serial API: " + err.message);
            if (port) { try { await port.close(); } catch(e) {} }
        }
    }
""")
external fun jsReadDeviceId(): kotlin.js.JsAny?

/**
 * Реализация для commonMain (поиск порта)
 */
actual suspend fun findSerialPort(data: ByteArray) {
    val i8Array = Int8Array(data.size)
    for (i in data.indices) {
        i8Array.set(i, data[i])
    }
    jsModbusFpsTest(i8Array, 159)
}

/**
 * Реализация для кнопки ID в интерфейсе
 */
actual suspend fun readDeviceIdentification() {
    jsReadDeviceId()
}