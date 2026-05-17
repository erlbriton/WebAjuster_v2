package org.example.project

class WasmPlatform : Platform {
    override val name: String = "Web (WebAssembly)"
}

actual fun getPlatform(): Platform = WasmPlatform()