// Модуль для управления видимостью осциллографа

const OscilloscopeManager = {
    isVisible: false, // 🔥 Начинаем скрытым

    toggle(visible) {
        const table = document.querySelector('.param-table');
        if (!table) return;

        this.isVisible = visible;

        if (visible) {
            table.classList.remove('hidden');
            console.log('[OscilloscopeManager] 🔍 Осциллограф показан');
        } else {
            table.classList.add('hidden');
            console.log('[OscilloscopeManager] 👁️ Осциллограф скрыт');
        }
    }
};

// Глобальная функция для вызова из Kotlin
window.toggleOscilloscopeVisibility = function(isVisible) {
    OscilloscopeManager.toggle(isVisible);
};