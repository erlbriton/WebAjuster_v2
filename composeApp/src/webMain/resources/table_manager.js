const TableManager = {
    DEFAULT_HEIGHT: 20,
    params: [],

    init(scopeWorker) {
        const tbody = document.getElementById('paramTableBody');
        if (!tbody) return;
        tbody.innerHTML = '';

        const sourceParams = window.ramParameters && window.ramParameters.length > 0
            ? window.ramParameters
            : [];

        if (sourceParams.length === 0) {
            console.warn('[TableManager] ⚠️ RAM-параметры не загружены');
            return;
        }

        this.params = sourceParams.map((param, idx) => ({
            name: param.name,
            hexId: `hex-${idx}`,
            physId: `phys-${idx}`,
            graphId: `graph-${idx}`,
            graphIdx: idx,
            register: param.register,
            unit: param.unit || '--',
            isDiscrete: param.register.includes('.')
        }));

        this.params.forEach((param, idx) => {
            const row = document.createElement('tr');
            if (idx === 0) row.classList.add('selected');
            row.style.height = this.DEFAULT_HEIGHT + 'px';

            const nameCell = document.createElement('td');
            nameCell.textContent = param.name;
            row.appendChild(nameCell);

            const hexCell = document.createElement('td');
            hexCell.id = param.hexId;
            hexCell.className = 'hex-value';
            hexCell.textContent = 'x0000';
            row.appendChild(hexCell);

            const physCell = document.createElement('td');
            physCell.id = param.physId;
            physCell.className = 'phys-value';
            physCell.textContent = '0.00';
            row.appendChild(physCell);

            const unitCell = document.createElement('td');
            unitCell.textContent = param.unit;
            row.appendChild(unitCell);

            const graphCell = document.createElement('td');
            graphCell.className = 'graph-cell';
            graphCell.style.height = this.DEFAULT_HEIGHT + 'px';

            const canvas = document.createElement('canvas');
            canvas.id = param.graphId;
            canvas.width = 400;
            canvas.height = this.DEFAULT_HEIGHT;
            graphCell.appendChild(canvas);

            row.appendChild(graphCell);
            tbody.appendChild(row);

            setTimeout(() => {
                try {
                    const offscreen = canvas.transferControlToOffscreen();
                    scopeWorker.postMessage({
                        type: 'initGraph',
                        id: param.graphIdx,
                        canvas: offscreen,
                        width: 400,
                        height: this.DEFAULT_HEIGHT,
                        isDiscrete: param.isDiscrete
                    }, [offscreen]);
                } catch (e) {
                    console.error(`[TableManager] ❌ Ошибка инициализации графика #${param.graphIdx}:`, e);
                }
            }, 100 + idx * 50);
        });

        tbody.addEventListener('click', function(e) {
            const row = e.target.closest('tr');
            if (!row) return;
            tbody.querySelectorAll('tr').forEach(r => r.classList.remove('selected'));
            row.classList.add('selected');
        });

        // 🔥 НОВОЕ: Инициализация регулировки ширины столбцов
        this.initColumnResize();
    },

    updateRow(index, hexValue, physicalValue) {
        const hexEl = document.getElementById('hex-' + index);
        const physEl = document.getElementById('phys-' + index);
        if (hexEl) hexEl.textContent = 'x' + hexValue.toString(16).toUpperCase().padStart(4, '0');
        if (physEl) physEl.textContent = physicalValue.toFixed(2);
    },

    setRowHeight(index, height) {
        const rows = document.querySelectorAll('#paramTableBody tr');
        if (rows[index]) {
            const row = rows[index];
            row.style.height = height + 'px';
            const graphCell = row.querySelector('.graph-cell');
            if (graphCell) {
                graphCell.style.height = height + 'px';
            }
        }
    },

    getGraphCellSize(index) {
        const rows = document.querySelectorAll('#paramTableBody tr');
        if (rows[index]) {
            const graphCell = rows[index].querySelector('.graph-cell');
            if (graphCell) {
                const rect = graphCell.getBoundingClientRect();
                return { width: Math.round(rect.width), height: Math.round(rect.height) };
            }
        }
        return null;
    },

    updateAllCanvasSizes(scopeWorker) {
        const rows = document.querySelectorAll('#paramTableBody tr');
        rows.forEach((row, idx) => {
            const graphCell = row.querySelector('.graph-cell');
            if (graphCell) {
                const rect = graphCell.getBoundingClientRect();
                scopeWorker.postMessage({
                    type: 'updateSettings',
                    id: idx,
                    width: Math.round(rect.width),
                    height: Math.round(rect.height)
                });
            }
        });
    },

    // 🔥 НОВАЯ ФУНКЦИЯ: Регулировка ширины столбцов мышкой
    initColumnResize() {
        const tbody = document.getElementById('paramTableBody');
        if (!tbody) return;
        const table = tbody.closest('table');
        if (!table) return;

        // Создаём colgroup, если его нет
        let colgroup = table.querySelector('colgroup');
        if (!colgroup) {
            colgroup = document.createElement('colgroup');
            // Начальные ширины: Name, Hex, Physical, Unit, Graph
            const widths = [150, 80, 80, 50, 400];
            widths.forEach(w => {
                const col = document.createElement('col');
                col.style.width = w + 'px';
                colgroup.appendChild(col);
            });
            table.insertBefore(colgroup, table.firstChild);
        }

        const cols = colgroup.querySelectorAll('col');
        const headers = table.querySelectorAll('thead th');

        if (headers.length === 0) {
            console.warn('[TableManager] ⚠️ Заголовки таблицы не найдены');
            return;
        }

        // Добавляем ручки к заголовкам, если их ещё нет
        headers.forEach((th) => {
            if (th.querySelector('.resize-handle')) return;
            const handle = document.createElement('div');
            handle.className = 'resize-handle';
            th.style.position = 'relative';
            th.appendChild(handle);
        });

        let isResizing = false;
        let currentCol = null;
        let startX = 0;
        let startWidth = 0;

        headers.forEach((th, idx) => {
            const handle = th.querySelector('.resize-handle');
            if (!handle || !cols[idx]) return;

            handle.addEventListener('mousedown', (e) => {
                e.preventDefault();
                isResizing = true;
                currentCol = cols[idx];
                startX = e.clientX;
                startWidth = currentCol.offsetWidth || parseInt(currentCol.style.width) || 100;
                document.body.style.cursor = 'col-resize';
                document.body.style.userSelect = 'none';
            });
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing || !currentCol) return;
            const newWidth = Math.max(40, startWidth + (e.clientX - startX));
            currentCol.style.width = newWidth + 'px';

            // Если изменилась колонка Graph (последняя) — обновляем размеры canvas
            if (currentCol === cols[cols.length - 1]) {
                this.updateAllCanvasSizes(window.scopeWorker);
            }
        });

        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                currentCol = null;
                document.body.style.cursor = '';
                document.body.style.userSelect = '';
            }
        });

        console.log('[TableManager] ✅ Column resize инициализирован');
    }
};