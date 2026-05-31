// Осциллограф через нативный Canvas 2D — без PixiJS, без CDN
// Работает быстрее чем Compose Canvas на Windows из-за прямого доступа к GPU через браузер

window.oscilloData = {};
window.oscilloApps = {};

// Инициализация канваса для параметра
window.oscilloInit = function(code, canvasId, minVal, maxVal) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        console.warn('[Oscillo] Canvas not found: ' + canvasId);
        return;
    }

    // Сохраняем контекст и настройки
    window.oscilloApps[code] = {
        canvas: canvas,
        ctx: canvas.getContext('2d'),
        minVal: minVal,
        maxVal: maxVal,
        animFrame: null
    };

    if (!window.oscilloData[code]) {
        window.oscilloData[code] = [];
    }

    console.log('[Oscillo] Initialized: ' + canvasId);
};

// Добавить новое значение и перерисовать
window.oscilloPush = function(code, value, minVal, maxVal) {
    if (!window.oscilloData[code]) {
        window.oscilloData[code] = [];
    }

    const buf = window.oscilloData[code];
    buf.push(value);
    if (buf.length > 300) buf.shift();

    const app = window.oscilloApps[code];
    if (!app || !app.ctx) return;

    // Отменяем предыдущий кадр если не успел отрисоваться
    if (app.animFrame) {
        cancelAnimationFrame(app.animFrame);
    }

    // Рисуем в следующем кадре — браузер сам выберет лучший момент
    app.animFrame = requestAnimationFrame(function() {
        window.oscilloDraw(code, minVal, maxVal);
        app.animFrame = null;
    });
};

// Отрисовка через нативный Canvas 2D
window.oscilloDraw = function(code, minVal, maxVal) {
    const app = window.oscilloApps[code];
    const data = window.oscilloData[code];
    if (!app || !app.ctx || !data || data.length < 2) return;

    const canvas = app.canvas;
    const ctx = app.ctx;
    const w = canvas.width;
    const h = canvas.height;
    const range = (maxVal - minVal) || 1;

    // Очищаем
    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = '#FAFAFA';
    ctx.fillRect(0, 0, w, h);

    // Сетка
    ctx.strokeStyle = '#EBEBEB';
    ctx.lineWidth = 0.5;
    for (let x = 0; x < w; x += 40) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, h);
        ctx.stroke();
    }
    for (let y = 0; y < h; y += h/4) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
        ctx.stroke();
    }

    // Линия сигнала
    ctx.strokeStyle = '#4A90E2';
    ctx.lineWidth = 1.5;
    ctx.lineJoin = 'round';
    ctx.beginPath();

    const stepX = w / (data.length - 1);

    for (let i = 0; i < data.length; i++) {
        const x = i * stepX;
        const y = h - ((data[i] - minVal) / range) * h;
        const yClamp = Math.max(0, Math.min(h, y));

        if (i === 0) {
            ctx.moveTo(x, yClamp);
        } else {
            ctx.lineTo(x, yClamp);
        }
    }
    ctx.stroke();
};

// Создать canvas элемент в контейнере
window.oscilloCreate = function(canvasId, containerId) {
    if (document.getElementById(canvasId)) return;

    const container = document.getElementById(containerId);
    if (!container) {
        console.warn('[Oscillo] Container not found: ' + containerId);
        return;
    }

    const canvas = document.createElement('canvas');
    canvas.id = canvasId;
    canvas.style.width = '100%';
    canvas.style.height = '100%';
    canvas.style.display = 'block';

    // Устанавливаем реальный размер canvas в пикселях
    const rect = container.getBoundingClientRect();
    canvas.width = rect.width > 0 ? rect.width : 400;
    canvas.height = rect.height > 0 ? rect.height : 64;

    container.appendChild(canvas);
    console.log('[Oscillo] Created canvas: ' + canvasId + ' (' + canvas.width + 'x' + canvas.height + ')');
};

// Удалить canvas
window.oscilloRemove = function(canvasId, code) {
    const app = window.oscilloApps[code];
    if (app && app.animFrame) {
        cancelAnimationFrame(app.animFrame);
    }
    delete window.oscilloApps[code];
    delete window.oscilloData[code];

    const el = document.getElementById(canvasId);
    if (el) el.remove();
};

console.log('[Oscillo] pixi_oscillo.js loaded');