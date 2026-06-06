// Левая панель мониторинга параметров

class LeftPanel {
    constructor() {
        this.params = [];
        this.rowHeight = 25;
        this.history = {};
        console.log('[LeftPanel] Конструктор создан');
    }

    // Построение таблицы из JSON, полученного от Kotlin
    buildFromJSON(jsonStr) {
        try {
            const params = JSON.parse(jsonStr);
            console.log('[LeftPanel] Получено параметров из Kotlin: ' + params.length);

            if (params.length === 0) {
                console.warn('[LeftPanel] Список параметров пуст');
                return;
            }

            const table = document.getElementById('paramsTable');
            if (!table) {
                console.error('[LeftPanel] Элемент paramsTable не найден в DOM');
                return;
            }

            // Очищаем таблицу перед построением
            table.innerHTML = '';
            this.params = params;

            // Создаём строку для каждого параметра
            params.forEach((param, index) => {
                const row = this.createRow(param, index);
                table.appendChild(row);
            });

            console.log('[LeftPanel] Таблица построена, строк: ' + params.length);

        } catch (error) {
            console.error('[LeftPanel] Ошибка построения таблицы:', error);
            console.error(error.stack);
        }
    }

    // Создание одной строки таблицы
    createRow(param, index) {
        const row = document.createElement('div');
        row.id = 'param-row-' + index;
        row.style.cssText = [
            'display: grid',
            'grid-template-columns: 150px 100px 100px 60px 1fr',
            'border-bottom: 1px solid #eee',
            'align-items: center',
            'font-size: 12px',
            'font-family: monospace',
            'height: 60px'
            //'height: ' + this.rowHeight + 'px'
        ].join(';');

        // Колонка 1: Имя
        const nameCell = document.createElement('div');
        nameCell.textContent = param.name;
        nameCell.style.cssText = 'padding: 5px; cursor: pointer;';

        // Колонка 2: Hex
        const hexCell = document.createElement('div');
        hexCell.id = 'hex-' + index;
        hexCell.style.cssText = 'padding: 5px; color: #666;';

        // Колонка 3: Physical
        const physCell = document.createElement('div');
        physCell.id = 'phys-' + index;
        physCell.style.cssText = 'padding: 5px; font-weight: bold;';

        // Колонка 4: Unit
        const unitCell = document.createElement('div');
        unitCell.textContent = param.unit;
        unitCell.style.cssText = 'padding: 5px; color: #666;';

        // Колонка 5: Canvas для графика
        const graphCell = document.createElement('div');
        graphCell.style.cssText = 'position: relative; height: ' + this.rowHeight + 'px;';

        const canvas = document.createElement('canvas');
        canvas.id = 'canvas-' + index;
        canvas.width = 300;
        canvas.height = 60;
        //canvas.height = this.rowHeight;
        canvas.style.cssText = 'width: 100%; height: 100%;';
        canvas.style.background = param.isDiscrete ? '#f9f9f9' : '#fff';

        graphCell.appendChild(canvas);

        // Сборка строки
        row.appendChild(nameCell);
        row.appendChild(hexCell);
        row.appendChild(physCell);
        row.appendChild(unitCell);
        row.appendChild(graphCell);

        // Клик по имени для изменения высоты (только для аналоговых)
        if (!param.isDiscrete) {
            nameCell.addEventListener('click', () => {
                this.toggleRowHeight(index);
            });
        }

        return row;
    }

    // Переключение высоты строки: 25 -> 100 -> 200 -> 25
    toggleRowHeight(index) {
        const row = document.getElementById('param-row-' + index);
        const canvas = document.getElementById('canvas-' + index);

        if (!row || !canvas) return;

        const currentHeight = parseInt(row.style.height) || this.rowHeight;
        let newHeight;
        if (currentHeight < 100) newHeight = 100;
        else if (currentHeight < 200) newHeight = 200;
        else newHeight = this.rowHeight;

        row.style.height = newHeight + 'px';
        canvas.height = newHeight;

        console.log('[LeftPanel] Высота строки ' + index + ' изменена на ' + newHeight + 'px');
    }

    // Отрисовка мини-графика для параметра
    drawGraph(index) {
        const canvas = document.getElementById('canvas-' + index);
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        const history = this.history[index] || [];

        if (history.length < 2) return; // Нужно минимум 2 точки

        const width = canvas.width;
        const height = canvas.height;

        // Очищаем canvas
        ctx.clearRect(0, 0, width, height);
        ctx.fillStyle = '#fff';
        ctx.fillRect(0, 0, width, height);

        // Находим min и max для масштабирования
        const minVal = Math.min(...history);
        const maxVal = Math.max(...history);
        const range = maxVal - minVal || 1;

        // Рисуем график
        ctx.strokeStyle = '#4A90E2';
        ctx.lineWidth = 1.5;
        ctx.beginPath();

        const stepX = width / (history.length - 1);
        for (let i = 0; i < history.length; i++) {
            const x = i * stepX;
            // Нормализуем значение по высоте canvas (с отступом 5px)
            const y = height - ((history[i] - minVal) / range) * (height - 10) - 5;

            if (i === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        }
        ctx.stroke();

        console.log('[LeftPanel] График для параметра ' + index + ' отрисован, точек: ' + history.length);
    }

    // Обновление значений hex и physical для всех параметров
   // Обновление значений hex и physical для всех параметров
   updateAllValues(jsonStr) {
       try {
           const updates = JSON.parse(jsonStr);

           updates.forEach(update => {
               const hexCell = document.getElementById('hex-' + update.index);
               const physCell = document.getElementById('phys-' + update.index);

               if (hexCell) hexCell.textContent = update.hex;
               if (physCell) physCell.textContent = update.physical;

               // НОВОЕ: Сохраняем значение в историю
               if (!this.history[update.index]) {
                   this.history[update.index] = [];
               }
               this.history[update.index].push(update.value);

               // Ограничиваем историю последними 100 точками
               if (this.history[update.index].length > 100) {
                   this.history[update.index].shift();
               }

               // НОВОЕ: Перерисовываем график
               this.drawGraph(update.index);
           });

           console.log('[LeftPanel] Обновлено значений: ' + updates.length);
       } catch (error) {
           console.error('[LeftPanel] Ошибка обновления значений:', error);
       }
   }

   // Обновление данных от Modbus (вызывается из main.js)
   updateFromModbus(index, hexValue, physicalValue) {
       // Обновляем ячейки таблицы
       const hexCell = document.getElementById('hex-' + index);
       const physCell = document.getElementById('phys-' + index);

       if (hexCell) {
           hexCell.textContent = '0x' + hexValue.toString(16).toUpperCase().padStart(4, '0');
       }
       if (physCell) {
           physCell.textContent = physicalValue.toFixed(2);
       }

       // Сохраняем в историю для графика
       if (!this.history[index]) {
           this.history[index] = [];
       }
       this.history[index].push(physicalValue);

       // Ограничиваем историю 100 точками
       if (this.history[index].length > 100) {
           this.history[index].shift();
       }

       // Перерисовываем график
       this.drawGraph(index);

       console.log('[LeftPanel] Modbus: параметр ' + index + ' = ' + physicalValue);
   }

}

// Создаём глобальный экземпляр
window.leftPanel = new LeftPanel();

// Глобальная функция, которую Kotlin вызывает через @JsFun
window.buildLeftPanel = function(jsonStr) {
    console.log('=== buildLeftPanel ВЫЗВАНА! ===');
    console.log('Длина JSON: ' + jsonStr.length);
    console.log('Содержимое JSON: ' + jsonStr.substring(0, 200));

    try {
        window.leftPanel.buildFromJSON(jsonStr);
        console.log('=== buildLeftPanel успешно завершена ===');
    } catch (error) {
        console.error('=== ОШИБКА в buildLeftPanel ===', error);
        console.error(error.stack);
    }
};

// Глобальная функция для обновления значений
window.updateLeftPanelValues = function(jsonStr) {
    window.leftPanel.updateAllValues(jsonStr);
};


console.log('[LeftPanel] Module loaded, buildLeftPanel зарегистрирована');