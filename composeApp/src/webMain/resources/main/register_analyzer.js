// register_analyzer.js - Анализ параметров для динамического чтения регистров

import { state } from './app_state.js';

/**
 * Анализирует ramParameters и возвращает структуру для чтения
 */
function analyzeRegisters() {
    const params = window.ramParameters;
    if (!params || params.length === 0) {
        console.error('[RegisterAnalyzer] ⚠️ ramParameters не загружены! Данные: ', params);
        return { registers: [], paramMapping: [] };
    }

    const registers = new Map(); // addr -> { addr }
    const paramMapping = [];

    window.ramParameters.forEach((param, paramIdx) => {
        const registerStr = param.register; // "r0000" или "r0000.5"
        const parts = registerStr.split('.');
        const regAddrHex = parts[0].replace('r', '');
        const regAddr = parseInt(regAddrHex, 16);

        const isDiscrete = parts.length > 1;
        const bitIndex = isDiscrete ? parseInt(parts[1], 16) : -1;

        // Определяем размер (TFloat занимает 2 регистра, остальные 1)
        let regSize = 1;
        if (param.type === 'TFloat') {
            regSize = 2;
        }

        // Добавляем регистр(ы) в список уникальных
        for (let i = 0; i < regSize; i++) {
            if (!registers.has(regAddr + i)) {
                registers.set(regAddr + i, { addr: regAddr + i });
            }
        }

        paramMapping[paramIdx] = {
            regAddr,
            isDiscrete,
            bitIndex,
            isFloat: param.type === 'TFloat',
            scale: parseFloat(param.scale) || 1.0
        };
    });

    // Сортируем регистры по адресу
    const registersArray = Array.from(registers.values())
        .sort((a, b) => a.addr - b.addr);

    console.log('[RegisterAnalyzer] ✅ Уникальных регистров:', registersArray.length);
    console.log('[RegisterAnalyzer] ✅ Параметров:', paramMapping.length);

    return {
        registers: registersArray,
        paramMapping: paramMapping
    };
}

/**
 * Группирует адреса регистров в блоки для Modbus RTU
 * (учитывает пропуски до 3 регистров)
 */
export function buildChunks(registersArray) {
    if (registersArray.length === 0) return [];

    const chunks = [];
    let currentChunk = {
        startAddr: registersArray[0].addr,
        count: 1
    };

    for (let i = 1; i < registersArray.length; i++) {
        const nextAddr = registersArray[i].addr;
        const lastAddr = currentChunk.startAddr + currentChunk.count - 1;

        // Если пропуск между последним и текущим <= 3 регистра
        if (nextAddr - lastAddr <= 4) {
            currentChunk.count = (nextAddr - lastAddr) + currentChunk.count;
        } else {
            chunks.push(currentChunk);
            currentChunk = { startAddr: nextAddr, count: 1 };
        }
    }
    chunks.push(currentChunk);

    console.log('[RegisterAnalyzer] ✅ Сформировано чанков:', chunks.length);
    return chunks;
}

window.analyzeRegisters = analyzeRegisters;
window.buildChunks = buildChunks;

console.log('[RegisterAnalyzer] ✅ Функции теперь доступны в window');

console.log('[RegisterAnalyzer] ✅ Модуль загружен');