package com.pockethost.desktop.process

import com.pockethost.desktop.util.AppLogger
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap

data class ProcessInfo(
    val pid: Long,
    val serverId: String,
    val process: Process,
    val startTime: Long,
    val outputStream: BufferedWriter,
    val inputStream: BufferedReader
)

data class ProcessStats(
    val cpuPercent: Float,
    val memoryBytes: Long,
    val threadCount: Int
)

object ProcessManager {
    private val processes = ConcurrentHashMap<String, ProcessInfo>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startProcess(
        serverId: String,
        command: List<String>,
        workingDir: File,
        environment: Map<String, String> = emptyMap(),
        onOutput: (String) -> Unit = {},
        onExit: (Int) -> Unit = {}
    ): ProcessInfo? {
        return try {
            val processBuilder = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true)

            val env = processBuilder.environment()
            environment.forEach { (k, v) -> env[k] = v }

            workingDir.mkdirs()
            AppLogger.info("Starting process $serverId: ${command.joinToString(" ")} in $workingDir")
            val process = processBuilder.start()
            val pid = process.pid()
            AppLogger.info("Process $serverId PID=$pid started")

            val info = ProcessInfo(
                pid = pid,
                serverId = serverId,
                process = process,
                startTime = System.currentTimeMillis(),
                outputStream = BufferedWriter(OutputStreamWriter(process.outputStream)),
                inputStream = BufferedReader(InputStreamReader(process.inputStream))
            )

            processes[serverId] = info

            scope.launch {
                try {
                    var line: String?
                    while (info.inputStream.readLine().also { line = it } != null) {
                        onOutput(line!!)
                    }
                } catch (e: Exception) {
                    // process ended
                } finally {
                    try {
                        val exitCode = process.waitFor()
                        processes.remove(serverId)
                        onExit(exitCode)
                    } catch (e: Exception) {
                        processes.remove(serverId)
                        onExit(-1)
                    }
                }
            }

            info
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopProcess(serverId: String, graceful: Boolean = true): Boolean {
        val info = processes[serverId] ?: return false
        return try {
            if (graceful) {
                try {
                    writeInput(serverId, "stop\n")
                } catch (_: Exception) {}
                // Wait 10s then force kill
                scope.launch {
                    delay(10000)
                    if (isProcessAlive(serverId)) {
                        try { info.process.destroyForcibly() } catch (_: Exception) {}
                    }
                }
                // Also schedule normal destroy after 1s if still alive and no graceful stop?
                // Try graceful destroy after 2s fallback
                scope.launch {
                    delay(2000)
                    // if still alive but not exited via input, try destroy()
                    if (isProcessAlive(serverId)) {
                        // Check if process output still active, try destroy
                    }
                }
            } else {
                info.process.destroyForcibly()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun writeInput(serverId: String, data: String): Boolean {
        val info = processes[serverId] ?: return false
        return try {
            info.outputStream.write(data)
            info.outputStream.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isProcessAlive(serverId: String): Boolean {
        return processes[serverId]?.process?.isAlive ?: false
    }

    fun getProcessInfo(serverId: String): ProcessInfo? = processes[serverId]

    fun getAllProcesses(): Map<String, ProcessInfo> = processes.toMap()

    fun getProcessStats(serverId: String): ProcessStats? {
        val info = processes[serverId] ?: return null
        return getWindowsProcessStats(info.pid)
    }

    private fun getWindowsProcessStats(pid: Long): ProcessStats? {
        return try {
            // Get threads count via (Get-Process -Id pid).Threads.Count, also CPU/WorkingSet
            val process = ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "\$p=Get-Process -Id $pid; \$threads=\$p.Threads.Count; \$obj=[PSCustomObject]@{CPU=\$p.CPU;WorkingSet64=\$p.WorkingSet64;Threads=\$threads}; \$obj | ConvertTo-Json -Compress"
            ).start()
            val output = process.inputStream.bufferedReader().readText()
            process.errorStream.bufferedReader().readText() // drain
            process.waitFor()
            if (output.isBlank()) return null
            val cpuRegex = Regex("\"CPU\"\\s*:\\s*(null|[0-9.]+)")
            val memRegex = Regex("\"WorkingSet64\"\\s*:\\s*(\\d+)")
            val thrRegex = Regex("\"Threads\"\\s*:\\s*(\\d+)")
            val cpuStr = cpuRegex.find(output)?.groupValues?.get(1)
            val cpu = if (cpuStr == null || cpuStr == "null") 0f else cpuStr.toFloatOrNull() ?: 0f
            val mem = memRegex.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val thr = thrRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            ProcessStats(cpuPercent = cpu, memoryBytes = mem, threadCount = thr)
        } catch (e: Exception) {
            // Fallback via wmic if powershell fails
            try {
                val p = ProcessBuilder("wmic", "process", "where", "processId=$pid", "get", "ThreadCount", "/format:list").start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                val thr = Regex("ThreadCount=(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                ProcessStats(0f, 0L, thr)
            } catch (_: Exception) { null }
        }
    }
}
