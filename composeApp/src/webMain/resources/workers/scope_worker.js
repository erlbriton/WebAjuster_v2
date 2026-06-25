// scope_worker.js - Dedicated Worker для рендера графиков
importScripts('ringBuffer.js');

let canvas = null;
let ctx = null;
let buffers = [];
let isRunning = false;
let paramMapping = [];

let config = {
    paramsCount: 78,
    maxCapacity: 1000
};

let serialPort = null;

self.onmessage = function(event) {
    const msg = event.data;
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
            self.postMessage({ type: 'initialized' });
            break;
        case 'setSerialPort':
            serialPort = msg.port;
            serialPort.onmessage = function(e) { handleSerialData(e.data); };
            serialPort.start();
            break;
        case 'setCanvas':
            canvas = msg.canvas;
            ctx = canvas.getContext('2d');
            break;
        case 'start': startRendering(); break;
        case 'stop': stopRendering(); break;
        case 'clearBuffers': clearBuffers(); break;
        case 'resize':
            if (canvas) {
                canvas.width = msg.width;
                canvas.height = msg.height;
            }
            break;
    }
};

function initBuffers() {
    buffers = new Array(config.paramsCount);
    for (let i = 0; i < config.paramsCount; i++) {
        buffers[i] = new RingBuffer(config.maxCapacity);
    }
}

function handleSerialData(data) {
    if (data.type !== 'data') return;
    if (!buffers || buffers.length === 0) return;

    const rawBytes = data.buffer;
    if (!rawBytes || rawBytes.length === 0) return;

    const values = [];
    for (let i = 0; i < rawBytes.length; i += 2) {
        if (i + 1 < rawBytes.length) {
            values.push((rawBytes[i] << 8) | rawBytes[i + 1]);
        }
    }

    for (let i = 0; i < buffers.length; i++) {
        const param = paramMapping[i];
        if (!param || !param.register) {
            buffers[i].push(0);
            continue;
        }

        // Парсинг формата "r0000.D"
        const [addrPart, bitPart] = param.register.split('.');
        const regAddr = parseInt(addrPart.replace('r', ''), 16);

        const rawValue = values[regAddr] !== undefined ? values[regAddr] : 0;
        let finalValue = rawValue;

        if (bitPart) {
            const bitIndex = bitPart.toUpperCase().charCodeAt(0) - 'A'.charCodeAt(0);
            finalValue = (rawValue & (1 << bitIndex)) !== 0 ? 1 : 0;
        }

        buffers[i].push(finalValue);
    }
}

function startRendering() {
    if (isRunning) return;
    isRunning = true;
    requestAnimationFrame(renderLoop);
}

function stopRendering() { isRunning = false; }

function clearBuffers() {
    for (let buf of buffers) if (buf && buf.clear) buf.clear();
}

function renderLoop(timestamp) {
    if (!isRunning) return;
    if (buffers.length > 0) drawGraphs();
    requestAnimationFrame(renderLoop);
}

function drawGraphs() {
    const graphData = [];
    const limit = Math.min(50, buffers.length);
    for (let i = 0; i < limit; i++) {
        graphData.push(buffers[i].getLinearData());
    }
    self.postMessage({ type: 'graphData', data: graphData });
}

console.log('[ScopeWorker] ✅ Worker загружен');