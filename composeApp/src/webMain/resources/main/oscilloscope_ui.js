// oscilloscope_ui.js - UI осциллографа

import { state } from './app_state.js';
import { drawGraphsFromData } from './scope_handler.js';

let oscColumnWeights = [0.15, 0.15, 0.20, 0.10, 0.40];
let oscResizingColumn = -1;
let oscStartX = 0;
let oscStartWeights = [];

export function createOscilloscopeTable() {
    const tbody = document.getElementById('oscTableBody');

    console.log('[UI Проверка] Найдена ли таблица (tbody):', tbody, '| Параметры:', window.ramParameters?.length);

    if (!tbody || !window.ramParameters) return;

    tbody.innerHTML = '';

    window.ramParameters.forEach((param, i) => {
        const row = document.createElement('tr');
        row.style.height = '24px';

        // 1. Создаем ячейки данных (Name, Hex, Physical, Unit)
        [param.name, param.hex, param.value, param.unit].forEach(text => {
            const td = document.createElement('td');
            td.textContent = text || '';
            row.appendChild(td);
        }); // <-- Цикл текстовых ячеек завершается строго здесь

        // 2. Создаем ячейку для графика
        const graphCell = document.createElement('td');
        graphCell.className = 'graph-cell';
        graphCell.style.position = 'relative';
        graphCell.style.padding = '0';

        const graphCanvas = document.createElement('canvas');
        graphCanvas.id = `osc-graph-canvas-${i}`;
        graphCanvas.style.cssText = `display: block;`;

        graphCell.appendChild(graphCanvas);
        row.appendChild(graphCell);

        tbody.appendChild(row);
    });

    updateOscTableColumnWidths();

    // Даем DOM время на отрисовку, затем подхватываем контексты холстов
    setTimeout(() => {
        initGraphContexts();
        updateGraphCanvasSizes();
    }, 50);

    console.log('[Main] ✅ Таблица осциллографа заполнена и создана');
}

export function initGraphContexts() {
    state.graphContexts = [];
    if (!window.ramParameters) return;

    for (let i = 0; i < window.ramParameters.length; i++) {
        const canvas = document.getElementById(`osc-graph-canvas-${i}`);
        if (canvas) {
            const ctx = canvas.getContext('2d');
            state.graphContexts.push({ canvas, ctx });
        }
    }
    console.log('[Main] ✅ Инициализировано контекстов:', state.graphContexts.length);
}

export function updateGraphCanvasSizes() {
    let sizeChanged = false;

    state.graphContexts.forEach((graph, index) => {
        if (!graph || !graph.canvas) return;
        const cell = graph.canvas.parentElement;
        if (cell) {
            const newWidth = cell.offsetWidth;
            const newHeight = 24;

            if (graph.canvas.width !== newWidth || graph.canvas.height !== newHeight) {
                graph.canvas.width = newWidth;
                graph.canvas.height = newHeight;
                sizeChanged = true;
            }
        }
    });

    if (sizeChanged && state.lastGraphData) {
        drawGraphsFromData(state.lastGraphData);
    }
}

function updateOscTableColumnWidths() {
    const table = document.querySelector('#oscTable table');
    if (!table) return;

    const headers = table.querySelectorAll('thead th');
    headers.forEach((header, index) => {
        if (index < oscColumnWeights.length) {
            header.style.width = (oscColumnWeights[index] * 100) + '%';
            header.style.position = 'relative';

            if (index < oscColumnWeights.length - 1) {
                const oldResizer = header.querySelector('.osc-resizer');
                if (oldResizer) oldResizer.remove();

                const resizer = document.createElement('div');
                resizer.className = 'osc-resizer';
                resizer.dataset.column = index;

                resizer.addEventListener('mousedown', (e) => {
                    startOscColumnResize(e, index);
                });

                header.appendChild(resizer);
            }
        }
    });
}

function startOscColumnResize(e, columnIndex) {
    e.preventDefault();
    e.stopPropagation();
    oscResizingColumn = columnIndex;
    oscStartX = e.clientX;
    oscStartWeights = [...oscColumnWeights];

    const resizer = e.target;
    resizer.classList.add('dragging');

    document.addEventListener('mousemove', oscColumnResize);
    document.addEventListener('mouseup', stopOscColumnResize);
}

function oscColumnResize(e) {
    if (oscResizingColumn === -1) return;

    const table = document.querySelector('#oscTable table');
    if (!table) return;

    const deltaX = e.clientX - oscStartX;
    const totalWidth = table.offsetWidth;
    const deltaWeight = deltaX / totalWidth;

    const newWeight = Math.max(0.05, oscStartWeights[oscResizingColumn] + deltaWeight);
    oscColumnWeights[oscResizingColumn] = newWeight;

    if (oscResizingColumn < oscColumnWeights.length - 1) {
        const nextWeight = Math.max(0.05, oscStartWeights[oscResizingColumn + 1] - deltaWeight);
        oscColumnWeights[oscResizingColumn + 1] = nextWeight;
    }

    updateOscTableColumnWidths();
    updateGraphCanvasSizes();
}

function stopOscColumnResize(e) {
    if (oscResizingColumn === -1) return;

    const resizer = document.querySelector('.osc-resizer.dragging');
    if (resizer) {
        resizer.classList.remove('dragging');
    }

    document.removeEventListener('mousemove', oscColumnResize);
    document.removeEventListener('mouseup', stopOscColumnResize);
    oscResizingColumn = -1;
}

console.log('[OscilloscopeUI] ✅ oscilloscope_ui.js загружен');