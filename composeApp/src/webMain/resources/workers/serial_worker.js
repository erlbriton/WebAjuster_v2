let scopePort = null;
let serialReader = null;
let serialWriter = null;
let isConnected = false;
let isRunning = false;
let pollingChunks = [];
let config = { slaveAddress: 1 }; // Базовый адрес устройства по умолчанию

console.log('[SerialWorker] ✅ Worker загружен');

// Функция расчета CRC16 для Modbus RTU
function calculateModbusCRC(buffer) {
    let crc = 0xFFFF;
    for (let i = 0; i < buffer.length; i++) {
        crc ^= buffer[i];
        for (let j = 0; j < 8; j++) {
            if ((crc & 0x0001) !== 0) {
                crc >>= 1;
                crc ^= 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    // Возвращаем в Little Endian (младший байт, затем старший)
    return new Uint8Array([crc & 0xFF, (crc >> 8) & 0xFF]);
}

self.onmessage = async (e) => {
    const msg = e.data;
    console.log(`[SerialWorker] Получено сообщение: ${msg.type}`);

    switch (msg.type) {
        case 'init':
            if (msg.config) config = msg.config;
            break;

        case 'setScopePort':
            scopePort = e.ports[0];
            console.log('[SerialWorker] ✅ Порт для Scope Worker установлен');
            break;

        case 'initChunks':
            pollingChunks = msg.chunks || [];
            console.log(`[SerialWorker] ✅ Чанки инициализированы: ${pollingChunks.length}`);
            break;

        case 'setStreams':
            serialReader = msg.readable.getReader();
            serialWriter = msg.writable.getWriter();
            isConnected = true;
            console.log('[SerialWorker] ✅ Streams получены, reader/writer созданы');
            // Сообщаем главному потоку, что мы готовы
            self.postMessage({ type: 'connected' });
            break;

        case 'start':
            console.log(`[Oscilloscope] Попытка запуска. isRunning: ${isRunning}, isConnected: ${isConnected}`);
            if (!isConnected) {
                console.error('[SerialWorker] ❌ Невозможно запустить: нет потоков (streams).');
                return;
            }
            if (!isRunning) {
                isRunning = true;
                console.log('[SerialWorker] 📡 Опрос запущен');
                readLoop();
                writeLoop();
            }
            break;

        case 'stop':
            isRunning = false;
            console.log('[SerialWorker] 🛑 Опрос остановлен');
            break;
    }
};

// Цикл отправки запросов (Master)
// Цикл отправки запросов (Master)
async function writeLoop() {
    console.log('[SerialWorker] writeLoop started');

    while (isConnected && isRunning) {
        if (!pollingChunks || pollingChunks.length === 0) {
            console.warn('[SerialWorker] ⚠️ Список регистров пуст! Подставляем тестовый чанк (адрес 0, 10 регистров)');
            pollingChunks = [{ startAddr: 0, count: 10 }];
        }

        for (const chunk of pollingChunks) {
            if (!isRunning) break;

            try {
                // 1. Формируем тело команды (6 байт)
                const body = new Uint8Array([
                    config.slaveAddress || 1, // Адрес устройства
                    0x03,                     // Команда чтения регистров
                    (chunk.startAddr >> 8) & 0xFF,
                    chunk.startAddr & 0xFF,
                    (chunk.count >> 8) & 0xFF,
                    chunk.count & 0xFF
                ]);

                // 2. Считаем контрольную сумму (2 байта)
                const crcBytes = calculateModbusCRC(body);

                // 3. Склеиваем всё в финальный пакет (8 байт)
                const finalPacket = new Uint8Array(body.length + 2);
                finalPacket.set(body);
                finalPacket.set(crcBytes, body.length);

                console.log(`[SerialWorker] 📤 Отправка запроса (startAddr: ${chunk.startAddr}, count: ${chunk.count}), пакет:`, finalPacket);

                // 4. Отправляем в устройство
                await serialWriter.write(finalPacket);

                // Пауза между запросами разных блоков, чтобы устройство успело ответить
                await new Promise(res => setTimeout(res, 20));
            } catch (error) {
                console.error('[SerialWorker] writeLoop error:', error.message);
            }
        }
    }
}

// Цикл чтения ответов (Слушатель)
async function readLoop() {
    console.log('[SerialWorker] readLoop started');
    let buffer = new Uint8Array(0);

    try {
        while (isConnected && isRunning) {
            const { value, done } = await serialReader.read();
            
            if (value && value.length > 0) {
                console.log('[SerialWorker] 📥 Получено байт:', value.length, value);
                
                // Добавляем новые байты к общему буферу
                const newBuffer = new Uint8Array(buffer.length + value.length);
                newBuffer.set(buffer);
                newBuffer.set(value, buffer.length);
                buffer = newBuffer;

                // Разбираем буфер на валидные пакеты Modbus
                buffer = processBuffer(buffer);
            }

            if (done) {
                console.log('[SerialWorker] Reader закрыт');
                break;
            }
        }
    } catch (error) {
        console.error('[SerialWorker] readLoop error:', error.message);
    }
}

// Функция парсинга и проверки входящих данных
function processBuffer(buffer) {
    // Минимальная длина ответа: Адрес(1) + Команда(1) + Байт-счетчик(1) + Данные(минимум 2) + CRC(2) = 7 байт
    while (buffer.length >= 5) {
        // Проверяем начало пакета (совпадает ли адрес и команда)
        if (buffer[0] !== (config.slaveAddress || 1) || buffer[1] !== 0x03) {
            // Мусор или сбой синхронизации — сдвигаем буфер на 1 байт
            buffer = buffer.slice(1);
            continue;
        }

        const byteCount = buffer[2];
        const expectedLength = 3 + byteCount + 2; // Заголовок + Данные + CRC

        if (buffer.length >= expectedLength) {
            const packet = buffer.slice(0, expectedLength);
            
            // Проверяем контрольную сумму
            const body = packet.slice(0, expectedLength - 2);
            const crc = packet.slice(expectedLength - 2);
            const expectedCrc = calculateModbusCRC(body);

            if (crc[0] === expectedCrc[0] && crc[1] === expectedCrc[1]) {
                // Пакет валидный!
                const dataBytes = packet.slice(3, 3 + byteCount);
                console.log(`[SerialWorker] ✅ Валидный ответ получен, байт данных: ${dataBytes.length}`);
                
                // Отправляем чистые данные в ScopeWorker для отрисовки графиков
                if (scopePort) {
                    scopePort.postMessage({ type: 'data', buffer: dataBytes });
                }
                
                // Отрезаем обработанный пакет от буфера
                buffer = buffer.slice(expectedLength);
            } else {
                console.warn('[SerialWorker] ⚠️ Ошибка CRC в ответе устройства');
                buffer = buffer.slice(1); // Ищем следующий пакет
            }
        } else {
            // Пакет еще не докачался целиком, ждем следующей порции байт из readLoop
            break;
        }
    }
    return buffer;
}