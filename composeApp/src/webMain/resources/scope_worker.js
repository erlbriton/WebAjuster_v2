// scope_worker.js - Dedicated Worker для рендера графиков
// Работает в ОТДЕЛЬНОМ потоке, независимом от главного

importScripts('ringBuffer.js');

let canvas = null;
let ctx = null;
let buffers = [];
let isRunning = false;

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
    buffers = [];
    for (let i = 0; i < config.paramsCount; i++) {
        buffers.push(new RingBuffer(config.maxCapacity));
    }
    console.log('[ScopeWorker] ✅ Буферы инициализированы:', buffers.length);
}

// === ОБРАБОТКА ДАННЫХ ОТ SERIAL WORKER ===
function handleSerialData(data) {
    if (data.type !== 'data') return;

    const values = data.values;

    for (let i = 0; i < Math.min(values.length, config.paramsCount); i++) {
        buffers[i].push(values[i]);
    }
}

// === ЗАПУСК РЕНДЕРА ===
function startRendering() {
    if (isRunning) return;

    isRunning = true;
    console.log('[ScopeWorker]  Рендер запущен');

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
        buf.clear();
    }
    //console.log('[ScopeWorker] 🧹 Буферы очищены');
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
    for (let i = 0; i < Math.min(2, buffers.length); i++) {
        const data = buffers[i].getLinearData();
        graphData.push(data);
        //console.log(`[ScopeWorker] 📊 Буфер ${i}: длина = ${data.length}`);
    }

    self.postMessage({
        type: 'graphData',
        data: graphData
    });

    //console.log('[ScopeWorker]  Данные отправлены в main thread');
}

console.log('[ScopeWorker] ✅ Worker загружен');