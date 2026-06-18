// main.js - Главный поток (Main Thread)

let serialWorker = null;
let scopeWorker = null;
let messageChannel = null;
let isInitialized = false;

const config = {
    slaveAddress: 0x01,
    registerAddr: 0x002d,
    paramsCount: 78,
    baudRate: 115200
};

window.initApplication = async function() {
    console.log('[Main] 🚀 Инициализация приложения...');

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

        const oscContainer = document.getElementById('osc-container');
        if (oscContainer) {
            const canvas = document.createElement('canvas');
            canvas.width = 800;
            canvas.height = 600;
            canvas.style.width = '100%';
            canvas.style.height = '100%';
            oscContainer.appendChild(canvas);

            const offscreen = canvas.transferControlToOffscreen();
            scopeWorker.postMessage({
                type: 'setCanvas',
                canvas: offscreen
            }, [offscreen]);

            console.log('[Main] ✅ Canvas передан в Scope Worker');
        }

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
        console.error('[Main] ❌ Ошибка инициализации:', error.message);
        alert('Ошибка инициализации: ' + error.message);
    }
};

function handleSerialWorkerMessage(event) {
    const msg = event.data;

    switch (msg.type) {
        case 'configReceived':
            console.log('[Main] Serial Worker получил конфигурацию');
            break;
        case 'connected':
            console.log('[Main] ✅ Порт подключён');
            break;
        case 'disconnected':
            console.log('[Main] 🔌 Порт отключён');
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

// 🔥 ПРАВИЛЬНО: передаём САМИ STREAMS (они transferable!)
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

        // 🔥 Передаём САМИ STREAMS (они transferable!)
        const readable = port.readable;
        const writable = port.writable;

        serialWorker.postMessage({
            type: 'setStreams',
            readable: readable,
            writable: writable
        }, [readable, writable]);  // ← transfer list

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

window.readDeviceId = async function() {
    if (!isInitialized) {
        alert('Приложение не инициализировано');
        return;
    }

    console.log('[Main] 🎯 readDeviceId вызвана');

    const request = new Uint8Array([0x01, 0x11]);
    const crc = calculateCRC16(request);
    const fullRequest = new Uint8Array([0x01, 0x11, crc & 0xFF, (crc >> 8) & 0xFF]);

    console.log('[Main] 📤 Запрос:', Array.from(fullRequest).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));

    serialWorker.postMessage({
        type: 'transceive',
        data: Array.from(fullRequest)
    });
};

let transceiveCallback = null;

function handleTransceiveResponse(data) {
    console.log('[Main] 📥 Ответ:', Array.from(data).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));

    if (data.length > 2 && data[1] === 0x11) {
        const byteCount = data[2];
        const idData = data.slice(3, 3 + byteCount);
        const deviceId = new TextDecoder('ascii').decode(idData);

        console.log('[Main] ✅ ID устройства:', deviceId);

        if (transceiveCallback) {
            transceiveCallback(null, deviceId);
        }
    } else {
        const error = 'Неверный формат ответа';
        console.error('[Main] ❌', error);
        if (transceiveCallback) {
            transceiveCallback(error, null);
        }
    }
}

function handleTransceiveError(error) {
    console.error('[Main] ❌ Ошибка:', error);
    if (transceiveCallback) {
        transceiveCallback(error, null);
    }
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

window.addEventListener('load', function() {
    console.log('[Main] Страница загружена');
    window.initApplication();
});

console.log('[Main] ✅ main.js загружен');