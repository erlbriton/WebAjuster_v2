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
            // Получаем порт от Serial Worker
            serialPort = msg.port;
            serialPort.onmessage = function(e) {
                handleSerialData(e.data);
            };
            serialPort.start();
            console.log('[ScopeWorker] ✅ Порт от Serial Worker установлен');
            break;

        case 'setCanvas':
            // Получаем OffscreenCanvas из главного потока
            canvas = msg.canvas;
            ctx = canvas.getContext('2d');
            console.log('[ScopeWorker] ✅ Canvas получен, размер:', canvas.width, 'x', canvas.height);
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

    // Распределяем значения по буферам
    for (let i = 0; i < Math.min(values.length, config.paramsCount); i++) {
        buffers[i].push(values[i]);
    }
}

// === ЗАПУСК РЕНДЕРА ===
function startRendering() {
    if (isRunning) return;

    isRunning = true;
    console.log('[ScopeWorker]  Рендер запущен');

    // Запускаем renderLoop
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
    console.log('[ScopeWorker] 🧹 Буферы очищены');
}

// === ЦИКЛ РЕНДЕРА (Canvas 2D) ===
let lastRenderTime = 0;
function renderLoop(timestamp) {
    if (!isRunning) return;

    // Ограничиваем FPS до 60
    if (timestamp - lastRenderTime < 16.67) {
        requestAnimationFrame(renderLoop);
        return;
    }
    lastRenderTime = timestamp;

    // Рисуем графики
    if (ctx && buffers.length > 0) {
        drawGraphs();
    }

    requestAnimationFrame(renderLoop);
}

// === ОТРИСОВКА ГРАФИКОВ ===
function drawGraphs() {
    const width = canvas.width;
    const height = canvas.height;

    // Очищаем canvas
    ctx.fillStyle = '#1a1a1a';
    ctx.fillRect(0, 0, width, height);

    // Рисуем сетку
    ctx.strokeStyle = '#333';
    ctx.lineWidth = 1;
    for (let x = 0; x < width; x += 50) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, height);
        ctx.stroke();
    }
    for (let y = 0; y < height; y += 50) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();
    }

    // Рисуем первые 2 графика (для примера)
    const colors = ['#00ff00', '#ff0000', '#0000ff', '#ffff00'];
    const graphsToDraw = Math.min(2, buffers.length);

    for (let g = 0; g < graphsToDraw; g++) {
        const data = buffers[g].getLinearData();
        if (data.length === 0) continue;

        ctx.strokeStyle = colors[g % colors.length];
        ctx.lineWidth = 2;
        ctx.beginPath();

        const stepX = width / config.maxCapacity;
        const centerY = height / 2;
        const scaleY = height / 4 / 1100; // Масштаб

        for (let i = 0; i < data.length; i++) {
            const x = i * stepX;
            const y = centerY - (data[i] * scaleY);

            if (i === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        }

        ctx.stroke();
    }
}

console.log('[ScopeWorker] ✅ Worker загружен');