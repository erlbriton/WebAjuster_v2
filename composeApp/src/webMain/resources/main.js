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
                    const rawValue = bit === -1 ? regValue : (regValue >> bit) & 1;

                    const settings = TableManager.paramSettings[graphIdx] ?? {};
                    const param = TableManager.params[graphIdx] ?? {};
                    const scale = settings.scale ?? param.scale ?? 1.0;

                    const physicalValue = rawValue * scale;

                    TableManager.updateRow(graphIdx, rawValue, physicalValue);

                    scopeWorker.postMessage({
                        type: 'data',
                        id: graphIdx,
                        v1: physicalValue,
                        t: timestamp
                    });
                });
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
        SerialManager.oscilloChunks = SerialManager._buildChunks(addresses);
        SerialManager.oscilloCurrentIdx = 0;

        console.log('[Main] 🚀 Запущен с ' + addresses.length + ' адресами');
    };

    tryStart();
};

window.oscilloStop = function() {
    SerialManager.oscilloAddresses = [];
    SerialManager.oscilloChunks = [];
    regToBitsMap = {};

    // 🔥 НОВОЕ: очищаем все графики в Worker'е
    if (window.scopeWorker) {
        window.scopeWorker.postMessage({ type: 'clearAllGraphs' });
    }

    const tbody = document.getElementById('paramTableBody');
    if (tbody) {
        tbody.innerHTML = '';
    }

    if (window.TableManager) {
        TableManager.params = [];
        TableManager.paramSettings = {};
    }

    window.ramParameters = [];

    console.log('[Main] 🧹 Осциллограф очищен');
};

window.buildLeftPanel = function(jsonStr) {
    console.log('[Main] 🏗️ buildLeftPanel вызван, длина: ' + jsonStr.length);
    try {
        window.ramParameters = JSON.parse(jsonStr);

        console.log('[Main] 📋 Первые 3 параметра:');
        for (let i = 0; i < Math.min(3, window.ramParameters.length); i++) {
            console.log(`  [${i}]:`, JSON.stringify(window.ramParameters[i]));
        }

        console.log('[Main] ✅ Сохранено ' + window.ramParameters.length + ' параметров');
    } catch (e) {
        console.error('[Main] ❌ Ошибка JSON:', e.message);
        window.ramParameters = [];
    }
};

window.updateLeftPanelValues = function(jsonStr) {};

document.addEventListener('DOMContentLoaded', () => {
    const popup = document.getElementById('paramSettingsPopup');
    if (!popup) return;

    const applyBtn = document.getElementById('popupApply');
    const cancelBtn = document.getElementById('popupCancel');
    const heightInput = document.getElementById('popupHeight');
    const maxInput = document.getElementById('popupMax');
    const autoMaxCheckbox = document.getElementById('popupAutoMax');
    const scaleInput = document.getElementById('popupScale');

    applyBtn.addEventListener('click', () => {
        const index = parseInt(popup.dataset.paramIndex);
        const height = heightInput.value;
        const maxVal = autoMaxCheckbox.checked ? '' : maxInput.value;
        const scale = scaleInput.value;

        if (TableManager.applyParamSettings) {
            TableManager.applyParamSettings(index, height, maxVal, scale);
        }
        TableManager.hideParamSettings();
    });

    cancelBtn.addEventListener('click', () => {
        TableManager.hideParamSettings();
    });

    [heightInput, maxInput, scaleInput].forEach(input => {
        if (input) {
            input.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    applyBtn.click();
                }
            });
        }
    });

    autoMaxCheckbox.addEventListener('change', () => {
        maxInput.disabled = autoMaxCheckbox.checked;
        if (autoMaxCheckbox.checked) {
            maxInput.value = '';
        }
    });

    if (scaleInput && maxInput) {
        scaleInput.addEventListener('input', (e) => {
            const newScale = parseFloat(e.target.value) || 1.0;
            const prevScale = parseFloat(e.target.dataset.prevScale) || 1.0;
            const currentMax = parseFloat(maxInput.value);

            if (!isNaN(currentMax) && prevScale !== 0 && prevScale !== newScale) {
                const newMax = currentMax * (newScale / prevScale);
                maxInput.value = Number.isInteger(newMax) ? newMax : parseFloat(newMax.toFixed(6));
            }

            e.target.dataset.prevScale = newScale;
        });
    }

    document.addEventListener('click', (e) => {
        if (popup.style.display === 'block' && !popup.contains(e.target)) {
            TableManager.hideParamSettings();
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && popup.style.display === 'block') {
            TableManager.hideParamSettings();
        }
    });
});

console.log('[Main] ✅ main.js загружен');