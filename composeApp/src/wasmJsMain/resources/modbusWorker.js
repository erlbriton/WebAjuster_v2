// modbusWorker.js
// Этот файл работает в отдельном потоке (фоне)
// Имитируем Modbus опрос: 1 Гц, значение 0-100 (пилообразный сигнал)

let value = 0;

setInterval(() => {
    // Генерация пилы
    value = (value + 1) % 100;

    // Отправка данных в главный поток (UI)
    self.postMessage({
        type: 'MODBUS_DATA',
        value: value,
        timestamp: Date.now()
    });
}, 1000); // 1000 мс = 1 Гц