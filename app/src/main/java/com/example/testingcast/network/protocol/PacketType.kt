package com.example.testingcast.network.protocol

enum class PacketType(val code: Byte) {
    VIDEO(0x01.toByte()),
    AUDIO(0x02.toByte()),
    CONTROL(0x03.toByte());

    companion object {
        fun fromCode(code: Byte): PacketType = entries.find { it.code == code } ?: CONTROL
    }
}
