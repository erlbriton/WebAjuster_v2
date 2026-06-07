const graphs = {};

self.onmessage = function(e) {
    const msg = e.data;
    const id = msg.id;

    if (msg.type === 'init') {
        const c = msg.canvas;
        graphs[id] = {
            canvas: c,
            ctx: c.getContext('2d'),
            history: [],
            w: msg.width || 300,
            h: msg.height || 60
        };
    }
    else if (msg.type === 'resize') {
        const g = graphs[id];
        if (g) {
            const dpr = self.devicePixelRatio || 1;
            g.canvas.width = msg.width * dpr;
            g.canvas.height = msg.height * dpr;
            g.w = msg.width;
            g.h = msg.height;
            g.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            draw(g);
        }
    }
    else if (msg.type === 'data') {
        const g = graphs[id];
        if (g) {
            g.history.push(msg.value);
            if (g.history.length > 100) g.history.shift();
            draw(g);
        }
    }
};

function draw(g) {
    const { ctx, history, w, h } = g;
    if (!ctx || history.length < 2) return;

    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, w, h);

    ctx.strokeStyle = '#f0f0f0';
    ctx.lineWidth = 0.5;

    for (let x = 0; x <= w; x += w / 5) {
        ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
    }
    for (let y = 0; y <= h; y += h / 3) {
        ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
    }

    const minVal = Math.min(...history);
    const maxVal = Math.max(...history);
    const range = maxVal - minVal || 1;

    ctx.strokeStyle = '#4A90E2';
    ctx.lineWidth = 1.5;
    ctx.beginPath();

    const stepX = w / (history.length - 1);
    const padding = 4;

    for (let i = 0; i < history.length; i++) {
        const x = i * stepX;
        const normalizedY = (history[i] - minVal) / range;
        const y = h - padding - (normalizedY * (h - padding * 2));
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
    }
    ctx.stroke();
}