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
            // Получаем новый OffscreenCanvas (рендер НЕ останавливаем!)
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
                    // 🔥 Важно: меняем размер OffscreenCanvas изнутри Worker'а
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

    // 🔥 Высота каждого графика
    const graphHeight = 20;

    // 🔥 Рисуем 50 графиков
    const colors = ['#00ff00', '#ff0000', '#0000ff', '#ffff00', '#ff00ff', '#00ffff', '#ff8800', '#88ff00', '#ff0088', '#00ff88', '#8800ff', '#0088ff', '#ff8888', '#88ff88', '#8888ff', '#ffff88', '#ff88ff', '#88ffff', '#ff4444', '#44ff44', '#4444ff', '#ffaa00', '#aa00ff', '#00aaff', '#ff00aa', '#00ffaa', '#aaff00', '#aa0088', '#00aa88', '#8800aa', '#88aa00', '#0088aa', '#aa8800', '#ff6666', '#66ff66', '#6666ff', '#ffff66', '#ff66ff', '#66ffff', '#ff3333', '#33ff33', '#3333ff', '#ffff33', '#ff33ff', '#33ffff', '#ff9999', '#99ff99', '#9999ff', '#ffff99', '#ff99ff'];
    const graphsToDraw = Math.min(20, buffers.length);

    for (let g = 0; g < graphsToDraw; g++) {
        const data = buffers[g].getLinearData();
        if (data.length === 0) continue;

        ctx.strokeStyle = colors[g];
        ctx.lineWidth = 2;
        ctx.beginPath();

        const stepX = width / config.maxCapacity;

        // 🔥 Позиция Y для каждого графика (сверху вниз)
        const graphTop = g * graphHeight;           // 0 для первого, 20 для второго
        const centerY = graphTop + graphHeight / 2; // 10 для первого, 30 для второго
        const scaleY = (graphHeight / 2) / 1100;    // Масштаб под высоту 20px

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