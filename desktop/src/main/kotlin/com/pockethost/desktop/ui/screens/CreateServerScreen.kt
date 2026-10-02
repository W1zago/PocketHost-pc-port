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
import com.pockethost.common.i18n.Strings
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
    val repository = remember { ServerRepository.instance }
    val manager = remember { MinecraftServerManager() }
    val scope = rememberCoroutineScope()
    val currentLang by Strings.language.collectAsState()

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
        versionLoadMsg = Strings.tr("server.create.loadingVersions")
        try {
            val allEntries = VersionManifestManager.fetchAllVersions(forceRefresh) { msg -> versionLoadMsg = msg }
            if (allEntries.isEmpty()) {
                versionLoadMsg = Strings.tr("server.create.failedManifest")
                isLoadingVersions = false
                return
            }
            versionEntries = allEntries

            if (loader == MinecraftLoader.VANILLA) {
                val filtered = VersionManifestManager.getFiltered(allEntries, versionFilter)
                val strList = filtered.map { it.id }
                availableVersions = strList
                if (version !in strList && strList.isNotEmpty()) version = strList.first()
                versionLoadMsg = Strings.tr("server.create.loadedVersionsVanilla")
                    .replace("{count}", allEntries.size.toString())
                    .replace("{filter}", versionFilter.label)
            } else {
                val loaderVersions = manager.fetchAvailableVersions(loader)
                val loaderSet = loaderVersions.toSet()
                val filtered = allEntries.filter { it.id in loaderSet }
                val strList = if (filtered.isNotEmpty()) filtered.map { it.id } else loaderVersions
                availableVersions = strList
                if (version !in strList && strList.isNotEmpty()) version = strList.first()
                versionLoadMsg = Strings.tr("server.create.loadedVersionsLoader")
                    .replace("{count}", strList.size.toString())
                    .replace("{loader}", loader.name)
                    .replace("{total}", allEntries.size.toString())
            }
        } catch (e: Exception) {
            versionLoadMsg = Strings.tr("server.create.error").replace("{msg}", e.message ?: "")
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
        if (p == null || !NetworkUtils.isValidPort(p)) { error = Strings.tr("error.invalidPort"); return false }
        if (!NetworkUtils.isPortAvailable(p)) { error = Strings.tr("error.portInUse"); return false }
        val max = maxMemory.toIntOrNull()
        val min = minMemory.toIntOrNull()
        if (max == null || min == null || max < 512 || min < 256 || min > max) { error = Strings.tr("error.invalidMemory"); return false }
        if (customDir.isBlank()) { error = Strings.tr("error.chooseDir"); return false }
        val dirFile = java.io.File(customDir)
        if (dirFile.exists() && !dirFile.isDirectory) { error = Strings.tr("error.notDirectory"); return false }
        try {
            val testParent = dirFile.parentFile ?: dirFile
            if (!testParent.exists() && !testParent.mkdirs()) { error = Strings.tr("error.cannotCreateDir").replace("{path}", testParent.absolutePath); return false }
        } catch (e: Exception) { error = Strings.tr("error.invalidPath").replace("{msg}", e.message ?: ""); return false }
        error = null; return true
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(Strings.tr("server.create.title"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("${Strings.tr("server.create.step")} $step ${Strings.tr("server.create.of")} 3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { step / 3f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                1 -> {
                    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(Strings.tr("server.create.typeAndName"), style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(Strings.tr("server.name")) }, placeholder = { Text(Strings.tr("server.namePlaceholder")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text(Strings.tr("server.software"), style = MaterialTheme.typography.bodyMedium)
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
                                row.forEach { (l, labelText) ->
                                    val selected = loader == l
                                    FilterChip(
                                        selected = selected,
                                        onClick = { loader = l },
                                        label = { Text(labelText, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (loader == MinecraftLoader.SPIGOT || loader == MinecraftLoader.FORGE || loader == MinecraftLoader.NEOFORGE || loader == MinecraftLoader.BUKKIT) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Text(Strings.tr("server.create.manualNotice").replace("{loader}", loader.name), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                2 -> {
                    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(Strings.tr("server.create.configuration"), style = MaterialTheme.typography.titleMedium)
                        Text(Strings.tr("server.create.versionFilter"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (loader == MinecraftLoader.VANILLA) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                VersionManifestManager.allFilters.forEach { f ->
                                    val count = when (f) {
                                        is VersionManifestManager.ManifestFilter.All -> versionEntries.size
                                        else -> typeCounts[f.typeValue] ?: 0
                                    }
                                    val chipLabel = if (count > 0) "${f.label} ($count)" else f.label
                                    FilterChip(
                                        selected = versionFilter == f,
                                        onClick = { versionFilter = f },
                                        label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Text(
                                Strings.tr("server.create.vanillaNotice"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                Strings.tr("server.create.loaderNotice").replace("{loader}", loader.name).replace("{count}", availableVersions.size.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                VersionManifestManager.allFilters.forEach { f ->
                                    val count = when (f) {
                                        is VersionManifestManager.ManifestFilter.All -> versionEntries.size
                                        else -> typeCounts[f.typeValue] ?: 0
                                    }
                                    val chipLabel = if (count > 0) "${f.label} ($count)" else f.label
                                    FilterChip(
                                        selected = false,
                                        enabled = false,
                                        onClick = {},
                                        label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(versionLoadMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (isLoadingVersions) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            OutlinedButton(onClick = { scope.launch { loadVersions(true) } }, enabled = !isLoadingVersions) { Text(Strings.tr("server.create.refresh"), style = MaterialTheme.typography.labelSmall) }
                        }
                        OutlinedTextField(
                            value = versionSearch,
                            onValueChange = { versionSearch = it },
                            label = { Text(Strings.tr("server.create.searchVersion")) },
                            placeholder = { Text(Strings.tr("server.create.searchPlaceholder")) },
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
                                Strings.tr("server.create.showingCount").replace("{shown}", displayVersions.size.toString()).replace("{total}", availableVersions.size.toString()).replace("{search}", versionSearch),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = version,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("${Strings.tr("server.version")} (${displayVersions.size}/${availableVersions.size})") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.heightIn(max = 380.dp)
                            ) {
                                if (displayVersions.isEmpty()) {
                                    DropdownMenuItem(text = { Text(Strings.tr("server.create.nothingFound"), color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {})
                                } else {
                                    displayVersions.forEach { v ->
                                        val entry = versionEntries.find { it.id == v }
                                        val typeLabel = entry?.type ?: "release"
                                        val itemLabel = if (loader == MinecraftLoader.VANILLA) "$v  · $typeLabel" else v
                                        DropdownMenuItem(
                                            text = { Text(itemLabel, style = MaterialTheme.typography.bodySmall) },
                                            onClick = { version = v; expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) }, label = { Text(Strings.tr("server.port")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = minMemory, onValueChange = { minMemory = it.filter { c -> c.isDigit() } }, label = { Text(Strings.tr("server.memory.min")) }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = maxMemory, onValueChange = { maxMemory = it.filter { c -> c.isDigit() } }, label = { Text(Strings.tr("server.memory.max")) }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(Strings.tr("server.offlineMode"), style = MaterialTheme.typography.bodyMedium)
                                    Text(Strings.tr("server.offlineModeSubtitle"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = offlineMode, onCheckedChange = { offlineMode = it })
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(Strings.tr("server.renderDistance"), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { renderDistance = 8 }, colors = if (renderDistance == 8) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text(Strings.tr("server.renderDistance.low")) }
                                    Button(onClick = { renderDistance = 12 }, colors = if (renderDistance == 12) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text(Strings.tr("server.renderDistance.medium")) }
                                    Button(onClick = { renderDistance = 16 }, colors = if (renderDistance == 16) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(), modifier = Modifier.weight(1f)) { Text(Strings.tr("server.renderDistance.high")) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Slider(value = renderDistance.toFloat(), onValueChange = { renderDistance = it.toInt() }, valueRange = 2f..32f, steps = 29, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(12.dp))
                                    Text("$renderDistance ${Strings.tr("server.renderDistance.chunks")}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(Strings.tr("server.renderDistance.note").replace("{distance}", renderDistance.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            Strings.tr("server.create.availableVersions").replace("{count}", availableVersions.size.toString()).replace("{loader}", loader.name) +
                                if (loader == MinecraftLoader.VANILLA) Strings.tr("server.create.allTypesNote") else Strings.tr("server.create.releasesOnlyNote"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(Strings.tr("server.directory"), style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = customDir, onValueChange = { customDir = it }, label = { Text(Strings.tr("server.directory.path")) }, modifier = Modifier.weight(1f), singleLine = true)
                            Button(onClick = {
                                try {
                                    val chooser = javax.swing.JFileChooser(customDir.ifBlank { com.pockethost.common.util.AppPaths.serversDir.absolutePath })
                                    chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                    chooser.dialogTitle = Strings.tr("server.directory.chooseFolder").replace("{name}", if (name.isBlank()) "my-server" else name)
                                    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                        val sel = chooser.selectedFile
                                        if (sel != null) {
                                            val target = if (sel.absolutePath.endsWith(name) || name.isBlank()) sel else java.io.File(sel, name)
                                            customDir = target.absolutePath
                                        }
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }) { Text(Strings.tr("server.directory.browse")) }
                        }
                        Text("${Strings.tr("server.directory.default")} ${com.pockethost.common.util.AppPaths.serversDir.absolutePath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (customDir.isNotBlank()) {
                            val free = try { java.io.File(customDir).let { if (it.exists()) it.freeSpace / 1024 / 1024 / 1024 else it.parentFile?.freeSpace?.let { s -> s / 1024 / 1024 / 1024 } ?: 0 } } catch (_: Exception) { 0 }
                            Text("${Strings.tr("server.directory.freeSpace")} $free GB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                3 -> {
                    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(Strings.tr("server.create.creatingTitle"), style = MaterialTheme.typography.titleMedium)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(Strings.tr("server.create.summary"), style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Text("${Strings.tr("server.name")}: $name")
                                Text("${Strings.tr("server.info.loader")}: $loader")
                                Text("${Strings.tr("server.version")}: $version")
                                Text("${Strings.tr("server.port")}: $port")
                                Text("${Strings.tr("server.info.memory")}: $minMemory - $maxMemory MB")
                                Text("${Strings.tr("server.offlineTitle")}: ${if (offlineMode) Strings.tr("server.create.offlineYes") else Strings.tr("server.create.offlineNo")}")
                                Text("${Strings.tr("server.renderDistance")}: $renderDistance ${Strings.tr("server.renderDistance.chunks")}")
                                Text("${Strings.tr("server.info.directory")}: $customDir", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (isCreating) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Text(Strings.tr("server.create.pleaseWait"))
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
            }, enabled = !isCreating) { Text(if (step == 1) Strings.tr("common.cancel") else Strings.tr("common.back")) }

            when (step) {
                1 -> Button(onClick = { if (validateStep1()) step = 2 }) { Text(Strings.tr("common.next")) }
                2 -> Button(onClick = { if (validateStep2()) step = 3 }) { Text(Strings.tr("common.next")) }
                3 -> {
                    if (isCreating) {
                        OutlinedButton(onClick = {}, enabled = false) { Text(Strings.tr("server.create.creatingBtn")) }
                    } else {
                        Button(onClick = {
                            scope.launch {
                                isCreating = true
                                error = null
                                progressLog = Strings.tr("server.create.starting") + "\n"
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
                                        repository.addServer(server)
                                        progressLog += Strings.tr("server.create.saved") + "\n"
                                        isCreating = false
                                        onServerCreated(server)
                                    } else {
                                        val msg = Strings.tr("error.creationFailed")
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
                        }) { Text(Strings.tr("server.create.btn")) }
                    }
                }
            }
        }
    }
}
