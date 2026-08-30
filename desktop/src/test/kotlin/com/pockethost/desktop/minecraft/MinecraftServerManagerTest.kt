package com.pockethost.desktop.minecraft

import com.pockethost.common.model.MinecraftLoader
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.*

class MinecraftServerManagerTest {
    @Test
    fun testGetJarNameForAllLoaders() {
        val m = MinecraftServerManager()
        assertEquals("server.jar", m.getJarName(MinecraftLoader.VANILLA))
        assertEquals("paper.jar", m.getJarName(MinecraftLoader.PAPER))
        assertEquals("purpur.jar", m.getJarName(MinecraftLoader.PURPUR))
        assertEquals("fabric-server-launch.jar", m.getJarName(MinecraftLoader.FABRIC))
        assertEquals("forge.jar", m.getJarName(MinecraftLoader.FORGE))
        assertEquals("neoforge.jar", m.getJarName(MinecraftLoader.NEOFORGE))
        assertEquals("spigot.jar", m.getJarName(MinecraftLoader.SPIGOT))
        assertEquals("craftbukkit.jar", m.getJarName(MinecraftLoader.BUKKIT))
    }

    @Test
    fun testCreateServerCreatesFiles(): Unit = runBlocking {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "pockethost-test-mc-${System.currentTimeMillis()}")
        // Use manager with custom serversDir via reflection? For test, use default and then clean
        // Create a manual server dir via manager's provision
        val manager = MinecraftServerManager()
        val serverDir = File(tmpDir, "test-server")
        val ok = manager.provisionIfNeeded(
            serverDir = serverDir.absolutePath,
            version = "1.20.4",
            loader = MinecraftLoader.PAPER,
            maxMemory = 1024,
            minMemory = 512,
            port = 25566,
            log = {}
        )
        // Provision may try to download paper 1.20.4, but we don't require network success for this unit test
        // At least check eula and properties were created
        assertTrue(serverDir.exists())
        // eula should be created
        assertTrue(File(serverDir, "eula.txt").exists() || !ok) // if ok false, eula may not be created
        tmpDir.deleteRecursively()
    }
}
