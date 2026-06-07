const scopeWorker = new Worker('scope_worker.js');
let port, writer, reader;
let isDeviceConnected = false;

const paramSettings = {};
let currentPopupIndex = null;

window.connectToDevice = async function() {
    try {
        port = await navigator.serial.requestPort();
        await port.open({ baudRate: 115200 });
        console.log('[Main] ✅ Порт открыт');
        isDeviceConnected = true;

        initParamTable();
        setupContextMenu();
        startSerial();
    } catch (err) {
        console.error('[Main] ❌', err.message);
        showDisconnectDialog('Ошибка подключения: ' + err.message);
    }
};

function initParamTable() {
    const tbody = document.getElementById('paramTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    const params = [
        { name: 'REG_0x2D', hexId: 'hex-0', physId: 'phys-0', graphId: 'graph-0', graphIdx: 0 },
        { name: 'REG_0x2E', hexId: 'hex-1', physId: 'phys-1', graphId: 'graph-1', graphIdx: 1 }
    ];

    params.forEach((param, idx) => {
        const row = document.createElement('tr');
        if (idx === 0) row.classList.add('selected');
        row.style.height = '20px';

        const nameCell = document.createElement('td');
        nameCell.textContent = param.name;
        row.appendChild(nameCell);

        const hexCell = document.createElement('td');
        hexCell.id = param.hexId;
        hexCell.className = 'hex-value';
        hexCell.textContent = 'x0000';
        row.appendChild(hexCell);

        const physCell = document.createElement('td');
        physCell.id = param.physId;
        physCell.className = 'phys-value';
        physCell.textContent = '0.00';
        row.appendChild(physCell);

        const unitCell = document.createElement('td');
        unitCell.textContent = '--';
        row.appendChild(unitCell);

        const graphCell = document.createElement('td');
        graphCell.className = 'graph-cell';
        graphCell.style.height = '20px';

        const canvas = document.createElement('canvas');
        canvas.id = param.graphId;
        canvas.width = 400;
        canvas.height = 20;
        graphCell.appendChild(canvas);

        row.appendChild(graphCell);
        tbody.appendChild(row);

        setTimeout(() => {
            try {
                const offscreen = canvas.transferControlToOffscreen();
                scopeWorker.postMessage({
                    type: 'initGraph',
                    id: param.graphIdx,
                    canvas: offscreen,
                    width: 400,
                    height: 20
                }, [offscreen]);
            } catch (e) {
                console.error(`[Main] ❌ Ошибка инициализации графика #${param.graphIdx}:`, e);
            }
        }, 100 + idx * 50);
    });

    // Клик для переключения активной строки
    tbody.addEventListener('click', function(e) {
        const row = e.target.closest('tr');
        if (!row) return;
        tbody.querySelectorAll('tr').forEach(r => r.classList.remove('selected'));
        row.classList.add('selected');
    });

    // 🔥 Настраиваем ресайз столбцов
    setupColumnResize();
}

function setupColumnResize() {
    const table = document.getElementById('paramTable');
    const thead = table.querySelector('thead');
    const ths = thead.querySelectorAll('th');

    // Добавляем ручки ресайза в каждый заголовок (кроме последнего)
    ths.forEach((th, idx) => {
        if (idx === ths.length - 1) return;
        const handle = document.createElement('div');
        handle.className = 'resize-handle';
        th.appendChild(handle);
    });

    let isResizing = false;
    let currentTh = null;
    let startX = 0;
    let startWidth = 0;

    thead.addEventListener('mousedown', (e) => {
        if (!e.target.classList.contains('resize-handle')) return;

        isResizing = true;
        currentTh = e.target.closest('th');
        startX = e.pageX;
        startWidth = currentTh.offsetWidth;

        table.classList.add('resizing');
        e.preventDefault();
    });

    document.addEventListener('mousemove', (e) => {
        if (!isResizing || !currentTh) return;
        const delta = e.pageX - startX;
        const newWidth = Math.max(40, startWidth + delta);
        currentTh.style.width = newWidth + 'px';
    });

    document.addEventListener('mouseup', () => {
        if (isResizing) {
            isResizing = false;
            currentTh = null;
            table.classList.remove('resizing');

            // 🔥 Обновляем размеры canvas после ресайза
            updateCanvasSizes();
        }
    });
}

function updateCanvasSizes() {
    const rows = document.querySelectorAll('#paramTableBody tr');
    rows.forEach((row, idx) => {
        const graphCell = row.querySelector('.graph-cell');
        if (graphCell) {
            const rect = graphCell.getBoundingClientRect();
            scopeWorker.postMessage({
                type: 'updateSettings',
                id: idx,
                width: Math.round(rect.width),
                height: Math.round(rect.height)
            });
        }
    });
}

function setupContextMenu() {
    const tbody = document.getElementById('paramTableBody');
    const popup = document.getElementById('paramSettingsPopup');
    const heightInput = document.getElementById('popupHeight');
    const maxInput = document.getElementById('popupMax');
    const applyBtn = document.getElementById('popupApply');

    tbody.addEventListener('contextmenu', function(e) {
        const row = e.target.closest('tr');
        if (!row) return;
        e.preventDefault();

        currentPopupIndex = Array.from(tbody.children).indexOf(row);
        const settings = paramSettings[currentPopupIndex] || { height: 20, maxVal: null };

        heightInput.value = settings.height;
        maxInput.value = settings.maxVal !== null ? settings.maxVal : '';

        popup.style.left = e.pageX + 'px';
        popup.style.top = e.pageY + 'px';
        popup.style.display = 'block';
    });

    applyBtn.addEventListener('click', function() {
        if (currentPopupIndex === null) return;

        const newHeight = parseInt(heightInput.value) || 60;
        const newMax = maxInput.value.trim();
        const maxVal = newMax === '' ? null : parseFloat(newMax);

        paramSettings[currentPopupIndex] = { height: newHeight, maxVal: maxVal };

        const rows = tbody.querySelectorAll('tr');
        if (rows[currentPopupIndex]) {
            const row = rows[currentPopupIndex];
            row.style.height = newHeight + 'px';

            const graphCell = row.querySelector('.graph-cell');
            if (graphCell) {
                graphCell.style.height = newHeight + 'px';

                scopeWorker.postMessage({
                    type: 'updateSettings',
                    id: currentPopupIndex,
                    height: newHeight,
                    maxVal: maxVal
                });
            }
        }

        popup.style.display = 'none';
    });

    document.addEventListener('click', function(e) {
        if (!e.target.closest('#paramSettingsPopup') && popup.style.display === 'block') {
            popup.style.display = 'none';
        }
    });
}

async function startSerial() {
    try {
        writer = port.writable.getWriter();
        reader = port.readable.getReader();
    } catch (e) {
        console.error('[Main] ❌ Ошибка получения writer/reader:', e.message);
        handleDeviceError(e);
        return;
    }

    let buf = [];

    (async () => {
        while (port && isDeviceConnected) {
            try {
                const body = new Uint8Array([0x01, 0x03, 0x00, 0x2D, 0x00, 0x02]);
                let crc = 0xFFFF;
                for (let b of body) { crc ^= b; for (let i = 0; i < 8; i++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1; }
                await writer.write(new Uint8Array([...body, crc & 0xFF, (crc >> 8) & 0xFF]));
            } catch (e) {
                console.warn('[Main] ⚠️ Ошибка отправки:', e.message);
                handleDeviceError(e);
                break;
            }
            await new Promise(r => setTimeout(r, 100));
        }
    })();

    (async () => {
        while (port && isDeviceConnected) {
            try {
                const { value, done } = await reader.read();
                if (done) break;
                if (!value) continue;

                buf.push(...value);

                while (buf.length >= 9) {
                    if (buf[0] === 0x01 && buf[1] === 0x03 && buf[2] === 0x04) {
                        let crc = 0xFFFF;
                        for (let i = 0; i < 7; i++) {
                            crc ^= buf[i];
                            for (let j = 0; j < 8; j++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                        }

                        if (crc === (buf[7] | (buf[8] << 8))) {
                            const v1 = (buf[3] << 8) | buf[4];
                            const v2 = (buf[5] << 8) | buf[6];

                            updateParamRow(0, v1, v1);
                            updateParamRow(1, v2, v2);

                            const now = performance.now();
                            scopeWorker.postMessage({ type: 'data', id: 0, v1: v1, t: now });
                            scopeWorker.postMessage({ type: 'data', id: 1, v1: v2, t: now });
                        }
                        buf.splice(0, 9);
                    } else {
                        buf.shift();
                    }
                }
            } catch (e) {
                console.error('[Main] ❌ Ошибка чтения:', e.message);
                handleDeviceError(e);
                break;
            }
        }
    })();
}

function updateParamRow(index, hexValue, physicalValue) {
    const hexEl = document.getElementById('hex-' + index);
    const physEl = document.getElementById('phys-' + index);
    if (hexEl) hexEl.textContent = 'x' + hexValue.toString(16).toUpperCase().padStart(4, '0');
    if (physEl) physEl.textContent = physicalValue.toFixed(2);
}

function handleDeviceError(error) {
    console.error('[Main] 🔌 Ошибка устройства:', error.message);
    isDeviceConnected = false;
    scopeWorker.postMessage({ type: 'stop' });

    if (error.message.includes('device has been lost') || error.message.includes('disconnected')) {
        showDisconnectDialog('Устройство отключено! Проверьте USB-кабель.');
    } else {
        showDisconnectDialog('Ошибка связи: ' + error.message);
    }
}

function showDisconnectDialog(message) {
    let dialog = document.getElementById('disconnectDialog');
    if (!dialog) {
        dialog = document.createElement('div');
        dialog.id = 'disconnectDialog';
        dialog.innerHTML = `
            <div style="position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); background: white; border: 2px solid #f44336; border-radius: 8px; padding: 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.3); z-index: 100000; min-width: 350px; text-align: center;">
                <div style="font-size: 48px; margin-bottom: 10px;">⚠️</div>
                <h3 style="margin: 10px 0; color: #f44336;">Внимание!</h3>
                <p id="disconnectMessage" style="margin: 15px 0; color: #666;">${message}</p>
                <button onclick="document.getElementById('disconnectDialog').style.display='none'" style="padding: 10px 30px; background: #2196f3; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; margin-top: 10px;">Закрыть</button>
            </div>
            <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 99999;"></div>
        `;
        document.body.appendChild(dialog);
    } else {
        const msgEl = document.getElementById('disconnectMessage');
        if (msgEl) msgEl.textContent = message;
        dialog.style.display = 'block';
    }
}

console.log('[Main] main.js LOADED OK');