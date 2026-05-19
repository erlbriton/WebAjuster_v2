package org.example.project.logic

import kotlin.js.js

// Чтение сохраненного имени порта напрямую из window
fun getWasmPortName(): kotlin.js.JsString = js("window.lastDetectedPortName || ''")

// Исправленная функция для кнопки ID
fun executeSerialTransceiveJs(hexPacket: String, expectedSize: Int): kotlin.js.Promise<kotlin.js.JsString?>? = js(
    """
    (function() {
        try {
            if (typeof window.wasmSerialTransceive === 'function') {
                var bytesIn = new Int8Array(hexPacket.length / 2);
                for (var i = 0; i < hexPacket.length; i += 2) {
                    bytesIn[i / 2] = parseInt(hexPacket.substr(i, 2), 16);
                }
                return window.wasmSerialTransceive(bytesIn, expectedSize).then(function(uint8Array) {
                    if (!uint8Array || uint8Array.length === 0) return null;
                    var hexOut = "";
                    for (var j = 0; j < uint8Array.length; j++) {
                        hexOut += (uint8Array[j] & 0xFF).toString(16).padStart(2, '0');
                    }
                    return hexOut;
                });
            } else {
                console.error("❌ window.wasmSerialTransceive не инициализирован для ID кнопки!");
            }
        } catch(e) {
            console.error("💥 Ошибка ID команды: " + e.message);
        }
        return null;
    })()
    """
)

// Асинхронная функция для вызова опроса таблиц
fun executeRealTransceiveJsAsync(hexPacket: String, expectedSize: Int): kotlin.js.Promise<kotlin.js.JsString?>? = js(
    """
    (function() {
        try {
            if (typeof window.wasmSerialTransceive === 'function') {
                var bytesIn = new Int8Array(hexPacket.length / 2);
                for (var i = 0; i < hexPacket.length; i += 2) {
                    bytesIn[i / 2] = parseInt(hexPacket.substr(i, 2), 16);
                }
                return window.wasmSerialTransceive(bytesIn, expectedSize).then(function(uint8Array) {
                    if (!uint8Array || uint8Array.length === 0) return null;
                    var hexOut = "";
                    for (var j = 0; j < uint8Array.length; j++) {
                        hexOut += (uint8Array[j] & 0xFF).toString(16).padStart(2, '0');
                    }
                    return hexOut;
                });
            } else {
                console.error("❌ window.wasmSerialTransceive не инициализирован для обновления данных!");
            }
        } catch(e) {
            console.error("💥 Ошибка трансивера опроса: " + e.message);
        }
        return null;
    })()
    """
)