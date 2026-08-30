package com.pockethost.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pockethost.common.model.Server
import com.pockethost.common.model.ServerStatus
import com.pockethost.common.repository.ServerRepository
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ServerListScreen(
    onServerSelected: (Server) -> Unit,
    onCreateServer: () -> Unit,
    selectedServerId: String? = null,
    onError: (String) -> Unit = {}
) {
    val repository = remember { ServerRepository.instance }
    val servers by repository.getAllServers().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    Row(Modifier.fillMaxSize()) {
        // Left list
        Surface(
            modifier = Modifier.width(380.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Servers", style = MaterialTheme.typography.titleLarge)
                        Text("${servers.size} total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onCreateServer) {
                        Text("+ New")
                    }
                }
                HorizontalDivider()
                if (servers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No servers yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Create your first Minecraft server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onCreateServer) { Text("Create Server") }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(servers) { server ->
                            ServerListItem(
                                server = server,
                                selected = server.id == selectedServerId,
                                onClick = { onServerSelected(server) },
                                onStart = {
                                    scope.launch {
                                        try {
                                            val err = startServer(server)
                                            if (err != null) onError(err)
                                        } catch (e: Exception) { e.printStackTrace(); onError(e.message ?: "Start failed") }
                                    }
                                },
                                onStop = {
                                    scope.launch {
                                        try {
                                            val err = stopServer(server)
                                            if (err != null) onError(err)
                                        } catch (e: Exception) { e.printStackTrace(); onError(e.message ?: "Stop failed") }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Right preview
        Box(Modifier.fillMaxSize().weight(1f)) {
            val selected = servers.find { it.id == selectedServerId }
            if (selected != null) {
                ServerPreview(selected, onStart = {
                    scope.launch {
                        val err = startServer(selected)
                        if (err != null) onError(err)
                    }
                }, onStop = {
                    scope.launch {
                        val err = stopServer(selected)
                        if (err != null) onError(err)
                    }
                }, onOpen = { onServerSelected(selected) })
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Select a server", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Choose a server from the list to view details", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ServerListItem(
    server: Server,
    selected: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(10.dp).background(
                    color = when (server.status) {
                        ServerStatus.RUNNING -> Color(0xFF4CAF50)
                        ServerStatus.STOPPED -> Color.Gray
                        ServerStatus.ERROR -> Color(0xFFF44336)
                        else -> Color(0xFFFFC107)
                    }, shape = CircleShape
                )
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${server.config.minecraftLoader?.name ?: server.type.name} ${server.config.minecraftVersion ?: ""} • Port ${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = server.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (server.status) {
                        ServerStatus.RUNNING -> Color(0xFF4CAF50)
                        ServerStatus.ERROR -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (server.status == ServerStatus.RUNNING) {
                IconButton(onClick = onStop) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
            } else if (server.status == ServerStatus.STOPPED || server.status == ServerStatus.ERROR) {
                IconButton(onClick = onStart) { Icon(Icons.Default.PlayArrow, contentDescription = "Start") }
            } else {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
fun ServerPreview(server: Server, onStart: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(server.name, style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(server.status)
            Spacer(Modifier.width(8.dp))
            Text("${server.config.minecraftLoader?.name ?: "MINECRAFT"} • Port ${server.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Server Information", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow("Directory", server.workingDirectory)
                InfoRow("Version", server.config.minecraftVersion ?: "Unknown")
                InfoRow("Loader", server.config.minecraftLoader?.name ?: "Unknown")
                InfoRow("Memory", "${server.config.minMemory} - ${server.config.maxMemory} MB")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (server.status) {
                ServerStatus.STOPPED, ServerStatus.ERROR -> {
                    Button(onClick = onStart) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start") }
                }
                ServerStatus.RUNNING -> {
                    Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop")
                    }
                    OutlinedButton(onClick = onStop) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Restart") }
                }
                else -> { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            OutlinedButton(onClick = onOpen) { Text("Open Details") }
        }
    }
}

@Composable
fun StatusChip(status: ServerStatus) {
    val (color, text) = when (status) {
        ServerStatus.RUNNING -> Color(0xFF4CAF50) to "Running"
        ServerStatus.STOPPED -> Color.Gray to "Stopped"
        ServerStatus.STARTING -> Color(0xFFFFC107) to "Starting"
        ServerStatus.STOPPING -> Color(0xFFFFC107) to "Stopping"
        ServerStatus.ERROR -> Color(0xFFF44336) to "Error"
    }
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 16.dp), maxLines = 1)
    }
}

// Helpers to control server - returns error message or null on success
suspend fun startServer(server: Server): String? {
    val repo = ServerRepository.instance
    if (com.pockethost.desktop.process.ProcessManager.isProcessAlive(server.id)) {
        val msg = "Server already running (PID=${com.pockethost.desktop.process.ProcessManager.getProcessInfo(server.id)?.pid}) - stop it first"
        repo.appendLog(server.id, "[PocketHost] $msg")
        return msg
    }
    // Check port still free (another app may have taken it)
    if (!com.pockethost.common.util.NetworkUtils.isPortAvailable(server.port)) {
        val msg = "Port ${server.port} already in use - stop other server or change port in server.properties"
        repo.appendLog(server.id, "[PocketHost] $msg")
        repo.updateServerStatus(server.id, ServerStatus.ERROR)
        return msg
    }
    val provisioner = com.pockethost.desktop.minecraft.MinecraftServerManager()
    // Validate java
    val version = server.config.minecraftVersion
    val required = com.pockethost.desktop.java.JavaManager.requiredJavaForMinecraft(version)
    val java = com.pockethost.desktop.java.JavaManager.ensureJava(required) { msg -> println(msg) }
    if (java == null) {
        val msg = "Failed to ensure Java $required - check internet or install manually"
        repo.appendLog(server.id, "[PocketHost] $msg")
        repo.updateServerStatus(server.id, ServerStatus.ERROR)
        return msg
    }
    repo.updateServerStatus(server.id, ServerStatus.STARTING)
    repo.appendLog(server.id, "[PocketHost] Starting server ${server.name} with Java ${java.path}")

    val dir = File(server.workingDirectory)
    val ok = provisioner.provisionIfNeeded(dir.absolutePath, version ?: "1.21.4", server.config.minecraftLoader, server.config.maxMemory, server.config.minMemory, server.port) { msg ->
        kotlinx.coroutines.runBlocking { repo.appendLog(server.id, "[Provision] $msg") }
    }
    if (!ok) {
        val msg = "Provision failed - check logs and internet"
        repo.updateServerStatus(server.id, ServerStatus.ERROR)
        repo.appendLog(server.id, "[PocketHost] $msg")
        return msg
    }

    // Determine jar
    val jarName = provisioner.getJarName(server.config.minecraftLoader ?: com.pockethost.common.model.MinecraftLoader.PAPER)
    var jarFile = File(dir, jarName)
    if (!jarFile.exists()) jarFile = File(dir, "server.jar")
    if (!jarFile.exists()) {
        val msg = "JAR not found: $jarName - download failed or manual install required"
        repo.appendLog(server.id, "[PocketHost] $msg")
        repo.updateServerStatus(server.id, ServerStatus.ERROR)
        return msg
    }

    val command = mutableListOf<String>()
    command.add(java.path)
    command.add("-Xmx${server.config.maxMemory}M")
    command.add("-Xms${server.config.minMemory}M")
    command.add("-XX:+UseG1GC")
    command.add("-Djava.awt.headless=true")
    command.add("-jar")
    command.add(jarFile.absolutePath)
    command.add("nogui")

    val process = com.pockethost.desktop.process.ProcessManager.startProcess(
        serverId = server.id,
        command = command,
        workingDir = dir,
        environment = server.config.environment,
        onOutput = { line ->
            kotlinx.coroutines.runBlocking { repo.appendLog(server.id, line) }
        },
        onExit = { code ->
            kotlinx.coroutines.runBlocking {
                repo.appendLog(server.id, "[PocketHost] Process exited with code $code")
                repo.updateServerStatus(server.id, if (code == 0) ServerStatus.STOPPED else ServerStatus.ERROR)
            }
        }
    )
    if (process == null) {
        val msg = "Failed to start process - check Java and server files"
        repo.updateServerStatus(server.id, ServerStatus.ERROR)
        repo.appendLog(server.id, "[PocketHost] $msg")
        return msg
    } else {
        repo.updateServerPid(server.id, process.pid)
        repo.updateServerStatus(server.id, ServerStatus.RUNNING)
        repo.appendLog(server.id, "[PocketHost] Server started PID=${process.pid}")
        return null
    }
}

suspend fun stopServer(server: Server): String? {
    val repo = ServerRepository.instance
    repo.updateServerStatus(server.id, ServerStatus.STOPPING)
    repo.appendLog(server.id, "[PocketHost] Stopping server...")
    val ok = com.pockethost.desktop.process.ProcessManager.stopProcess(server.id, graceful = true)
    if (!ok) {
        val msg = "No running process found"
        repo.appendLog(server.id, "[PocketHost] $msg")
        repo.updateServerStatus(server.id, ServerStatus.STOPPED)
        return msg
    }
    // Wait until process actually exits (up to 15s), then force
    var waited = 0
    while (waited < 15000 && com.pockethost.desktop.process.ProcessManager.isProcessAlive(server.id)) {
        kotlinx.coroutines.delay(500)
        waited += 500
    }
    if (com.pockethost.desktop.process.ProcessManager.isProcessAlive(server.id)) {
        repo.appendLog(server.id, "[PocketHost] Server didn't stop gracefully, forcing...")
        com.pockethost.desktop.process.ProcessManager.stopProcess(server.id, graceful = false)
        kotlinx.coroutines.delay(2000)
    }
    repo.updateServerStatus(server.id, ServerStatus.STOPPED)
    repo.appendLog(server.id, "[PocketHost] Server stopped")
    return null
}
