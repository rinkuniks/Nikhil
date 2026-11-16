package com.example.testingcast.network

import com.example.testingcast.network.protocol.ControlCommands
import com.example.testingcast.network.protocol.PacketSerializer
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PacketSerializerTest {

    @Test
    fun testVideoPacketSerializationRoundTrip() {
        val payload = "TestVideoDataPayload".toByteArray(Charsets.UTF_8)
        val originalPacket = StreamPacket(
            type = PacketType.VIDEO,
            timestamp = 123456789L,
            sequenceNumber = 42L,
            flags = 1,
            payload = payload
        )

        val outputStream = ByteArrayOutputStream()
        PacketSerializer.writePacket(outputStream, originalPacket)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val deserializedPacket = PacketSerializer.readPacket(inputStream)

        assertNotNull(deserializedPacket)
        assertEquals(PacketType.VIDEO, deserializedPacket?.type)
        assertEquals(123456789L, deserializedPacket?.timestamp)
        assertEquals(42L, deserializedPacket?.sequenceNumber)
        assertEquals(1.toShort(), deserializedPacket?.flags)
        assertArrayEquals(payload, deserializedPacket?.payload)
    }

    @Test
    fun testControlPacketCreationAndParsing() {
        val payloadData = "CustomControlParam".toByteArray(Charsets.UTF_8)
        val controlPacket = StreamPacket.createControl(
            command = ControlCommands.START,
            payloadData = payloadData,
            sequenceNumber = 100L
        )

        val outputStream = ByteArrayOutputStream()
        PacketSerializer.writePacket(outputStream, controlPacket)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val deserializedPacket = PacketSerializer.readPacket(inputStream)

        assertNotNull(deserializedPacket)
        assertEquals(PacketType.CONTROL, deserializedPacket?.type)

        val parsedControl = StreamPacket.parseControlCommand(deserializedPacket!!)
        assertNotNull(parsedControl)
        assertEquals(ControlCommands.START, parsedControl?.first)
        assertArrayEquals(payloadData, parsedControl?.second)
    }

    @Test
    fun testInvalidMagicHeaderReturnsNull() {
        val invalidBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
        val inputStream = ByteArrayInputStream(invalidBytes)
        val packet = PacketSerializer.readPacket(inputStream)
        assertNull(packet)
    }

    @Test
    fun testEmptyStreamReturnsNull() {
        val inputStream = ByteArrayInputStream(ByteArray(0))
        val packet = PacketSerializer.readPacket(inputStream)
        assertNull(packet)
    }
}
