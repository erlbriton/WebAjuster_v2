// scope_handler.js - Обработка Scope Worker и отрисовка графиков

import { state } from './app_state.js';

export function handleScopeWorkerMessage(event) {
    const msg = event.data;

    switch (msg.type) {
        case 'initialized':
            console.log('[Main] Scope Worker инициализирован');
            break;
        case 'graphData':
            drawGraphsFromData(msg.data);
            break;
        case 'error':
            console.error('[Main] Ошибка Scope Worker:', msg.message);
            break;
    }
}

export function drawGraphsFromData(allData) {
    console.log('[ScopeHandler] Данные:', allData.length, 'Контексты:', state.graphContexts?.length, 'Visible:', state.oscTableVisible);
    if (!state.oscTableVisible) return;

    state.lastGraphData = allData;

    // Расширенная палитра ярких цветов для темного фона
    const colors = [
        '#00ff00', // 1. Зеленый (как и был)
        '#ff0000', // 2. Красный (как и был)
        '#00ffff', // 3. Бирюзовый / Циан
        '#ffff00', // 4. Желтый
        '#ff00ff', // 5. Пурпурный
        '#ff9900', // 6. Оранжевый
        '#ff3366', // 7. Розовый
        '#33ccff', // 8. Голубой
        '#ffffff', // 9. Белый
        '#ccff33'  // 10. Салатовый
    ];

    for (let i = 0; i < Math.min(allData.length, state.graphContexts.length); i++) {
        const graph = state.graphContexts[i];
        if (!graph || !graph.ctx) continue;

        const ctx = graph.ctx;
        const canvas = graph.canvas;
        const width = canvas.width;
        const height = canvas.height;
        const data = allData[i] || [];

        ctx.fillStyle = '#1a1a1a';
        ctx.fillRect(0, 0, width, height);

        if (data.length === 0) continue;

        const pixelsPerPoint = 1;
        const maxPoints = Math.floor(width / pixelsPerPoint);
        const pointsToShow = Math.min(data.length, maxPoints);
        const startIndex = data.length - pointsToShow;

        const stepX = pixelsPerPoint;
        const centerY = height / 2;
        const scaleY = (height / 2) / 1100;

        // Берем цвет по остатку от деления, чтобы они никогда не кончались
        ctx.strokeStyle = colors[i % colors.length];
        ctx.lineWidth = 2;
        ctx.beginPath();

        for (let j = 0; j < pointsToShow; j++) {
            const dataIndex = startIndex + j;
            const x = j * stepX;
            const y = centerY - (data[dataIndex] * scaleY);

            if (j === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        }
        ctx.stroke();
    }
}

console.log('[ScopeHandler] ✅ scope_handler.js загружен');