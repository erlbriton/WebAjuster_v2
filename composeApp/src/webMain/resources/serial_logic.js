window.runModbusTest = async function(dataArray, expectedBytes) {
    if (!navigator.serial) return;

    let port;
    try {
        port = await navigator.serial.requestPort();
    } catch (e) { return; }

    const requestData = new Uint8Array(dataArray.buffer, dataArray.byteOffset, dataArray.byteLength);
    let frames = 0;
    let fpsLastReport = performance.now();

    // Слушатель аппаратного отключения (выгрузка драйвера ОС)
    navigator.serial.addEventListener("disconnect", (event) => {
        if (event.target === port) {
            console.error("%c--- УСТРОЙСТВО ФИЗИЧЕСКИ ИЗВЛЕЧЕНО ---", "color: red;");
        }
    });

    while (true) {
        let reader = null;
        let writer = null;

        try {
            // Проверяем, не открыт ли порт (важно для восстановления после 6 сек)
            if (port.readable || port.writable) {
                await port.close().catch(() => {});
            }

            await port.open({ baudRate: 115200, bufferSize: 16384 }); // Максимальный буфер
            console.log("%c--- СЕССИЯ ВОССТАНОВЛЕНА ---", "color: #00ff00; font-weight: bold;");

            while (true) {
                reader = port.readable.getReader();
                writer = port.writable.getWriter();

                try {
                    // Используем короткий таймаут на запись, чтобы не виснуть, если USB-буфер забит
                    await writer.write(requestData);

                    let responseBuffer = new Uint8Array(0);
                    let startTime = performance.now();

                    // Жесткий контроль времени сбора пакета
                    while (responseBuffer.length < expectedBytes) {
                        if (performance.now() - startTime > 300) throw new Error("Link Timeout");

                        const { value, done } = await reader.read();
                        if (done) throw new Error("Port Closed");

                        if (value) {
                            let newBuf = new Uint8Array(responseBuffer.length + value.length);
                            newBuf.set(responseBuffer);
                            newBuf.set(value, responseBuffer.length);
                            responseBuffer = newBuf;
                        }
                    }

                    if (responseBuffer.length >= expectedBytes) {
                        frames++;
                        if (window.onModbusData) window.onModbusData(responseBuffer.slice(0, expectedBytes));
                    }

                    const now = performance.now();
                    if (now - fpsLastReport >= 1000) {
                        console.warn("REAL FPS: " + ((frames * 1000) / (now - fpsLastReport)).toFixed(2));
                        frames = 0;
                        fpsLastReport = now;
                    }

                } finally {
                    // Сброс замков после каждой итерации — критично для Web Serial
                    if (writer) writer.releaseLock();
                    if (reader) {
                        await reader.cancel().catch(() => {});
                        reader.releaseLock();
                    }
                }

                // Минимальная пауза, чтобы дать процессору «выдохнуть»
                await new Promise(r => setTimeout(r, 1));
            }
        } catch (err) {
            console.error("%cОШИБКА: " + err.message, "color: #ffaa00;");

            // Если мы попали в "мертвую зону" (6 секунд),
            // закрытие порта может длиться долго. Используем таймаут.
            try {
                await port.close().catch(() => {});
            } catch(e) {}

            // Агрессивное ожидание: чем дольше нет связи, тем реже пробуем,
            // чтобы не спамить драйвер запросами.
            await new Promise(r => setTimeout(r, 1500));
        }
    }
};

// Функция выбора файла (обновленная)
// Функция выбора файла (обновленная)
window.showFilePickerNative = async function(callback) {
    try {
        const [handle] = await window.showOpenFilePicker({
            types: [{
                description: 'Settings & Text',
                accept: {'text/plain': ['.ini', '.txt']}
            }],
            multiple: false
        });

        const file = await handle.getFile();
        const isTxt = file.name.toLowerCase().endsWith('.txt');

        const reader = new FileReader();
        reader.onload = () => {
            callback({
                name: file.name,
                content: reader.result,
                isAlreadyTxt: isTxt
            });
        };

        // КЛЮЧЕВОЙ МОМЕНТ ДЛЯ СОВМЕСТИМОСТИ:
        // .ini файлы читаем в Windows-1251 (чтобы не было кракозябр из винды)
        // .txt файлы читаем в UTF-8 (стандарт для Linux/Kubuntu и современного веба)
        const encoding = isTxt ? "utf-8" : "windows-1251";

        reader.readAsText(file, encoding);

    } catch (e) {
        console.log("Выбор файла отменен");
        callback(null);
    }
};

// Функция сохранения (обновленная — вызывает диалог выбора папки)
window.saveFileAsTxt = async function(originalName, content) {
    try {
        // Подготавливаем новое имя (заменяем .ini на .txt)
        const newName = originalName.replace(/\.[^/.]+$/, "") + ".txt";

        // Это вызовет системное окно "Сохранить как"
        const newHandle = await window.showSaveFilePicker({
            suggestedName: newName,
            types: [{
                description: 'Text Document',
                accept: {'text/plain': ['.txt']},
            }],
        });

        // Записываем данные в выбранный файл
        const writable = await newHandle.createWritable();
        await writable.write(content);
        await writable.close();

    } catch (e) {
        // Если пользователь нажал "Отмена" в окне сохранения
        console.log("Сохранение отменено пользователем");
    }
};