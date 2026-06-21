// app_state.js - Единое хранилище состояния приложения

export const state = {
    // Workers и каналы
    serialWorker: null,
    scopeWorker: null,
    messageChannel: null,

    // Флаги состояния
    isInitialized: false,
    isConnected: false,
    oscTableVisible: false,

    // UI и графика
    oscWrapper: null,
    graphContexts: [],
    lastGraphData: null,

    // Конфигурация
    config: {
        slaveAddress: 0x01,
        registerAddr: 0x002d,
        paramsCount: 78,
        baudRate: 115200,
        maxCapacity: 1000
    }
};

console.log('[AppState] ✅ Модуль состояния загружен');