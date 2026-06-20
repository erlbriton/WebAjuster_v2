// main.js - Главный поток (Main Thread)
// 🔥 ОСЦИЛЛОГРАФ ВЫНЕСЕН ИЗ COMPOSE - создаётся в document.body!

let serialWorker = null;
let scopeWorker = null;
let messageChannel = null;
let isInitialized = false;
let isConnected = false;

// 🔥 Глобальные переменные для осциллографа
let oscWrapper = null;
let graphContexts = [];
let oscTableVisible = false;
let oscColumnWeights = [0.15, 0.15, 0.20, 0.10, 0.40];

const config = {
    slaveAddress: 0x01,
    registerAddr: 0x002d,
    paramsCount: 78,
    baudRate: 115200,
    maxCapacity: 1000
};

window.initApplication = async function() {
    console.log('[Main]  Инициализация приложения...');

    try {
        messageChannel = new MessageChannel();
        console.log('[Main] ✅ MessageChannel создан');

        serialWorker = new Worker('serial_worker.js');
        serialWorker.onmessage = handleSerialWorkerMessage;
        serialWorker.onerror = handleError;
        console.log('[Main] ✅ Serial Worker создан');

        scopeWorker = new Worker('scope_worker.js');
        scopeWorker.onmessage = handleScopeWorkerMessage;
        scopeWorker.onerror = handleError;
        console.log('[Main] ✅ Scope Worker создан');

        serialWorker.postMessage({
            type: 'setScopePort',
            port: messageChannel.port1
        }, [messageChannel.port1]);

        scopeWorker.postMessage({
            type: 'setSerialPort',
            port: messageChannel.port2
        }, [messageChannel.port2]);

        console.log('[Main] ✅ Порты переданы воркерам');

        createOscilloscopeOutsideCompose();

        serialWorker.postMessage({
            type: 'init',
            config: config
        });

        scopeWorker.postMessage({
            type: 'init',
            config: {
                paramsCount: config.paramsCount,
                maxCapacity: 1000
            }
        });

        isInitialized = true;
        console.log('[Main] ✅ Приложение инициализировано');

    } catch (error) {
        console.error('[Main]  Ошибка инициализации:', error.message);
        alert('Ошибка инициализации: ' + error.message);
    }
};

// 🔥 ГЛАВНАЯ ФУНКЦИЯ: Создаёт осциллограф ВНЕ Compose
function createOscilloscopeOutsideCompose() {
    oscWrapper = document.createElement('div');
    oscWrapper.id = 'osc-wrapper';
    oscWrapper.style.cssText = `
        position: fixed !important;
        top: 0 !important;
        left: 0 !important;
        width: 50vw !important;
        height: 100vh !important;
        background: #1a1a1a !important;
        z-index: 2147483647 !important;
        display: none;
        box-shadow: 5px 0 20px rgba(0,0,0,0.5);
        pointer-events: auto;
        overflow: hidden;
    `;

    // 🔥 Перемещаем таблицу осциллографа ВНУТРЬ wrapper
    const oscTable = document.getElementById('oscTable');
    if (oscTable) {
        oscWrapper.appendChild(oscTable);
        oscTable.style.display = 'block';
        oscTable.style.width = '100%';
        oscTable.style.height = '100%';
        console.log('[Main] ✅ Таблица осциллографа перемещена внутрь wrapper');
    }

    // 🔥 Перемещаем панель ввода ВНУТРЬ осциллографа
    const inputPanel = document.getElementById('paramInputPanel');
    if (inputPanel) {
        oscWrapper.appendChild(inputPanel);
        inputPanel.style.position = 'absolute';
        inputPanel.style.bottom = '0';
        inputPanel.style.left = '0';
        inputPanel.style.width = '100%';
        inputPanel.style.zIndex = '10';
        console.log('[Main] ✅ Панель ввода перемещена внутрь осциллографа');
    }

    document.body.appendChild(oscWrapper);
    console.log('[Main] ✅ Wrapper осциллографа создан');
}

function handleSerialWorkerMessage(event) {
    const msg = event.data;

    switch (msg.type) {
        case 'configReceived':
            console.log('[Main] Serial Worker получил конфигурацию');
            break;
        case 'connected':
            console.log('[Main] ✅ Порт подключён');
            isConnected = true;
            break;
        case 'disconnected':
            console.log('[Main]  Порт отключён');
            isConnected = false;
            break;
        case 'started':
            console.log('[Main]  Опрос запущен');
            break;
        case 'stopped':
            console.log('[Main] ⏹️ Опрос остановлен');
            break;
        case 'data':
            updateTableData(msg.values);
            break;
        case 'transceiveResponse':
            handleTransceiveResponse(msg.data);
            break;
        case 'transceiveError':
            console.error('[Main] Ошибка transceive:', msg.message);
            handleTransceiveError(msg.message);
            break;
        case 'error':
            console.error('[Main] Ошибка Serial Worker:', msg.message);
            alert('Ошибка: ' + msg.message);
            break;
    }
}

function handleScopeWorkerMessage(event) {
    const msg = event.data;

    switch (msg.type) {
        case 'initialized':
            console.log('[Main] Scope Worker инициализирован');
            break;
        case 'graphData':
            console.log('[Main] 📊 Получены данные графиков:', msg.data.length, 'буферов');
            drawGraphsFromData(msg.data);
            break;
        case 'error':
            console.error('[Main] Ошибка Scope Worker:', msg.message);
            break;
    }
}

function handleError(error) {
    console.error('[Main] ❌ Ошибка воркера:', error.message);
}

function updateTableData(values) {
    // console.log('[Main] Данные для таблицы:', values);
}

window.connectToDevice = async function() {
    if (!isInitialized) {
        alert('Приложение не инициализировано');
        return;
    }

    console.log('[Main] 🔌 Запрос порта у пользователя...');

    try {
        const port = await navigator.serial.requestPort();
        await port.open({ baudRate: config.baudRate });

        console.log('[Main] ✅ Порт открыт, передаём STREAMS в Serial Worker...');

        const readable = port.readable;
        const writable = port.writable;

        serialWorker.postMessage({
            type: 'setStreams',
            readable: readable,
            writable: writable
        }, [readable, writable]);

        console.log('[Main] ✅ Streams переданы в Serial Worker');

    } catch (error) {
        console.error('[Main] ❌ Ошибка подключения:', error.message);
        alert('Ошибка подключения: ' + error.message);
    }
};

window.disconnectFromDevice = function() {
    serialWorker.postMessage({ type: 'disconnect' });
};

window.startOscilloscope = function() {
    serialWorker.postMessage({ type: 'start' });
    scopeWorker.postMessage({ type: 'start' });
};

window.stopOscilloscope = function() {
    serialWorker.postMessage({ type: 'stop' });
    scopeWorker.postMessage({ type: 'stop' });
};

// 🔥 ФУНКЦИЯ ДЛЯ KOTLIN: Чтение Device ID
window.readDeviceId = async function() {
    console.log('[Main] 🎯 readDeviceId вызвана из Kotlin');

    if (!isInitialized) {
        alert('Приложение не инициализировано');
        return;
    }

    try {
        if (!isConnected) {
            console.log('[Main]  Порт не подключён, подключаем...');
            const port = await navigator.serial.requestPort();
            await port.open({ baudRate: config.baudRate });

            const readable = port.readable;
            const writable = port.writable;

            serialWorker.postMessage({
                type: 'setStreams',
                readable: readable,
                writable: writable
            }, [readable, writable]);

            await new Promise((resolve) => {
                const handler = (e) => {
                    if (e.data.type === 'connected') {
                        serialWorker.removeEventListener('message', handler);
                        resolve();
                    }
                };
                serialWorker.addEventListener('message', handler);
            });
            console.log('[Main] ✅ Порт подключён');
        } else {
            console.log('[Main] ✅ Порт уже открыт');
        }

        console.log('[Main] 📤 Отправка 0x11...');
        const request = new Uint8Array([0x01, 0x11]);
        const crc = calculateCRC16(request);
        const fullRequest = new Uint8Array([0x01, 0x11, crc & 0xFF, (crc >> 8) & 0xFF]);

        serialWorker.postMessage({ type: 'transceive', data: Array.from(fullRequest) });

        const response = await new Promise((resolve, reject) => {
            const timeout = setTimeout(() => reject(new Error('Таймаут')), 3000);
            const handler = (e) => {
                if (e.data.type === 'transceiveResponse') {
                    clearTimeout(timeout);
                    serialWorker.removeEventListener('message', handler);
                    resolve(e.data.data);
                } else if (e.data.type === 'transceiveError') {
                    clearTimeout(timeout);
                    serialWorker.removeEventListener('message', handler);
                    reject(new Error(e.data.message));
                }
            };
            serialWorker.addEventListener('message', handler);
        });

        if (response.length > 2 && response[1] === 0x11) {
            const byteCount = response[2];
            const idData = new Uint8Array(response.slice(3, 3 + byteCount));
            const deviceId = new TextDecoder('ascii').decode(idData);
            console.log('[Main] ✅ ID:', deviceId);
            showDeviceIdPopup(deviceId);
        } else {
            throw new Error('Неверный ответ');
        }

    } catch (error) {
        console.error('[Main] ❌ Ошибка:', error.message);
        alert('Ошибка: ' + error.message);
    }
};

function showDeviceIdPopup(deviceId) {
    const popupId = 'device-id-popup-' + Date.now();
    const overlayId = 'device-id-overlay-' + Date.now();

    const popup = document.createElement('div');
    popup.id = popupId;
    popup.style.cssText = `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        background: white;
        padding: 15px 25px;
        border-radius: 8px;
        box-shadow: 0 10px 40px rgba(0,0,0,0.3);
        z-index: 100000;
        font-family: 'Courier New', monospace;
        text-align: center;
    `;

    popup.innerHTML = `
        <div style="font-size: 16px; padding: 10px; background: #f5f5f5; border-radius: 5px; word-break: break-all; margin-bottom: 10px;">
            ${deviceId}
        </div>
        <button onclick="document.getElementById('${popupId}').remove(); document.getElementById('${overlayId}').remove();"
                style="padding: 8px 20px; background: #2196f3; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 14px;">
            Закрыть
        </button>
    `;

    const overlay = document.createElement('div');
    overlay.id = overlayId;
    overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0,0,0,0.5);
        z-index: 99999;
    `;
    overlay.onclick = () => {
        popup.remove();
        overlay.remove();
    };

    document.body.appendChild(overlay);
    document.body.appendChild(popup);
}

function handleTransceiveResponse(data) {
    console.log('[Main] 📥 Ответ:', Array.from(data).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));
}

function handleTransceiveError(error) {
    console.error('[Main]  Ошибка:', error);
}

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

// 🔥 ФУНКЦИЯ ДЛЯ KOTLIN: Открыть/закрыть осциллограф
window.toggleOscilloscopeVisibility = async function(isVisible) {
    console.log('[Main] 🎯 toggleOscilloscopeVisibility вызвана с isVisible =', isVisible);

    if (!oscWrapper) {
        console.error('[Main] ❌ Осциллограф не создан');
        return;
    }

    const inputPanel = document.getElementById('paramInputPanel');

    if (isVisible) {
        console.log('[Main] 🔍 Открываем осциллограф...');

        // 🔥 Перемещаем панель ввода
        if (inputPanel && inputPanel.parentNode !== oscWrapper) {
            oscWrapper.appendChild(inputPanel);
            inputPanel.style.position = 'absolute';
            inputPanel.style.bottom = '0';
            inputPanel.style.left = '0';
            inputPanel.style.width = '100%';
            inputPanel.style.zIndex = '10';
            inputPanel.style.background = '#1a1a1a';
            inputPanel.style.borderTop = '1px solid #444';
            inputPanel.style.borderLeft = 'none';
            inputPanel.style.borderRight = 'none';
        }

        if (inputPanel) {
            inputPanel.classList.remove('hidden');
            inputPanel.style.display = 'flex';
        }

        // 🔥 Создаём таблицу осциллографа
        oscTableVisible = true;
        createOscilloscopeTable();

        // Показываем wrapper
        oscWrapper.style.display = 'block';
        oscWrapper.style.visibility = 'visible';
        oscWrapper.style.opacity = '1';

        // 🔥 Обновляем размеры canvas после того, как wrapper стал видимым
        setTimeout(() => {
            updateGraphCanvasSizes();
        }, 100);

        // Запускаем опрос
        serialWorker.postMessage({ type: 'start' });
        scopeWorker.postMessage({ type: 'start' });

        console.log('[Main] ✅ Осциллограф открыт и запущен');

    } else {
        console.log('[Main] 👁️ Закрываем осциллограф...');

        serialWorker.postMessage({ type: 'stop' });
        scopeWorker.postMessage({ type: 'stop' });

        oscWrapper.style.display = 'none';
        oscTableVisible = false;

        if (inputPanel) {
            inputPanel.classList.add('hidden');
        }

        console.log('[Main] ✅ Осциллограф закрыт');
    }
};

window.addEventListener('load', function() {
    console.log('[Main] Страница загружена');
    window.initApplication();
});

//  Обработчик resize
let resizeTimeout = null;

window.addEventListener('resize', function() {
    if (!oscWrapper || oscWrapper.style.display === 'none') return;

    if (resizeTimeout) clearTimeout(resizeTimeout);

    resizeTimeout = setTimeout(() => {
        console.log('[Main]  Resize finished');
        updateGraphCanvasSizes();
    }, 300);
});

//  Создание таблицы осциллографа (2 строки с графиками)
function createOscilloscopeTable() {
    const tbody = document.getElementById('oscTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';

    // Создаём 2 строки для 2 графиков
    for (let i = 0; i < 2; i++) {
        const row = document.createElement('tr');
        row.style.height = '24px';

        // Name (пустой)
        const nameCell = document.createElement('td');
        row.appendChild(nameCell);

        // Hex (пустой)
        const hexCell = document.createElement('td');
        row.appendChild(hexCell);

        // Physical (пустой)
        const physCell = document.createElement('td');
        row.appendChild(physCell);

        // Unit (пустой)
        const unitCell = document.createElement('td');
        row.appendChild(unitCell);

        // Graph - canvas в каждой ячейке
        const graphCell = document.createElement('td');
        graphCell.className = 'graph-cell';

        const graphCanvas = document.createElement('canvas');
        graphCanvas.id = `osc-graph-canvas-${i}`;
        graphCanvas.width = 200;
        graphCanvas.height = 24;
        graphCanvas.style.cssText = `
            display: block;
            width: 100%;
            height: 100%;
        `;
        graphCell.appendChild(graphCanvas);
        row.appendChild(graphCell);

        tbody.appendChild(row);
    }

    updateOscTableColumnWidths();
    initGraphContexts();
    console.log('[Main] ✅ Таблица осциллографа создана');
}

// 🔥 Инициализация контекстов
function initGraphContexts() {
    graphContexts = [];
    for (let i = 0; i < 2; i++) {
        const canvas = document.getElementById(`osc-graph-canvas-${i}`);
        console.log(`[Main]  Canvas ${i}:`, canvas);
        if (canvas) {
            const ctx = canvas.getContext('2d');
            graphContexts.push({ canvas, ctx });
        }
    }
    console.log('[Main] ✅ Инициализировано контекстов:', graphContexts.length);
}

// 🔥 Обновление размеров canvas
function updateGraphCanvasSizes() {
    graphContexts.forEach((graph, index) => {
        if (!graph || !graph.canvas) return;
        const cell = graph.canvas.parentElement;
        if (cell) {
            console.log(`[Main] 📐 Canvas ${index}: cell.offsetWidth = ${cell.offsetWidth}`);
            graph.canvas.width = cell.offsetWidth;
            graph.canvas.height = 24;
        }
    });
}

// 🔥 Обновление ширин колонок
function updateOscTableColumnWidths() {
    const table = document.querySelector('#oscTable table');
    if (!table) return;

    const headers = table.querySelectorAll('thead th');
    headers.forEach((header, index) => {
        if (index < oscColumnWeights.length) {
            header.style.width = (oscColumnWeights[index] * 100) + '%';
            header.style.position = 'relative';

            // Добавляем ресайзер (кроме последнего столбца)
            if (index < oscColumnWeights.length - 1) {
                const oldResizer = header.querySelector('.osc-resizer');
                if (oldResizer) oldResizer.remove();

                const resizer = document.createElement('div');
                resizer.className = 'osc-resizer';
                resizer.dataset.column = index;

                resizer.addEventListener('mousedown', (e) => {
                    startOscColumnResize(e, index);
                });

                header.appendChild(resizer);
            }
        }
    });
}

// 🔥 Ресайз колонок
let oscResizingColumn = -1;
let oscStartX = 0;
let oscStartWeights = [];

function startOscColumnResize(e, columnIndex) {
    e.preventDefault();
    e.stopPropagation();
    oscResizingColumn = columnIndex;
    oscStartX = e.clientX;
    oscStartWeights = [...oscColumnWeights];

    const resizer = e.target;
    resizer.classList.add('dragging');

    document.addEventListener('mousemove', oscColumnResize);
    document.addEventListener('mouseup', stopOscColumnResize);
}

function oscColumnResize(e) {
    if (oscResizingColumn === -1) return;

    const table = document.querySelector('#oscTable table');
    if (!table) return;

    const deltaX = e.clientX - oscStartX;
    const totalWidth = table.offsetWidth;
    const deltaWeight = deltaX / totalWidth;

    const newWeight = Math.max(0.05, oscStartWeights[oscResizingColumn] + deltaWeight);
    oscColumnWeights[oscResizingColumn] = newWeight;

    if (oscResizingColumn < oscColumnWeights.length - 1) {
        const nextWeight = Math.max(0.05, oscStartWeights[oscResizingColumn + 1] - deltaWeight);
        oscColumnWeights[oscResizingColumn + 1] = nextWeight;
    }

    updateOscTableColumnWidths();
}

function stopOscColumnResize(e) {
    if (oscResizingColumn === -1) return;

    const resizer = document.querySelector('.osc-resizer.dragging');
    if (resizer) {
        resizer.classList.remove('dragging');
    }

    document.removeEventListener('mousemove', oscColumnResize);
    document.removeEventListener('mouseup', stopOscColumnResize);
    oscResizingColumn = -1;
}

// 🔥 Отрисовка графиков из данных
function drawGraphsFromData(allData) {
    if (!oscTableVisible) return;

    const colors = ['#00ff00', '#ff0000'];

    for (let i = 0; i < Math.min(allData.length, graphContexts.length); i++) {
        const graph = graphContexts[i];
        if (!graph || !graph.ctx) continue;

        const ctx = graph.ctx;
        const canvas = graph.canvas;
        const width = canvas.width;
        const height = canvas.height;
        const data = allData[i] || [];

        // Очищаем
        ctx.fillStyle = '#1a1a1a';
        ctx.fillRect(0, 0, width, height);

        if (data.length === 0) continue;

        // 🔥 Рисуем последние точки, заполняя всю ширину canvas
        const pointsToShow = Math.min(data.length, config.maxCapacity);
        const startIndex = data.length - pointsToShow;
        const stepX = width / pointsToShow;
        const centerY = height / 2;
        const scaleY = (height / 2) / 1100;

        ctx.strokeStyle = colors[i];
        ctx.lineWidth = 2;
        ctx.beginPath();

        for (let j = 0; j < pointsToShow; j++) {
            const x = j * stepX;
            const dataIndex = startIndex + j;
            const y = centerY - (data[dataIndex] * scaleY);

            if (j === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        }
        ctx.stroke();
    }
}

console.log('[Main] ✅ main.js загружен');