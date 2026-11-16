package com.example.testingcast.network.protocol

import java.nio.ByteBuffer

data class StreamPacket(
    val type: PacketType,
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Long = 0L,
    val flags: Short = 0,
    val payload: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StreamPacket

        if (type != other.type) return false
        if (timestamp != other.timestamp) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (flags != other.flags) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + flags.toInt()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        fun createControl(command: String, payloadData: ByteArray = ByteArray(0), sequenceNumber: Long = 0L): StreamPacket {
            val commandBytes = command.toByteArray(Charsets.UTF_8)
            val fullPayload = ByteArray(4 + commandBytes.size + payloadData.size)

            val buffer = ByteBuffer.wrap(fullPayload)
            buffer.putInt(commandBytes.size)
            buffer.put(commandBytes)
            buffer.put(payloadData)

            return StreamPacket(
                type = PacketType.CONTROL,
                timestamp = System.currentTimeMillis(),
                sequenceNumber = sequenceNumber,
                flags = 0,
                payload = fullPayload
            )
        }

        fun parseControlCommand(packet: StreamPacket): Pair<String, ByteArray>? {
            if (packet.type != PacketType.CONTROL || packet.payload.size < 4) return null
            val buffer = ByteBuffer.wrap(packet.payload)
            val cmdLen = buffer.int
            if (cmdLen < 0 || cmdLen > packet.payload.size - 4) return null
            val cmdBytes = ByteArray(cmdLen)
            buffer.get(cmdBytes)
            val command = String(cmdBytes, Charsets.UTF_8)
            val remaining = ByteArray(buffer.remaining())
            buffer.get(remaining)
            return Pair(command, remaining)
        }
    }
}
