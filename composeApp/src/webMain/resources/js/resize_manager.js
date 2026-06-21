// Модуль для управления ресайзом столбцов таблицы

const ResizeManager = {
    init(scopeWorker) {
        const table = document.getElementById('paramTable');
        const thead = table.querySelector('thead');
        const ths = thead.querySelectorAll('th');

        // Добавляем ручки ресайза в каждый заголовок (кроме последнего)
        ths.forEach((th, idx) => {
            if (idx === ths.length - 1) return;
            const handle = document.createElement('div');
            handle.className = 'resize-handle';
            th.appendChild(handle);
        });

        let isResizing = false;
        let currentTh = null;
        let startX = 0;
        let startWidth = 0;

        thead.addEventListener('mousedown', (e) => {
            if (!e.target.classList.contains('resize-handle')) return;

            isResizing = true;
            currentTh = e.target.closest('th');
            startX = e.pageX;
            startWidth = currentTh.offsetWidth;

            table.classList.add('resizing');
            e.preventDefault();
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing || !currentTh) return;
            const delta = e.pageX - startX;
            const newWidth = Math.max(40, startWidth + delta);
            currentTh.style.width = newWidth + 'px';
        });

        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                currentTh = null;
                table.classList.remove('resizing');
                TableManager.updateAllCanvasSizes(scopeWorker);
            }
        });
    }
};