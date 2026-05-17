package org.example.project.logic

object ModbusUtils {
    /**
     * Вычисляет CRC16 Modbus и возвращает массив из 2 байт:
     * [0] - Младший байт (Low Byte) -> передается первым
     * [1] - Старший байт (High Byte) -> передается вторым
     */
    fun calculateCRC16(data: ByteArray): ByteArray {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0 until 8) {
                if ((crc and 0x0001) != 0) {
                    crc = (crc ushr 1) xor 0xA001
                } else {
                    crc = crc ushr 1
                }
            }
        }
        return byteArrayOf(
            (crc and 0xFF).toByte(),        // Младший байт (Low)
            ((crc ushr 8) and 0xFF).toByte() // Старший байт (High)
        )
    }

    /**
     * Добавляет CRC16 к массиву байт
     */
    fun appendCRC(packet: ByteArray): ByteArray {
        return packet + calculateCRC16(packet)
    }
}