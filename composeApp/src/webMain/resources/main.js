// main.js - Главный поток (Main Thread)

let serialWorker = null;
let scopeWorker = null;
let messageChannel = null;
let isInitialized = false;
let isConnected = false;

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
            isConnected = true;
            break;
        case 'disconnected':
            console.log('[Main] 🔌 Порт отключён');
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

// 🔥 ГЛАВНАЯ ФУНКЦИЯ ДЛЯ KOTLIN (имя точно readDeviceId!)
window.readDeviceId = async function() {
    console.log('[Main] 🎯 readDeviceId вызвана из Kotlin');

    if (!isInitialized) {
        alert('Приложение не инициализировано');
        return;
    }

    try {
        // 1. Если порт не открыт — открываем
        if (!isConnected) {
            console.log('[Main] 🔌 Порт не подключён, подключаем...');
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

        // 2. Отправляем Modbus 0x11
        console.log('[Main] 📤 Отправка 0x11...');
        const request = new Uint8Array([0x01, 0x11]);
        const crc = calculateCRC16(request);
        const fullRequest = new Uint8Array([0x01, 0x11, crc & 0xFF, (crc >> 8) & 0xFF]);

        serialWorker.postMessage({ type: 'transceive', data: Array.from(fullRequest) });

        // 3. Ждём ответ
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

        // 4. Парсим и показываем popup
        if (response.length > 2 && response[1] === 0x11) {
            const byteCount = response[2];
            // 🔥 ВАЖНО: преобразуем в Uint8Array перед декодированием!
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
        z-index: 10000;
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
        z-index: 9999;
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
    console.error('[Main] ❌ Ошибка:', error);
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

/// 🔥 ФУНКЦИЯ ДЛЯ KOTLIN: Открыть/закрыть осциллограф (с параметром!)
 window.toggleOscilloscopeVisibility = async function(isVisible) {
 //alert('ФУНКЦИЯ ВЫЗВАНА! isVisible = ' + isVisible);  // 🔥
     console.log('[Main] 🎯 toggleOscilloscopeJS вызвана с isVisible =', isVisible);

     const oscContainer = document.getElementById('osc-container');
     if (!oscContainer) {
         console.error('[Main] ❌ Контейнер осциллографа не найден');
         return;
     }

     if (isVisible) {
         // 🔥 ОТКРЫВАЕМ осциллограф
         console.log('[Main] 🔍 Открываем осциллограф...');

         // 1. Если порт не открыт — открываем
         if (!isConnected) {
             console.log('[Main] 🔌 Порт не подключён, подключаем...');
             try {
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
             } catch (error) {
                 console.error('[Main] ❌ Ошибка подключения:', error.message);
                 alert('Ошибка подключения: ' + error.message);
                 return;
             }
         }

         // 2. Показываем контейнер
         oscContainer.classList.add('visible');
         oscContainer.style.left = '0';
         oscContainer.style.right = 'auto';

         // 3. Запускаем опрос
         serialWorker.postMessage({ type: 'start' });
         scopeWorker.postMessage({ type: 'start' });

         console.log('[Main] ✅ Осциллограф открыт и запущен');

     } else {
         // 🔥 ЗАКРЫВАЕМ осциллограф
         console.log('[Main] 👁️ Закрываем осциллограф...');

         // 1. Останавливаем опрос
         serialWorker.postMessage({ type: 'stop' });
         scopeWorker.postMessage({ type: 'stop' });

         // 2. Скрываем контейнер
         oscContainer.classList.remove('visible');

         console.log('[Main] ✅ Осциллограф закрыт');
     }
 };

console.log('[Main] ✅ main.js загружен');