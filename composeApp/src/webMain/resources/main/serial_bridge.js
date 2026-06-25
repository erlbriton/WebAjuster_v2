window.activeWasmSerialPort = null;
window.lastDetectedPortName = "";
window.serialIdleTimeout = null;

// Главная функция трансивера
window.wasmSerialTransceive = async (requestBytes, expectedLen) => {////////////////////////\
    if (window.serialIdleTimeout) {
        clearTimeout(window.serialIdleTimeout);
        window.serialIdleTimeout = null;
    }

    let port = window.activeWasmSerialPort;
    let reader = null;
    let writer = null;

    try {
        // 1. ПРИНУДИТЕЛЬНЫЙ ЗАПРОС ПОРТА (без getPorts)
        if (!port) {
            console.log("[WebSerial] Запрашиваем выбор порта у пользователя...");
            port = await navigator.serial.requestPort();
            window.activeWasmSerialPort = port;
        }

        // 2. Попытка открытия
        if (!port.readable) {
            await port.open({
                baudRate: 115200,
                dataBits: 8,
                stopBits: 1,
                parity: "none",
                flowControl: "none"
            });
            await new Promise(r => setTimeout(r, 400));
            await port.setSignals({ dataTerminalReady: true, requestToSend: true });
            await new Promise(r => setTimeout(r, 200));
        }

        if (port.getInfo && !window.lastDetectedPortName) {
            let detectedComName = "USB-Device";
            const info = port.getInfo();
            if (info.usbVendorId === 0x1A86) detectedComName = "CH340";
            else if (info.usbVendorId === 0x10C4) detectedComName = "CP210x";
            else if (info.usbVendorId === 0x0403) detectedComName = "FTDI";
            window.lastDetectedPortName = detectedComName;
        }

        // 3. Обмен данными
        writer = port.writable.getWriter();
        reader = port.readable.getReader();
        await writer.write(requestBytes);

        let buf = new Uint8Array(0);
        let isTimeout = false;
        const timeout = setTimeout(() => { isTimeout = true; if (reader) reader.cancel().catch(() => {}); }, 1500);

        try {
            while (!isTimeout) {
                const { value, done } = await reader.read();
                if (value && value.length > 0) {
                    let nb = new Uint8Array(buf.length + value.length);
                    nb.set(buf);
                    nb.set(value, buf.length);
                    buf = nb;
                    if (buf.length >= expectedLen) break;
                }
                if (done) break;
            }
        } finally {
                clearTimeout(timeout);
            }

            if (reader) { reader.releaseLock(); reader = null; }
            if (writer) { writer.releaseLock(); writer = null; }

            return buf;
        } catch (err) {
        console.error("💥 Ошибка WebSerial: " + err.message);
        if (reader) try { reader.releaseLock(); } catch(e) {}
        if (writer) try { writer.releaseLock(); } catch(e) {}
        window.lastDetectedPortName = "";
        return null;
    }
};

// Функция ручного сброса
window.wasmForceChooseNewPort = async () => {
    if (window.serialIdleTimeout) clearTimeout(window.serialIdleTimeout);
    if (window.activeWasmSerialPort) {
        try { await window.activeWasmSerialPort.close(); } catch(e) {}
    }
    window.activeWasmSerialPort = null;
    window.lastDetectedPortName = "";
    try {
        const newPort = await navigator.serial.requestPort();
        window.activeWasmSerialPort = newPort;
        return true;
    } catch (e) {
        return false;
    }
};