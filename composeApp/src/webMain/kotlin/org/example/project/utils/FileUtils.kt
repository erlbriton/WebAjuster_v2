package org.example.project.utils

import org.example.project.models.DeviceInfoIni

// Убедитесь, что пакет совпадает с остальными файлами

data class DeviceInfo(
    val id: String,
    val location: String
)

expect suspend fun pickDirectory(): List<DeviceInfoIni>?
expect suspend fun pickSingleFile(): DeviceInfoIni?