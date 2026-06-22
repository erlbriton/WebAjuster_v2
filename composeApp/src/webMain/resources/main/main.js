// main.js - Главный поток (Main Thread)

import { state } from './app_state.js';
import { handleSerialWorkerMessage } from './serial_handler.js';
import { handleScopeWorkerMessage } from './scope_handler.js';
import { createOscilloscopeTable, updateGraphCanvasSizes } from './oscilloscope_ui.js';
import { analyzeRegisters, buildChunks } from './register_analyzer.js';

// Все переменные состояния теперь находятся в state (app_state.js)
// Глобальными остаются только функции, которые вызывает Kotlin

window.initApplication = async function() {
    console.log('[Main] 🚀 Инициализация приложения...');

    try {
        state.messageChannel = new MessageChannel();
        console.log('[Main] ✅ MessageChannel создан');

        state.serialWorker = new Worker('../workers/serial_worker.js');
        state.serialWorker.onmessage = handleSerialWorkerMessage;
        state.serialWorker.onerror = handleError;
        console.log('[Main] ✅ Serial Worker создан');

        state.scopeWorker = new Worker('../workers/scope_worker.js');
        state.scopeWorker.onmessage = handleScopeWorkerMessage;
        state.scopeWorker.onerror = handleError;
        console.log('[Main] ✅ Scope Worker создан');

        state.serialWorker.postMessage({
            type: 'setScopePort',
            port: state.messageChannel.port1
        }, [state.messageChannel.port1]);

        state.scopeWorker.postMessage({
            type: 'setSerialPort',
            port: state.messageChannel.port2
        }, [state.messageChannel.port2]);

        console.log('[Main] ✅ Порты переданы воркерам');

        createOscilloscopeOutsideCompose();

       // Убрали принудительную инициализацию со старым конфигом,
               // теперь будем ждать реальные данные из Kotlin
              state.serialWorker.postMessage({
                          type: 'init',
                          config: state.config
                      });

                      state.scopeWorker.postMessage({
                          type: 'init',
                          config: {
                              paramsCount: state.config.paramsCount,
                              maxCapacity: 1000
                          }
                      });

                      console.log('[Main] ✅ Воркеры инициализированы базовым конфигом');

        state.isInitialized = true;
        console.log('[Main] ✅ Приложение инициализировано');

    } catch (error) {
        console.error('[Main] ❌ Ошибка инициализации:', error.message);
        alert('Ошибка инициализации: ' + error.message);
    }
};

function createOscilloscopeOutsideCompose() {
    state.oscWrapper = document.createElement('div');
    state.oscWrapper.id = 'osc-wrapper';
    state.oscWrapper.style.cssText = `
        position: fixed !important;
        top: 0 !important;
        left: 0 !important;
        width: 50vw !important;
        height: 100vh !important;
        background: #1a1a1a !important;
        z-index: 2147483647 !important;
        display: none;
        box-shadow: 5px 0 20px rgba(0,0,0,0.5);
        pointer-events: auto;
        overflow: hidden;
    `;

    const oscTable = document.getElementById('oscTable');
    if (oscTable) {
        state.oscWrapper.appendChild(oscTable);
        oscTable.style.display = 'block';
        oscTable.style.width = '100%';
        oscTable.style.height = '100%';
        console.log('[Main] ✅ Таблица осциллографа перемещена внутрь wrapper');
    }

    const inputPanel = document.getElementById('paramInputPanel');
    if (inputPanel) {
        state.oscWrapper.appendChild(inputPanel);
        inputPanel.style.position = 'absolute';
        inputPanel.style.bottom = '0';
        inputPanel.style.left = '0';
        inputPanel.style.width = '100%';
        inputPanel.style.zIndex = '10';
        console.log('[Main] ✅ Панель ввода перемещена внутрь осциллографа');
    }

    document.body.appendChild(state.oscWrapper);
    console.log('[Main] ✅ Wrapper осциллографа создан');
}

function handleError(error) {
    console.error('[Main] ❌ Ошибка воркера:', error.message);
}

window.startOscilloscope = function() {
    console.log('[Main] 🛠️ Генерация карты регистров...');

    // Анализируем параметры и строим чанки
    const analysis = analyzeRegisters();
    const chunks = buildChunks(analysis.registers);

    console.log('[Main] ✅ Карта чанков готова:', chunks);

    // Передаем чанки в воркер перед запуском
    state.serialWorker.postMessage({
        type: 'initChunks',
        chunks: chunks
    });

    // Передаем карту параметров в scope_worker для отображения
    state.scopeWorker.postMessage({
        type: 'initParams',
        params: analysis.paramMapping
    });

   window.startOscilloscope();
};

window.stopOscilloscope = function() {
    state.serialWorker.postMessage({ type: 'stop' });
    state.scopeWorker.postMessage({ type: 'stop' });
};

window.toggleOscilloscopeVisibility = async function(isVisible) {
    console.log('[Main]  toggleOscilloscopeVisibility вызвана с isVisible =', isVisible);

    if (!state.oscWrapper) {
        console.error('[Main] ❌ Осциллограф не создан');
        return;
    }

    const inputPanel = document.getElementById('paramInputPanel');

    if (isVisible) {
        console.log('[Main] 🔍 Открываем осциллограф...');

        if (inputPanel && inputPanel.parentNode !== state.oscWrapper) {
            state.oscWrapper.appendChild(inputPanel);
            inputPanel.style.position = 'absolute';
            inputPanel.style.bottom = '0';
            inputPanel.style.left = '0';
            inputPanel.style.width = '100%';
            inputPanel.style.zIndex = '10';
            inputPanel.style.background = '#1a1a1a';
            inputPanel.style.borderTop = '1px solid #444';
            inputPanel.style.borderLeft = 'none';
            inputPanel.style.borderRight = 'none';
        }

        if (inputPanel) {
            inputPanel.classList.remove('hidden');
            inputPanel.style.display = 'flex';
        }

        state.oscTableVisible = true;
        createOscilloscopeTable();

        state.oscWrapper.style.display = 'block';
        state.oscWrapper.style.visibility = 'visible';
        state.oscWrapper.style.opacity = '1';

        setTimeout(() => {
            updateGraphCanvasSizes();
        }, 100);

       // В момент открытия берем данные, которые уже есть в памяти (window.ramParameters)
               // и отправляем их в воркеры
               if (window.ramParameters) {
                   const analysis = analyzeRegisters();
                   const chunks = buildChunks(analysis.registers);

                   console.log('[Main] 🚀 Отправка параметров в воркеры при открытии...');
                   state.serialWorker.postMessage({ type: 'initChunks', chunks: chunks });
                   state.scopeWorker.postMessage({ type: 'initParams', params: analysis.paramMapping });
               }

               state.serialWorker.postMessage({ type: 'start' });
               state.scopeWorker.postMessage({ type: 'start' });

               console.log('[Main] ✅ Осциллограф открыт и запущен');

    } else {
        console.log('[Main] 👁️ Закрываем осциллограф...');

        state.serialWorker.postMessage({ type: 'stop' });
        state.scopeWorker.postMessage({ type: 'stop' });

        state.oscWrapper.style.display = 'none';
        state.oscTableVisible = false;

        if (inputPanel) {
            inputPanel.classList.add('d-none', 'hidden'); // Улучшено скрытие
        }

        console.log('[Main] ✅ Осциллограф закрыт');
    }
};

window.addEventListener('load', function() {
    console.log('[Main] Страница загружена');
    window.initApplication();
});

let resizeTimeout = null;

window.addEventListener('resize', function() {
    if (!state.oscWrapper || state.oscWrapper.style.display === 'none') return;

    if (resizeTimeout) clearTimeout(resizeTimeout);

    resizeTimeout = setTimeout(() => {
        console.log('[Main] 📐 Resize finished');
        updateGraphCanvasSizes();
    }, 300);
});

window.generateRegisterMap = function() {
    if (!window.ramParameters || window.ramParameters.length === 0) {
        console.error('[Main] Ошибка: ramParameters пуст!');
        return [];
    }

    console.log('[Main] Запуск анализа параметров...');

    const map = window.ramParameters.map((p, idx) => {
        const regMatch = p.register.match(/r([0-9A-Fa-f]+)/);
        const addr = regMatch ? parseInt(regMatch[1], 16) : -1;

        return {
            id: idx,
            addr: addr,
            raw: p
        };
    });

    console.log('[Main] ✅ Карта параметров создана, элементов:', map.length);
    return map;
};

import { analyzeRegisters, buildChunks } from './register_analyzer.js';

window.receiveParametersFromKotlin = function(jsonString) {
    console.log('[Main] 📥 Получены данные из Kotlin, парсинг...');
    const params = JSON.parse(jsonString);
    window.ramParameters = params;

    const analysis = analyzeRegisters();
    const chunks = buildChunks(analysis.registers);

    console.log('[Main] ✅ Карта чанков и параметров готова, отправляю в воркеры');

    state.serialWorker.postMessage({ type: 'initChunks', chunks: chunks });
    state.scopeWorker.postMessage({ type: 'initParams', params: analysis.paramMapping });
};

console.log('[Main] ✅ main.js загружен');