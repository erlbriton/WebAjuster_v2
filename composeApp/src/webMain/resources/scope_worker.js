const graphs = {};
const MAX_POINTS = 1000; // Фиксированное количество точек на экране

self.onmessage = (e) => {
    const msg = e.data;

    if (msg.type === 'initGraph') {
        const c = msg.canvas;
        graphs[msg.id] = {
            ctx: c.getContext('2d'),
            w: c.width,
            h: c.height,
            buffer: [] // Храним только значения для отрисовки
        };
    }
    else if (msg.type === 'data') {
        const g = graphs[msg.id];
        if (g) {
            g.buffer.push(msg.v1);
            if (g.buffer.length > MAX_POINTS) g.buffer.shift();
        }
    }
};

setInterval(() => {
    for (const id in graphs) {
        const g = graphs[id];
        if (!g.ctx || g.buffer.length < 2) continue;

        g.ctx.clearRect(0, 0, g.w, g.h);
        g.ctx.fillStyle = '#FAFAFA';
        g.ctx.fillRect(0, 0, g.w, g.h);

        // Находим min/max для масштабирования по высоте
        let minVal = Infinity, maxVal = -Infinity;
        for (let v of g.buffer) {
            if (v < minVal) minVal = v;
            if (v > maxVal) maxVal = v;
        }
        const range = maxVal - minVal || 1;

        g.ctx.strokeStyle = '#4A90E2';
        g.ctx.lineWidth = 1;
        g.ctx.beginPath();

        // 🔥 Растягиваем ВСЕ точки буфера на 100% ширины столбца Graph
        for (let i = 0; i < g.buffer.length; i++) {
            const x = (i / (g.buffer.length - 1)) * g.w;
            const normalizedY = (g.buffer[i] - minVal) / range;
            const y = g.h - (normalizedY * g.h);

            if (i === 0) g.ctx.moveTo(x, y);
            else g.ctx.lineTo(x, y);
        }
        g.ctx.stroke();
    }
}, 16);