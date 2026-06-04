// scope_worker.js - Временная ось X (иммунитет к блокировкам Compose)
let ctx, w, h;
let d1 = [], d2 = [];
const TIME_WINDOW = 2000; // Вектор видимости: 2 секунды
const MAX_POINTS = 500;

self.onmessage = (e) => {
    if (e.data.type === 'init') {
        const c = e.data.canvas;
        ctx = c.getContext('2d');
        w = c.width; h = c.height;
        setInterval(draw, 16); // 60 FPS независимая отрисовка
        console.log('[Worker] 🕒 Time-based renderer started');
    }
    else if (e.data.type === 'data') {
        const now = e.data.t;
        d1.push({ t: now, v: e.data.v1 });
        d2.push({ t: now, v: e.data.v2 });
        if (d1.length > MAX_POINTS) d1.shift();
        if (d2.length > MAX_POINTS) d2.shift();
    }
};

function draw() {
    if (!ctx) return;
    const now = performance.now();

    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = '#FAFAFA'; ctx.fillRect(0, 0, w, h);

    // Сетка
    ctx.strokeStyle = '#EBEBEB'; ctx.lineWidth = 0.5;
    for (let x = 0; x < w; x += 40) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke(); }
    for (let y = 0; y < h; y += h/4) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke(); }

    const line = (arr, col, idx) => {
        if (arr.length < 2) return;
        const zh = h / 2;
        const cy = idx * zh + zh / 2;
        ctx.strokeStyle = col; ctx.lineWidth = 1.5; ctx.beginPath();

        let started = false;
        for (let i = 0; i < arr.length; i++) {
            const age = now - arr[i].t;
            if (age > TIME_WINDOW) continue; // 🔥 Отбрасываем точки старше окна

            // 🔥 Позиция X зависит от времени, а не от индекса!
            const x = w - (age / TIME_WINDOW) * w;
            const y = cy - (arr[i].v / 1100) * (zh / 2);

            if (!started) { ctx.moveTo(x, y); started = true; }
            else ctx.lineTo(x, y);
        }
        ctx.stroke();
    };

    line(d1, '#4A90E2', 0);
    line(d2, '#E24A4A', 1);
}