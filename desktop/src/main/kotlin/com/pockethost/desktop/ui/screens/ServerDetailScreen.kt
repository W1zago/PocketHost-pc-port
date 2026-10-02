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
import com.pockethost.common.i18n.Strings
import com.pockethost.common.model.Server
import com.pockethost.common.model.ServerStatus
import com.pockethost.common.repository.ServerRepository
import com.pockethost.common.util.FileUtils
import com.pockethost.desktop.process.ProcessManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ServerDetailScreen(serverId: String, onBack: () -> Unit, onServerDeleted: () -> Unit, onError: (String) -> Unit = {}) {
    val repo = remember { ServerRepository.instance }
    val servers by repo.getAllServers().collectAsState(emptyList())
    val server = servers.find { it.id == serverId }
    val currentLang by Strings.language.collectAsState()

    if (server == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Strings.tr("server.detail.notFound"))
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBack) { Text(Strings.tr("common.back")) }
            }
        }
        return
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        Strings.tr("server.tab.overview"),
        Strings.tr("server.tab.console"),
        Strings.tr("server.tab.files"),
        Strings.tr("server.tab.settings")
    )
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.tr("common.back")) }
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
                                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(Strings.tr("server.start"))
                                }
                            }
                            ServerStatus.RUNNING -> {
                                Button(onClick = {
                                    scope.launch {
                                        val err = stopServer(server)
                                        if (err != null) onError(err)
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                    Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text(Strings.tr("server.stop"))
                                }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val err1 = stopServer(server)
                                        if (err1 != null) onError(err1)
                                        kotlinx.coroutines.delay(3000)
                                        val err2 = startServer(server)
                                        if (err2 != null) onError(err2)
                                    }
                                }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(Strings.tr("server.restart")) }
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
                Text(Strings.tr("server.info.title"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow(Strings.tr("server.info.serverId"), server.id)
                InfoRow(Strings.tr("server.info.type"), server.type.name)
                InfoRow(Strings.tr("server.info.port"), server.port.toString())
                InfoRow(Strings.tr("server.info.directory"), server.workingDirectory)
                server.config.minecraftVersion?.let { InfoRow(Strings.tr("server.info.version"), it) }
                server.config.minecraftLoader?.let { InfoRow(Strings.tr("server.info.loader"), it.name) }
                InfoRow(Strings.tr("server.info.memory"), "${server.config.minMemory} MB - ${server.config.maxMemory} MB")
                val created = java.time.Instant.ofEpochMilli(server.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                InfoRow(Strings.tr("server.info.created"), created)
                server.lastStarted?.let {
                    val started = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    InfoRow(Strings.tr("server.info.lastStarted"), started)
                }
                if (server.pid != -1L) InfoRow(Strings.tr("server.info.pid"), server.pid.toString())
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.tr("server.perf.title"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (server.status == ServerStatus.RUNNING && stats != null) {
                    Text("CPU: ${stats.cpuPercent}%")
                    Text("Memory: ${stats.memoryBytes / 1024 / 1024} MB")
                    Text("Threads: ${stats.threadCount}")
                } else if (server.status == ServerStatus.RUNNING) {
                    Text(Strings.tr("server.perf.collecting"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(Strings.tr("server.perf.notRunning"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.tr("server.actions.title"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    try {
                        val dir = File(server.workingDirectory)
                        if (dir.exists()) {
                            ProcessBuilder("explorer", dir.absolutePath).start()
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text(Strings.tr("server.actions.openFolder"))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text(Strings.tr("server.actions.backup"))
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
        Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF2D2D2D)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${logs.size} ${Strings.tr("console.lines")}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try {
                            val text = logs.joinToString("\n")
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
                        } catch (_: Exception) {}
                    }, enabled = logs.isNotEmpty()) { Text(Strings.tr("console.copyAll"), style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = {
                        try {
                            val file = java.io.File(System.getProperty("java.io.tmpdir"), "pockethost-${server.id}.log")
                            file.writeText(logs.joinToString("\n"))
                            java.awt.Desktop.getDesktop().open(file)
                        } catch (_: Exception) {}
                    }, enabled = logs.isNotEmpty()) { Text(Strings.tr("console.save"), style = MaterialTheme.typography.labelSmall) }
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
                            Text(Strings.tr("console.noLogs"), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
                    placeholder = { Text(Strings.tr("console.placeholder")) },
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
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = Strings.tr("console.send"))
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
                Text(Strings.tr("console.requiresRunning"), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var fileError by remember { mutableStateOf<String?>(null) }
    val baseDir = remember(server.workingDirectory) { File(server.workingDirectory) }

    LaunchedEffect(currentPath) {
        if (!FileUtils.validatePath(currentPath, baseDir)) {
            fileError = Strings.tr("files.accessDenied")
            currentPath = baseDir
            return@LaunchedEffect
        }
        files = currentPath.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        fileError = null
    }

    LaunchedEffect(selectedFile) {
        selectedFile?.let { file ->
            if (!FileUtils.validatePath(file, baseDir)) {
                fileContent = Strings.tr("files.accessDenied")
                fileError = Strings.tr("files.accessDenied")
                return@let
            }
            if (file.isFile) {
                try {
                    if (file.length() > 1024 * 1024) {
                        fileContent = Strings.tr("files.tooLarge").replace("{size}", (file.length() / 1024).toString())
                    } else {
                        fileContent = file.readText()
                    }
                    editedContent = fileContent
                    isEditing = false
                } catch (e: Exception) {
                    fileContent = Strings.tr("files.cannotRead").replace("{msg}", e.message ?: "")
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
                            Icon(Icons.Default.Home, contentDescription = Strings.tr("files.root"))
                        }
                        Text(currentPath.name.ifEmpty { server.name }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            try {
                                ProcessBuilder("explorer", currentPath.absolutePath).start()
                            } catch (e: Exception) {}
                        }) { Icon(Icons.Default.FolderOpen, contentDescription = Strings.tr("files.openExplorer")) }
                    }
                }
                HorizontalDivider()
                fileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) }
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
                                if (!FileUtils.validatePath(file, baseDir)) {
                                    fileError = Strings.tr("files.accessDenied")
                                    return@FileListItem
                                }
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
        Box(Modifier.weight(1f).fillMaxSize()) {
            if (selectedFile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.tr("files.selectPreview"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    OutlinedButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text(Strings.tr("common.edit")) }
                                } else {
                                    Button(onClick = {
                                        try {
                                            file.writeText(editedContent)
                                            fileContent = editedContent
                                            isEditing = false
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text(Strings.tr("common.save")) }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(onClick = { editedContent = fileContent; isEditing = false }) { Text(Strings.tr("common.cancel")) }
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
    val serverDir = remember(server.workingDirectory) { File(server.workingDirectory) }
    var isOffline by remember(server.id) { mutableStateOf(com.pockethost.desktop.util.ServerPropertiesManager.isOfflineMode(serverDir)) }
    var renderDistance by remember(server.id) { mutableStateOf(com.pockethost.desktop.util.ServerPropertiesManager.getRenderDistance(serverDir)) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.tr("settings.general"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow(Strings.tr("server.name"), server.name)
                InfoRow(Strings.tr("server.port"), server.port.toString())
                InfoRow(Strings.tr("server.info.type"), server.status.name)
                InfoRow(Strings.tr("settings.autoStart"), if (server.autoStart) Strings.tr("settings.enabled") else Strings.tr("settings.disabled"))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.tr("settings.offlineSection"), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(Strings.tr("settings.offlineTitle"), style = MaterialTheme.typography.bodyMedium)
                        Text(Strings.tr("server.offlineModeSubtitle"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isOffline, onCheckedChange = { offline ->
                        isOffline = offline
                        try {
                            com.pockethost.desktop.util.ServerPropertiesManager.setOfflineMode(serverDir, offline)
                            statusMsg = if (offline) Strings.tr("settings.offlineModeEnabled") else Strings.tr("settings.offlineModeDisabled")
                        } catch (e: Exception) { statusMsg = "${Strings.tr("server.create.error").replace("{msg}", e.message ?: "")}" }
                    })
                }
                statusMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                Text(Strings.tr("settings.offlineNote"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.tr("server.renderDistance"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        renderDistance = 8
                        com.pockethost.desktop.util.ServerPropertiesManager.setRenderDistance(serverDir, 8)
                        statusMsg = "Render distance = 8 (${Strings.tr("server.renderDistance.low")})"
                    }, colors = if (renderDistance == 8) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text(Strings.tr("server.renderDistance.low")) }
                    Button(onClick = {
                        renderDistance = 12
                        com.pockethost.desktop.util.ServerPropertiesManager.setRenderDistance(serverDir, 12)
                        statusMsg = "Render distance = 12 (${Strings.tr("server.renderDistance.medium")})"
                    }, colors = if (renderDistance == 12) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text(Strings.tr("server.renderDistance.medium")) }
                    Button(onClick = {
                        renderDistance = 16
                        com.pockethost.desktop.util.ServerPropertiesManager.setRenderDistance(serverDir, 16)
                        statusMsg = "Render distance = 16 (${Strings.tr("server.renderDistance.high")})"
                    }, colors = if (renderDistance == 16) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text(Strings.tr("server.renderDistance.high")) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(value = renderDistance.toFloat(), onValueChange = { renderDistance = it.toInt() }, onValueChangeFinished = {
                        com.pockethost.desktop.util.ServerPropertiesManager.setRenderDistance(serverDir, renderDistance)
                        statusMsg = "Render distance = $renderDistance"
                    }, valueRange = 2f..32f, steps = 29, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text("$renderDistance ${Strings.tr("server.renderDistance.chunks")}", style = MaterialTheme.typography.bodyMedium)
                }
                Text(Strings.tr("settings.renderDistanceNote").replace("{distance}", renderDistance.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.tr("settings.dangerZone"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(Strings.tr("settings.dangerZoneNote"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text(Strings.tr("settings.deleteServer"))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(Strings.tr("settings.deleteConfirmTitle")) },
            text = { Text(Strings.tr("settings.deleteConfirmText").replace("{name}", server.name).replace("{dir}", server.workingDirectory)) },
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
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(Strings.tr("common.delete")) }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteConfirm = false }) { Text(Strings.tr("common.cancel")) } }
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
