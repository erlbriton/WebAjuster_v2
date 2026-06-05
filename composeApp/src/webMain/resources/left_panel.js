// Левая панель мониторинга параметров

class LeftPanel {
    constructor() {
        this.params = [];
        this.canvasWorkers = {};
        this.rowHeight = 25;
    }

    // Парсинг ini-файла
    parseIniFile(text) {
        const sections = {};
        let currentSection = null;

        const lines = text.split('\n');
        for (let line of lines) {
            line = line.trim();
            if (!line || line.startsWith(';')) continue;

            if (line.startsWith('[') && line.endsWith(']')) {
                currentSection = line.slice(1, -1);
                sections[currentSection] = {};
                continue;
            }

            if (currentSection && line.includes('=')) {
                const [key, ...valueParts] = line.split('=');
                sections[currentSection][key.trim()] = valueParts.join('=').trim();
            }
        }

        return sections;
    }

    // Парсинг параметров из секции [RAM]
    parseParams(ramSection) {
        const params = [];

        for (let [key, value] of Object.entries(ramSection)) {
            const parts = value.split('/');
            if (parts.length < 8) continue;

            const param = {
                id: key,
                name: parts[0],
                description: parts[1],
                type: parts[2],
                address: parts[3],
                register: parts[4],
                unit: parts[5],
                scale: parseFloat(parts[6].replace(',', '.')) || 1,
                size: parseInt(parts[7]) || 2,
                flags: parts[8] || '0',
                isDiscrete: parts[2] === 'TBit'
            };

            params.push(param);
        }

        return params;
    }

    // Создание строки таблицы
    createRow(param, index) {
        const row = document.createElement('div');
        row.id = `param-row-${index}`;
        row.style.cssText = `
            display: grid;
            grid-template-columns: 150px 100px 100px 60px 1fr;
            border-bottom: 1px solid #eee;
            align-items: center;
            font-size: 12px;
            font-family: monospace;
            height: ${this.rowHeight}px;
        `;

        // Колонка 1: Имя
        const nameCell = document.createElement('div');
        nameCell.textContent = param.name;
        nameCell.style.padding = '5px';
        nameCell.style.cursor = 'pointer';
        nameCell.title = param.description;

        // Колонка 2: Hex
        const hexCell = document.createElement('div');
        hexCell.id = `hex-${index}`;
        hexCell.style.padding = '5px';
        hexCell.style.color = '#666';

        // Колонка 3: Physical
        const physCell = document.createElement('div');
        physCell.id = `phys-${index}`;
        physCell.style.padding = '5px';
        physCell.style.fontWeight = 'bold';

        // Колонка 4: Unit
        const unitCell = document.createElement('div');
        unitCell.textContent = param.unit;
        unitCell.style.padding = '5px';
        unitCell.style.color = '#666';

        // Колонка 5: Canvas для графика
        const graphCell = document.createElement('div');
        graphCell.style.position = 'relative';
        graphCell.style.height = `${this.rowHeight}px`;

        const canvas = document.createElement('canvas');
        canvas.id = `canvas-${index}`;
        canvas.width = 300;
        canvas.height = this.rowHeight;
        canvas.style.width = '100%';
        canvas.style.height = '100%';
        canvas.style.background = param.isDiscrete ? '#f9f9f9' : '#fff';

        graphCell.appendChild(canvas);

        // Сборка строки
        row.appendChild(nameCell);
        row.appendChild(hexCell);
        row.appendChild(physCell);
        row.appendChild(unitCell);
        row.appendChild(graphCell);

        // Клик для изменения высоты (для аналоговых)
        if (!param.isDiscrete) {
            nameCell.addEventListener('click', () => {
                this.toggleRowHeight(index, param.isDiscrete);
            });
        }

        return row;
    }

    // Изменение высоты строки
    toggleRowHeight(index, isDiscrete) {
        if (isDiscrete) return;

        const row = document.getElementById(`param-row-${index}`);
        const canvas = document.getElementById(`canvas-${index}`);

        const currentHeight = parseInt(row.style.height) || this.rowHeight;
        const newHeight = currentHeight < 100 ? 100 : (currentHeight < 200 ? 200 : this.rowHeight);

        row.style.height = `${newHeight}px`;
        canvas.height = newHeight;
    }

    // Обновление значения параметра
    updateValue(index, hexValue, physicalValue) {
        const hexCell = document.getElementById(`hex-${index}`);
        const physCell = document.getElementById(`phys-${index}`);

        if (hexCell) hexCell.textContent = '0x' + hexValue.toString(16).toUpperCase().padStart(4, '0');
        if (physCell) physCell.textContent = physicalValue.toFixed(2);
    }

    // Инициализация панели
    async init(iniFilePath) {
        try {
            const response = await fetch(iniFilePath);
            const text = await response.text();
            const ini = this.parseIniFile(text);

            this.params = this.parseParams(ini['RAM'] || {});

            console.log(`Загружено ${this.params.length} параметров`);

            const table = document.getElementById('paramsTable');
            table.innerHTML = '';

            this.params.forEach((param, index) => {
                const row = this.createRow(param, index);
                table.appendChild(row);
            });

        } catch (error) {
            console.error('Ошибка инициализации левой панели:', error);
        }
    }
}

// Создаём глобальный экземпляр
window.leftPanel = new LeftPanel();
console.log('LeftPanel module loaded');