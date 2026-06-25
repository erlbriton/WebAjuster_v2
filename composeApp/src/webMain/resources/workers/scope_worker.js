// scope_worker.js - Dedicated Worker для рендера графиков
// Работает в ОТДЕЛЬНОМ потоке, независимом от главного

importScripts('ringBuffer.js');

let canvas = null;
let ctx = null;
let buffers = [];
let isRunning = false;
let paramMapping = []; // Добавлено для хранения структуры параметров

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

        case 'initParams': // Новый кейс для динамической настройки
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
    buffers = [];
    for (let i = 0; i < config.paramsCount; i++) {
        buffers.push(new RingBuffer(config.maxCapacity));
    }
    console.log('[ScopeWorker] ✅ Буферы инициализированы:', buffers.length);
}

// === ОБРАБОТКА ДАННЫХ ОТ SERIAL WORKER ===
function handleSerialData(data) {
    // 1. Проверяем тип сообщения
    if (data.type !== 'data') return;

    // 2. Проверка: существуют ли вообще буферы
    if (typeof buffers === 'undefined' || !buffers) {
        console.error('[ScopeWorker] ❌ ОШИБКА: массив buffers не существует! Данные пришли до инициализации.');
        return;
    }

    // 3. Проверка входящих данных
    const rawBytes = data.buffer;
    if (!rawBytes || rawBytes.length === 0) {
        console.warn('[ScopeWorker] ⚠️ Получен пустой буфер данных.');
        return;
    }

    // 4. Парсинг байтов в 16-битные значения
    const values = [];
    for (let i = 0; i < rawBytes.length; i += 2) {
        if (i + 1 < rawBytes.length) {
            const value = (rawBytes[i] << 8) | rawBytes[i + 1];
            values.push(value);
        }
    }

    // 5. Лог для отладки наполнения
    console.log(`[ScopeWorker] Распарсено ${values.length} значений. Первое: ${values[0] || 'n/a'}`);

    // 6. Наполнение буферов
    const count = Math.min(values.length, buffers.length);
    for (let i = 0; i < count; i++) {
        buffers[i].push(values[i]);
        // Ограничение размера для предотвращения утечки памяти
        if (buffers[i].length > 1000) {
            buffers[i].shift();
        }
    }

    // 7. Лог для проверки состояния буфера после записи
    if (buffers[0]) {
        console.log(`[ScopeWorker] Текущая длина buffers[0]: ${buffers[0].length}. Последнее значение: ${buffers[0][buffers[0].length - 1]}`);
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
    // Изменено: берем все доступные буферы (или хотя бы первые 10, если их очень много)
    const limit = Math.min(10, buffers.length);
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