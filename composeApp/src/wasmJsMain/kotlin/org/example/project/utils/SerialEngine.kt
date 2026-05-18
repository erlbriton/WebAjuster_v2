@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.example.project.logic

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

// Интерфейс для безопасного обмена данными между JS и Kotlin/Wasm БЕЗ использования dynamic
external interface SerialResponse : JsAny {
    val buffer: Uint8Array?
    val portName: String?
}

@JsFun("""
    async (requestBytes, expectedLen) => {
        let port;
        let reader;
        let writer;
        try {
            port = await navigator.serial.requestPort();
            
            let detectedComName = "USB-UART"; // Базовое имя, если чип совсем экзотический
            
            if (port.getInfo) {
                const info = port.getInfo();
                const vid = info.usbVendorId;
                const pid = info.usbProductId;
                
                // === ОПРЕДЕЛЯЕМ ИСКЛЮЧИТЕЛЬНО ИМЯ АДАПТЕРА ПО VID ===
                if (vid !== undefined) {
                    if (vid === 0x1A86) {
                        detectedComName = "CH340"; 
                    } else if (vid === 0x10C4) {
                        detectedComName = "CP210x"; // Сюда четко попадает ваш CP2103
                    } else if (vid === 0x0403) {
                        detectedComName = "FTDI"; 
                    } else if (vid === 0x067B) {
                        detectedComName = "PL2303"; 
                    } else if (vid === 0x2341) {
                        detectedComName = "Arduino"; 
                    }
                }
                
                console.log("[WebSerial] Адаптер успешно определен. VID: 0x" + vid.toString(16) + " -> " + detectedComName);
            }

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
                        nb.set(buf); nb.set(value, buf.length);
                        buf = nb;
                        if (buf.length >= expectedLen) break;
                    }
                    if (done) break;
                }
            } finally {
                clearTimeout(timeout);
            }

            reader.releaseLock();
            writer.releaseLock();
            await port.close();

            return { buffer: buf, portName: detectedComName };

        } catch (err) {
            console.error("💥 Ошибка Serial Engine: " + err.message);
            if (reader) try { reader.releaseLock(); } catch(e) {}
            if (writer) try { writer.releaseLock(); } catch(e) {}
            if (port) try { await port.close(); } catch(e) {}
            return null;
        }
    }
""")
private external fun jsTransceive(request: Uint8Array, expectedLen: Int): kotlin.js.Promise<SerialResponse?>?

object SerialEngine {

    /**
     * Основной мост между Kotlin Coroutines и JS Web Serial API
     */
    suspend fun transceive(request: ByteArray, expectedLen: Int): ByteArray? {
        val jsRequest = Uint8Array(request.size)
        for (i in request.indices) { jsRequest[i] = request[i] }

        val promise = jsTransceive(jsRequest, expectedLen) ?: return null

        val jsResult: SerialResponse? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            promise.then { valData ->
                continuation.resumeWith(Result.success(valData))
                null
            }.catch { err ->
                continuation.resumeWith(Result.failure(Exception("JS Promise Rejected")))
                null
            }
        }

        if (jsResult == null) return null

        // Передаем чистое имя адаптера во ViewModel для отображения в LineTwoTable
        try {
            val portName = jsResult.portName
            if (portName != null && portName.isNotEmpty()) {
                org.example.project.viewmodels.MainViewModel.instance.setConnectedPort(portName)
            }
        } catch (e: Exception) {
            println("Не удалось записать имя адаптера в UI")
        }

        // Извлекаем буфер ответа
        val jsResponse: Uint8Array? = jsResult.buffer

        if (jsResponse == null || jsResponse.length == 0) return null

        val ktResult = ByteArray(jsResponse.length)
        for (i in 0 until jsResponse.length) { ktResult[i] = jsResponse[i] }
        return ktResult
    }
}