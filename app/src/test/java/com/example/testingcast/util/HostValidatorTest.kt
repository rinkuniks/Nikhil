package com.example.testingcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostValidatorTest {

    @Test
    fun `accepts valid ipv4 and hostnames`() {
        assertTrue(HostValidator.isValidHost("192.168.43.1"))
        assertTrue(HostValidator.isValidHost(" 10.0.0.5 "))
        assertTrue(HostValidator.isValidHost("living-room-tv.local"))
    }

    @Test
    fun `rejects malformed hosts`() {
        assertFalse(HostValidator.isValidHost(""))
        assertFalse(HostValidator.isValidHost("256.1.1.1"))
        assertFalse(HostValidator.isValidHost("192.168.1"))
        assertFalse(HostValidator.isValidHost("bad host"))
        assertFalse(HostValidator.isValidHost("-tv.local"))
    }

    @Test
    fun `parses ports within range only`() {
        assertEquals(8888, HostValidator.parsePort("8888"))
        assertNull(HostValidator.parsePort("0"))
        assertNull(HostValidator.parsePort("70000"))
        assertNull(HostValidator.parsePort("abc"))
    }
}
