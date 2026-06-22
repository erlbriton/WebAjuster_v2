// device_connection.js - Подключение к устройству

import { state } from './app_state.js';
import { calculateCRC16, showDeviceIdPopup } from './utils.js';

window.connectToDevice = async function() {
   if (!state.isInitialized) {
           console.log('[DeviceConnection] ⚠️ Вызов до инициализации. Запуск initApplication...');
           await window.initApplication();
       }

    console.log('[Main] 🔌 Запрос порта у пользователя...');

    try {
        const port = await navigator.serial.requestPort();
        await port.open({ baudRate: state.config.baudRate });

        console.log('[Main] ✅ Порт открыт, передаём STREAMS в Serial Worker...');

        const readable = port.readable;
        const writable = port.writable;

        state.serialWorker.postMessage({
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
    state.serialWorker.postMessage({ type: 'disconnect' });
};

window.readDeviceId = async function() {
    console.log('[Main] 🎯 readDeviceId вызвана из Kotlin');

    if (!state.isInitialized) {
        alert('Приложение не инициализировано');
        return;
    }

    try {
        if (!state.isConnected) {
            console.log('[Main] 🔌 Порт не подключён, подключаем...');
            const port = await navigator.serial.requestPort();
            await port.open({ baudRate: state.config.baudRate });

            const readable = port.readable;
            const writable = port.writable;

            state.serialWorker.postMessage({
                type: 'setStreams',
                readable: readable,
                writable: writable
            }, [readable, writable]);

            await new Promise((resolve) => {
                const handler = (e) => {
                    if (e.data.type === 'connected') {
                        state.serialWorker.removeEventListener('message', handler);
                        resolve();
                    }
                };
                state.serialWorker.addEventListener('message', handler);
            });
            console.log('[Main] ✅ Порт подключён');
        } else {
            console.log('[Main] ✅ Порт уже открыт');
        }

        console.log('[Main] 📤 Отправка 0x11...');
        const request = new Uint8Array([0x01, 0x11]);
        const crc = calculateCRC16(request);
        const fullRequest = new Uint8Array([0x01, 0x11, crc & 0xFF, (crc >> 8) & 0xFF]);

        state.serialWorker.postMessage({ type: 'transceive', data: Array.from(fullRequest) });

        const response = await new Promise((resolve, reject) => {
            const timeout = setTimeout(() => reject(new Error('Таймаут')), 3000);
            const handler = (e) => {
                if (e.data.type === 'transceiveResponse') {
                    clearTimeout(timeout);
                    state.serialWorker.removeEventListener('message', handler);
                    resolve(e.data.data);
                } else if (e.data.type === 'transceiveError') {
                    clearTimeout(timeout);
                    state.serialWorker.removeEventListener('message', handler);
                    reject(new Error(e.data.message));
                }
            };
            state.serialWorker.addEventListener('message', handler);
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

console.log('[DeviceConnection] ✅ device_connection.js загружен');