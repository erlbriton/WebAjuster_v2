@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.example.project.utils

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

// =================================================================================
// СВЕРХНАДЕЖНЫЙ JS-МОСТ С ЗАЩИТОЙ ОТ ЗАВИСАНИЙ И УВЕЛИЧЕННЫМИ ПАУЗАМИ ДЛЯ CP210x
// =================================================================================

@JsFun("""
    () => {
        // Главная функция трансивера
        window.wasmSerialTransceive = async (requestBytes, expectedLen) => {
            if (window.serialIdleTimeout) {
                clearTimeout(window.serialIdleTimeout);
                window.serialIdleTimeout = null;
            }

            let port = window.activeWasmSerialPort;
            let reader = null;
            let writer = null;

            try {
                // 1. Если кэша нет, ищем в ранее разрешенных
                if (!port) {
                    const ports = await navigator.serial.getPorts();
                    if (ports.length > 0) {
                        port = ports[0]; 
                        console.log("[WebSerial] Подхватываем ранее разрешенный порт из getPorts()");
                    } else {
                        console.log("[WebSerial] Список getPorts() пуст. Запрашиваем окно у пользователя...");
                        port = await navigator.serial.requestPort(); 
                    }
                    window.activeWasmSerialPort = port;
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

                    // Даем CP210x чуть больше времени прийти в себя после открытия порта
                    await new Promise(r => setTimeout(r, 400));
                    await port.setSignals({ dataTerminalReady: true, requestToSend: true });
                    await new Promise(r => setTimeout(r, 200));
                }

                if (port.getInfo && !window.lastDetectedPortName) {
                    let detectedComName = "USB-Device";
                    const info = port.getInfo();
                    if (info.usbVendorId === 0x1A86) detectedComName = "CH340"; 
                    else if (info.usbVendorId === 0x10C4) detectedComName = "CP210x";
                    else if (info.usbVendorId === 0x0403) detectedComName = "FTDI"; 
                    window.lastDetectedPortName = detectedComName;
                    console.log("[WebSerial] 👍 Порт успешно открыт! Адаптер: " + detectedComName);
                }

                // 3. Обмен данными
                writer = port.writable.getWriter();
                reader = port.readable.getReader();

                await writer.write(requestBytes);

                let buf = new Uint8Array(0);
                let isTimeout = false;

                // Увеличиваем таймаут ожидания ответа до 1500мс для надежности первой инициализации
                const timeout = setTimeout(() => {
                    isTimeout = true;
                    if (reader) reader.cancel().catch(() => {});
                }, 1500);

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

                if (reader) { reader.releaseLock(); reader = null; }
                if (writer) { writer.releaseLock(); writer = null; }

                // Keep-Alive таймер бездействия (5 секунд)
                window.serialIdleTimeout = setTimeout(async () => {
                    if (window.activeWasmSerialPort && window.activeWasmSerialPort.readable) {
                        console.log("[WebSerial] Закрытие порта по таймауту бездействия.");
                        const p = window.activeWasmSerialPort;
                        window.activeWasmSerialPort = null;
                        window.lastDetectedPortName = "";
                        try { await p.close(); } catch(e) {}
                    }
                }, 5000);

                return buf;

            } catch (err) {
                console.error("💥 КРИТИЧЕСКАЯ ОШИБКА WebSerial API: " + err.message);
                
                if (reader) try { reader.releaseLock(); } catch(e) {}
                if (writer) try { writer.releaseLock(); } catch(e) {}
                
                // Вызываем forget() ТОЛЬКО если порт реально "умер" или отключен физически
                if (port && (err.message.includes("holding") || err.message.includes("device lost"))) {
                    console.warn("[WebSerial] Вызываем port.forget() для очистки дефектного разрешения...");
                    try { await port.forget(); } catch(e) {}
                    window.activeWasmSerialPort = null;
                }

                window.lastDetectedPortName = "";
                return null;
            }
        };

        // Функция ручного сброса и принудительного вызова диалога
        window.wasmForceChooseNewPort = async () => {
            console.log("[WebSerial] Принудительный сброс. Открываем окно выбора порта...");
            if (window.serialIdleTimeout) clearTimeout(window.serialIdleTimeout);
            
            if (window.activeWasmSerialPort) {
                try { await window.activeWasmSerialPort.close(); } catch(e) {}
            }
            
            window.activeWasmSerialPort = null;
            window.lastDetectedPortName = "";

            try {
                const newPort = await navigator.serial.requestPort();
                window.activeWasmSerialPort = newPort;
                console.log("[WebSerial] Пользователь выбрал новый порт:", newPort);
                return true;
            } catch (e) {
                console.log("[WebSerial] Выбор порта отменен пользователем.");
                return false;
            }
        };
    }
""")
external fun initWebSerialBridge()

@JsFun("() => window.lastDetectedPortName || ''")
external fun getLastDetectedPortName(): String

@JsFun("() => { window.activeWasmSerialPort = null; window.lastDetectedPortName = ''; console.log('[WebSerial] Кэш очищен.'); }")
external fun resetSerialConnection()

@JsFun("() => window.wasmForceChooseNewPort ? window.wasmForceChooseNewPort() : Promise.resolve(false)")
private external fun jsForceChooseNewPort(): kotlin.js.Promise<JsAny?>?

@JsFun("(request, expectedLen) => { if (window.wasmSerialTransceive) return window.wasmSerialTransceive(request, expectedLen); return Promise.resolve(null); }")
private external fun jsTransceive(request: Uint8Array, expectedLen: Int): kotlin.js.Promise<Uint8Array?>?

// =================================================================================
// КOTLIN ИНТЕРФЕЙС ДВИЖКА
// =================================================================================

object SerialEngine {

    init {
        initWebSerialBridge()
    }

    /**
     * Вызовите этот метод из Compose UI (например, при клике на кнопку "Сменить COM-порт"),
     * чтобы гарантированно открыть системное окно выбора устройства.
     */
    fun forceRequestNewPort() {
        jsForceChooseNewPort()
    }

    suspend fun transceive(request: ByteArray, expectedLen: Int): ByteArray? {
        val jsRequest = Uint8Array(request.size)
        for (i in request.indices) { jsRequest[i] = request[i] }

        val promise = jsTransceive(jsRequest, expectedLen) ?: return null

        val jsResponse: Uint8Array? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            promise.then { valData ->
                continuation.resumeWith(Result.success(valData))
                null
            }.catch {
                continuation.resumeWith(Result.success(null))
                null
            }
        }

        if (jsResponse == null || jsResponse.length == 0) {
            return null
        }

        try {
            val portName = getLastDetectedPortName()
            if (portName.isNotEmpty()) {
                org.example.project.viewmodels.MainViewModel.instance.setConnectedPort(portName)
            }
        } catch (e: Exception) { }

        val ktResult = ByteArray(jsResponse.length)
        for (i in 0 until jsResponse.length) { ktResult[i] = jsResponse[i] }
        return ktResult
    }
}