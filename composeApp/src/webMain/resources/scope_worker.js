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

    if (msg.type === 'clearAllGraphs') {
        for (const id in graphs) {
            delete graphs[id];
        }
        console.log('[Worker] 🧹 Все графики очищены');
        return;
    }

       if (msg.type === 'initGraph') {
           const c = msg.canvas;

           // 🔥 Для дискретных параметров: чередуем цвета (синий/коричневый)
           let discreteColor;
           if (msg.isDiscrete) {
               discreteColor = (msg.id % 2 === 0) ? '#00BFFF' : '#FF8C00';
           }

           graphs[msg.id] = {
               canvas: c,
               ctx: c.getContext('2d'),
               w: msg.width || c.width,
               h: msg.height || c.height,
               buffer: [],
               settings: { maxVal: null },
               color: msg.isDiscrete ? discreteColor : COLORS[msg.id % COLORS.length],
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

                                    // 🔥 Отрисовка дискретных параметров
                                    if (g.isDiscrete) {
                                        if (g.buffer.length === 0) continue;

                                        const newestPoint = g.buffer[g.buffer.length - 1];
                                        if (!newestPoint || newestPoint.t === undefined) continue;

                                        const newestTime = newestPoint.t;

                                        const COLOR_ZERO = '#c4a882';

                                        // 🔥 Собираем видимые точки
                                        const visible = [];
                                        for (let p of g.buffer) {
                                            if (!p || p.t === undefined) continue;
                                            const age = newestTime - p.t;
                                            if (age >= 0 && age <= TIME_WINDOW) {
                                                visible.push({ ...p, age: age });
                                            }
                                        }

                                        if (visible.length === 0) continue;
                                        visible.sort((a, b) => a.age - b.age);

                                        // 🔥 Рисуем каждую точку
                                        for (let i = 0; i < visible.length; i++) {
                                            const p = visible[i];
                                            if (!p || p.v === undefined) continue;

                                            const val = (p.v >= 0.5) ? 1 : 0;
                                            const x = g.w - (p.age / TIME_WINDOW) * g.w;

                                            if (val === 0) {
                                                g.ctx.fillStyle = COLOR_ZERO;
                                                g.ctx.fillRect(x, g.h - 2, 2, 2);
                                            } else {
                                                g.ctx.fillStyle = g.color;
                                                g.ctx.fillRect(x, 0, 2, g.h);
                                            }
                                        }

                                        continue;
                                    }

        // 🔥 Отрисовка аналоговых параметров
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