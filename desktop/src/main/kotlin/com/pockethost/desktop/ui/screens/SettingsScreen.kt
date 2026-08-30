package com.pockethost.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pockethost.common.i18n.Language
import com.pockethost.common.i18n.Strings
import com.pockethost.common.util.AppPaths
import com.pockethost.desktop.java.JavaManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    var javaInfo by remember { mutableStateOf<String>("Checking...") }
    var appDirInfo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val java = JavaManager.findJava()
        javaInfo = if (java != null) "${java.path} (v${java.version} ${java.vendor})" else "Not found - will auto-download on server start"
        appDirInfo = AppPaths.appDir.absolutePath
    }

    val lang by Strings.language.collectAsState()
    var serversDirPath by remember { mutableStateOf(AppPaths.serversDir.absolutePath) }
    var isCustom by remember { mutableStateOf(AppPaths.isCustomServersDir()) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(Strings.tr("settings.title"), style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.tr("settings.language"), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = lang == Language.EN, onClick = { Strings.setLanguage(Language.EN) }, label = { Text("English") })
                    FilterChip(selected = lang == Language.UK, onClick = { Strings.setLanguage(Language.UK) }, label = { Text("Українська") })
                }
                Text("Current: ${lang.name} - Sidebar and Settings will update live", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.tr("settings.general"), style = MaterialTheme.typography.titleMedium)
                Text("App Directory: $appDirInfo", style = MaterialTheme.typography.bodySmall)
                Text("Servers: $serversDirPath", style = MaterialTheme.typography.bodySmall)
                if (isCustom) Text("Custom location (new servers will be created here)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                else Text("Default: %USERPROFILE%\\.pockethost\\servers (C:)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Database: ${AppPaths.databaseFile().absolutePath}", style = MaterialTheme.typography.bodySmall)
                Text("Java: $javaInfo", style = MaterialTheme.typography.bodySmall)
                var checking by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        // Open folder chooser
                        try {
                            val chooser = javax.swing.JFileChooser(serversDirPath)
                            chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                            chooser.dialogTitle = "Choose servers directory"
                            val res = chooser.showOpenDialog(null)
                            if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                                val selected = chooser.selectedFile
                                if (selected != null) {
                                    AppPaths.setCustomServersDir(selected.absolutePath)
                                    serversDirPath = selected.absolutePath
                                    isCustom = true
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }) { Text("Choose Servers Folder") }
                    if (isCustom) {
                        OutlinedButton(onClick = {
                            AppPaths.resetServersDir()
                            serversDirPath = AppPaths.serversDir.absolutePath
                            isCustom = false
                        }) { Text("Reset to Default") }
                    }
                    OutlinedButton(onClick = {
                        try { ProcessBuilder("explorer", serversDirPath).start() } catch (_: Exception) {}
                    }) { Text("Open") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            checking = true
                            val j = JavaManager.ensureJava(17) { msg -> javaInfo = msg }
                            javaInfo = if (j != null) "${j.path} (v${j.version})" else "Download failed"
                            checking = false
                        }
                    }, enabled = !checking) {
                        if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Check / Download Java 17")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Logs & Firewall (P3)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("App log: ${com.pockethost.desktop.util.AppLogger.getLogFile().absolutePath}", style = MaterialTheme.typography.bodySmall)
                Text("Server logs: ${AppPaths.logsDir.absolutePath}", style = MaterialTheme.typography.bodySmall)
                var fwCheck by remember { mutableStateOf<String>("Check") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        try {
                            val f = com.pockethost.desktop.util.AppLogger.getLogFile()
                            if (f.exists()) ProcessBuilder("explorer", "/select,", f.absolutePath).start()
                            else ProcessBuilder("explorer", AppPaths.logsDir.absolutePath).start()
                        } catch (_: Exception) {}
                    }) { Text("Open Logs") }
                    OutlinedButton(onClick = {
                        val port = 25565
                        val ok = com.pockethost.desktop.network.FirewallManager.checkFirewallRuleExists(port)
                        fwCheck = if (ok) "Firewall OK for $port" else "No rule for $port (need Admin)"
                    }) { Text(fwCheck) }
                }
                Text("• Windows: netsh advfirewall (needs Admin) • Linux: ufw/iptables • macOS: pfctl", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("MVP Info", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("• Only Minecraft servers (8 loaders, 4 auto-download)")
                Text("• Portable .zip distribution")
                Text("• Windows 10/11 only")
                Text("• DB: SQLDelight at ~/.pockethost/config/pockethost.db")
                Text("• Phase 2: themes, system tray, auto-start, bundled JRE, installers")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("PocketHost Desktop v1.0.0 MVP")
                Text("Kotlin Multiplatform + Compose Desktop + SQLDelight")
                Text("Ported from Android ANServer")
            }
        }
    }
}
