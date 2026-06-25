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

    // Жесткие пропорции: 1 секунда = 20 пикселей.
    // При опросе 20мс в одной секунде укладывается 50 точек.
    // 20 пикселей / 50 точек = ровно 0.4 пикселя на одну точку данных.
    const stepX = 0.4;
    const lineSpacing = 20;

    // СВЕРХТОЧНЫЙ РАСЧЕТ СЕТКИ ПО ВРЕМЕНИ:
    // Скорость: 20px за 1000ms = 0.02 пикселя в миллисекунду.
    // Теперь сетка идет идеально ровно, даже если из-за обилия текста прыгает FPS!
    const gridOffset = (performance.now() * 0.02) % lineSpacing;

    for (let i = 0; i < Math.min(allData.length, state.graphContexts.length); i++) {
        const graph = state.graphContexts[i];
        if (!graph || !graph.ctx) continue;

        const ctx = graph.ctx;
        const canvas = graph.canvas;
        const width = canvas.width;
        const height = canvas.height;
        const data = allData[i] || [];

        // Очистка экрана (черный фон осциллографа)
        ctx.fillStyle = '#1a1a1a';
        ctx.fillRect(0, 0, width, height);

        // ОБНОВЛЕНИЕ ТЕКСТА: Работает БЕЗ задержек, на каждом кадре
        if (data.length > 0) {
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

        if (data.length === 0) continue;

        // ОТРИСОВКА СЕТКИ: Линии идут строго через каждые 20 пикселей (1 секунда)
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
        ctx.lineWidth = 1;
        ctx.beginPath();

        // Рисуем линии справа налево, смещая их на выверенный по времени gridOffset
        for (let x = width - gridOffset; x > 0; x -= lineSpacing) {
            ctx.moveTo(x, 0);
            ctx.lineTo(x, height);
        }
        ctx.stroke();

        // ОТРИСОВКА СИГНАЛА (Движение справа налево)
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

            // Самая последняя точка всегда привязана к правому краю
            const x = width - (pointsToShow - 1 - j) * stepX;
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