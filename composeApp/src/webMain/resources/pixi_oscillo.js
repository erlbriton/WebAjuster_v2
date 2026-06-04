// === УМНЫЙ ДВИЖОК ОСЦИЛЛОГРАФА (Multi-Channel) ===

window.oscilloApps = {}; // Настройки каналов
window.oscilloData = {}; // Данные каналов

// 1. Инициализация канала (вызывается один раз при старте)
window.oscilloInit = function(code, canvasId, minVal, maxVal) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return console.warn('[Oscillo] Canvas not found');

    // Запоминаем контекст и размеры для этого канала
    window.oscilloApps[code] = {
        ctx: canvas.getContext('2d'),
        width: canvas.width,
        height: canvas.height,
        minVal: minVal,
        maxVal: maxVal
    };

    // Создаем пустой буфер
    if (!window.oscilloData[code]) {
        window.oscilloData[code] = [];
    }
};

// 2. Прием данных (вызывается из main.js постоянно)
window.oscilloPush = function(code, value, minVal, maxVal) {
    if (!window.oscilloData[code]) return;

    // Сохраняем данные
    window.oscilloData[code].push(value);
    if (window.oscilloData[code].length > 300) {
        window.oscilloData[code].shift(); // Храним только последние 300 точек
    }

    // Обновляем настройки масштаба, если они пришли
    if (minVal !== undefined && maxVal !== undefined) {
        if (window.oscilloApps[code]) {
            window.oscilloApps[code].minVal = minVal;
            window.oscilloApps[code].maxVal = maxVal;
        }
    }

    // 🔥 ЗАПУСКАЕМ ОТРИСОВКУ (не чаще 1 раза за кадр)
    if (!window._isDrawing) {
        window._isDrawing = true;
        requestAnimationFrame(() => {
            _drawAll();
            window._isDrawing = false;
        });
    }
};

// 3. Секретная функция: Рисует ВСЕ каналы за один проход
function _drawAll() {
    const codes = Object.keys(window.oscilloApps);
    if (codes.length === 0) return;

    // Берем настройки из первого канала
    const mainApp = window.oscilloApps[codes[0]];
    const ctx = mainApp.ctx;
    const w = mainApp.width;
    const h = mainApp.height;

    // Очистка (ОДИН РАЗ для всех!)
    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = '#FAFAFA';
    ctx.fillRect(0, 0, w, h);

    // Сетка
    ctx.strokeStyle = '#EBEBEB';
    ctx.lineWidth = 0.5;
    for (let x = 0; x < w; x += 40) {
        ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
    }
    for (let y = 0; y < h; y += h/4) {
        ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
    }

    // Делим экран на зоны (если 2 канала — каждый займет 50% высоты)
    const zoneHeight = h / codes.length;

    codes.forEach((code, index) => {
        const data = window.oscilloData[code];
        const cfg = window.oscilloApps[code];

        if (!data || data.length < 2) return;

        // Центр зоны для этого графика
        const centerY = (index * zoneHeight) + (zoneHeight / 2);
        const range = cfg.maxVal - cfg.minVal;
        const scaleY = (zoneHeight / 2) / (range || 1);

        // Цвет линии
        ctx.strokeStyle = index === 0 ? '#4A90E2' : '#E24A4A';
        ctx.lineWidth = 1.5;
        ctx.beginPath();

        const stepX = w / (data.length - 1);

        for (let i = 0; i < data.length; i++) {
            const x = i * stepX;
            // Расчет Y: значение отнимается от центра зоны
            const val = (data[i] - cfg.minVal) * scaleY;
            const y = centerY - val;

            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        }
        ctx.stroke();
    });
}

console.log('[Oscillo] Multi-channel engine ready');