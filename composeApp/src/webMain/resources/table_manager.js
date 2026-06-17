const TableManager = {
    DEFAULT_HEIGHT: 20,
    params: [],
    paramSettings: {},
    visibleGraphIds: new Set(),
    visibilityObserver: null,
    domCache: {},
    _resizeThrottled: false,
    _resizeTimeout: null,

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

        console.log(`[TableManager] 🔥 Начинаем создание ${this.params.length} графиков`);

        this.params.forEach((param, idx) => {
            const row = document.createElement('tr');
            row.id = 'row-' + idx;
            if (idx === 0) row.classList.add('selected');
            row.style.height = this.DEFAULT_HEIGHT + 'px';

            const nameCell = document.createElement('td');
            nameCell.textContent = param.name;
            row.appendChild(nameCell);

            const hexCell = document.createElement('td');
            hexCell.id = param.hexId;
            if (param.isDiscrete) {
                hexCell.className = 'discrete-cell';
                hexCell.innerHTML = `<div class="discrete-indicator discrete-off" id="indicator-${idx}">0</div>`;
            } else {
                hexCell.className = 'hex-value';
                hexCell.textContent = 'x0000';
            }
            row.appendChild(hexCell);

            const physCell = document.createElement('td');
            physCell.id = param.physId;
            physCell.className = 'phys-value';
            physCell.textContent = param.isDiscrete ? '' : '0.00';
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

            try {
                console.log(`[TableManager] 📤 Отправляю initGraph для параметра #${idx} (${param.name})`);
                const offscreen = canvas.transferControlToOffscreen();
                scopeWorker.postMessage({
                    type: 'initGraph',
                    id: param.graphIdx,
                    canvas: offscreen,
                    width: 400,
                    height: this.DEFAULT_HEIGHT,
                    isDiscrete: param.isDiscrete,
                    scale: param.scale || 1.0
                }, [offscreen]);
                console.log(`[TableManager] ✅ initGraph отправлен для #${idx}`);
            } catch (e) {
                console.error(`[TableManager] ❌ Ошибка инициализации графика #${param.graphIdx}:`, e);
            }
        });

        console.log(`[TableManager] ✅ Все графики созданы`);

        // 🔥 Кэшируем DOM элементы
        this.params.forEach((param, idx) => {
            this.domCache[idx] = {
                hexEl: document.getElementById(param.hexId),
                physEl: document.getElementById(param.physId),
                indicator: document.getElementById(`indicator-${idx}`)
            };
        });

        tbody.addEventListener('click', function(e) {
            const row = e.target.closest('tr');
            if (!row) return;
            tbody.querySelectorAll('tr').forEach(r => r.classList.remove('selected'));
            row.classList.add('selected');

            const paramName = row.cells[0]?.textContent;
            const userInputField = document.getElementById('userInputField');
            if (userInputField && paramName) {
                userInputField.value = paramName + '=';
                userInputField.focus();
                userInputField.setSelectionRange(userInputField.value.length, userInputField.value.length);
            }
        });

        this.initColumnResize();
        this.initContextMenu();
        this.initVisibilityObserver();
    },

    initVisibilityObserver() {
        const tbody = document.getElementById('paramTableBody');
        if (!tbody) {
            console.error('[TableManager] ❌ paramTableBody не найден');
            return;
        }

        const scrollContainer = tbody.parentElement.parentElement;

        this.visibilityObserver = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                const idx = parseInt(entry.target.dataset.paramIndex);
                if (isNaN(idx)) return;

                if (entry.isIntersecting) {
                    this.visibleGraphIds.add(idx);
                } else {
                    this.visibleGraphIds.delete(idx);
                }
            });

            if (window.scopeWorker) {
                window.scopeWorker.postMessage({
                    type: 'updateVisibleGraphs',
                    visibleIds: Array.from(this.visibleGraphIds)
                });
            }
        }, {
            root: scrollContainer,
            threshold: 0.1
        });

        this.params.forEach((param, idx) => {
            const row = document.getElementById('row-' + idx);
            if (row) {
                row.dataset.paramIndex = idx;
                this.visibilityObserver.observe(row);
            }
        });

        console.log(`[TableManager] ✅ Visibility observer инициализирован, наблюдаем за ${this.params.length} строками`);
    },

    updateRow(index, hexValue, physicalValue) {
        const param = this.params[index];
        const cached = this.domCache[index];

        if (!cached) return;

        if (param && param.isDiscrete) {
            if (cached.indicator) {
                const val = hexValue & 1;
                cached.indicator.textContent = val ? '1' : '0';
                cached.indicator.className = 'discrete-indicator ' + (val ? 'discrete-on' : 'discrete-off');
            }
        } else {
            if (cached.hexEl) cached.hexEl.textContent = 'x' + hexValue.toString(16).toUpperCase().padStart(4, '0');
            if (cached.physEl) cached.physEl.textContent = physicalValue.toFixed(2);
        }
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
        // 🔥 Throttling: не чаще раза в 200мс при ресайзе
        if (this._resizeThrottled) {
            clearTimeout(this._resizeTimeout);
            this._resizeTimeout = setTimeout(() => {
                this._resizeThrottled = false;
                this._doUpdateAllCanvasSizes(scopeWorker);
            }, 200);
            return;
        }

        this._resizeThrottled = true;
        this._doUpdateAllCanvasSizes(scopeWorker);

        this._resizeTimeout = setTimeout(() => {
            this._resizeThrottled = false;
        }, 200);
    },

    _doUpdateAllCanvasSizes(scopeWorker) {
        const rows = document.querySelectorAll('#paramTableBody tr');
        const updates = [];

        rows.forEach((row, idx) => {
            const graphCell = row.querySelector('.graph-cell');
            if (graphCell) {
                const rect = graphCell.getBoundingClientRect();
                updates.push({
                    id: idx,
                    width: Math.round(rect.width),
                    height: Math.round(rect.height)
                });
            }
        });

        // 🔥 Отправляем ОДНО сообщение со всеми размерами
        scopeWorker.postMessage({
            type: 'updateAllSizes',
            updates: updates
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

        popup.style.display = 'block';
        popup.style.left = '0px';
        popup.style.top = '0px';

        const popupRect = popup.getBoundingClientRect();
        const popupWidth = popupRect.width;
        const popupHeight = popupRect.height;

        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;

        let finalX = x;
        let finalY = y;

        if (x + popupWidth > viewportWidth) {
            finalX = viewportWidth - popupWidth - 10;
        }

        if (y + popupHeight > viewportHeight) {
            finalY = viewportHeight - popupHeight - 10;
        }

        if (finalX < 10) {
            finalX = 10;
        }

        if (finalY < 10) {
            finalY = 10;
        }

        popup.style.left = `${finalX}px`;
        popup.style.top = `${finalY}px`;
        popup.dataset.paramIndex = index;
    },

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