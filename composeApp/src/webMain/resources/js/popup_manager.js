// Модуль для управления контекстным меню настроек параметра

const PopupManager = {
    settings: {},
    currentIndex: null,

    init(scopeWorker) {
        const tbody = document.getElementById('paramTableBody');
        const popup = document.getElementById('paramSettingsPopup');
        const heightInput = document.getElementById('popupHeight');
        const maxInput = document.getElementById('popupMax');
        const applyBtn = document.getElementById('popupApply');

        // Открытие попапа по правому клику
        tbody.addEventListener('contextmenu', (e) => {
            const row = e.target.closest('tr');
            if (!row) return;
            e.preventDefault();

            this.currentIndex = Array.from(tbody.children).indexOf(row);
            const settings = this.settings[this.currentIndex] || { height: TableManager.DEFAULT_HEIGHT, maxVal: null };

            heightInput.value = settings.height;
            maxInput.value = settings.maxVal !== null ? settings.maxVal : '';

            popup.style.left = e.pageX + 'px';
            popup.style.top = e.pageY + 'px';
            popup.style.display = 'block';
        });

        // Кнопка Применить
        applyBtn.addEventListener('click', () => {
            if (this.currentIndex === null) return;

            const newHeight = parseInt(heightInput.value) || TableManager.DEFAULT_HEIGHT;
            const newMax = maxInput.value.trim();
            const maxVal = newMax === '' ? null : parseFloat(newMax);

            this.settings[this.currentIndex] = { height: newHeight, maxVal: maxVal };

            TableManager.setRowHeight(this.currentIndex, newHeight);

            const size = TableManager.getGraphCellSize(this.currentIndex);
            if (size) {
                scopeWorker.postMessage({
                    type: 'updateSettings',
                    id: this.currentIndex,
                    height: size.height,
                    width: size.width,
                    maxVal: maxVal
                });
            }

            popup.style.display = 'none';
        });

        // Закрытие при клике вне попапа
        document.addEventListener('click', (e) => {
            if (!e.target.closest('#paramSettingsPopup') && popup.style.display === 'block') {
                popup.style.display = 'none';
            }
        });
    }
};