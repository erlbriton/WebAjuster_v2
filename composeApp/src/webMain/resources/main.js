let port, writer, reader;

window.connectToDevice = async function() {
    try {
        // 1. Запрашиваем и открываем порт
        port = await navigator.serial.requestPort();
        await port.open({ baudRate: 115200 });
        console.log('[Main] ✅ Порт открыт');

        // 2. Запускаем логику опроса (без старого Canvas, чтобы не было ошибок)
        startSerial();
    } catch (err) {
        console.error('[Main] ❌ Ошибка подключения:', err.message);
    }
};

async function startSerial() {
    console.log('[Main]  startSerial запущена');

    try {
        writer = port.writable.getWriter();
        reader = port.readable.getReader();
    } catch (e) {
        console.error('[Main] ❌ Не удалось получить writer/reader:', e.message);
        return;
    }

    let buf = [];

    // --- ЦИКЛ ОТПРАВКИ ЗАПРОСОВ (Modbus RTU) ---
    (async () => {
        console.log('[Main] 🔄 Запущен цикл отправки запросов...');
        while (port) {
            try {
                // Запрос: Read Holding Registers (03), Address 0x002D, Count 2
                const body = new Uint8Array([0x01, 0x03, 0x00, 0x2D, 0x00, 0x02]);
                let crc = 0xFFFF;
                for (let b of body) {
                    crc ^= b;
                    for (let i = 0; i < 8; i++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                }

                // Отправляем пакет с CRC
                const packet = new Uint8Array([...body, crc & 0xFF, (crc >> 8) & 0xFF]);
                await writer.write(packet);
            } catch (e) {
                console.warn('[Main] ⚠️ Ошибка отправки:', e.message);
                break;
            }
            // Пауза 50мс между запросами
            await new Promise(r => setTimeout(r, 50));
        }
        console.log('[Main] ️ Цикл отправки завершен');
    })();

    // --- ЦИКЛ ЧТЕНИЯ ОТВЕТОВ ---
    (async () => {
        console.log('[Main] 🔄 Запущен цикл чтения...');
        while (port) {
            try {
                const { value, done } = await reader.read();
                if (done) {
                    console.log('[Main] 🔚 Поток чтения завершен');
                    break;
                }
                if (!value) continue;

                // 🔥 Логирование сырых данных (первые 20 байт)
                const hexData = Array.from(value.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join(' ');
                console.log('[Main] 📦 Принято ' + value.length + ' байт: ' + hexData);

                buf.push(...value);

                // Парсинг буфера
                while (buf.length >= 9) {
                    // Проверяем заголовок: ID=01, Func=03, Len=04
                    if (buf[0] === 0x01 && buf[1] === 0x03 && buf[2] === 0x04) {
                        // Считаем CRC для первых 7 байт
                        let crc = 0xFFFF;
                        for (let i = 0; i < 7; i++) {
                            crc ^= buf[i];
                            for (let j = 0; j < 8; j++)
                                crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                        }

                        const incomingCrc = buf[7] | (buf[8] << 8);
                        const calcCrc = crc & 0xFFFF;

                        if (calcCrc === incomingCrc) {
                            // Пакет валидный! Извлекаем данные
                            const v1 = (buf[3] << 8) | buf[4];
                            const v2 = (buf[5] << 8) | buf[6];

                            console.log('[Main] ✅ CRC OK! v1=' + v1 + ', v2=' + v2);

                            // Обновляем левую панель
                            if (window.leftPanel && typeof window.leftPanel.updateFromModbus === 'function') {
                                window.leftPanel.updateFromModbus(0, v1, v1);
                                console.log('[Main] 📡 Данные отправлены в LeftPanel');
                            } else {
                                console.warn('[Main] ⚠️ LeftPanel.updateFromModbus не найден');
                            }

                            // Если есть старый Worker, отправляем и туда (опционально)
                            // if (typeof scopeWorker !== 'undefined') {
                            //    scopeWorker.postMessage({ type: 'data', v1, v2, t: performance.now() });
                            // }
                        } else {
                            console.warn('[Main] ❌ CRC mismatch: calc=' + calcCrc.toString(16) + ', got=' + incomingCrc.toString(16));
                        }

                        // Удаляем обработанный пакет из буфера
                        buf.splice(0, 9);
                    } else {
                        // Синхронизация: удаляем байт, если заголовок неверный
                        buf.shift();
                    }
                }
            } catch (e) {
                console.error('[Main] ❌ Ошибка чтения:', e.message);
                break;
            }
        }
        console.log('[Main] ⏹️ Цикл чтения завершен');
    })();
}

console.log('[Main] main.js LOADED OK');