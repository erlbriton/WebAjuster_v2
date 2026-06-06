const scopeWorker = new Worker('scope_worker.js');
const leftGraphWorker = new Worker('left_graph_worker.js');

let port, writer, reader;

window.connectToDevice = async function() {
    try {
        port = await navigator.serial.requestPort();
        await port.open({ baudRate: 115200 });
        console.log('[Main] ✅ Порт открыт');

        const canvas = document.getElementById('oscCanvas');
        if (canvas) {
            const offscreen = canvas.transferControlToOffscreen();
            scopeWorker.postMessage({ type: 'init', canvas: offscreen }, [offscreen]);
        }

        startSerial();
    } catch (err) {
        console.error('[Main] ❌', err.message);
    }
};

async function startSerial() {
    try {
        writer = port.writable.getWriter();
        reader = port.readable.getReader();
    } catch (e) {
        console.error('[Main] ❌ Ошибка получения writer/reader:', e.message);
        return;
    }

    let buf = [];

    (async () => {
        while (port) {
            try {
                const body = new Uint8Array([0x01, 0x03, 0x00, 0x2D, 0x00, 0x02]);
                let crc = 0xFFFF;
                for (let b of body) {
                    crc ^= b;
                    for (let i = 0; i < 8; i++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                }
                await writer.write(new Uint8Array([...body, crc & 0xFF, (crc >> 8) & 0xFF]));
            } catch (e) {
                console.warn('[Main] ⚠️ Ошибка отправки:', e.message);
                break;
            }
            await new Promise(r => setTimeout(r, 5));
        }
    })();

    (async () => {
        while (port) {
            try {
                const { value, done } = await reader.read();
                if (done) break;
                if (!value) continue;

                buf.push(...value);

                while (buf.length >= 9) {
                    if (buf[0] === 0x01 && buf[1] === 0x03 && buf[2] === 0x04) {
                        let crc = 0xFFFF;
                        for (let i = 0; i < 7; i++) {
                            crc ^= buf[i];
                            for (let j = 0; j < 8; j++)
                                crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
                        }

                        if (crc === (buf[7] | (buf[8] << 8))) {
                            const v1 = (buf[3] << 8) | buf[4];
                            const v2 = (buf[5] << 8) | buf[6];

                            scopeWorker.postMessage({ type: 'data', v1, v2, t: performance.now() });

                            if (window.leftPanel && typeof window.leftPanel.updateFromModbus === 'function') {
                                window.leftPanel.updateFromModbus(0, v1, v1);
                            }
                        }
                        buf.splice(0, 9);
                    } else {
                        buf.shift();
                    }
                }
            } catch (e) {
                console.error('[Main] ❌ Ошибка чтения:', e.message);
                break;
            }
        }
    })();
}

console.log('[Main] main.js LOADED OK');