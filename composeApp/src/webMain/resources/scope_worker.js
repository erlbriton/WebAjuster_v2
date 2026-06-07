const graphs = {};
const TIME_WINDOW = 32000;
const MAX_POINTS = 5000;
const MAX_GAP = 1000;

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
            settings: { maxVal: null }
        };
        console.log(`[Worker] Graph #${msg.id} initialized: w=${graphs[msg.id].w}, h=${graphs[msg.id].h}`);
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
            if (msg.height !== undefined && msg.height > 0) {
                g.canvas.height = msg.height;
                g.h = msg.height;
                g.ctx = g.canvas.getContext('2d');
            }
            if (msg.maxVal !== undefined) {
                g.settings.maxVal = msg.maxVal;
            }
            console.log(`[Worker] #${msg.id} updated: w=${g.w}, h=${g.h}, maxVal=${g.settings.maxVal}`);
        }
    }
};

setInterval(() => {
    const now = performance.now();

    for (const id in graphs) {
        const g = graphs[id];
        if (!g.ctx || g.buffer.length === 0) continue;

        g.ctx.clearRect(0, 0, g.w, g.h);

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

        g.ctx.strokeStyle = '#4A90E2';
        g.ctx.lineWidth = 1.5;
        g.ctx.beginPath();

               let drawing = false;
               for (let i = 0; i < visible.length; i++) {
                   const p = visible[i];
                   const x = g.w - (p.age / TIME_WINDOW) * g.w;
                   const y = g.h - ((p.v - bottomVal) / range) * g.h;

                   // 🔥 ОТЛАДКА: выводим координаты ПЕРВОЙ точки (самой новой)
                   if (i === 0 && Math.random() < 0.01) {
                       console.log(`[Worker] FIRST point (newest): age=${p.age.toFixed(1)}, x=${x.toFixed(1)}, g.w=${g.w}`);
                   }

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