package org.example.project.utils

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

// Объявляем функции, которые теперь живут в serial_bridge.js
@JsFun("() => window.lastDetectedPortName || ''")
external fun getLastDetectedPortName(): String

@JsFun("() => { window.activeWasmSerialPort = null; window.lastDetectedPortName = ''; console.log('[WebSerial] Кэш очищен.'); }")
external fun resetSerialConnection()

@JsFun("() => window.wasmForceChooseNewPort ? window.wasmForceChooseNewPort() : Promise.resolve(false)")
private external fun jsForceChooseNewPort(): kotlin.js.Promise<JsAny?>?

@JsFun("(request, expectedLen) => { if (window.wasmSerialTransceive) return window.wasmSerialTransceive(request, expectedLen); return Promise.resolve(null); }")
private external fun jsTransceive(request: Uint8Array, expectedLen: Int): kotlin.js.Promise<Uint8Array?>?

object SerialEngine {
    // Больше не нужно вызывать initWebSerialBridge(), так как JS загружается через HTML

    fun forceRequestNewPort() {
        jsForceChooseNewPort()
    }

    suspend fun transceive(request: ByteArray, expectedLen: Int): ByteArray? {
        val jsRequest = Uint8Array(request.size)
        for (i in request.indices) { jsRequest[i] = request[i] }

        val promise = jsTransceive(jsRequest, expectedLen) ?: return null

        val jsResponse: Uint8Array? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            promise.then { valData ->
                continuation.resumeWith(Result.success(valData as Uint8Array?))
                null
            }.catch {
                continuation.resumeWith(Result.success(null))
                null
            }
        }

        if (jsResponse == null || jsResponse.length == 0) return null

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