package org.example.project

class JsPlatform: Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

actual suspend fun findSerialPort(data: ByteArray) {
    // В вебе здесь может быть логика через Web Serial API
    // или просто пустая заглушка, чтобы проект собрался
    println("Web Serial: findSerialPort не реализован для JS")
}

actual suspend fun readDeviceIdentification() {
    println("Web Serial: readDeviceIdentification не реализован для JS")
}