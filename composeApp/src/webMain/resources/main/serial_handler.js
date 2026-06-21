// serial_handler.js - Обработка Serial Worker

import { state } from './app_state.js';

export function handleSerialWorkerMessage(event) {
    const msg = event.data;

    switch (msg.type) {
        case 'configReceived':
            console.log('[Main] Serial Worker получил конфигурацию');
            break;
        case 'connected':
            console.log('[Main] ✅ Порт подключён');
            state.isConnected = true;
            break;
        case 'disconnected':
            console.log('[Main] 🔌 Порт отключён');
            state.isConnected = false;
            break;
        case 'started':
            console.log('[Main] 📡 Опрос запущен');
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

function handleTransceiveResponse(data) {
    console.log('[Main] 📥 Ответ:', Array.from(data).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));
}

function handleTransceiveError(error) {
    console.error('[Main] ❌ Ошибка:', error);
}

function updateTableData(values) {
    // console.log('[Main] Данные для таблицы:', values);
}

console.log('[SerialHandler] ✅ serial_handler.js загружен');