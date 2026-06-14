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

               // 🔥 Функции чтения: 0x03, 0x04, 0x11
               if (funcCode === 0x03 || funcCode === 0x04 || funcCode === 0x11) {
                   expectedLength = 3 + SerialManager.buffer[2] + 2;
               }
               // 🔥 Функция записи: 0x10 (ответ всегда 8 байт)
               else if (funcCode === 0x10) {
                   expectedLength = 8;
               }

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

// 🔥 Функция вычисления CRC16 для Modbus
function calculateCRC16(data) {
    let crc = 0xFFFF;
    for (let i = 0; i < data.length; i++) {
        crc ^= data[i];
        for (let j = 0; j < 8; j++) {
            if (crc & 0x0001) {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    return crc;
}

// 🔥 Чтение ID устройства
window.readDeviceId = async function() {
    console.log('[Main] 🎯 readDeviceId ВЫЗВАНА!');

    try {
        if (!SerialManager.isConnected) {
            console.log('[Main] 🔌 Порт не открыт, подключаем...');
            await SerialManager.connect();
            await startSerial();
            console.log('[Main] ✅ Порт подключен');
        }

        const addr = 0x01;
        console.log(`[Main] 🔍 Пробуем адрес 0x${addr.toString(16).toUpperCase()}...`);

        const request = new Uint8Array([addr, 0x11]);
        const crc = calculateCRC16(request);
        const fullRequest = new Uint8Array([addr, 0x11, crc & 0xFF, (crc >> 8) & 0xFF]);

        console.log(`[Main] 📤 Запрос 0x11:`,
            Array.from(fullRequest).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));

        const response = await window.wasmSerialTransceive(fullRequest);

        if (response.length > 0 && response[1] === 0x11) {
            console.log('[Main] 📥 Ответ на 0x11:',
                Array.from(response).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));

            const byteCount = response[2];
            const idData = response.slice(3, 3 + byteCount);
            const deviceId = new TextDecoder('ascii').decode(idData);

            console.log(`[Main] ✅ ID устройства:`, deviceId);

            showCustomPopup(`ID устройства: ${deviceId}\nАдрес: 0x${addr.toString(16).toUpperCase()}`);
            return;
        }

        console.error('[Main] ❌ Устройство не ответило на адресе 0x01');
        showCustomPopup('Ошибка: устройство не отвечает на адресе 0x01');

    } catch (err) {
        console.error('[Main] ❌ Ошибка:', err.message);
        showCustomPopup(`Ошибка: ${err.message}`);
    }
};

// 🔥 Функция для красивого всплывающего окна БЕЗ заголовка браузера
function showCustomPopup(text) {
    if (!document.getElementById('custom-popup-styles')) {
        const style = document.createElement('style');
        style.id = 'custom-popup-styles';
        style.textContent = `
            .custom-overlay {
                position: fixed; inset: 0;
                background: rgba(0, 0, 0, 0.4);
                backdrop-filter: blur(5px);
                -webkit-backdrop-filter: blur(5px);
                z-index: 20000;
                display: flex; align-items: center; justify-content: center;
                animation: fadeIn 0.2s ease-out;
            }
            .custom-box {
                background: #ffffff;
                padding: 28px 32px;
                border-radius: 16px;
                min-width: 320px; max-width: 480px;
                box-shadow: 0 20px 40px rgba(0,0,0,0.15), 0 0 0 1px rgba(0,0,0,0.05);
                font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                text-align: center;
                animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
            }
            .custom-icon { font-size: 42px; margin-bottom: 12px; display: block; line-height: 1; }
            .custom-text {
                font-size: 15px; color: #374151; margin-bottom: 24px;
                white-space: pre-line; line-height: 1.6; font-weight: 500;
            }
            .custom-btn {
                padding: 10px 28px;
                background: #2563eb; color: white;
                border: none; border-radius: 8px;
                cursor: pointer; font-family: inherit; font-size: 14px; font-weight: 600;
                transition: all 0.2s ease;
                box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
            }
            .custom-btn:hover { background: #1d4ed8; transform: translateY(-1px); box-shadow: 0 6px 16px rgba(37, 99, 235, 0.35); }
            .custom-btn:active { transform: translateY(0); }

            @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
            @keyframes slideUp { from { opacity: 0; transform: translateY(20px) scale(0.95); } to { opacity: 1; transform: translateY(0) scale(1); } }
        `;
        document.head.appendChild(style);
    }

    const overlay = document.createElement('div');
    overlay.className = 'custom-overlay';

    const box = document.createElement('div');
    box.className = 'custom-box';

    box.innerHTML = `
        <span class="custom-icon">📡</span>
        <div class="custom-text">${text}</div>
        <button class="custom-btn" id="custom-popup-close">Закрыть</button>
    `;

    overlay.appendChild(box);
    document.body.appendChild(overlay);

    const closePopup = () => {
        if (document.body.contains(overlay)) {
            document.body.removeChild(overlay);
        }
    };

    document.getElementById('custom-popup-close').addEventListener('click', closePopup);
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) closePopup();
    });
}

// 🔥 Обработка полей ввода внизу осциллографа
const userInputField = document.getElementById('userInputField');
const outputField = document.getElementById('outputField');

if (userInputField) {
    userInputField.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            const text = userInputField.value.trim();
            if (text) {
                console.log('[Main] 📝 Введён текст:', text);
                processUserInput(text);
            }
        }
    });
}

// 🔥 Функция записи значения в регистр Modbus
async function processUserInput(text) {
    if (!outputField) return;

    // Парсим строку вида "IExcRef_min=1254"
    const match = text.match(/^([^=]+)=(.+)$/);
    if (!match) {
        outputField.value = '❌ Формат: Имя=Значение';
        return;
    }

    const paramName = match[1].trim();
    const valueStr = match[2].trim();

    // Проверяем, что значение - число
    const value = parseInt(valueStr);
    if (isNaN(value) || value < 0 || value > 65535) {
        outputField.value = `❌ Некорректное значение: ${valueStr}`;
        return;
    }

    // Ищем параметр по имени
    const param = TableManager.params.find(p => p.name === paramName);
    if (!param) {
        outputField.value = `❌ Параметр не найден: ${paramName}`;
        return;
    }

    // Извлекаем адрес регистра из param.register (формат "rXXXX")
    const regMatch = param.register.match(/r([0-9a-fA-F]+)/);
    if (!regMatch) {
        outputField.value = `❌ Неверный формат регистра: ${param.register}`;
        return;
    }

    const regAddr = parseInt(regMatch[1], 16);

    // Формируем Modbus запрос 0x10 (Write Multiple Registers)
    // [адрес, 0x10, адрес_рег_high, адрес_рег_low, кол-во_рег_high, кол-во_рег_low, байт_каунт, знач_high, знач_low, CRC]
    const requestWithoutCrc = new Uint8Array([
        0x01,                           // Адрес устройства
        0x10,                           // Функция Write Multiple Registers
        (regAddr >> 8) & 0xFF,         // Адрес регистра high byte
        regAddr & 0xFF,                // Адрес регистра low byte
        0x00, 0x01,                    // Количество регистров: 1
        0x02,                          // Byte count: 2 байта данных
        (value >> 8) & 0xFF,           // Значение high byte
        value & 0xFF                   // Значение low byte
    ]);

    const crc = calculateCRC16(requestWithoutCrc);
    const request = new Uint8Array([
        ...requestWithoutCrc,
        crc & 0xFF,
        (crc >> 8) & 0xFF
    ]);

    console.log(`[Main] 📝 Запись ${paramName}=${value} в регистр 0x${regAddr.toString(16).toUpperCase()}:`,
        Array.from(request).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));

    try {
        const response = await window.wasmSerialTransceive(request);

        if (response.length > 0 && response[1] === 0x10) {
            console.log('[Main] ✅ Запись успешна:',
                Array.from(response).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));
            outputField.value = `✅ ${paramName}=${value} записано`;

            // Обновляем отображение в таблице
            TableManager.updateRow(param.graphIdx, value, value * (param.scale || 1.0));
        } else {
            console.error('[Main] ❌ Ошибка записи:', response);
            outputField.value = `❌ Ошибка записи: нет ответа`;
        }
    } catch (err) {
        console.error('[Main] ❌ Ошибка:', err.message);
        outputField.value = `❌ Ошибка: ${err.message}`;
    }
}

console.log('[Main] ✅ main.js загружен');