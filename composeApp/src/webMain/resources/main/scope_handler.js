// scope_handler.js - Обработка Scope Worker и отрисовка графиков

import { state } from './app_state.js';

// Глобальный счетчик для анимации движения сетки
let gridOffset = 0;

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

// Переменная для хранения времени последнего обновления текстовых ячеек
let lastTextUpdateTime = 0;

export function drawGraphsFromData(allData) {
    if (!state.oscTableVisible) return;

    state.lastGraphData = allData;

    const colors = [
        '#00ff00', '#ff0000', '#00ffff', '#ffff00', '#ff00ff',
        '#ff9900', '#ff3366', '#33ccff', '#ffffff', '#ccff33'
    ];

    // Масштабирование под 1 Гц (50 точек опроса при 20мс = 20 пикселей ширины окна сетки)
    const stepX = 0.4;
    const lineSpacing = 20;
    gridOffset = (gridOffset + stepX) % lineSpacing;

    // Проверяем, прошло ли 300 мс с момента последнего обновления текста
    const currentTime = Date.now();
    const shouldUpdateText = (currentTime - lastTextUpdateTime) >= 300;

    if (shouldUpdateText) {
        lastTextUpdateTime = currentTime;
    }

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

        // Текстовые значения обновляются только по таймеру (раз в 300 мс)
        if (shouldUpdateText) {
            const currentValue = Math.floor(data[data.length - 1]);
            const row = canvas.closest('tr');
            if (row && row.cells.length >= 3) {
                const hexCell = row.cells[1];
                const physCell = row.cells[2];

                if (hexCell) {
                    hexCell.textContent = '0x' + currentValue.toString(16).toUpperCase().padStart(4, '0');
                }
                if (physCell) {
                    physCell.textContent = currentValue.toString();
                }
            }
        }

        ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
        ctx.lineWidth = 1;
        ctx.beginPath();

        for (let x = lineSpacing - gridOffset; x < width; x += lineSpacing) {
            ctx.moveTo(x, 0);
            ctx.lineTo(x, height);
        }
        ctx.stroke();

        const maxPoints = Math.floor(width / stepX);
        const pointsToShow = Math.min(data.length, maxPoints);
        const startIndex = data.length - pointsToShow;

        const centerY = height / 2;
        const scaleY = (height / 2) / 1100;

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