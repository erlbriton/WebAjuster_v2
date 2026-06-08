const scopeWorker = new Worker('scope_worker.js');
let isDeviceConnected = false;

window.connectToDevice = async function() {
    try {
        await SerialManager.connect();
        isDeviceConnected = true;

        startSerial();

        console.log('[Main] ✅ COM-порт подключен, осциллограф не активен');
    } catch (err) {
        console.error('[Main] ❌', err.message);
        SerialManager.showError('Ошибка подключения: ' + err.message);
    }
};

window.initOscilloscope = function() {
    console.log('[Main] 🔍 Инициализация осциллографа');

    const tbody = document.getElementById('paramTableBody');
    if (tbody && tbody.children.length > 0) {
        console.log('[Main] ⚠️ Осциллограф уже инициализирован');
        return;
    }

    TableManager.init(scopeWorker);
    PopupManager.init(scopeWorker);
    ResizeManager.init(scopeWorker);

    console.log('[Main] ✅ Осциллограф инициализирован');
};

async function startSerial() {
    // 🔥 Callback при получении данных от осциллографа
    SerialManager.onData = (values, timestamp) => {
        values.forEach((v, idx) => {
            TableManager.updateRow(idx, v, v);
            scopeWorker.postMessage({ type: 'data', id: idx, v1: v, t: timestamp });
        });
    };

    await SerialManager.start();
}

console.log('[Main] main.js LOADED OK');