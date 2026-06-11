const graphs = {};
const TIME_WINDOW = 32000;
const MAX_POINTS = 5000;
const MAX_GAP = 1000;

const COLORS = [
    '#4A90E2', '#E24A4A', '#50C878', '#FFB347',
    '#9B59B6', '#1ABC9C', '#F39C12', '#E74C3C',
];

self.onmessage = (e) => {
    const msg = e.data;

    if (msg.type === 'initGraph') {
        const c = msg.canvas;
        graphs[msg.id] = {
            canvas: c,
            ctx: c.getContext('2d'),
            w: msg.width || c.width,
            h: msg.height || c.height,
            buffer: [],
            settings: { maxVal: null },
            color: COLORS[msg.id % COLORS.length]
        };
    }
    else if (msg.type === 'data') {
        const g = graphs[msg.id];
        if (g) {
            g.buffer.push({ t: msg.t, v: msg.v1 });
            if (g.buffer.length > MAX_POINTS) g.buffer.shift();
        }
    }
    else if (msg.type === 'updateSettings') {
        const g = graphs[msg.id];
        if (g) {
            if (msg.width !== undefined && msg.width > 0) {
                g.canvas.width = msg.width;
                g.w = msg.width;
            }
            if (msg.height !== undefined && msg.height > 0) {
                g.canvas.height = msg.height;
                g.h = msg.height;
            }
            g.ctx = g.canvas.getContext('2d');
            if (msg.maxVal !== undefined) {
                g.settings.maxVal = msg.maxVal;
            }
        }
    }
};

setInterval(() => {
    const now = performance.now();

    for (const id in graphs) {
        const g = graphs[id];
        if (!g.ctx) continue;

        g.ctx.clearRect(0, 0, g.w, g.h);

        // Если буфер пуст, ничего не рисуем
        if (g.buffer.length === 0) continue;

        const newestPoint = g.buffer[g.buffer.length - 1];
        const newestTime = newestPoint.t;

        // Фильтруем видимые точки
        const visible = [];
        for (let p of g.buffer) {
            const age = newestTime - p.t;
            if (age >= 0 && age <= TIME_WINDOW) {
                visible.push({ ...p, age: age });
            }
        }

        if (visible.length < 2) continue;
        visible.sort((a, b) => a.age - b.age);

        // 🔥 ОПТИМИЗАЦИЯ ДЛЯ ДИСКРЕТНЫХ ПАРАМЕТРОВ
        if (g.isDiscrete) {
            // Для дискретных сигналов Y всегда фиксирован:
            // 1 (High) -> Вверху (отступ 4px)
            // 0 (Low)  -> Внизу (отступ 4px)
            const yHigh = 4;
            const yLow = g.h - 4;

            g.ctx.strokeStyle = g.color;
            g.ctx.lineWidth = 2;
            g.ctx.beginPath();

            let first = true;
            let prevX, prevY;

            for (let i = 0; i < visible.length; i++) {
                const p = visible[i];
                // Определяем уровень: > 0.5 считаем за 1, иначе 0
                const isHigh = p.v > 0.5;
                const y = isHigh ? yHigh : yLow;
                const x = g.w - (p.age / TIME_WINDOW) * g.w;

                if (first) {
                    g.ctx.moveTo(x, y);
                    first = false;
                } else {
                    // Рисуем "Ступеньку" (Square Wave)
                    // Если уровень изменился, сначала рисуем вертикальную линию на позиции ПРЕДЫДУЩЕЙ точки
                    if (y !== prevY) {
                        g.ctx.lineTo(prevX, y);
                    }
                    // Затем горизонтальную линию до текущей точки
                    g.ctx.lineTo(x, y);
                }
                prevX = x;
                prevY = y;
            }
            g.ctx.stroke();
            continue; // Переходим к следующему графику, минуя тяжелый код аналоговых
        }

        // 🔥 КОД ДЛЯ АНАЛОГОВЫХ ПАРАМЕТРОВ (стандартный)
        let minV = visible[0].v, maxV = visible[0].v;
        for (let i = 1; i < visible.length; i++) {
            if (visible[i].v < minV) minV = visible[i].v;
            if (visible[i].v > maxV) maxV = visible[i].v;
        }

        let range;
        let bottomVal;

        if (g.settings.maxVal !== null && g.settings.maxVal !== undefined) {
            bottomVal = 0;
            range = g.settings.maxVal - bottomVal || 1;
        } else {
            bottomVal = minV;
            range = maxV - minV || 1;
        }

        g.ctx.strokeStyle = g.color;
        g.ctx.lineWidth = 1.5;
        g.ctx.beginPath();

        let drawing = false;
        for (let i = 0; i < visible.length; i++) {
            const p = visible[i];
            const x = g.w - (p.age / TIME_WINDOW) * g.w;
            const y = g.h - ((p.v - bottomVal) / range) * g.h;

            if (i > 0) {
                const prevP = visible[i - 1];
                const timeGap = p.t - prevP.t;
                if (timeGap > MAX_GAP) {
                    drawing = false;
                }
            }

            if (!drawing) {
                g.ctx.moveTo(x, y);
                drawing = true;
            } else {
                g.ctx.lineTo(x, y);
            }
        }
        g.ctx.stroke();
    }
}, 16);