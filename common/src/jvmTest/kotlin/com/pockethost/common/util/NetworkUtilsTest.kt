package com.pockethost.common.util

import kotlin.test.*

class NetworkUtilsTest {
    @Test
    fun testIsValidPort() {
        assertTrue(NetworkUtils.isValidPort(25565))
        assertTrue(NetworkUtils.isValidPort(1024))
        assertTrue(NetworkUtils.isValidPort(65535))
        assertFalse(NetworkUtils.isValidPort(80))
        assertFalse(NetworkUtils.isValidPort(1023))
        assertFalse(NetworkUtils.isValidPort(65536))
        assertFalse(NetworkUtils.isValidPort(-1))
    }

    @Test
    fun testGetAvailablePort() {
        val port = NetworkUtils.getAvailablePort(25565)
        assertTrue(port >= 25565)
        assertTrue(NetworkUtils.isValidPort(port))
        assertTrue(NetworkUtils.isPortAvailable(port))
    }

    @Test
    fun testPortAvailability() {
        // Find a free port, bind it, check availability
        val free = NetworkUtils.getAvailablePort(50000)
        assertTrue(NetworkUtils.isPortAvailable(free))
    }
}
