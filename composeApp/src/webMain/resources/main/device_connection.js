// device_connection.js - Подключение к устройству

import { state } from './app_state.js';
import { calculateCRC16, showDeviceIdPopup } from './utils.js';

if (!window.state) {
    window.state = {
        isInitialized: false,
        isConnected: false,
        config: { baudRate: 115200 },
        serialWorker: null,
        scopeWorker: null
    };
}

if (!window.state.serialWorker) {
    window.state.serialWorker = new Worker('./serial_worker.js');
}
if (!window.state.scopeWorker) {
    window.state.scopeWorker = new Worker('./scope_worker.js');
}

window.connectToDevice = async function() {
    // ... (код инициализации воркеров тот же) ...
    if (!state.serialWorker) {
        state.serialWorker = new Worker('./serial_worker.js');
        state.scopeWorker = new Worker('./scope_worker.js');
    }

    console.log('[Main] 🔌 Запрос порта у пользователя...');

    try {
        const port = await navigator.serial.requestPort();
        await port.open({ baudRate: state.config.baudRate });

        console.log('[Main] ✅ Порт открыт, передаём STREAMS в Serial Worker...');

        const readable = port.readable;
        const writable = port.writable;

        // Создаем обработчик ответа от воркера
        const onWorkerConnected = (e) => {
            if (e.data.type === 'connected') {
                console.log('[DeviceConnection] ✅ Воркер подтвердил готовность, можно запускать процессы');
                state.isConnected = true; // Теперь можно безопасно ставить флаг
                state.serialWorker.removeEventListener('message', onWorkerConnected);

                if (typeof window.onPortReady === 'function') {
                    window.onPortReady();
                }
            }
        };

        state.serialWorker.addEventListener('message', onWorkerConnected);

        state.serialWorker.postMessage({
            type: 'setStreams',
            readable: readable,
            writable: writable
        }, [readable, writable]);

    } catch (error) {
        console.error('[Main] ❌ Ошибка подключения:', error);
    }
};

window.disconnectFromDevice = function() {
    state.serialWorker.postMessage({ type: 'disconnect' });
};

window.readDeviceId = async function() {
    console.log('[Main] 🎯 readDeviceId вызвана из Kotlin');

    if (window.state.isConnected) {
        return true;
    }

    try {
        const port = await navigator.serial.requestPort();
        await port.open({ baudRate: window.state.config.baudRate });
        window.state.isConnected = true;
        return true;
    } catch (error) {
        console.error('[Main] ❌ ОШИБКА:', error);
        return false;
    }
};

console.log('[DeviceConnection] ✅ device_connection.js загружен');