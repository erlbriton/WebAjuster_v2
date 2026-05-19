package org.example.project.logic

/**
 * Версия утилит Modbus без массивов (использует List<Int> и String).
 * Полностью решает проблему компиляции Cloneable в webMain.
 */
object WebModbusConverter { // Если объект внутри называется по-другому, например ModbusUtils, переименуй обратно

    /**
     * Принимает HEX-строку, считает CRC16 и возвращает готовую HEX-строку с CRC.
     * Пример: "010300000002" -> "010300000002C40B"
     */
    fun appendCRCToHex(hexPacket: String): String {
        val bytes = mutableListOf<Int>()
        var i = 0
        while (i < hexPacket.length) {
            val byteStr = hexPacket.substring(i, i + 2)
            bytes.add(byteStr.toInt(16) and 0xFF)
            i += 2
        }

        var crc = 0xFFFF
        for (b in bytes) {
            crc = crc xor b
            for (j in 0 until 8) {
                if ((crc and 0x0001) != 0) {
                    crc = (crc ushr 1) xor 0xA001
                } else {
                    crc = crc ushr 1
                }
            }
        }

        val low = crc and 0xFF
        val high = (crc ushr 8) and 0xFF

        val lowHex = low.toString(16).uppercase().padStart(2, '0')
        val highHex = high.toString(16).uppercase().padStart(2, '0')

        return hexPacket.uppercase() + lowHex + highHex
    }
}