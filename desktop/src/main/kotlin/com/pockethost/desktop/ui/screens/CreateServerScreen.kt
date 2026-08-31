package com.pockethost.desktop.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import com.pockethost.desktop.minecraft.VersionManifestManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateServerScreen(onServerCreated: (Server) -> Unit, onCancel: () -> Unit, onError: (String) -> Unit = {}) {
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
    var customDir by remember { mutableStateOf("") }
    var offlineMode by remember { mutableStateOf(false) }
    var renderDistance by remember { mutableStateOf(10) }
    var isCreating by remember { mutableStateOf(false) }
    var progressLog by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun updateCustomDirFromName() {
        if (customDir.isBlank() || customDir.startsWith(com.pockethost.common.util.AppPaths.serversDir.absolutePath)) {
            customDir = java.io.File(com.pockethost.common.util.AppPaths.serversDir, if (name.isBlank()) "my-server" else name).absolutePath
        }
    }
    LaunchedEffect(name) { if (name.isNotBlank()) updateCustomDirFromName() }

    var versionEntries by remember { mutableStateOf<List<com.pockethost.desktop.minecraft.VersionEntry>>(emptyList()) }
    var versionFilter by remember { mutableStateOf<VersionManifestManager.ManifestFilter>(VersionManifestManager.ManifestFilter.All) }
    var versionSearch by remember { mutableStateOf("") }
    var isLoadingVersions by remember { mutableStateOf(false) }
    var versionLoadMsg by remember { mutableStateOf("") }

    suspend fun loadVersions(forceRefresh: Boolean = false) {
        isLoadingVersions = true
        versionLoadMsg = "Завантаження версій..."
        try {
            // Always fetch full manifest from Mojang as source of truth — містить ВСІ версії (release/snapshot/alpha/beta)
            val allEntries = VersionManifestManager.fetchAllVersions(forceRefresh) { msg -> versionLoadMsg = msg }
            if (allEntries.isEmpty()) {
                versionLoadMsg = "Не вдалося завантажити маніфест"
                isLoadingVersions = false
                return
            }
            versionEntries = allEntries

            if (loader == MinecraftLoader.VANILLA) {
                // Vanilla: показуємо ВСІ типи, фільтр (All/Release/Snapshot/Alpha/Beta) працює
                val filtered = VersionManifestManager.getFiltered(allEntries, versionFilter)
                val strList = filtered.map { it.id }
                availableVersions = strList
                if (version !in strList && strList.isNotEmpty()) version = strList.first()
                versionLoadMsg = "Завантажено ${allEntries.size} версій (фільтр: ${versionFilter.label}) — всього release/snapshot/alpha/beta"
            } else {
                // For other loaders: fetch their supported versions, then filter manifest to only those (всі релізи, без ліміту 30/50)
                val loaderVersions = manager.fetchAvailableVersions(loader)
                val loaderSet = loaderVersions.toSet()
                val filtered = allEntries.filter { it.id in loaderSet }
                val strList = if (filtered.isNotEmpty()) filtered.map { it.id } else loaderVersions
                availableVersions = strList
                if (version !in strList && strList.isNotEmpty()) version = strList.first()
                versionLoadMsg = "Завантажено ${strList.size} версій для $loader (з ${allEntries.size} всього в Mojang)"
            }
        } catch (e: Exception) {
            versionLoadMsg = "Помилка: ${e.message}"
        }
        isLoadingVersions = false
    }

    LaunchedEffect(loader) {
        loadVersions(false)
    }
    LaunchedEffect(versionFilter) {
        if (loader == MinecraftLoader.VANILLA && versionEntries.isNotEmpty()) {
            val filtered = VersionManifestManager.getFiltered(versionEntries, versionFilter)
            val strList = filtered.map { it.id }
            availableVersions = strList
            if (version !in strList && strList.isNotEmpty()) version = strList.first()
        }
    }

    // Display list with search filter (не перезавантажує мережу)
    val displayVersions = remember(availableVersions, versionSearch) {
        if (versionSearch.isBlank()) availableVersions
        else availableVersions.filter { it.contains(versionSearch.trim(), ignoreCase = true) }
    }
    val typeCounts = remember(versionEntries) { VersionManifestManager.countByType(versionEntries) }

    fun validateStep1(): Boolean {
        val nameError = com.pockethost.common.util.FileUtils.validateServerName(name)
        if (nameError != null) { error = nameError; return false }
        error = null; return true
    }
    fun validateStep2(): Boolean {
        val p = port.toIntOrNull()
        if (p == null || !NetworkUtils.isValidPort(p)) { error = "Invalid port (1024-65535)"; return false }
        if (!NetworkUtils.isPortAvailable(p)) { error = "Port $p already in use"; return false }
        val max = maxMemory.toIntOrNull()
        val min = minMemory.toIntOrNull()
        if (max == null || min == null || max < 512 || min < 256 || min > max) { error = "Invalid memory values"; return false }
        if (customDir.isBlank()) { error = "Choose server directory"; return false }
        val dirFile = java.io.File(customDir)
        if (dirFile.exists() && !dirFile.isDirectory) { error = "Path exists and is not a directory"; return false }
        // Check disk writable by trying to create
        try {
            val testParent = dirFile.parentFile ?: dirFile
            if (!testParent.exists() && !testParent.mkdirs()) { error = "Cannot create directory ${testParent.absolutePath}"; return false }
        } catch (e: Exception) { error = "Invalid path: ${e.message}"; return false }
        error = null; return true
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Create New Server", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Step $step of 3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { step / 3f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                1 -> {
                    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Configuration", style = MaterialTheme.typography.titleMedium)
                        // === Фільтр версій: ВСІ типи з Mojang (release/snapshot/old_alpha/old_beta) ===
                        Text("Фільтр версій — Mojang manifest (всі типи)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (loader == MinecraftLoader.VANILLA) {
                            // Chips з лічильниками: Всі (800) | Release (120) | Snapshot (500) | Beta (...) | Alpha (...)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                VersionManifestManager.allFilters.forEach { f ->
                                    val count = when (f) {
                                        is VersionManifestManager.ManifestFilter.All -> versionEntries.size
                                        else -> typeCounts[f.typeValue] ?: 0
                                    }
                                    val label = if (count > 0) "${f.label} ($count)" else f.label
                                    FilterChip(
                                        selected = versionFilter == f,
                                        onClick = { versionFilter = f },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            // Пояснення
                            Text(
                                "Vanilla підтримує всі типи: релізи, снапшоти (напр. 24w14a), бети та альфи. Інші лоадери — тільки релізи.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Для Paper/Fabric/Purpur/Forge etc — всі релізи без ліміту, снапшоти не підтримуються лоадером
                            Text(
                                "Для $loader доступні тільки релізи (${availableVersions.size}) — снапшоти не підтримуються цим лоадером. Вибери Vanilla для снапшотів.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Для не-ванілли теж показуємо chips неактивними для інформації
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                VersionManifestManager.allFilters.forEach { f ->
                                    val count = when (f) {
                                        is VersionManifestManager.ManifestFilter.All -> versionEntries.size
                                        else -> typeCounts[f.typeValue] ?: 0
                                    }
                                    val label = if (count > 0) "${f.label} ($count)" else f.label
                                    FilterChip(
                                        selected = false,
                                        enabled = false,
                                        onClick = {},
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                        // Статус + оновити + пошук
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(versionLoadMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (isLoadingVersions) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            OutlinedButton(onClick = { scope.launch { loadVersions(true) } }, enabled = !isLoadingVersions) { Text("Оновити", style = MaterialTheme.typography.labelSmall) }
                        }
                        // Текстовий пошук по версіях (фільтрує локально без мережі)
                        OutlinedTextField(
                            value = versionSearch,
                            onValueChange = { versionSearch = it },
                            label = { Text("Пошук версії (напр. 1.21, 24w, 1.20.1)") },
                            placeholder = { Text("Введи частину назви...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (versionSearch.isNotBlank()) {
                                    TextButton(onClick = { versionSearch = "" }) { Text("×") }
                                }
                            }
                        )
                        if (versionSearch.isNotBlank()) {
                            Text(
                                "Показано ${displayVersions.size} з ${availableVersions.size} (фільтр: \"$versionSearch\")",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Version dropdown — показує відфільтрований список (з пошуком) + тип для Vanilla
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = version,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Minecraft Version (${displayVersions.size}/${availableVersions.size})") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.heightIn(max = 380.dp)
                            ) {
                                if (displayVersions.isEmpty()) {
                                    DropdownMenuItem(text = { Text("Нічого не знайдено", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {})
                                } else {
                                    displayVersions.forEach { v ->
                                        val entry = versionEntries.find { it.id == v }
                                        val typeLabel = entry?.type ?: "release"
                                        val label = if (loader == MinecraftLoader.VANILLA) "$v  · $typeLabel" else v
                                        DropdownMenuItem(
                                            text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                            onClick = { version = v; expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = minMemory, onValueChange = { minMemory = it.filter { c -> c.isDigit() } }, label = { Text("Min Memory (MB)") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = maxMemory, onValueChange = { maxMemory = it.filter { c -> c.isDigit() } }, label = { Text("Max Memory (MB)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        // Offline mode toggle (per-server)
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("Офлайн режим (піратський акаунт)", style = MaterialTheme.typography.bodyMedium)
                                    Text("online-mode=false, без перевірки Mojang", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = offlineMode, onCheckedChange = { offlineMode = it })
                            }
                        }
                        // Render distance per-server
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Render Distance (промальовка чанків)", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { renderDistance = 8 }, colors = if (renderDistance == 8) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text("Низька") }
                                    Button(onClick = { renderDistance = 12 }, colors = if (renderDistance == 12) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text("Середня") }
                                    Button(onClick = { renderDistance = 16 }, colors = if (renderDistance == 16) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text("Висока") }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Slider(value = renderDistance.toFloat(), onValueChange = { renderDistance = it.toInt() }, valueRange = 2f..32f, steps = 29, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(12.dp))
                                    Text("$renderDistance чанків", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("view-distance=$renderDistance (застосується при наступному старті)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "Available: ${availableVersions.size} versions for $loader" +
                                if (loader == MinecraftLoader.VANILLA) " (всі типи: release/snapshot/beta/alpha)" else " (релізи)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // User-chosen directory
                        Text("Server directory (choose disk/folder):", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = customDir, onValueChange = { customDir = it }, label = { Text("Path") }, modifier = Modifier.weight(1f), singleLine = true)
                            Button(onClick = {
                                try {
                                    val chooser = javax.swing.JFileChooser(customDir.ifBlank { com.pockethost.common.util.AppPaths.serversDir.absolutePath })
                                    chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                    chooser.dialogTitle = "Choose server folder (e.g. D:\\Servers\\${if (name.isBlank()) "my-server" else name})"
                                    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                        val sel = chooser.selectedFile
                                        if (sel != null) {
                                            // If user picked existing folder, append name if not already
                                            val target = if (sel.absolutePath.endsWith(name) || name.isBlank()) sel else java.io.File(sel, name)
                                            customDir = target.absolutePath
                                        }
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }) { Text("Browse") }
                        }
                        Text("Default: ${com.pockethost.common.util.AppPaths.serversDir.absolutePath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (customDir.isNotBlank()) {
                            val free = try { java.io.File(customDir).let { if (it.exists()) it.freeSpace / 1024 / 1024 / 1024 else it.parentFile?.freeSpace?.let { s -> s / 1024 / 1024 / 1024 } ?: 0 } } catch (_: Exception) { 0 }
                            Text("Free space: ${free} GB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                3 -> {
                    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                Text("Offline: ${if (offlineMode) "так (online-mode=false)" else "ні (online-mode=true)"}")
                                Text("Render Distance: $renderDistance чанків")
                                Text("Directory: $customDir", style = MaterialTheme.typography.bodySmall)
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
                                    Text(progressLog, style = MaterialTheme.typography.bodySmall)
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
                                        minMemory = minMemory.toInt(),
                                        customDir = customDir.ifBlank { null },
                                        offlineMode = offlineMode,
                                        viewDistance = renderDistance
                                    ) { msg ->
                                        progressLog += msg + "\n"
                                    }
                                    if (server != null) {
                                        repo.addServer(server)
                                        progressLog += "Server saved to database!\n"
                                        isCreating = false
                                        onServerCreated(server)
                                    } else {
                                        val msg = "Failed to create server. Check logs - try Vanilla/Paper."
                                        error = msg
                                        progressLog += "ERROR: Failed\n"
                                        isCreating = false
                                        onError(msg)
                                    }
                                } catch (e: Exception) {
                                    val msg = e.message ?: "Unknown error"
                                    error = msg
                                    progressLog += "Exception: $msg\n"
                                    isCreating = false
                                    onError(msg)
                                }
                            }
                        }) { Text("Create Server") }
                    }
                }
            }
        }
    }
}
