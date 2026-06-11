const scopeWorker = new Worker('scope_worker.js');
window.scopeWorker = scopeWorker;
let isDeviceConnected = false;
let regToBitsMap = {};

window.connectToDevice = async function() {
    try {
        await SerialManager.connect();
        isDeviceConnected = true;
        startSerial();
        console.log('[Main] ✅ COM-порт подключен');
    } catch (err) {
        console.error('[Main] ❌', err.message);
        SerialManager.showError('Ошибка подключения: ' + err.message);
    }
};

window.initOscilloscope = function() {
    console.log('[Main] 🔍 Инициализация осциллографа');
    const tbody = document.getElementById('paramTableBody');
    if (!tbody) {
        console.error('[Main] ❌ Таблица не найдена');
        return;
    }
    if (tbody.children.length > 0) {
        console.log('[Main] ⚠️ Осциллограф уже инициализирован');
        return;
    }
    TableManager.init(scopeWorker);
    console.log('[Main] ✅ Осциллограф инициализирован');
};

async function startSerial() {
    SerialManager.onData = (values, startAddress, timestamp) => {
        values.forEach((regValue, offset) => {
            const regAddr = startAddress + offset;
            const bitsInfo = regToBitsMap[regAddr];

            if (bitsInfo) {
                bitsInfo.forEach(({ graphIdx, bit }) => {
                    let value;
                    if (bit === -1) {
                        value = regValue;
                    } else {
                        value = (regValue >> bit) & 1;
                    }

                    // 🔥 ОТЛАДКА: показываем извлечение битов
                    if (bit !== -1) {
                        console.log(`[Main] 🔍 Регистр 0x${regAddr.toString(16)}, бит ${bit} → значение ${value} (регистр=0x${regValue.toString(16)}, график=${graphIdx})`);
                    }

                    TableManager.updateRow(graphIdx, value, value);
                    scopeWorker.postMessage({
                        type: 'data',
                        id: graphIdx,
                        v1: value,
                        t: timestamp
                    });
                });
            } else {
                console.warn(`[Main] ⚠️ Регистр 0x${regAddr.toString(16)} не найден в карте`);
            }
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
    console.log('[Main] 🎬 oscilloStart вызван');

    const tryStart = () => {
        if (!window.ramParameters || window.ramParameters.length === 0) {
            console.log('[Main] ⏳ Ждем загрузки параметров...');
            setTimeout(tryStart, 300);
            return;
        }

        window.initOscilloscope();

        // Создаем карту регистров и битов
        regToBitsMap = {};
        const uniqueAddresses = new Set();

        window.ramParameters.forEach((param, idx) => {
            const match = param.register.match(/r([0-9a-fA-F]+)(?:\.([0-9a-fA-F]+))?/);
            if (match) {
                const regAddr = parseInt(match[1], 16);
                const bitNum = match[2] !== undefined ? parseInt(match[2], 16) : -1;

                if (!regToBitsMap[regAddr]) {
                    regToBitsMap[regAddr] = [];
                }
                regToBitsMap[regAddr].push({ graphIdx: idx, bit: bitNum });
                uniqueAddresses.add(regAddr);
            }
        });

        const addresses = Array.from(uniqueAddresses).sort((a, b) => a - b);
        SerialManager.oscilloAddresses = addresses;
        SerialManager.oscilloCurrentIdx = 0;

        // 🔥 ОТЛАДКА: показываем карту регистров
        console.log('[Main] 📋 Карта регистров:');
        for (const [addr, bits] of Object.entries(regToBitsMap)) {
            console.log(`  Регистр 0x${parseInt(addr).toString(16)}:`, bits);
        }
        console.log('[Main] 🚀 Запущен с ' + addresses.length + ' адресами');
    };

    tryStart();
};

window.oscilloStop = function() {
    SerialManager.oscilloAddresses = [];
    regToBitsMap = {};
};

window.buildLeftPanel = function(jsonStr) {
    console.log('[Main] 🏗️ buildLeftPanel вызван, длина: ' + jsonStr.length);
    try {
        window.ramParameters = JSON.parse(jsonStr);
        console.log('[Main] ✅ Сохранено ' + window.ramParameters.length + ' параметров');
    } catch (e) {
        console.error('[Main] ❌ Ошибка JSON:', e.message);
        window.ramParameters = [];
    }
};

window.updateLeftPanelValues = function(jsonStr) {};

console.log('[Main] ✅ main.js загружен');