@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.example.project.logic

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

// Выносим функцию на самый верхний уровень (Top-level) — теперь компилятор будет счастлив
@JsFun("""
    async (requestBytes, expectedLen) => {
        let port;
        let reader;
        let writer;
        try {
            port = await navigator.serial.requestPort();
            await port.open({ 
                baudRate: 115200, 
                dataBits: 8, 
                stopBits: 1, 
                parity: "none", 
                flowControl: "none" 
            });

            // Ваши проверенные тайминги и сигналы управления
            await new Promise(r => setTimeout(r, 500));
            await port.setSignals({ dataTerminalReady: true, requestToSend: true });
            await new Promise(r => setTimeout(r, 200));

            writer = port.writable.getWriter();
            reader = port.readable.getReader();

            // Отправляем пакет данных
            await writer.write(requestBytes);

            let buf = new Uint8Array(0);
            let isTimeout = false;

            // Предохранитель на 1000 мс
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

            return buf;

        } catch (err) {
            console.error("💥 Ошибка Serial Engine: " + err.message);
            if (reader) try { reader.releaseLock(); } catch(e) {}
            if (writer) try { writer.releaseLock(); } catch(e) {}
            if (port) try { await port.close(); } catch(e) {}
            return null;
        }
    }
""")
private external fun jsTransceive(request: Uint8Array, expectedLen: Int): kotlin.js.Promise<Uint8Array?>?

// Сам объект движка оставляем чистым, он просто вызывает внешнюю функцию
object SerialEngine {

    /**
     * Основной мост между Kotlin Coroutines и JS Web Serial API
     */
    suspend fun transceive(request: ByteArray, expectedLen: Int): ByteArray? {
        // Конвертируем Kotlin ByteArray -> JS Uint8Array
        val jsRequest = Uint8Array(request.size)
        for (i in request.indices) { jsRequest[i] = request[i] }

        // Вызываем нашу top-level JS функцию
        val promise = jsTransceive(jsRequest, expectedLen) ?: return null

        // Ожидаем выполнение Promise внутри корутины
        val jsResponse = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            promise.then { valData ->
                continuation.resumeWith(Result.success(valData))
                null
            }.catch { err ->
                continuation.resumeWith(Result.failure(Exception("JS Promise Rejected")))
                null
            }
        }

        if (jsResponse == null || jsResponse.length == 0) return null

        // Конвертируем JS Uint8Array -> Kotlin ByteArray
        val ktResult = ByteArray(jsResponse.length)
        for (i in 0 until jsResponse.length) { ktResult[i] = jsResponse[i] }
        return ktResult
    }
}