package com.pockethost.common.repository

import com.pockethost.common.database.DatabaseFactory
import com.pockethost.common.model.MinecraftLoader
import com.pockethost.common.model.Server
import com.pockethost.common.model.ServerConfig
import com.pockethost.common.model.ServerStatus
import com.pockethost.common.model.ServerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ServerRepository private constructor() {
    private val database = DatabaseFactory.getDatabase()
    private val queries = database.serversQueries
    private val serversCache = MutableStateFlow<List<Server>>(emptyList())
    private val logsCache = mutableMapOf<String, MutableStateFlow<List<String>>>()

    init {
        refreshCache()
    }

    private fun refreshCache() {
        val servers = queries.getAllServers().executeAsList().map { it.toServer() }
        serversCache.value = servers
    }

    fun getAllServers(): Flow<List<Server>> = serversCache

    fun getServer(id: String): Flow<Server?> = serversCache.map { list -> list.find { it.id == id } }

    suspend fun getServersByType(type: ServerType): List<Server> = withContext(Dispatchers.IO) {
        queries.getServersByType(type.name).executeAsList().map { it.toServer() }
    }

    suspend fun getRunningServers(): List<Server> = withContext(Dispatchers.IO) {
        queries.getRunningServers().executeAsList().map { it.toServer() }
    }

    suspend fun addServer(server: Server) = withContext(Dispatchers.IO) {
        queries.insertServer(
            id = server.id,
            name = server.name,
            type = server.type.name,
            port = server.port.toLong(),
            workingDirectory = server.workingDirectory,
            status = server.status.name,
            autoStart = if (server.autoStart) 1L else 0L,
            createdAt = server.createdAt,
            lastStarted = server.lastStarted,
            pid = server.pid,
            maxMemory = server.config.maxMemory.toLong(),
            minMemory = server.config.minMemory.toLong(),
            minecraftVersion = server.config.minecraftVersion,
            minecraftLoader = server.config.minecraftLoader?.name,
            loaderVersion = server.config.loaderVersion,
            environment = Json.encodeToString(server.config.environment),
            startCommand = server.config.startCommand,
            stopCommand = server.config.stopCommand
        )
        refreshCache()
    }

    suspend fun updateServer(server: Server) = withContext(Dispatchers.IO) {
        queries.updateServer(
            name = server.name,
            port = server.port.toLong(),
            status = server.status.name,
            autoStart = if (server.autoStart) 1L else 0L,
            lastStarted = server.lastStarted,
            pid = server.pid,
            maxMemory = server.config.maxMemory.toLong(),
            minMemory = server.config.minMemory.toLong(),
            id = server.id
        )
        refreshCache()
    }

    suspend fun updateServerStatus(serverId: String, status: ServerStatus) = withContext(Dispatchers.IO) {
        queries.updateServerStatus(status.name, serverId)
        refreshCache()
    }

    suspend fun updateServerPid(serverId: String, pid: Long) = withContext(Dispatchers.IO) {
        queries.updateServerPid(pid, serverId)
        refreshCache()
    }

    suspend fun deleteServer(serverId: String) = withContext(Dispatchers.IO) {
        queries.deleteServerLogs(serverId)
        queries.deleteServer(serverId)
        refreshCache()
        logsCache.remove(serverId)
    }

    fun getServerLogs(serverId: String, limit: Long = 1000): Flow<List<String>> {
        val flow = logsCache.getOrPut(serverId) { MutableStateFlow(emptyList()) }
        // initial load
        try {
            val logs = queries.getServerLogs(serverId, limit).executeAsList().map { it.message }
            flow.value = logs
        } catch (e: Exception) {
            // ignore
        }
        return flow
    }

    suspend fun appendLog(serverId: String, message: String, level: String = "INFO") = withContext(Dispatchers.IO) {
        queries.insertLog(
            serverId = serverId,
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message
        )
        val flow = logsCache.getOrPut(serverId) { MutableStateFlow(emptyList()) }
        val current = flow.value.toMutableList()
        current.add(message)
        // keep last 1000
        if (current.size > 1000) current.removeAt(0)
        flow.value = current
    }

    suspend fun cleanOldLogs(olderThanDays: Int = 7) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        queries.deleteOldLogs(cutoff)
    }

    private fun com.pockethost.database.Servers.toServer(): Server {
        return Server(
            id = id,
            name = name,
            type = ServerType.valueOf(type),
            port = port.toInt(),
            workingDirectory = workingDirectory,
            status = ServerStatus.valueOf(status),
            autoStart = autoStart == 1L,
            createdAt = createdAt,
            lastStarted = lastStarted,
            pid = pid,
            config = ServerConfig(
                maxMemory = maxMemory.toInt(),
                minMemory = minMemory.toInt(),
                minecraftVersion = minecraftVersion,
                minecraftLoader = minecraftLoader?.let { MinecraftLoader.valueOf(it) },
                loaderVersion = loaderVersion,
                environment = environment?.let {
                    try { Json.decodeFromString<Map<String, String>>(it) } catch (e: Exception) { emptyMap() }
                } ?: emptyMap(),
                startCommand = startCommand ?: "",
                stopCommand = stopCommand ?: ""
            )
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: ServerRepository? = null
        val instance: ServerRepository
            get() = INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServerRepository().also { INSTANCE = it }
            }
    }
}
