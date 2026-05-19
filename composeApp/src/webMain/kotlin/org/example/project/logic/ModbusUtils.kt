package org.example.project.logic

/**
 * Специальный конвертер для webMain (ViewModel),
 * изолированный от конфликтов имен с другими платформами.
 */
object WebModbusConverter {

    fun calculateCRC16(data: ByteArray): ByteArray {
        var crc = 0xFFFF
        val size = data.size
        for (i in 0 until size) {
            val b = data[i]
            crc = crc xor (b.toInt() and 0xFF)
            for (j in 0 until 8) {
                if ((crc and 0x0001) != 0) {
                    crc = (crc ushr 1) xor 0xA001
                } else {
                    crc = crc ushr 1
                }
            }
        }
        return byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc ushr 8) and 0xFF).toByte()
        )
    }

    /**
     * Добавляет CRC16 к массиву байт
     */
    fun appendCRC(packet: ByteArray): ByteArray {
        val crcBytes = calculateCRC16(packet)
        val result = ByteArray(packet.size + 2)
        for (i in packet.indices) {
            result[i] = packet[i]
        }
        result[packet.size] = crcBytes[0]
        result[packet.size + 1] = crcBytes[1]
        return result
    }
}