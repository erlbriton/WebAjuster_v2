const scopeWorker = new Worker('scope_worker.js');
window.scopeWorker = scopeWorker;
let isDeviceConnected = false;

window.connectToDevice = async function() {
    try {
        await SerialManager.connect();
        isDeviceConnected = true;
        startSerial();
        console.log('[Main] ✅ COM-порт подключен, осциллограф не активен');
    } catch (err) {
        console.error('[Main] ', err.message);
        SerialManager.showError('Ошибка подключения: ' + err.message);
    }
};

window.initOscilloscope = function() {
    console.log('[Main] 🔍 Инициализация осциллографа');

    // 🔥 Проверяем, не инициализирован ли уже
    const tbody = document.getElementById('paramTableBody');
    if (tbody && tbody.children.length > 0) {
        console.log('[Main] ⚠️ Осциллограф уже инициализирован');
        return;
    }

    TableManager.init(scopeWorker);
    console.log('[Main] ✅ Осциллограф инициализирован');
};

async function startSerial() {
    SerialManager.onData = (values, timestamp) => {
        // 🔥 Используем oscilloCurrentIdx - 1 (потому что он уже увеличен)
        const paramIdx = (SerialManager.oscilloCurrentIdx - 1 + SerialManager.oscilloAddresses.length) % SerialManager.oscilloAddresses.length;
        values.forEach((v) => {
            TableManager.updateRow(paramIdx, v, v);
            scopeWorker.postMessage({ type: 'data', id: paramIdx, v1: v, t: timestamp });
        });
    };
    await SerialManager.start();
}

window.wasmSerialTransceive = async function(requestBytes) {
    if (!SerialManager || !SerialManager.isConnected || !SerialManager.writer || !SerialManager.reader) {
        return new Uint8Array(0);
    }
    const uint8Request = new Uint8Array(requestBytes.buffer, requestBytes.byteOffset, requestBytes.byteLength);
    SerialManager.paused = true;
    try {
        await new Promise(r => setTimeout(r, 300));
        SerialManager.buffer.splice(0, SerialManager.buffer.length);
        await SerialManager.writer.write(uint8Request);
        const timeout = 2000;
        const startTime = performance.now();
        while (performance.now() - startTime < timeout) {
            if (SerialManager.buffer.length >= 5) {
                const funcCode = SerialManager.buffer[1];
                let expectedLength = 0;
                if (funcCode === 0x03 || funcCode === 0x04) expectedLength = 3 + SerialManager.buffer[2] + 2;
                else if (funcCode === 0x06 || funcCode === 0x10) expectedLength = 8;
                else if (funcCode & 0x80) expectedLength = 5;
                if (expectedLength > 0 && SerialManager.buffer.length >= expectedLength) {
                    const response = new Uint8Array(SerialManager.buffer.slice(0, expectedLength));
                    SerialManager.buffer.splice(0, expectedLength);
                    return response;
                }
            }
            await new Promise(r => setTimeout(r, 10));
        }
        return new Uint8Array(0);
    } catch (e) {
        return new Uint8Array(0);
    } finally {
        SerialManager.paused = false;
    }
};

window.ramParameters = [];

window.oscilloStart = function(registersStr, baudRate) {
    console.log('[Main] oscilloStart');
    const tryStart = () => {
        if (!window.ramParameters || window.ramParameters.length === 0) {
            setTimeout(tryStart, 300);
            return;
        }
        window.initOscilloscope();
        const addresses = [];
        window.ramParameters.forEach(p => {
            const match = p.register.match(/r([0-9a-fA-F]+)/);
            if (match) addresses.push(parseInt(match[1], 16));
        });
        SerialManager.oscilloAddresses = addresses;
        SerialManager.oscilloCurrentIdx = 0;
        console.log('[Main] Запущен с ' + addresses.length + ' адресами');
    };
    tryStart();
};

window.oscilloStop = function() {
    SerialManager.oscilloAddresses = [];
};

// 🔥 ИСПРАВЛЕНО: НЕ создаёт таблицу, только сохраняет параметры
window.buildLeftPanel = function(jsonStr) {
    console.log('[Main] buildLeftPanel вызван, длина: ' + jsonStr.length);
    try {
        window.ramParameters = JSON.parse(jsonStr);
        console.log('[Main] Сохранено ' + window.ramParameters.length + ' параметров');
    } catch (e) {
        console.error('[Main] Ошибка JSON:', e.message);
    }
};

window.updateLeftPanelValues = function(jsonStr) {};

console.log('[Main] OK');