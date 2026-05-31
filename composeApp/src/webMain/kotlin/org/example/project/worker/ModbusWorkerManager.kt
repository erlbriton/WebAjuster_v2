package org.example.project.worker

object ModbusWorkerManager {
    private var lastValidValue: Double = 0.0

    fun init(onDataReceived: (Double) -> Unit) {
        executeWasmWorkerLaunch { rawValue ->
            // 1. Проверяем на валидность числа (чтобы не прилетел NaN или Infinity из JS)
            if (!rawValue.isNaN() && rawValue.isFinite()) {

                // 2. Защита от спама: передаем данные дальше, только если значение РЕАЛЬНО изменилось
                if (rawValue != lastValidValue) {
                    lastValidValue = rawValue
                    onDataReceived(rawValue)
                }

            } else {
                // Здесь в будущем можно выводить лог ошибки, если Modbus прислал мусор
                println("Предупреждение: Вокер прислал некорректное число (NaN/Infinity)")
            }
        }
    }
}

fun executeWasmWorkerLaunch(onData: (Double) -> Unit): Unit = js("""
    {
        const worker = new Worker('modbusWorker.js');
        worker.onmessage = function(event) {
            if (event.data && event.data.type === 'MODBUS_DATA') {
                // Приводим к числу на стороне JS перед передачей в Kotlin
                const num = Number(event.data.value);
                onData(num);
            }
        };
    }
""")