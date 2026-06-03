package org.example.project.jsinterop

import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.js


// ============================================================================
// 🔥 ТОП-ЛЕВЕЛ JS ФУНКЦИИ (единственное место, где можно использовать js())
// ============================================================================

@JsName("startModbusConnection")
fun startModbusConnection(): Unit = js("window.connectToDevice()")
@JsName("testJsBridge")
fun testJsBridge(): Unit = js("console.log('✅ Kotlin вызвал JS! SerialConnection:', typeof SerialConnection)")

@JsName("checkJsClasses")
fun checkJsClasses(): Unit = js("console.log('🔍 JS классы:', {Serial: typeof SerialConnection, Modbus: typeof ModbusParser, Ring: typeof RingBuffer, Oscillo: typeof oscilloInit})")

// 🔥 Осциллограф через JS
@JsName("jsOscilloCreate")
fun jsOscilloCreate(canvasId: String, containerId: String): Unit = js("window.oscilloCreate(canvasId, containerId)")

@JsName("jsOscilloInit")
fun jsOscilloInit(code: String, canvasId: String, minVal: Double, maxVal: Double): Unit = js("window.oscilloInit(code, canvasId, minVal, maxVal)")

@JsName("jsOscilloPush")
fun jsOscilloPush(code: String, value: Double, minVal: Double, maxVal: Double): Unit = js("window.oscilloPush(code, value, minVal, maxVal)")

// 🔥 Чтение значения из JS (упрощённо — синхронно)
@JsName("getModbusValue")
fun getModbusValue(): Double = js("window.__modbusLastValue !== undefined ? window.__modbusLastValue : 550")

@JsName("getModbusValue2")
fun getModbusValue2(): Double = js("window.__modbusLastValue2 !== undefined ? window.__modbusLastValue2 : 550")

// 🔥 Открытие порта через JS (возвращает Promise как JsAny)
@JsName("openPortJs")
fun openPortJs(baudRate: Int): JsAny = js("SerialConnection ? new SerialConnection().connect(baudRate) : Promise.reject('No SerialConnection')")

@JsName("jsModbusConnect")
fun jsModbusConnect(baudRate: Int): JsAny = js("window.jsModbus.connect(baudRate)")

@JsName("jsModbusDisconnect")
fun jsModbusDisconnect(): Unit = js("window.jsModbus.disconnect()")

@JsName("jsModbusIsConnected")
fun jsModbusIsConnected(): Boolean = js("window.jsModbus.isConnected()")

@JsName("jsModbusGetValue")
fun jsModbusGetValue(index: Int): Double = js("window.jsModbus.getLastValue(index) ?? 550")

@JsName("getTestValueFromJs")
fun getTestValueFromJs(): Double = js("window.__modbusLastValues?.[0] ?? 999")