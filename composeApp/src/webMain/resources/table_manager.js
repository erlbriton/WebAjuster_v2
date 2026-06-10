const TableManager = {
    DEFAULT_HEIGHT: 20,
    params: [],

    init(scopeWorker) {
        const tbody = document.getElementById('paramTableBody');
        if (!tbody) {
            console.error('[TableManager] Не найден tbody!');
            return;
        }
        tbody.innerHTML = '';

        const sourceParams = window.ramParameters && window.ramParameters.length > 0
            ? window.ramParameters
            : [];

        if (sourceParams.length === 0) {
            console.warn('[TableManager] Нет параметров');
            return;
        }

        console.log('[TableManager] Создаю ' + sourceParams.length + ' строк');

        this.params = sourceParams.map((param, idx) => ({
            name: param.name,
            hexId: 'hex-' + idx,
            physId: 'phys-' + idx,
            graphId: 'graph-' + idx,
            graphIdx: idx,
            register: param.register,
            unit: param.unit || '--',
            maxHeight: 1100,
            rowHeight: 20
        }));

        this.params.forEach((param, idx) => {
            const row = document.createElement('tr');
            if (idx === 0) row.classList.add('selected');
            row.style.height = param.rowHeight + 'px';

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
            graphCell.style.height = param.rowHeight + 'px';

            const canvas = document.createElement('canvas');
            canvas.id = param.graphId;
            canvas.width = 400;
            canvas.height = param.rowHeight;
            graphCell.appendChild(canvas);

            row.appendChild(graphCell);
            tbody.appendChild(row);

            // 🔥 Передаём canvas в worker СРАЗУ, без setTimeout
            try {
                const offscreen = canvas.transferControlToOffscreen();
                scopeWorker.postMessage({
                    type: 'initGraph',
                    id: param.graphIdx,
                    canvas: offscreen,
                    width: 400,
                    height: param.rowHeight,
                    maxVal: param.maxHeight
                }, [offscreen]);
                console.log('[TableManager] График ' + idx + ' создан');
            } catch (e) {
                console.error('[TableManager] Ошибка графика ' + idx + ':', e);
            }
        });

        tbody.addEventListener('click', function(e) {
            const row = e.target.closest('tr');
            if (!row) return;
            tbody.querySelectorAll('tr').forEach(r => r.classList.remove('selected'));
            row.classList.add('selected');
        });

        tbody.addEventListener('contextmenu', function(e) {
            e.preventDefault();
            const row = e.target.closest('tr');
            if (!row) return;
            const idx = Array.from(tbody.querySelectorAll('tr')).indexOf(row);
            const param = TableManager.params[idx];
            if (param) {
                TableManager.showSettingsDialog(e.clientX, e.clientY, param, idx);
            }
        });

        console.log('[TableManager] Таблица создана, строк: ' + this.params.length);
    },

    showSettingsDialog(x, y, param, idx) {
        const oldDialog = document.getElementById('paramSettingsDialog');
        if (oldDialog) oldDialog.remove();

        const dialog = document.createElement('div');
        dialog.id = 'paramSettingsDialog';
        dialog.style.cssText = 'position:fixed;left:' + x + 'px;top:' + y + 'px;background:white;border:2px solid #2196f3;border-radius:8px;padding:15px;box-shadow:0 4px 20px rgba(0,0,0,0.3);z-index:100000;min-width:250px;';

        dialog.innerHTML = '<div style="font-weight:bold;margin-bottom:10px;color:#2196f3;">Настройки: ' + param.name + '</div>' +
            '<div style="margin-bottom:10px;"><label style="display:block;margin-bottom:5px;font-size:12px;">Высота строки (px):</label><input type="number" id="rowHeightInput" value="' + param.rowHeight + '" min="10" max="100" style="width:100%;padding:5px;border:1px solid #ddd;border-radius:4px;"></div>' +
            '<div style="margin-bottom:10px;"><label style="display:block;margin-bottom:5px;font-size:12px;">Максимальное значение:</label><input type="number" id="maxValInput" value="' + param.maxHeight + '" min="100" max="10000" style="width:100%;padding:5px;border:1px solid #ddd;border-radius:4px;"></div>' +
            '<div style="display:flex;gap:10px;"><button id="applyBtn" style="flex:1;padding:8px;background:#2196f3;color:white;border:none;border-radius:4px;cursor:pointer;">Применить</button><button id="cancelBtn" style="flex:1;padding:8px;background:#ccc;border:none;border-radius:4px;cursor:pointer;">Отмена</button></div>';

        document.body.appendChild(dialog);

        document.getElementById('applyBtn').addEventListener('click', () => {
            const newRowHeight = parseInt(document.getElementById('rowHeightInput').value);
            const newMaxVal = parseInt(document.getElementById('maxValInput').value);

            if (newRowHeight && newMaxVal) {
                param.rowHeight = newRowHeight;
                param.maxHeight = newMaxVal;
                TableManager.recreateRow(idx, param);
                console.log('[TableManager] Настройки применены: высота=' + newRowHeight + ', макс=' + newMaxVal);
            }

            dialog.remove();
        });

        document.getElementById('cancelBtn').addEventListener('click', () => {
            dialog.remove();
        });

        setTimeout(() => {
            document.addEventListener('click', function closeDialog(e) {
                if (!dialog.contains(e.target)) {
                    dialog.remove();
                    document.removeEventListener('click', closeDialog);
                }
            });
        }, 100);
    },

    recreateRow(idx, param) {
        const tbody = document.getElementById('paramTableBody');
        if (!tbody) return;

        const rows = tbody.querySelectorAll('tr');
        if (!rows[idx]) return;

        const oldRow = rows[idx];
        const newRow = document.createElement('tr');
        if (idx === 0) newRow.classList.add('selected');
        newRow.style.height = param.rowHeight + 'px';

        const nameCell = document.createElement('td');
        nameCell.textContent = param.name;
        newRow.appendChild(nameCell);

        const hexCell = document.createElement('td');
        hexCell.id = param.hexId;
        hexCell.className = 'hex-value';
        hexCell.textContent = 'x0000';
        newRow.appendChild(hexCell);

        const physCell = document.createElement('td');
        physCell.id = param.physId;
        physCell.className = 'phys-value';
        physCell.textContent = '0.00';
        newRow.appendChild(physCell);

        const unitCell = document.createElement('td');
        unitCell.textContent = param.unit;
        newRow.appendChild(unitCell);

        const graphCell = document.createElement('td');
        graphCell.className = 'graph-cell';
        graphCell.style.height = param.rowHeight + 'px';

        const canvas = document.createElement('canvas');
        canvas.id = param.graphId;
        canvas.width = 400;
        canvas.height = param.rowHeight;
        graphCell.appendChild(canvas);

        newRow.appendChild(graphCell);
        tbody.replaceChild(newRow, oldRow);

        setTimeout(() => {
            try {
                const offscreen = canvas.transferControlToOffscreen();
                window.scopeWorker.postMessage({
                    type: 'initGraph',
                    id: param.graphIdx,
                    canvas: offscreen,
                    width: 400,
                    height: param.rowHeight,
                    maxVal: param.maxHeight
                }, [offscreen]);
            } catch (e) {
                console.error('[TableManager] Ошибка пересоздания графика ' + param.graphIdx + ':', e);
            }
        }, 100);
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
            if (graphCell) graphCell.style.height = height + 'px';
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
    }
};