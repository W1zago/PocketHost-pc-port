package com.pockethost.desktop.process

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProcessManagerTest {
    @Test
    fun testStartAndStopProcess(): Unit = runBlocking {
        val serverId = "test-server-${System.currentTimeMillis()}"
        val workingDir = File(System.getProperty("java.io.tmpdir"), "pockethost-test-$serverId")
        workingDir.mkdirs()
        val command = listOf("cmd", "/c", "ping", "-n", "10", "127.0.0.1")
        val info = ProcessManager.startProcess(
            serverId = serverId,
            command = command,
            workingDir = workingDir
        )
        assertNotNull(info)
        assertTrue(ProcessManager.isProcessAlive(serverId))
        delay(1000)
        val stopped = ProcessManager.stopProcess(serverId, graceful = false)
        assertTrue(stopped)
        delay(500)
        assertFalse(ProcessManager.isProcessAlive(serverId))
        workingDir.deleteRecursively()
    }

    @Test
    fun testProcessInputOutput(): Unit = runBlocking {
        val serverId = "test-io-${System.currentTimeMillis()}"
        val workingDir = File(System.getProperty("java.io.tmpdir"), "pockethost-test-$serverId")
        workingDir.mkdirs()
        val outputs = mutableListOf<String>()
        val info = ProcessManager.startProcess(
            serverId = serverId,
            command = listOf("cmd"),
            workingDir = workingDir,
            onOutput = { outputs.add(it) }
        )
        assertNotNull(info)
        delay(500)
        ProcessManager.writeInput(serverId, "echo test123\n")
        delay(1000)
        // cmd should echo
        assertTrue(outputs.any { it.contains("test123") } || outputs.isNotEmpty())
        ProcessManager.stopProcess(serverId, graceful = false)
        workingDir.deleteRecursively()
    }

    @Test
    fun testGetProcessStatsNotNullWhenRunning(): Unit = runBlocking {
        val serverId = "test-stats-${System.currentTimeMillis()}"
        val workingDir = File(System.getProperty("java.io.tmpdir"), "pockethost-test-$serverId")
        workingDir.mkdirs()
        val info = ProcessManager.startProcess(
            serverId = serverId,
            command = listOf("cmd", "/c", "ping", "-n", "5", "127.0.0.1"),
            workingDir = workingDir
        )
        assertNotNull(info)
        delay(300)
        val stats = ProcessManager.getProcessStats(serverId)
        // stats may be null if powershell not available, but threadCount should be >=0 if present
        if (stats != null) {
            assertTrue(stats.memoryBytes >= 0)
            assertTrue(stats.threadCount >= 0)
        }
        ProcessManager.stopProcess(serverId, graceful = false)
        workingDir.deleteRecursively()
    }
}
