const SerialManager = {
    port: null,
    writer: null,
    reader: null,
    isConnected: false,
    buffer: [],
    paused: false,
    onData: null,
    oscilloAddresses: [],
    oscilloChunks: [],            // 🔥 НОВОЕ: массив чанков {start, count}
    oscilloCurrentIdx: 0,
    lastRequestAddr: 0,
    lastRequestChunk: null,       // 🔥 НОВОЕ: последний запрошенный чанк

    async connect() {
        try {
            if (this.port && this.isConnected) {
                console.log('[Serial] ⚠️ Порт уже открыт');
                return true;
            }

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

// 🔥 НОВЫЙ МЕТОД: группировка адресов в чанки
_buildChunks(addresses, maxChunk = 125) {
    const sorted = [...addresses].sort((a, b) => a - b);
    const chunks = [];
    let start = null, prev = null;

    const addRange = (s, e) => {
        let cur = s;
        while (cur <= e) {
            const count = Math.min(e - cur + 1, maxChunk);
            chunks.push({ start: cur, count: count });
            cur += count;
        }
    };

    for (const addr of sorted) {
        if (start === null) {
            start = addr;
            prev = addr;
        } else if (addr === prev + 1) {
            prev = addr;
        } else {
            addRange(start, prev);
            start = addr;
            prev = addr;
        }
    }
    if (start !== null) {
        addRange(start, prev);
    }
    return chunks;
},

    _startSender() {
        (async () => {
            while (this.port && this.isConnected) {
                if (this.paused || this.oscilloChunks.length === 0) {
                    await new Promise(r => setTimeout(r, 10));
                    continue;
                }
                try {
                    const chunk = this.oscilloChunks[this.oscilloCurrentIdx];
                    this.lastRequestAddr = chunk.start;    // для совместимости
                    this.lastRequestChunk = chunk;         // 🔥 НОВОЕ: сохраняем весь чанк
                    this.oscilloCurrentIdx = (this.oscilloCurrentIdx + 1) % this.oscilloChunks.length;

                    // 🔥 ИЗМЕНЕНО: читаем chunk.count регистров вместо 1
                    const body = new Uint8Array([
                        0x01, 0x03,
                        (chunk.start >> 8) & 0xFF, chunk.start & 0xFF,
                        (chunk.count >> 8) & 0xFF, chunk.count & 0xFF
                    ]);
                    let crc = 0xFFFF;
                    for (let b of body) { crc ^= b; for (let i = 0; i < 8; i++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1; }
                    await this.writer.write(new Uint8Array([...body, crc & 0xFF, (crc >> 8) & 0xFF]));
                } catch (e) {
                    console.warn('[Serial] ⚠️ Ошибка отправки:', e.message);
                    this.handleDisconnect(e);
                    break;
                }
                await new Promise(r => setTimeout(r, 2));  // 🔥 Уменьшено с 5 до 2 мс
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

                    this.buffer.push(...value);

                    if (this.paused) continue;

                    while (this.buffer.length >= 5) {
                        if (this.buffer[0] === 0x01 && this.buffer[1] === 0x03) {
                            const byteCount = this.buffer[2];
                            const expectedLength = 3 + byteCount + 2;

                            if (this.buffer.length < expectedLength) break;

                            let crc = 0xFFFF;
                            for (let i = 0; i < expectedLength - 2; i++) {
                                crc ^= this.buffer[i];
                                for (let j = 0; j < 8; j++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                            }

                            if (crc === (this.buffer[expectedLength - 2] | (this.buffer[expectedLength - 1] << 8))) {
                                const values = [];
                                for (let i = 0; i < byteCount; i += 2) {
                                    values.push((this.buffer[3 + i] << 8) | this.buffer[4 + i]);
                                }

                                if (this.onData && this.lastRequestChunk) {
                                    this.onData(values, this.lastRequestChunk.start, performance.now());
                                }
                            }
                            this.buffer.splice(0, expectedLength);
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

window.wasmSerialTransceive = async function(requestBytes) {
    const uint8Request = new Uint8Array(requestBytes.buffer, requestBytes.byteOffset, requestBytes.byteLength);
    const reqHex = Array.from(uint8Request).map(b => b.toString(16).padStart(2, '0')).join(' ');
    console.log(`[Serial] 🔹 wasmSerialTransceive: запрос [${reqHex}]`);

    if (!SerialManager.isConnected || !SerialManager.writer || !SerialManager.reader) {
        console.error('[Serial] ❌ Порт не открыт!');
        return new Uint8Array(0);
    }

    SerialManager.paused = true;
    console.log('[Serial] ⏸️ Осциллограф приостановлен');

    try {
        await new Promise(r => setTimeout(r, 300));
        SerialManager.buffer.splice(0, SerialManager.buffer.length);
        console.log('[Serial] 🧹 Буфер очищен, длина:', SerialManager.buffer.length);

        await SerialManager.writer.write(uint8Request);
        console.log('[Serial] 📤 Запрос отправлен');

        const timeout = 2000;
        const startTime = performance.now();
        let lastLogLen = 0;

        while (performance.now() - startTime < timeout) {
            const curLen = SerialManager.buffer.length;

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