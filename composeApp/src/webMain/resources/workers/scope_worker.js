// scope_worker.js - Dedicated Worker для рендера графиков
// Работает в ОТДЕЛЬНОМ потоке, независимом от главного

importScripts('ringBuffer.js');

let canvas = null;
let ctx = null;
let buffers = [];
let isRunning = false;
let paramMapping = [];

// Конфигурация
let config = {
    paramsCount: 78,
    maxCapacity: 1000
};

// MessageChannel порт от Serial Worker
let serialPort = null;

// === ОБРАБОТКА СООБЩЕНИЙ ОТ ГЛАВНОГО ПОТОКА ===
self.onmessage = function(event) {
    const msg = event.data;

    console.log('[ScopeWorker] Получено сообщение:', msg.type);

    switch (msg.type) {
        case 'init':
            config = { ...config, ...msg.config };
            initBuffers();
            self.postMessage({ type: 'initialized' });
            break;

        case 'initParams':
            paramMapping = msg.params;
            config.paramsCount = paramMapping.length;
            initBuffers();
            console.log('[ScopeWorker] ✅ Параметры обновлены, размер:', config.paramsCount);
            self.postMessage({ type: 'initialized' });
            break;

        case 'setSerialPort':
            serialPort = msg.port;
            serialPort.onmessage = function(e) {
                handleSerialData(e.data);
            };
            serialPort.start();
            console.log('[ScopeWorker] ✅ Порт от Serial Worker установлен');
            break;

        case 'setCanvas':
            canvas = msg.canvas;
            ctx = canvas.getContext('2d');
            console.log('[ScopeWorker] ✅ Новый canvas получен, размер:', canvas.width, 'x', canvas.height);
            break;

        case 'start':
            startRendering();
            break;

        case 'stop':
            stopRendering();
            break;

        case 'clearBuffers':
            clearBuffers();
            break;

        case 'resize':
            if (canvas) {
                canvas.width = msg.width;
                canvas.height = msg.height;
                console.log('[ScopeWorker] 📐 OffscreenCanvas resized:', msg.width, 'x', msg.height);
            }
            break;
    }
};

// === ИНИЦИАЛИЗАЦИЯ БУФЕРОВ ===
function initBuffers() {
    buffers = new Array(config.paramsCount);
    for (let i = 0; i < config.paramsCount; i++) {
        buffers[i] = new RingBuffer(config.maxCapacity);
    }
    console.log('[ScopeWorker] ✅ Буферы инициализированы, количество:', buffers.length);
}

// === ОБРАБОТКА ДАННЫХ ОТ SERIAL WORKER ===
function handleSerialData(data) {
    if (data.type !== 'data') return;

    if (!buffers || buffers.length === 0) {
        console.error('[ScopeWorker] ❌ ОШИБКА: массив buffers пуст!');
        return;
    }

    const rawBytes = data.buffer;
    if (!rawBytes || rawBytes.length === 0) return;

    const values = [];
    for (let i = 0; i < rawBytes.length; i += 2) {
        if (i + 1 < rawBytes.length) {
            const value = (rawBytes[i] << 8) | rawBytes[i + 1];
            values.push(value);
        }
    }

    const count = Math.min(values.length, buffers.length);
    for (let i = 0; i < count; i++) {
        buffers[i].push(values[i]);
    }

    if (buffers[0]) {
        console.log(`[ScopeWorker] Буфер 0: размер ${buffers[0].length}, последний элемент: ${buffers[0].get(buffers[0].length - 1)}`);
    }
}

// === ЗАПУСК РЕНДЕРА ===
function startRendering() {
    if (isRunning) return;
    isRunning = true;
    console.log('[ScopeWorker] Рендер запущен');
    requestAnimationFrame(renderLoop);
}

// === ОСТАНОВКА РЕНДЕРА ===
function stopRendering() {
    isRunning = false;
    console.log('[ScopeWorker] ⏹️ Рендер остановлен');
}

// === ОЧИСТКА БУФЕРОВ ===
function clearBuffers() {
    for (let buf of buffers) {
        if (buf && typeof buf.clear === 'function') buf.clear();
    }
}

// === ЦИКЛ РЕНДЕРА ===
let lastRenderTime = 0;
function renderLoop(timestamp) {
    if (!isRunning) return;

    if (timestamp - lastRenderTime < 16.67) {
        requestAnimationFrame(renderLoop);
        return;
    }
    lastRenderTime = timestamp;

    if (buffers.length > 0) {
        drawGraphs();
    }

    requestAnimationFrame(renderLoop);
}

// === ОТПРАВКА ДАННЫХ В MAIN THREAD ===
function drawGraphs() {
    const graphData = [];
    const limit = Math.min(50, buffers.length);
    for (let i = 0; i < limit; i++) {
        const data = buffers[i].getLinearData();
        graphData.push(data);
    }

    self.postMessage({
        type: 'graphData',
        data: graphData
    });
}

console.log('[ScopeWorker] ✅ Worker загружен');