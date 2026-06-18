// serial_worker.js - Dedicated Worker для управления Serial-портом

let serialWriter = null;
let serialReader = null;
let isConnected = false;
let isRunning = false;

let config = {
    slaveAddress: 0x01,
    registerAddr: 0x002d,
    paramsCount: 78,
    baudRate: 115200
};

let scopePort = null;
let mainPort = null;

self.onmessage = function(event) {
    const msg = event.data;
    console.log('[SerialWorker] Получено сообщение:', msg.type);

    switch (msg.type) {
        case 'init':
            config = { ...config, ...msg.config };
            self.postMessage({ type: 'configReceived' });
            break;

        case 'setScopePort':
            scopePort = msg.port;
            scopePort.onmessage = function(e) {
                console.log('[SerialWorker] Сообщение от Scope Worker:', e.data);
            };
            scopePort.start();
            console.log('[SerialWorker] ✅ Порт для Scope Worker установлен');
            break;

        case 'setMainPort':
            mainPort = msg.port;
            mainPort.onmessage = function(e) {
                console.log('[SerialWorker] Сообщение от Main Thread:', e.data);
            };
            mainPort.start();
            console.log('[SerialWorker] ✅ Порт для Main Thread установлен');
            break;

        // 🔥 ПРАВИЛЬНО: принимаем STREAMS и создаём reader/writer
        case 'setStreams':
            try {
                serialReader = msg.readable.getReader();
                serialWriter = msg.writable.getWriter();
                isConnected = true;
                console.log('[SerialWorker] ✅ Streams получены, reader/writer созданы');
                self.postMessage({ type: 'connected' });
            } catch (error) {
                console.error('[SerialWorker] ❌ Ошибка создания reader/writer:', error.message);
                self.postMessage({ type: 'error', message: error.message });
            }
            break;

        case 'disconnect':
            disconnectPort();
            break;

        case 'start':
            startOscilloscope();
            break;

        case 'stop':
            stopOscilloscope();
            break;

        case 'transceive':
            handleTransceive(msg.data);
            break;
    }
};

async function disconnectPort() {
    stopOscilloscope();

    if (serialReader) {
        try { await serialReader.cancel(); } catch (e) {}
        serialReader = null;
    }

    if (serialWriter) {
        try { await serialWriter.close(); } catch (e) {}
        serialWriter = null;
    }

    isConnected = false;
    console.log('[SerialWorker] 🔌 Порт закрыт');
    self.postMessage({ type: 'disconnected' });
}

function startOscilloscope() {
    if (isRunning || !isConnected) {
        self.postMessage({ type: 'error', message: 'Невозможно запустить' });
        return;
    }

    isRunning = true;
    console.log('[SerialWorker]  Опрос запущен');
    self.postMessage({ type: 'started' });

    readLoop();
    writeLoop();
}

function stopOscilloscope() {
    isRunning = false;
    console.log('[SerialWorker] ⏹️ Опрос остановлен');
    self.postMessage({ type: 'stopped' });
}

async function readLoop() {
    console.log('[SerialWorker] readLoop started');

    try {
        while (isConnected && isRunning) {
            const { value, done } = await serialReader.read();
            if (done) break;
            if (!value) continue;

            parseModbusPackets(value);
        }
    } catch (error) {
        console.error('[SerialWorker] readLoop error:', error.message);
        self.postMessage({ type: 'error', message: error.message });
    }
}

let buffer = [];

function parseModbusPackets(data) {
    buffer.push(...data);

    while (buffer.length >= 5) {
        if (buffer[0] === config.slaveAddress && buffer[1] === 0x03) {
            const byteCount = buffer[2];
            const expectedLength = 3 + byteCount + 2;

            if (buffer.length < expectedLength) break;

            let crc = 0xFFFF;
            for (let i = 0; i < expectedLength - 2; i++) {
                crc ^= buffer[i];
                for (let j = 0; j < 8; j++) {
                    crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                }
            }

            const receivedCrc = buffer[expectedLength - 2] | (buffer[expectedLength - 1] << 8);

            if (crc === receivedCrc) {
                const values = [];
                for (let i = 0; i < byteCount; i += 2) {
                    values.push((buffer[3 + i] << 8) | buffer[4 + i]);
                }

                if (scopePort) {
                    scopePort.postMessage({
                        type: 'data',
                        values: values,
                        timestamp: performance.now()
                    });
                }

                if (mainPort) {
                    mainPort.postMessage({
                        type: 'data',
                        values: values,
                        timestamp: performance.now()
                    });
                }
            }

            buffer.splice(0, expectedLength);
        } else {
            buffer.shift();
        }
    }
}

async function writeLoop() {
    console.log('[SerialWorker] writeLoop started');

    while (isConnected && isRunning) {
        try {
            const body = new Uint8Array([
                config.slaveAddress,
                0x03,
                (config.registerAddr >> 8) & 0xFF,
                config.registerAddr & 0xFF,
                (config.paramsCount >> 8) & 0xFF,
                config.paramsCount & 0xFF
            ]);

            let crc = 0xFFFF;
            for (let pos = 0; pos < body.length; pos++) {
                crc ^= body[pos];
                for (let i = 8; i !== 0; i--) {
                    if ((crc & 0x0001) !== 0) {
                        crc >>= 1;
                        crc ^= 0xA001;
                    } else {
                        crc >>= 1;
                    }
                }
            }

            const finalPacket = new Uint8Array(8);
            finalPacket.set(body, 0);
            finalPacket[6] = crc & 0xFF;
            finalPacket[7] = (crc >> 8) & 0xFF;

            await serialWriter.write(finalPacket);

        } catch (error) {
            console.error('[SerialWorker] writeLoop error:', error.message);
        }

        await new Promise(res => setTimeout(res, 20));
    }
}

async function handleTransceive(requestData) {
    if (!isConnected || !serialWriter) {
        self.postMessage({ type: 'transceiveError', message: 'Порт не открыт' });
        return;
    }

    const wasRunning = isRunning;
    if (wasRunning) {
        isRunning = false;
        await new Promise(res => setTimeout(res, 50));
    }

    try {
        const requestBytes = new Uint8Array(requestData);
        await serialWriter.write(requestBytes);

        const timeout = 2000;
        const startTime = performance.now();
        const responseBuffer = [];

        while (performance.now() - startTime < timeout) {
            const { value, done } = await serialReader.read();
            if (done) break;
            if (value) {
                responseBuffer.push(...value);
            }

            if (responseBuffer.length >= 5) {
                const funcCode = responseBuffer[1];
                let expectedLength = 0;

                if (funcCode === 0x03 || funcCode === 0x04 || funcCode === 0x11) {
                    expectedLength = 3 + responseBuffer[2] + 2;
                } else if (funcCode === 0x06 || funcCode === 0x10) {
                    expectedLength = 8;
                } else if (funcCode & 0x80) {
                    expectedLength = 5;
                }

                if (expectedLength > 0 && responseBuffer.length >= expectedLength) {
                    let crc = 0xFFFF;
                    for (let i = 0; i < expectedLength - 2; i++) {
                        crc ^= responseBuffer[i];
                        for (let j = 0; j < 8; j++) {
                            crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                        }
                    }

                    const receivedCrc = responseBuffer[expectedLength - 2] | (responseBuffer[expectedLength - 1] << 8);

                    if (crc === receivedCrc) {
                        const response = responseBuffer.slice(0, expectedLength);
                        responseBuffer.splice(0, expectedLength);

                        self.postMessage({
                            type: 'transceiveResponse',
                            data: response
                        });

                        if (wasRunning) {
                            isRunning = true;
                            writeLoop();
                            readLoop();
                        }
                        return;
                    }
                }
            }

            await new Promise(res => setTimeout(res, 10));
        }

        self.postMessage({ type: 'transceiveError', message: 'Таймаут ответа' });

        if (wasRunning) {
            isRunning = true;
            writeLoop();
            readLoop();
        }
    } catch (error) {
        console.error('[SerialWorker] transceive error:', error.message);
        self.postMessage({ type: 'transceiveError', message: error.message });

        if (wasRunning) {
            isRunning = true;
            writeLoop();
            readLoop();
        }
    }
}

console.log('[SerialWorker] ✅ Worker загружен');