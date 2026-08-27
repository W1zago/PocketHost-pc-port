package com.pockethost.common.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ServerType = ServerType.MINECRAFT,
    val port: Int,
    val workingDirectory: String,
    val status: ServerStatus = ServerStatus.STOPPED,
    val autoStart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastStarted: Long? = null,
    val config: ServerConfig = ServerConfig(),
    val pid: Long = -1
)

@Serializable
enum class ServerType {
    MINECRAFT,
    UBUNTU_SERVER
}

@Serializable
enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

@Serializable
data class ServerConfig(
    val environment: Map<String, String> = emptyMap(),
    val startCommand: String = "",
    val stopCommand: String = "",
    val maxMemory: Int = 1024,
    val minMemory: Int = 512,
    val javaArgs: List<String> = emptyList(),
    val customArgs: Map<String, String> = emptyMap(),
    val minecraftVersion: String? = null,
    val minecraftLoader: MinecraftLoader? = null,
    val loaderVersion: String? = null
)

@Serializable
enum class MinecraftLoader {
    VANILLA,
    FORGE,
    FABRIC,
    NEOFORGE,
    PAPER,
    SPIGOT,
    BUKKIT,
    PURPUR
}

@Serializable
data class ProcessInfo(
    val pid: Long,
    val serverId: String,
    val startTime: Long,
    val cpuUsage: Float = 0f,
    val memoryUsage: Long = 0L,
    val threadCount: Int = 0,
    val isAlive: Boolean = true
)

@Serializable
data class SystemStats(
    val cpuUsagePercent: Float = 0f,
    val totalMemory: Long = 0L,
    val usedMemory: Long = 0L,
    val freeMemory: Long = 0L,
    val networkSent: Long = 0L,
    val networkReceived: Long = 0L,
    val activePorts: List<Int> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
