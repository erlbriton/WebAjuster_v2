class LeftPanel {
    constructor() {
        this.params = [];
        this.rowHeight = 60;
        this.worker = new Worker('left_graph_worker.js');
        this.resizeObservers = new Map();
        this.graphCells = new Map();
        console.log('[LeftPanel] Конструктор создан, Worker запущен');
    }

    buildFromJSON(jsonStr) {
        try {
            const params = JSON.parse(jsonStr);
            console.log('[LeftPanel] Получено параметров: ' + params.length);
            if (params.length === 0) return;

            const table = document.getElementById('paramsTable');
            if (!table) return;

            table.innerHTML = '';
            this.params = params;

            params.forEach((param, index) => {
                const row = this.createRow(param, index);
                table.appendChild(row);
            });

        } catch (error) {
            console.error('[LeftPanel] Ошибка:', error);
        }
    }

    createRow(param, index) {
        const row = document.createElement('div');
        row.id = 'param-row-' + index;
        row.style.cssText = `
            display: grid;
            grid-template-columns: 150px 100px 100px 60px 1fr;
            border-bottom: 1px solid #eee;
            align-items: center;
            font-size: 12px;
            font-family: monospace;
            height: ${this.rowHeight}px;
        `;

        const nameCell = document.createElement('div');
        nameCell.textContent = param.name;
        nameCell.style.cssText = 'padding: 5px; cursor: pointer;';

        const hexCell = document.createElement('div');
        hexCell.id = 'hex-' + index;
        hexCell.style.cssText = 'padding: 5px; color: #666;';

        const physCell = document.createElement('div');
        physCell.id = 'phys-' + index;
        physCell.style.cssText = 'padding: 5px; font-weight: bold;';

        const unitCell = document.createElement('div');
        unitCell.textContent = param.unit;
        unitCell.style.cssText = 'padding: 5px; color: #666;';

        const graphCell = document.createElement('div');
        graphCell.style.cssText = 'position: relative; width: 100%; height: 100%; overflow: hidden;';

        const canvas = document.createElement('canvas');
        canvas.id = 'canvas-' + index;
        canvas.style.cssText = 'display: block; width: 100%; height: 100%;';
        canvas.style.background = param.isDiscrete ? '#f9f9f9' : '#fff';

        graphCell.appendChild(canvas);

        this.graphCells.set(index, graphCell);

        // 🔥 Передача OffscreenCanvas в Worker
        setTimeout(() => {
            try {
                const rect = graphCell.getBoundingClientRect();
                const offscreen = canvas.transferControlToOffscreen();
                this.worker.postMessage({
                    type: 'init',
                    id: index,
                    canvas: offscreen,
                    width: rect.width || 300,
                    height: rect.height || this.rowHeight
                }, [offscreen]);
            } catch (e) {
                console.error('[LeftPanel] Ошибка transferControlToOffscreen:', e);
            }
        }, 100);

        // 🔥 ResizeObserver для синхронизации размеров
        const resizeObserver = new ResizeObserver(entries => {
            for (let entry of entries) {
                const { width, height } = entry.contentRect;
                if (width > 0 && height > 0) {
                    this.worker.postMessage({
                        type: 'resize',
                        id: index,
                        width: width,
                        height: height
                    });
                }
            }
        });
        resizeObserver.observe(graphCell);
        this.resizeObservers.set(index, resizeObserver);

        row.appendChild(nameCell);
        row.appendChild(hexCell);
        row.appendChild(physCell);
        row.appendChild(unitCell);
        row.appendChild(graphCell);

        if (!param.isDiscrete) {
            nameCell.addEventListener('click', () => {
                this.toggleRowHeight(index, row);
            });
        }

        return row;
    }

    toggleRowHeight(index, row) {
        const currentHeight = parseInt(row.style.height) || this.rowHeight;
        let newHeight = currentHeight < 100 ? 100 : (currentHeight < 200 ? 200 : 60);
        row.style.height = newHeight + 'px';
    }

    destroy() {
        this.resizeObservers.forEach(ro => ro.disconnect());
        this.resizeObservers.clear();
        this.graphCells.clear();
    }
}

window.leftPanel = new LeftPanel();
window.buildLeftPanel = function(jsonStr) {
    window.leftPanel.buildFromJSON(jsonStr);
    window.receiveParametersFromKotlin(jsonStr);
};

console.log('[LeftPanel] Module loaded with Worker');