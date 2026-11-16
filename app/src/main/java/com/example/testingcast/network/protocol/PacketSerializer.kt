package com.example.testingcast.network.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object PacketSerializer {
    val MAGIC_HEADER = byteArrayOf(0x54, 0x43, 0x53, 0x54) // "TCST"
    const val VERSION: Byte = 0x01
    const val MAX_PAYLOAD_SIZE = 10 * 1024 * 1024 // 10 MB limit for safety

    fun writePacket(outputStream: OutputStream, packet: StreamPacket) {
        val dataOut = DataOutputStream(outputStream)
        dataOut.write(MAGIC_HEADER)
        dataOut.writeByte(VERSION.toInt())
        dataOut.writeByte(packet.type.code.toInt())
        dataOut.writeShort(packet.flags.toInt())
        dataOut.writeLong(packet.timestamp)
        dataOut.writeLong(packet.sequenceNumber)
        dataOut.writeInt(packet.payload.size)
        if (packet.payload.isNotEmpty()) {
            dataOut.write(packet.payload)
        }
        dataOut.flush()
    }

    fun readPacket(inputStream: InputStream): StreamPacket? {
        val dataIn = DataInputStream(inputStream)

        val magicBuffer = ByteArray(4)
        try {
            dataIn.readFully(magicBuffer)
        } catch (e: Exception) {
            return null
        }

        if (!magicBuffer.contentEquals(MAGIC_HEADER)) {
            return null
        }

        val version = dataIn.readByte()
        if (version != VERSION) {
            return null
        }

        val typeCode = dataIn.readByte()
        val flags = dataIn.readShort()
        val timestamp = dataIn.readLong()
        val sequenceNumber = dataIn.readLong()
        val payloadLength = dataIn.readInt()

        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            return null
        }

        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) {
            dataIn.readFully(payload)
        }

        return StreamPacket(
            type = PacketType.fromCode(typeCode),
            timestamp = timestamp,
            sequenceNumber = sequenceNumber,
            flags = flags,
            payload = payload
        )
    }
}
