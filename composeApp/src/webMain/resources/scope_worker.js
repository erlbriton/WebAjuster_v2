const graphs = {};
const TIME_WINDOW = 32000; // 🔥 Меняй это: 2000 (растянут), 32000 (сжат)
const MAX_POINTS = 10000;

self.onmessage = (e) => {
    const msg = e.data;
    if (msg.type === 'initGraph') {
        const c = msg.canvas;
        graphs[msg.id] = {
            ctx: c.getContext('2d'),
            w: c.width,
            h: c.height,
            buffer: []
        };
    }
    else if (msg.type === 'data') {
        const g = graphs[msg.id];
        if (g) {
            g.buffer.push({ t: msg.t, v: msg.v1 });
            if (g.buffer.length > MAX_POINTS) g.buffer.shift();
        }
    }
};

setInterval(() => {
    const now = performance.now();

    for (const id in graphs) {
        const g = graphs[id];
        if (!g.ctx) continue;

        g.ctx.fillStyle = '#FAFAFA';
        g.ctx.fillRect(0, 0, g.w, g.h);

        // 🔥 Фильтруем точки: только те, что в пределах TIME_WINDOW
        const visible = [];
        for (let p of g.buffer) {
            const age = now - p.t;
            if (age >= 0 && age <= TIME_WINDOW) {
                visible.push({ age: age, v: p.v });
            }
        }

        if (visible.length < 2) continue;

        // Находим min/max для масштабирования по высоте
        let minV = Infinity, maxV = -Infinity;
        for (let p of visible) {
            if (p.v < minV) minV = p.v;
            if (p.v > maxV) maxV = p.v;
        }
        const range = maxV - minV || 1;

        // 🔥 Отрисовка: позиция X зависит от age и TIME_WINDOW
        g.ctx.strokeStyle = '#4A90E2';
        g.ctx.lineWidth = 1.5;
        g.ctx.beginPath();

        let started = false;
        for (let i = 0; i < visible.length; i++) {
            const p = visible[i];

            // 🔥 ГЛАВНАЯ ФОРМУЛА:
            // - age=0 (сейчас) → x = ширина (правый край)
            // - age=TIME_WINDOW → x = 0 (левый край)
            // - Чем БОЛЬШЕ TIME_WINDOW, тем СИЛЬНЕЕ сжатие точек
            const x = g.w - (p.age / TIME_WINDOW) * g.w;
            const y = g.h - ((p.v - minV) / range) * g.h;

            if (!started) { g.ctx.moveTo(x, y); started = true; }
            else { g.ctx.lineTo(x, y); }
        }
        g.ctx.stroke();
    }
}, 16);