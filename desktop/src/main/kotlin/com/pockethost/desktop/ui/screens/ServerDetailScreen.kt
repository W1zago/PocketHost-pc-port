package com.pockethost.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pockethost.common.model.Server
import com.pockethost.common.model.ServerStatus
import com.pockethost.common.repository.ServerRepository
import com.pockethost.desktop.process.ProcessManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ServerDetailScreen(serverId: String, onBack: () -> Unit, onServerDeleted: () -> Unit, onError: (String) -> Unit = {}) {
    val repo = remember { ServerRepository.instance }
    val servers by repo.getAllServers().collectAsState(emptyList())
    val server = servers.find { it.id == serverId }

    if (server == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Server not found")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        }
        return
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Console", "Files", "Settings")
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(server.status)
                            Spacer(Modifier.width(8.dp))
                            Text("${server.config.minecraftLoader?.name ?: "MINECRAFT"} ${server.config.minecraftVersion ?: ""} • Port ${server.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (server.status) {
                            ServerStatus.STOPPED, ServerStatus.ERROR -> {
                                Button(onClick = {
                                    scope.launch {
                                        val err = startServer(server)
                                        if (err != null) onError(err)
                                    }
                                }) {
                                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start")
                                }
                            }
                            ServerStatus.RUNNING -> {
                                Button(onClick = {
                                    scope.launch {
                                        val err = stopServer(server)
                                        if (err != null) onError(err)
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                    Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop")
                                }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val err1 = stopServer(server)
                                        if (err1 != null) onError(err1)
                                        kotlinx.coroutines.delay(3000)
                                        val err2 = startServer(server)
                                        if (err2 != null) onError(err2)
                                    }
                                }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Restart") }
                            }
                            else -> { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }

        Box(Modifier.fillMaxSize().weight(1f)) {
            when (selectedTab) {
                0 -> ServerOverviewTab(server)
                1 -> ServerConsoleTab(server)
                2 -> ServerFilesTab(server)
                3 -> ServerSettingsTab(server, onDeleted = onServerDeleted)
            }
        }
    }
}

@Composable
fun ServerOverviewTab(server: Server) {
    val stats = remember(server.id) { ProcessManager.getProcessStats(server.id) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Server Information", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow("Server ID", server.id)
                InfoRow("Type", server.type.name)
                InfoRow("Port", server.port.toString())
                InfoRow("Directory", server.workingDirectory)
                server.config.minecraftVersion?.let { InfoRow("Minecraft Version", it) }
                server.config.minecraftLoader?.let { InfoRow("Loader", it.name) }
                InfoRow("Memory", "${server.config.minMemory} MB - ${server.config.maxMemory} MB")
                val created = java.time.Instant.ofEpochMilli(server.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                InfoRow("Created", created)
                server.lastStarted?.let {
                    val started = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    InfoRow("Last Started", started)
                }
                if (server.pid != -1L) InfoRow("PID", server.pid.toString())
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Performance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (server.status == ServerStatus.RUNNING && stats != null) {
                    Text("CPU: ${stats.cpuPercent}%")
                    Text("Memory: ${stats.memoryBytes / 1024 / 1024} MB")
                    Text("Threads: ${stats.threadCount}")
                } else if (server.status == ServerStatus.RUNNING) {
                    Text("Collecting stats...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Poll every 5s - handled externally, for MVP just show placeholder
                } else {
                    Text("Server not running", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    try {
                        val dir = File(server.workingDirectory)
                        if (dir.exists()) {
                            // Open folder in explorer
                            ProcessBuilder("explorer", dir.absolutePath).start()
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text("Open Server Folder")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("Create Backup (Phase 2)")
                }
            }
        }
    }
}

@Composable
fun ServerConsoleTab(server: Server) {
    val repo = remember { ServerRepository.instance }
    val logs by repo.getServerLogs(server.id).collectAsState(emptyList())
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header with Copy All
        Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF2D2D2D)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${logs.size} lines", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try {
                            val text = logs.joinToString("\n")
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
                        } catch (_: Exception) {}
                    }, enabled = logs.isNotEmpty()) { Text("Copy All", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = {
                        try {
                            val file = java.io.File(System.getProperty("java.io.tmpdir"), "pockethost-${server.id}.log")
                            file.writeText(logs.joinToString("\n"))
                            java.awt.Desktop.getDesktop().open(file)
                        } catch (_: Exception) {}
                    }, enabled = logs.isNotEmpty()) { Text("Save", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Surface(modifier = Modifier.weight(1f).fillMaxWidth(), color = Color(0xFF1E1E1E)) {
            SelectionContainer {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color(0xFFCCCCCC)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (logs.isEmpty()) {
                        item {
                            Text("No logs yet. Start the server to see output.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (server.status == ServerStatus.RUNNING) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter command...") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (commandInput.isNotBlank()) {
                        ProcessManager.writeInput(server.id, commandInput + "\n")
                        scope.launch { repo.appendLog(server.id, "> $commandInput") }
                        commandInput = ""
                    }
                }, enabled = commandInput.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("help", "list", "stop", "save-all").forEach { cmd ->
                    OutlinedButton(onClick = {
                        ProcessManager.writeInput(server.id, "$cmd\n")
                        scope.launch { repo.appendLog(server.id, "> $cmd") }
                    }) { Text(cmd, style = MaterialTheme.typography.labelSmall) }
                }
            }
        } else {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("Server must be running to send commands", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ServerFilesTab(server: Server) {
    var currentPath by remember { mutableStateOf(File(server.workingDirectory)) }
    var files by remember { mutableStateOf(listOf<File>()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf("") }

    LaunchedEffect(currentPath) {
        files = currentPath.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    LaunchedEffect(selectedFile) {
        selectedFile?.let { file ->
            if (file.isFile) {
                try {
                    if (file.length() > 1024 * 1024) {
                        fileContent = "File too large (${file.length() / 1024} KB)"
                    } else {
                        fileContent = file.readText()
                    }
                    editedContent = fileContent
                    isEditing = false
                } catch (e: Exception) {
                    fileContent = "Cannot read file: ${e.message}"
                }
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
        Surface(Modifier.width(350.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentPath = File(server.workingDirectory) }) {
                            Icon(Icons.Default.Home, contentDescription = "Root")
                        }
                        Text(currentPath.name.ifEmpty { server.name }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            try {
                                ProcessBuilder("explorer", currentPath.absolutePath).start()
                            } catch (e: Exception) {}
                        }) { Icon(Icons.Default.FolderOpen, contentDescription = "Open in Explorer") }
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (currentPath.absolutePath != File(server.workingDirectory).absolutePath) {
                        item {
                            FileListItem(label = "..", isDirectory = true, size = "", selected = false, onClick = { currentPath = currentPath.parentFile ?: File(server.workingDirectory); selectedFile = null })
                        }
                    }
                    items(files) { file ->
                        FileListItem(
                            label = file.name,
                            isDirectory = file.isDirectory,
                            size = if (file.isFile) formatFileSize(file.length()) else "",
                            selected = file == selectedFile,
                            onClick = {
                                if (file.isDirectory) {
                                    currentPath = file
                                    selectedFile = null
                                } else {
                                    selectedFile = file
                                }
                            }
                        )
                    }
                }
            }
        }
        // Preview
        Box(Modifier.weight(1f).fillMaxSize()) {
            if (selectedFile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Select a file to preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val file = selectedFile!!
                Column(Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(file.name, style = MaterialTheme.typography.titleSmall)
                                Text(formatFileSize(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                if (!isEditing) {
                                    OutlinedButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text("Edit") }
                                } else {
                                    Button(onClick = {
                                        try {
                                            file.writeText(editedContent)
                                            fileContent = editedContent
                                            isEditing = false
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text("Save") }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(onClick = { editedContent = fileContent; isEditing = false }) { Text("Cancel") }
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { selectedFile = null }) { Icon(Icons.Default.Close, null) }
                            }
                        }
                    }
                    HorizontalDivider()
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { editedContent = it },
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = fileContent,
                                modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp).fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileListItem(label: String, isDirectory: Boolean, size: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp).clickable(onClick = onClick), color = bg, shape = RoundedCornerShape(4.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isDirectory) Icons.Default.Folder else Icons.Default.Description, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                if (size.isNotEmpty()) Text(size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ServerSettingsTab(server: Server, onDeleted: () -> Unit) {
    val repo = remember { ServerRepository.instance }
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("General", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow("Name", server.name)
                InfoRow("Port", server.port.toString())
                InfoRow("Status", server.status.name)
                InfoRow("Auto Start", if (server.autoStart) "Enabled" else "Disabled")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Delete this server and all its files. This action cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete Server")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Server?") },
            text = { Text("Are you sure you want to delete '${server.name}'? All files in ${server.workingDirectory} will be removed.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            if (ProcessManager.isProcessAlive(server.id)) {
                                ProcessManager.stopProcess(server.id, graceful = false)
                            }
                        } catch (_: Exception) {}
                        repo.deleteServer(server.id)
                        try { File(server.workingDirectory).deleteRecursively() } catch (_: Exception) {}
                        onDeleted()
                    }
                    showDeleteConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
        else -> "${bytes / 1024 / 1024 / 1024} GB"
    }
}
