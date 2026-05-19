@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.example.project.utils

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

// =================================================================================
// НАДЕЖНЫЙ JS-МОСТ С ЗАЩИТОЙ ОТ ЗАВИСАНИЯ ПОРТОВ
// =================================================================================

@JsFun("""
    () => {
        window.wasmSerialTransceive = async (requestBytes, expectedLen) => {
            if (window.serialIdleTimeout) {
                clearTimeout(window.serialIdleTimeout);
                window.serialIdleTimeout = null;
            }

            let port = window.activeWasmSerialPort;
            let reader = null;
            let writer = null;
            let isNewConnection = false;

            try {
                // 1. Проверяем кэшированный порт
                if (!port) {
                    const ports = await navigator.serial.getPorts();
                    if (ports.length > 0) {
                        // Берем первый доступный. 
                        // ВНИМАНИЕ: Если портов несколько, тут может выбраться не тот!
                        port = ports[0]; 
                        console.log("[WebSerial] Подхватываем ранее разрешенный порт из getPorts()");
                    } else {
                        console.log("[WebSerial] Нет разрешенных портов. Запрашиваем окно выбора у пользователя...");
                        port = await navigator.serial.requestPort(); 
                    }
                    window.activeWasmSerialPort = port;
                    isNewConnection = true;
                }

                // 2. Попытка открытия порта
                if (!port.readable) {
                    console.log("[WebSerial] Попытка вызвать port.open()...");
                    await port.open({ 
                        baudRate: 115200, 
                        dataBits: 8, 
                        stopBits: 1, 
                        parity: "none", 
                        flowControl: "none" 
                    });

                    await new Promise(r => setTimeout(r, 500));
                    await port.setSignals({ dataTerminalReady: true, requestToSend: true });
                    await new Promise(r => setTimeout(r, 200));
                    isNewConnection = true;
                }

                if (isNewConnection && port.getInfo) {
                    let detectedComName = "USB-UART";
                    const info = port.getInfo();
                    const vid = info.usbVendorId;
                    if (vid === 0x1A86) detectedComName = "CH340"; 
                    else if (vid === 0x10C4) detectedComName = "CP210x";
                    else if (vid === 0x0403) detectedComName = "FTDI"; 
                    else if (vid === 0x067B) detectedComName = "PL2303"; 
                    
                    window.lastDetectedPortName = detectedComName;
                    console.log("[WebSerial] 👍 Порт успешно открыт! Адаптер: " + detectedComName);
                }

                // 3. Работа с потоками данных
                writer = port.writable.getWriter();
                reader = port.readable.getReader();

                await writer.write(requestBytes);

                let buf = new Uint8Array(0);
                let isTimeout = false;

                const timeout = setTimeout(() => {
                    isTimeout = true;
                    if (reader) reader.cancel().catch(() => {});
                }, 1000);

                try {
                    while (!isTimeout) {
                        const { value, done } = await reader.read();
                        if (value && value.length > 0) {
                            let nb = new Uint8Array(buf.length + value.length);
                            nb.set(buf); 
                            nb.set(value, buf.length);
                            buf = nb;
                            if (buf.length >= expectedLen) break;
                        }
                        if (done) break;
                    }
                } finally {
                    clearTimeout(timeout);
                }

                reader.releaseLock(); reader = null;
                writer.releaseLock(); writer = null;

                // 4. Таймер удержания сессии (Keep-Alive)
                window.serialIdleTimeout = setTimeout(async () => {
                    if (window.activeWasmSerialPort && window.activeWasmSerialPort.readable) {
                        console.log("[WebSerial] Сессия закрыта по таймауту бездействия (3 сек).");
                        const p = window.activeWasmSerialPort;
                        window.activeWasmSerialPort = null;
                        try { await p.close(); } catch(e) {}
                    }
                }, 3000);

                return buf;

            } catch (err) {
                // КРИТИЧЕСКАЯ ИСПРАВЛЕННАЯ ЧАСТЬ: Если порт занят ОС, 
                // мы ОПАСЛИВО СБРАСЫВАЕМ ВСЕ КЭШИ, чтобы дать приложению шанс на восстановление.
                console.error("💥 КРИТИЧЕСКАЯ ОШИБКА WebSerial API: " + err.message);
                
                if (reader) try { reader.releaseLock(); } catch(e) {}
                if (writer) try { writer.releaseLock(); } catch(e) {}
                
                // Полностью уничтожаем ссылки на этот проблемный девайс в текущей сессии
                window.activeWasmSerialPort = null;
                window.lastDetectedPortName = "";
                
                return null;
            }
        };
    }
""")
external fun initWebSerialBridge()

@JsFun("() => window.lastDetectedPortName || ''")
external fun getLastDetectedPortName(): String

// Экспортируем функцию сброса для вызова из Kotlin (например, при кликах или ошибках)
@JsFun("() => { window.activeWasmSerialPort = null; window.lastDetectedPortName = ''; if(window.serialIdleTimeout) clearTimeout(window.serialIdleTimeout); console.log('[WebSerial] Кэш портов принудительно очищен.'); }")
external fun resetSerialConnection()

@JsFun("(request, expectedLen) => { if (window.wasmSerialTransceive) return window.wasmSerialTransceive(request, expectedLen); return Promise.resolve(null); }")
private external fun jsTransceive(request: Uint8Array, expectedLen: Int): kotlin.js.Promise<Uint8Array?>?

// =================================================================================
// ОБЪЕКТ ДВИЖКА
// =================================================================================

object SerialEngine {

    init {
        initWebSerialBridge()
    }

    suspend fun transceive(request: ByteArray, expectedLen: Int): ByteArray? {
        val jsRequest = Uint8Array(request.size)
        for (i in request.indices) { jsRequest[i] = request[i] }

        val promise = jsTransceive(jsRequest, expectedLen) ?: return null

        val jsResponse: Uint8Array? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            promise.then { valData ->
                continuation.resumeWith(Result.success(valData))
                null
            }.catch { err ->
                continuation.resumeWith(Result.failure(Exception("JS Promise Rejected")))
                null
            }
        }

        if (jsResponse == null || jsResponse.length == 0) {
            // Если буфер пуст, на всякий случай сбрасываем кэш, возможно устройство отвалилось физически
            resetSerialConnection()
            return null
        }

        try {
            val portName = getLastDetectedPortName()
            if (portName.isNotEmpty()) {
                org.example.project.viewmodels.MainViewModel.instance.setConnectedPort(portName)
            }
        } catch (e: Exception) {
            println("Не удалось записать имя адаптера в UI")
        }

        val ktResult = ByteArray(jsResponse.length)
        for (i in 0 until jsResponse.length) { ktResult[i] = jsResponse[i] }
        return ktResult
    }
}