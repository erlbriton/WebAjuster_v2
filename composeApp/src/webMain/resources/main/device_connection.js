// device_connection.js - Подключение к устройству

import { state } from './app_state.js';
import { calculateCRC16, showDeviceIdPopup } from './utils.js';

// Гарантируем, что объект state существует на самый ранний момент вызова из Kotlin
if (!window.state) {
    window.state = {
        isInitialized: false,
        isConnected: false,
        config: { baudRate: 115200 },
        serialWorker: null,
        scopeWorker: null
    };
}

// Создаем воркеры мгновенно при загрузке скрипта, не дожидаясь DOMContentLoaded или Kotlin
if (!window.state.serialWorker) {
    console.log('[DeviceConnection] 📦 Раннее создание serialWorker...');
    window.state.serialWorker = new Worker('./serial_worker.js');
}
if (!window.state.scopeWorker) {
    console.log('[DeviceConnection] 📦 Раннее создание scopeWorker...');
    window.state.scopeWorker = new Worker('./scope_worker.js');
}

window.connectToDevice = async function() {
   // Ждем в цикле, пока главный поток приложения (main.js) не создаст воркер
       // Если Kotlin вызвал функцию, а воркеры еще не созданы в main.js — создаем их экстренно прямо сейчас
           if (!state.serialWorker) {
               console.log('[DeviceConnection] 🚨 Экстренное создание воркеров до загрузки DOM...');
               try {
                   state.serialWorker = new Worker('./serial_worker.js');
                   state.scopeWorker = new Worker('./scope_worker.js');

                   // Минимальная базовая инициализация воркера, чтобы он не выдавал ошибку "не инициализирован"
                   state.serialWorker.postMessage({
                       type: 'init',
                       config: state.config || { baudRate: 115200 }
                   });

                   console.log('[DeviceConnection] ✅ Воркеры успешно созданы экстренным путем');
               } catch (e) {
                   console.error('[DeviceConnection] ❌ Не удалось создать воркеры:', e);
               }
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

    // Если порт еще не подключен, запускаем процедуру подключения
    if (!window.state.isConnected) {
        console.log('[Main] 🔌 Порт не подключён, подключаем...');
        try {
            const port = await navigator.serial.requestPort();

            if (!port.readable) {
                console.log('[Main] 🔌 Порт закрыт, вызываем open()...');
                await port.open({ baudRate: window.state.config.baudRate });
            }

            const readable = port.readable;
            const writable = port.writable;

            // Воркер теперь гарантированно существует, отправляем потоки
            window.state.serialWorker.postMessage({
                type: 'setStreams',
                readable: readable,
                writable: writable
            }, [readable, writable]);

            window.state.isConnected = true;
        } catch (error) {
            console.error('[Main] ❌ Ошибка при подключении порта:', error);
            return;
        }
    }

    // Дальнейший ваш код отправки команды в воркер для чтения ID...
};

console.log('[DeviceConnection] ✅ device_connection.js загружен');