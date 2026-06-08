const SerialManager = {
    port: null,
    writer: null,
    reader: null,
    isConnected: false,
    buffer: [],
    paused: false,
    onData: null,

    async connect() {
        try {
            this.port = await navigator.serial.requestPort();
            await this.port.open({ baudRate: 115200 });
            console.log('[Serial] ✅ Порт открыт');
            this.isConnected = true;
            return true;
        } catch (err) {
            console.error('[Serial] ❌', err.message);
            throw err;
        }
    },

    async start() {
        try {
            this.writer = this.port.writable.getWriter();
            this.reader = this.port.readable.getReader();
        } catch (e) {
            console.error('[Serial] ❌ Ошибка writer/reader:', e.message);
            throw e;
        }

        this._startSender();
        this._startReceiver();
    },

    _startSender() {
        (async () => {
            while (this.port && this.isConnected) {
                if (this.paused) {
                    await new Promise(r => setTimeout(r, 10));
                    continue;
                }
                try {
                    const body = new Uint8Array([0x01, 0x03, 0x00, 0x2D, 0x00, 0x02]);
                    let crc = 0xFFFF;
                    for (let b of body) { crc ^= b; for (let i = 0; i < 8; i++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1; }
                    await this.writer.write(new Uint8Array([...body, crc & 0xFF, (crc >> 8) & 0xFF]));
                } catch (e) {
                    console.warn('[Serial] ⚠️ Ошибка отправки:', e.message);
                    this.handleDisconnect(e);
                    break;
                }
                await new Promise(r => setTimeout(r, 100));
            }
        })();
    },

    _startReceiver() {
        (async () => {
            while (this.port && this.isConnected) {
                try {
                    const { value, done } = await this.reader.read();
                    if (done) break;
                    if (!value) continue;

                    // 🔥 ВАЖНО: пишем в this.buffer (тот же массив, что и SerialManager.buffer)
                    this.buffer.push(...value);

                    if (this.paused) continue;

                    while (this.buffer.length >= 9) {
                        if (this.buffer[0] === 0x01 && this.buffer[1] === 0x03 && this.buffer[2] === 0x04) {
                            let crc = 0xFFFF;
                            for (let i = 0; i < 7; i++) {
                                crc ^= this.buffer[i];
                                for (let j = 0; j < 8; j++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                            }

                            if (crc === (this.buffer[7] | (this.buffer[8] << 8))) {
                                const v1 = (this.buffer[3] << 8) | this.buffer[4];
                                const v2 = (this.buffer[5] << 8) | this.buffer[6];

                                if (this.onData) {
                                    this.onData([v1, v2], performance.now());
                                }
                            }
                            this.buffer.splice(0, 9);
                        } else {
                            this.buffer.shift();
                        }
                    }
                } catch (e) {
                    console.error('[Serial] ❌ Ошибка чтения:', e.message);
                    this.handleDisconnect(e);
                    break;
                }
            }
        })();
    },

    handleDisconnect(error) {
        console.error('[Serial] 🔌 Ошибка устройства:', error.message);
        this.isConnected = false;

        if (error.message.includes('device has been lost') || error.message.includes('disconnected')) {
            this.showError('Устройство отключено! Проверьте USB-кабель.');
        } else {
            this.showError('Ошибка связи: ' + error.message);
        }
    },

    showError(message) {
        let dialog = document.getElementById('disconnectDialog');
        if (!dialog) {
            dialog = document.createElement('div');
            dialog.id = 'disconnectDialog';
            dialog.innerHTML = `
                <div style="position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); background: white; border: 2px solid #f44336; border-radius: 8px; padding: 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.3); z-index: 100000; min-width: 350px; text-align: center;">
                    <div style="font-size: 48px; margin-bottom: 10px;">⚠️</div>
                    <h3 style="margin: 10px 0; color: #f44336;">Внимание!</h3>
                    <p id="disconnectMessage" style="margin: 15px 0; color: #666;">${message}</p>
                    <button onclick="document.getElementById('disconnectDialog').style.display='none'" style="padding: 10px 30px; background: #2196f3; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; margin-top: 10px;">Закрыть</button>
                </div>
                <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 99999;"></div>
            `;
            document.body.appendChild(dialog);
        } else {
            const msgEl = document.getElementById('disconnectMessage');
            if (msgEl) msgEl.textContent = message;
            dialog.style.display = 'block';
        }
    },

    stop() {
        this.isConnected = false;
    }
};

// 🔥 Глобальная функция для Kotlin/Wasm
window.wasmSerialTransceive = async function(requestBytes) {
    const uint8Request = new Uint8Array(requestBytes.buffer, requestBytes.byteOffset, requestBytes.byteLength);
    const reqHex = Array.from(uint8Request).map(b => b.toString(16).padStart(2, '0')).join(' ');
    console.log(`[Serial] 🔹 wasmSerialTransceive: запрос [${reqHex}]`);

    if (!SerialManager.isConnected || !SerialManager.writer || !SerialManager.reader) {
        console.error('[Serial] ❌ Порт не открыт!');
        return new Uint8Array(0);
    }

    // 1. Останавливаем осциллограф
    SerialManager.paused = true;
    console.log('[Serial] ⏸️ Осциллограф приостановлен');

    try {
        // 2. 🔥 Ждём 300мс, чтобы _startSender гарантированно остановился
        await new Promise(r => setTimeout(r, 300));

        // 3. 🔥 КРИТИЧНО: очищаем ТОТ ЖЕ массив, а не создаём новый!
        //    Если сделать SerialManager.buffer = [], то _startReceiver
        //    продолжит писать в старый массив, и ответы потеряются.
        SerialManager.buffer.splice(0, SerialManager.buffer.length);
        console.log('[Serial] 🧹 Буфер очищен, длина:', SerialManager.buffer.length);

        // 4. Отправляем запрос
        await SerialManager.writer.write(uint8Request);
        console.log('[Serial] 📤 Запрос отправлен');

        // 5. Ждём ответ с таймаутом и логированием
        const timeout = 2000;
        const startTime = performance.now();
        let lastLogLen = 0;

        while (performance.now() - startTime < timeout) {
            const curLen = SerialManager.buffer.length;

            // Логируем только при изменении размера буфера
            if (curLen > 0 && curLen !== lastLogLen) {
                const hex = SerialManager.buffer.slice(0, Math.min(curLen, 30))
                    .map(b => b.toString(16).padStart(2, '0')).join(' ');
                console.log(`[Serial] 📦 Буфер: ${curLen} байт [${hex}]`);
                lastLogLen = curLen;
            }

            if (curLen >= 5) {
                const funcCode = SerialManager.buffer[1];
                let expectedLength = 0;

                if (funcCode === 0x03 || funcCode === 0x04) {
                    const byteCount = SerialManager.buffer[2];
                    expectedLength = 3 + byteCount + 2;
                } else if (funcCode === 0x06 || funcCode === 0x10) {
                    expectedLength = 8;
                } else if (funcCode & 0x80) {
                    expectedLength = 5;
                }

                if (expectedLength > 0 && curLen >= expectedLength) {
                    // Проверка CRC
                    let crc = 0xFFFF;
                    for (let i = 0; i < expectedLength - 2; i++) {
                        crc ^= SerialManager.buffer[i];
                        for (let j = 0; j < 8; j++) {
                            crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                        }
                    }

                    const receivedCrc = SerialManager.buffer[expectedLength - 2] |
                                       (SerialManager.buffer[expectedLength - 1] << 8);

                    if (crc === receivedCrc) {
                        const response = new Uint8Array(SerialManager.buffer.slice(0, expectedLength));
                        SerialManager.buffer.splice(0, expectedLength);
                        const resHex = Array.from(response).map(b => b.toString(16).padStart(2, '0')).join(' ');
                        console.log(`[Serial] 📥 Ответ получен: [${resHex}]`);
                        return response;
                    } else {
                        console.warn(`[Serial] ⚠️ CRC не совпал. Ожид: ${crc.toString(16)}, Получ: ${receivedCrc.toString(16)}`);
                        SerialManager.buffer.shift();
                        lastLogLen = SerialManager.buffer.length;
                    }
                }
            }

            await new Promise(r => setTimeout(r, 10));
        }

        console.warn(`[Serial] ⚠️ ТАЙМАУТ. В буфере ${SerialManager.buffer.length} байт`);
        return new Uint8Array(0);
    } catch (e) {
        console.error('[Serial] ❌ Ошибка transceive:', e.message);
        return new Uint8Array(0);
    } finally {
        SerialManager.paused = false;
        console.log('[Serial] ▶️ Осциллограф возобновлён');
    }
};

console.log('[Serial] ✅ wasmSerialTransceive инициализирован');