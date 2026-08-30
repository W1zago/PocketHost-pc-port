package com.pockethost.common.model

import kotlin.test.*

class ServerTest {
    @Test
    fun testServerCreationDefaults() {
        val s = Server(name = "test", port = 25565, workingDirectory = "/tmp/test")
        assertEquals("test", s.name)
        assertEquals(25565, s.port)
        assertEquals(ServerStatus.STOPPED, s.status)
        assertEquals(ServerType.MINECRAFT, s.type)
        assertFalse(s.autoStart)
        assertNotNull(s.id)
    }

    @Test
    fun testServerConfigDefaults() {
        val c = ServerConfig()
        assertEquals(1024, c.maxMemory)
        assertEquals(512, c.minMemory)
        assertTrue(c.environment.isEmpty())
        assertNull(c.minecraftVersion)
    }

    @Test
    fun testMinecraftLoadersCount() {
        assertEquals(8, MinecraftLoader.values().size)
        assertTrue(MinecraftLoader.values().contains(MinecraftLoader.PAPER))
        assertTrue(MinecraftLoader.values().contains(MinecraftLoader.NEOFORGE))
    }

    @Test
    fun testServerStatusTransitions() {
        val s = Server(name = "s", port = 25565, workingDirectory = "/tmp", status = ServerStatus.STOPPED)
        assertEquals(ServerStatus.STOPPED, s.status)
        val running = s.copy(status = ServerStatus.RUNNING, pid = 1234)
        assertEquals(ServerStatus.RUNNING, running.status)
        assertEquals(1234, running.pid)
    }
}
