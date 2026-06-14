const TableManager = {
    DEFAULT_HEIGHT: 20,
    params: [],
    paramSettings: {},

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
            description: param.description || '',
            hexId: `hex-${idx}`,
            physId: `phys-${idx}`,
            graphId: `graph-${idx}`,
            graphIdx: idx,
            register: param.register,
            unit: param.unit || '--',
            isDiscrete: param.register.includes('.'),
            scale: param.scale || 1.0
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

            // 🔥 Инициализация графика мгновенно (без искусственной задержки)
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
        });

               tbody.addEventListener('click', function(e) {
                   const row = e.target.closest('tr');
                   if (!row) return;
                   tbody.querySelectorAll('tr').forEach(r => r.classList.remove('selected'));
                   row.classList.add('selected');

                   // 🔥 НОВОЕ: вставляем имя параметра в поле ввода
                   const paramName = row.cells[0]?.textContent;
                   const userInputField = document.getElementById('userInputField');
                   if (userInputField && paramName) {
                       userInputField.value = paramName + '=';
                       userInputField.focus();
                       // Ставим курсор в конец
                       userInputField.setSelectionRange(userInputField.value.length, userInputField.value.length);
                   }
               });

        this.initColumnResize();
        this.initContextMenu();
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

    initColumnResize() {
        const tbody = document.getElementById('paramTableBody');
        if (!tbody) return;
        const table = tbody.closest('table');
        if (!table) return;

        let colgroup = table.querySelector('colgroup');
        if (!colgroup) {
            colgroup = document.createElement('colgroup');
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
    },

    initContextMenu() {
        const tbody = document.getElementById('paramTableBody');
        if (!tbody) return;

        tbody.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            const row = e.target.closest('tr');
            if (!row) return;

            const index = Array.from(tbody.querySelectorAll('tr')).indexOf(row);
            if (index === -1) return;

            this.showParamSettings(e.clientX, e.clientY, index);
        });
    },

    showParamSettings(x, y, index) {
        const popup = document.getElementById('paramSettingsPopup');
        if (!popup) return;

        const param = this.params[index];
        if (!param) return;

        const settings = this.getParamSettings(index);
        const currentScale = settings.scale ?? param.scale ?? 1.0;

        // 🔥 Заполняем поля
        document.getElementById('popupParamName').value = param.name;
        document.getElementById('popupDescription').value = param.description ?? '';

        const descTextarea = document.getElementById('popupDescription');
        descTextarea.rows = 1;
        if (param.description) {
            descTextarea.rows = Math.min(Math.max(Math.ceil(param.description.length / 50), 2), 6);
        }

        const discreteFields = document.getElementById('discreteFields');
        const analogFields = document.getElementById('analogFields');

        if (param.isDiscrete) {
            discreteFields.style.display = 'block';
            analogFields.style.display = 'none';
        } else {
            discreteFields.style.display = 'none';
            analogFields.style.display = 'block';

            document.getElementById('popupHeight').value = settings.height;
            document.getElementById('popupMax').value = settings.maxVal ?? '';
            document.getElementById('popupAutoMax').checked = settings.maxVal === null;
            document.getElementById('popupMax').disabled = settings.maxVal === null;
            document.getElementById('popupScale').value = currentScale;

            document.getElementById('popupScale').dataset.prevScale = currentScale;
        }

        // 🔥 Показываем popup временно, чтобы измерить его размеры
        popup.style.display = 'block';
        popup.style.left = '0px';
        popup.style.top = '0px';

        const popupRect = popup.getBoundingClientRect();
        const popupWidth = popupRect.width;
        const popupHeight = popupRect.height;

        // 🔥 Получаем размеры видимой области
        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;

        // 🔥 Вычисляем позицию с учётом границ экрана
        let finalX = x;
        let finalY = y;

        // Если popup выходит за правый край — сдвигаем влево
        if (x + popupWidth > viewportWidth) {
            finalX = viewportWidth - popupWidth - 10;
        }

        // Если popup выходит за нижний край — сдвигаем вверх
        if (y + popupHeight > viewportHeight) {
            finalY = viewportHeight - popupHeight - 10;
        }

        // Если popup выходит за левый край — прижимаем к левому краю
        if (finalX < 10) {
            finalX = 10;
        }

        // Если popup выходит за верхний край — прижимаем к верхнему краю
        if (finalY < 10) {
            finalY = 10;
        }

        popup.style.left = `${finalX}px`;
        popup.style.top = `${finalY}px`;
        popup.dataset.paramIndex = index;
    },  // 🔥 ЗАПЯТАЯ ДОБАВЛЕНА ЗДЕСЬ

    getParamSettings(index) {
        return this.paramSettings[index] || {
            height: this.DEFAULT_HEIGHT,
            maxVal: null,
            scale: this.params[index]?.scale || 1.0
        };
    },

    applyParamSettings(index, height, maxVal, scale) {
        const param = this.params[index];
        if (!param) return;

        const newScale = scale ? parseFloat(scale) : (param.scale ?? 1.0);
        const newMaxVal = maxVal === '' || maxVal === null ? null : parseFloat(maxVal);

        // 🔥 Запоминаем предыдущую шкалу для сравнения
        const prevSettings = this.paramSettings[index] ?? {};
        const prevScale = prevSettings.scale ?? param.scale ?? 1.0;
        const scaleChanged = prevScale !== newScale;

        this.paramSettings[index] = {
            height: height ? parseInt(height) : this.DEFAULT_HEIGHT,
            maxVal: newMaxVal,
            scale: newScale
        };

        if (!param.isDiscrete && height && parseInt(height) >= 10) {
            this.setRowHeight(index, parseInt(height));
        }

        if (window.scopeWorker) {
            // 🔥 Если шкала изменилась — сначала очищаем буфер
            if (scaleChanged) {
                window.scopeWorker.postMessage({
                    type: 'clearBuffer',
                    id: index
                });
            }

            window.scopeWorker.postMessage({
                type: 'updateSettings',
                id: index,
                width: undefined,
                height: param.isDiscrete ? this.DEFAULT_HEIGHT : this.paramSettings[index].height,
                maxVal: this.paramSettings[index].maxVal,
                scale: this.paramSettings[index].scale
            });
        }
    },

    hideParamSettings() {
        const popup = document.getElementById('paramSettingsPopup');
        if (popup) {
            popup.style.display = 'none';
        }
    }
};