const graphs = {};
const TIME_WINDOW = 16000;
const MAX_POINTS = 5000;
const MAX_GAP = 1000;

const COLORS = [
    '#0066FF',  // Ярко-синий
    '#FF0033',  // Ярко-красный
    '#00CC44',  // Ярко-зелёный
    '#FF8800',  // Ярко-оранжевый
    '#AA00FF',  // Ярко-фиолетовый
    '#00CCCC',  // Ярко-бирюзовый
    '#FFCC00',  // Ярко-жёлтый
    '#FF0088',  // Ярко-розовый
];

self.onmessage = (e) => {
    const msg = e.data;

    // 🔥 НОВОЕ: полная очистка всех графиков
    if (msg.type === 'clearAllGraphs') {
        for (const id in graphs) {
            delete graphs[id];
        }
        console.log('[Worker] 🧹 Все графики очищены');
        return;
    }

    if (msg.type === 'initGraph') {
        const c = msg.canvas;
        graphs[msg.id] = {
            canvas: c,
            ctx: c.getContext('2d'),
            w: msg.width || c.width,
            h: msg.height || c.height,
            buffer: [],
            settings: { maxVal: null },
            color: COLORS[msg.id % COLORS.length],
            isDiscrete: msg.isDiscrete || false,
            lastValue: null,
            lastUpdateTime: 0
        };
    }
    else if (msg.type === 'data') {
        const g = graphs[msg.id];
        if (g) {
            g.buffer.push({ t: msg.t, v: msg.v1 });
            if (g.buffer.length > MAX_POINTS) g.buffer.shift();
        }
    }
    else if (msg.type === 'clearBuffer') {
        const g = graphs[msg.id];
        if (g) {
            g.buffer.length = 0;
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

            if (msg.scale !== undefined) {
                g.buffer.length = 0;
            }
        }
    }
};

let lastFrameTime = 0;
const TARGET_FPS = 60;
const FRAME_INTERVAL = 1000 / TARGET_FPS;

function renderLoop(timestamp) {
    if (timestamp - lastFrameTime < FRAME_INTERVAL) {
        requestAnimationFrame(renderLoop);
        return;
    }
    lastFrameTime = timestamp;

    for (const id in graphs) {
        const g = graphs[id];
        if (!g.ctx) continue;

        g.ctx.clearRect(0, 0, g.w, g.h);

        // Отрисовка дискретных параметров (ступенчатая функция)
        if (g.isDiscrete) {
            if (g.buffer.length === 0) continue;

            const newestPoint = g.buffer[g.buffer.length - 1];
            const newestTime = newestPoint.t;

            const visible = [];
            for (let p of g.buffer) {
                const age = newestTime - p.t;
                if (age >= 0 && age <= TIME_WINDOW) {
                    visible.push({ ...p, age: age });
                }
            }

            if (visible.length === 0) continue;
            visible.sort((a, b) => a.age - b.age);

            const yLow = g.h - 4;
            const yHigh = 4;

            g.ctx.globalAlpha = 1.0;
            g.ctx.strokeStyle = g.color;
            g.ctx.lineWidth = 1.0;
            g.ctx.lineJoin = 'miter';
            g.ctx.lineCap = 'butt';

            g.ctx.beginPath();

            const firstP = visible[0];
            const firstX = g.w - (firstP.age / TIME_WINDOW) * g.w;
            const firstY = firstP.v >= 0.5 ? yHigh : yLow;
            g.ctx.moveTo(0, firstY);
            g.ctx.lineTo(firstX, firstY);

            for (let i = 0; i < visible.length; i++) {
                const p = visible[i];
                const x = g.w - (p.age / TIME_WINDOW) * g.w;
                const y = p.v >= 0.5 ? yHigh : yLow;

                if (i > 0) {
                    const prevP = visible[i - 1];
                    const timeGap = p.t - prevP.t;

                    if (timeGap > MAX_GAP) {
                        g.ctx.stroke();
                        g.ctx.beginPath();
                        g.ctx.moveTo(x, y);
                    } else {
                        if (p.v !== prevP.v) {
                            g.ctx.lineTo(x, prevP.v >= 0.5 ? yHigh : yLow);
                            g.ctx.lineTo(x, y);
                        }
                    }
                }

                if (i < visible.length - 1) {
                    const nextP = visible[i + 1];
                    const nextX = g.w - (nextP.age / TIME_WINDOW) * g.w;
                    g.ctx.lineTo(nextX, y);
                } else {
                    g.ctx.lineTo(g.w, y);
                }
            }

            g.ctx.stroke();
            continue;
        }

        // Отрисовка аналоговых параметров
        if (g.buffer.length === 0) continue;

        const newestPoint = g.buffer[g.buffer.length - 1];
        const newestTime = newestPoint.t;

        const visible = [];
        for (let p of g.buffer) {
            const age = newestTime - p.t;
            if (age >= 0 && age <= TIME_WINDOW) {
                visible.push({ ...p, age: age });
            }
        }

        if (visible.length < 2) continue;
        visible.sort((a, b) => a.age - b.age);

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

        g.ctx.globalAlpha = 1.0;
        g.ctx.strokeStyle = g.color;
        g.ctx.lineWidth = 1.0;
        g.ctx.lineJoin = 'round';
        g.ctx.lineCap = 'round';
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

    requestAnimationFrame(renderLoop);
}

requestAnimationFrame(renderLoop);