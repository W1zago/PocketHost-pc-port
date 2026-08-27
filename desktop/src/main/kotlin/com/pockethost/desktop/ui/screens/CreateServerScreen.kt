package com.pockethost.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pockethost.common.model.MinecraftLoader
import com.pockethost.common.model.Server
import com.pockethost.common.repository.ServerRepository
import com.pockethost.common.util.NetworkUtils
import com.pockethost.desktop.minecraft.MinecraftServerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateServerScreen(onServerCreated: (Server) -> Unit, onCancel: () -> Unit) {
    val repo = remember { ServerRepository.instance }
    val manager = remember { MinecraftServerManager() }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }
    var name by remember { mutableStateOf("") }
    var loader by remember { mutableStateOf(MinecraftLoader.PAPER) }
    var version by remember { mutableStateOf("1.21.4") }
    var availableVersions by remember { mutableStateOf(listOf("1.21.4", "1.21.1", "1.20.4", "1.19.4")) }
    var port by remember { mutableStateOf("25565") }
    var maxMemory by remember { mutableStateOf("2048") }
    var minMemory by remember { mutableStateOf("1024") }
    var isCreating by remember { mutableStateOf(false) }
    var progressLog by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loader) {
        scope.launch {
            val versions = manager.fetchAvailableVersions(loader)
            if (versions.isNotEmpty()) {
                availableVersions = versions
                if (version !in versions) version = versions.first()
            }
        }
    }

    fun validateStep1(): Boolean {
        if (name.isBlank()) { error = "Name cannot be empty"; return false }
        if (name.length < 3) { error = "Name too short"; return false }
        if (!name.matches(Regex("[a-zA-Z0-9-_]+"))) { error = "Only letters, numbers, - and _ allowed"; return false }
        error = null; return true
    }
    fun validateStep2(): Boolean {
        val p = port.toIntOrNull()
        if (p == null || !NetworkUtils.isValidPort(p)) { error = "Invalid port (1024-65535)"; return false }
        if (!NetworkUtils.isPortAvailable(p)) { error = "Port $p already in use"; return false }
        val max = maxMemory.toIntOrNull()
        val min = minMemory.toIntOrNull()
        if (max == null || min == null || max < 512 || min < 256 || min > max) { error = "Invalid memory values"; return false }
        error = null; return true
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Create New Server", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Step $step of 3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = step / 3f, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                1 -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Server Type & Name", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Server Name") }, placeholder = { Text("my-minecraft-server") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("Choose server software:", style = MaterialTheme.typography.bodyMedium)
                        // Loader selection 2 columns
                        val loaders = listOf(
                            MinecraftLoader.PAPER to "Paper (Recommended)",
                            MinecraftLoader.VANILLA to "Vanilla",
                            MinecraftLoader.FABRIC to "Fabric",
                            MinecraftLoader.FORGE to "Forge",
                            MinecraftLoader.PURPUR to "Purpur",
                            MinecraftLoader.SPIGOT to "Spigot (Manual)",
                            MinecraftLoader.BUKKIT to "Bukkit (Manual)",
                            MinecraftLoader.NEOFORGE to "NeoForge (Manual)"
                        )
                        loaders.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (l, label) ->
                                    val selected = loader == l
                                    FilterChip(
                                        selected = selected,
                                        onClick = { loader = l },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (loader == MinecraftLoader.SPIGOT || loader == MinecraftLoader.FORGE || loader == MinecraftLoader.NEOFORGE || loader == MinecraftLoader.BUKKIT) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Text("Manual install required for $loader: you will need to place JAR manually in server folder after creation.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                2 -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Configuration", style = MaterialTheme.typography.titleMedium)
                        // Version dropdown
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = version,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Minecraft Version") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                availableVersions.forEach { v ->
                                    DropdownMenuItem(text = { Text(v) }, onClick = { version = v; expanded = false })
                                }
                            }
                        }
                        OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = minMemory, onValueChange = { minMemory = it.filter { c -> c.isDigit() } }, label = { Text("Min Memory (MB)") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = maxMemory, onValueChange = { maxMemory = it.filter { c -> c.isDigit() } }, label = { Text("Max Memory (MB)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Text("Available: ${availableVersions.size} versions for $loader", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                3 -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Creating Server", style = MaterialTheme.typography.titleMedium)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Summary:", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Text("Name: $name")
                                Text("Loader: $loader")
                                Text("Version: $version")
                                Text("Port: $port")
                                Text("Memory: $minMemory - $maxMemory MB")
                            }
                        }
                        if (isCreating) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Creating... please wait")
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(progressLog, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState()))
                                }
                            }
                        } else {
                            if (progressLog.isNotEmpty()) {
                                Card(Modifier.fillMaxWidth()) {
                                    Text(progressLog, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = {
                if (step == 1) onCancel() else step--
            }, enabled = !isCreating) { Text(if (step == 1) "Cancel" else "Back") }

            when (step) {
                1 -> Button(onClick = { if (validateStep1()) step = 2 }) { Text("Next") }
                2 -> Button(onClick = { if (validateStep2()) step = 3 }) { Text("Next") }
                3 -> {
                    if (isCreating) {
                        OutlinedButton(onClick = {}, enabled = false) { Text("Creating...") }
                    } else {
                        Button(onClick = {
                            scope.launch {
                                isCreating = true
                                error = null
                                progressLog = "Starting creation...\n"
                                try {
                                    val server = manager.createServer(
                                        name = name,
                                        version = version,
                                        loader = loader,
                                        port = port.toInt(),
                                        maxMemory = maxMemory.toInt(),
                                        minMemory = minMemory.toInt()
                                    ) { msg ->
                                        progressLog += msg + "\n"
                                    }
                                    if (server != null) {
                                        repo.addServer(server)
                                        progressLog += "Server saved to database!\n"
                                        isCreating = false
                                        onServerCreated(server)
                                    } else {
                                        error = "Failed to create server. Check logs."
                                        progressLog += "ERROR: Failed\n"
                                        isCreating = false
                                    }
                                } catch (e: Exception) {
                                    error = e.message
                                    progressLog += "Exception: ${e.message}\n"
                                    isCreating = false
                                }
                            }
                        }) { Text("Create Server") }
                    }
                }
            }
        }
    }
}
