@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.example.project.logic

import org.khronos.webgl.Uint8Array

@JsFun("""
    async () => {
        let port;
        let reader;
        let writer;

        // ── CRC16 Modbus ──────────────────────────────────────────────────
        function crc16modbus(buf) {
            let crc = 0xFFFF;
            for (let i = 0; i < buf.length; i++) {
                crc ^= buf[i];
                for (let j = 0; j < 8; j++) {
                    if (crc & 0x0001) { crc = (crc >> 1) ^ 0xA001; }
                    else { crc >>= 1; }
                }
            }
            return crc;
        }

        function buildPacket(bytes) {
            const crc = crc16modbus(bytes);
            const lo  = crc & 0xFF;
            const hi  = (crc >> 8) & 0xFF;
            const pkt = new Uint8Array(bytes.length + 2);
            pkt.set(bytes);
            pkt[bytes.length]     = lo;
            pkt[bytes.length + 1] = hi;
            console.log("📐 CRC для [" + 
                bytes.map(b => b.toString(16).padStart(2,'0').toUpperCase()).join(' ') +
                "] = " + lo.toString(16).padStart(2,'0').toUpperCase() + 
                " " + hi.toString(16).padStart(2,'0').toUpperCase());
            return pkt;
        }

        try {
            console.log("=== [CRC AUTO] 0x03 → 0x11 ===");
            
            port = await navigator.serial.requestPort();
            await port.open({ 
                baudRate: 115200,
                dataBits: 8,
                stopBits: 1,
                parity: "none",
                flowControl: "none" 
            });
            console.log("✅ Порт открыт");

            await new Promise(resolve => setTimeout(resolve, 500));
            await port.setSignals({ dataTerminalReady: true, requestToSend: true });
            await new Promise(resolve => setTimeout(resolve, 200));
            
            writer = port.writable.getWriter();
            reader = port.readable.getReader();

            // ── 1. Отправляем 0x03 ───────────────────────────────────────
            const req03 = buildPacket([0x01, 0x03, 0x00, 0x00, 0x00, 0x0A]);
            console.log("--> [0x03] Отправка: " +
                Array.from(req03).map(b => b.toString(16).padStart(2,'0').toUpperCase()).join(' '));
            await writer.write(req03);

            let buf03 = new Uint8Array(0);
            const t1 = setTimeout(() => { reader.cancel().catch(() => {}); }, 1000);
            try {
                while (true) {
                    const { value, done } = await reader.read();
                    if (value && value.length > 0) {
                        console.log("📦 [0x03] Получено: " +
                            Array.from(value).map(b => b.toString(16).padStart(2,'0').toUpperCase()).join(' '));
                        let nb = new Uint8Array(buf03.length + value.length);
                        nb.set(buf03); nb.set(value, buf03.length);
                        buf03 = nb;
                        if (buf03.length >= 25) {
                            console.log("✅ [0x03] Ответ получен (" + buf03.length + " байт)");
                            break;
                        }
                    }
                    if (done) break;
                }
            } finally { clearTimeout(t1); }

            await new Promise(resolve => setTimeout(resolve, 100));

            // ── 2. Отправляем 0x11 с автоматическим CRC ──────────────────
            const req11 = buildPacket([0x01, 0x11]);
            console.log("--> [0x11] Отправка: " +
                Array.from(req11).map(b => b.toString(16).padStart(2,'0').toUpperCase()).join(' '));
            await writer.write(req11);

            let buf11 = new Uint8Array(0);
            const t2 = setTimeout(() => {
                console.log("⏱️ [0x11] Таймаут");
                reader.cancel().catch(() => {});
            }, 1000);
            try {
                while (true) {
                    const { value, done } = await reader.read();
                    if (value && value.length > 0) {
                        console.log("📦 [0x11] Получено: " +
                            Array.from(value).map(b => b.toString(16).padStart(2,'0').toUpperCase()).join(' '));
                        let nb = new Uint8Array(buf11.length + value.length);
                        nb.set(buf11); nb.set(value, buf11.length);
                        buf11 = nb;
                        if (buf11.length >= 39) {
                            console.log("✅ [0x11] Полный ответ (" + buf11.length + " байт)");
                            break;
                        }
                    }
                    if (done) break;
                }
            } finally { clearTimeout(t2); }

            reader.releaseLock();
            writer.releaseLock();
            await port.close();
            console.log("🔒 Порт закрыт");

            if (buf11.length > 0) {
                const hex = Array.from(buf11)
                    .map(b => b.toString(16).padStart(2,'0').toUpperCase()).join(' ');
                console.log("🔥 [0x11] ОТВЕТ HEX: " + hex);
                let ascii = "";
                for (let i = 3; i < buf11.length - 2; i++) {
                    const c = buf11[i];
                    ascii += (c >= 32 && c <= 126) ? String.fromCharCode(c) : ".";
                }
                console.log("📝 [0x11] ASCII: " + ascii);
            } else {
                console.log("❌ [0x11] Ответ не получен. CRC в логе выше — проверь совпадает ли с 2C C0");
            }

        } catch (err) {
            console.error("💥 ОШИБКА: " + err.message);
            if (reader) { try { reader.releaseLock(); } catch(e) {} }
            if (writer) { try { writer.releaseLock(); } catch(e) {} }
            if (port)   { try { await port.close();   } catch(e) {} }
        }
    }
""")
external fun jsReadDeviceId(): kotlin.js.JsAny?

actual suspend fun findSerialPort(data: ByteArray) {}

actual suspend fun readDeviceIdentification() {
    jsReadDeviceId()
}
