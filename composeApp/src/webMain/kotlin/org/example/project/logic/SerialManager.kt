package org.example.project.logic

expect suspend fun findSerialPort(data: ByteArray)

expect suspend fun readDeviceIdentification()

expect suspend fun readRegister002D()