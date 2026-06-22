// utils.js - Вспомогательные функции

export function calculateCRC16(data) {
    let crc = 0xFFFF;
    for (let i = 0; i < data.length; i++) {
        crc ^= data[i];
        for (let j = 0; j < 8; j++) {
            if (crc & 0x0001) {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    return crc;
}

export function showDeviceIdPopup(deviceId) {
    const popupId = 'device-id-popup-' + Date.now();
    const overlayId = 'device-id-overlay-' + Date.now();

    const popup = document.createElement('div');
    popup.id = popupId;
    popup.style.cssText = `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        background: white;
        padding: 15px 25px;
        border-radius: 8px;
        box-shadow: 0 10px 40px rgba(0,0,0,0.3);
        z-index: 100000;
        font-family: 'Courier New', monospace;
        text-align: center;
    `;

    popup.innerHTML = `
        <div style="font-size: 16px; padding: 10px; background: #f5f5f5; border-radius: 5px; word-break: break-all; margin-bottom: 10px;">
            ${deviceId}
        </div>
        <button onclick="document.getElementById('${popupId}').remove(); document.getElementById('${overlayId}').remove();"
                style="padding: 8px 20px; background: #2196f3; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 14px;">
            Закрыть
        </button>
    `;

    const overlay = document.createElement('div');
    overlay.id = overlayId;
    overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0,0,0,0.5);
        z-index: 99999;
    `;
    overlay.onclick = () => {
        popup.remove();
        overlay.remove();
    };

    document.body.appendChild(overlay);
    document.body.appendChild(popup);
}

console.log('[Utils] ✅ utils.js загружен');