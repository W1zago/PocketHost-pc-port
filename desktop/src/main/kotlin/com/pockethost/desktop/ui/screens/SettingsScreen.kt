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
    val lang by Strings.language.collectAsState()
    var javaInfo by remember { mutableStateOf<String>(Strings.tr("settings.checking")) }
    var appDirInfo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val java = JavaManager.findJava()
        javaInfo = if (java != null) "${java.path} (v${java.version} ${java.vendor})" else Strings.tr("settings.javaNotFound")
        appDirInfo = AppPaths.appDir.absolutePath
    }

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
                Text(Strings.tr("settings.currentLang").replace("{lang}", lang.name), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.tr("settings.general"), style = MaterialTheme.typography.titleMedium)
                Text("${Strings.tr("settings.appDir")} $appDirInfo", style = MaterialTheme.typography.bodySmall)
                Text("${Strings.tr("settings.servers")} $serversDirPath", style = MaterialTheme.typography.bodySmall)
                if (isCustom) Text(Strings.tr("settings.customLocation"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                else Text(Strings.tr("settings.defaultLocation"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${Strings.tr("settings.database")} ${AppPaths.databaseFile().absolutePath}", style = MaterialTheme.typography.bodySmall)
                Text("${Strings.tr("settings.java")} $javaInfo", style = MaterialTheme.typography.bodySmall)
                var checking by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        try {
                            val chooser = javax.swing.JFileChooser(serversDirPath)
                            chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                            chooser.dialogTitle = Strings.tr("settings.chooseDialogTitle")
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
                    }) { Text(Strings.tr("settings.chooseFolder")) }
                    if (isCustom) {
                        OutlinedButton(onClick = {
                            AppPaths.resetServersDir()
                            serversDirPath = AppPaths.serversDir.absolutePath
                            isCustom = false
                        }) { Text(Strings.tr("settings.resetDefault")) }
                    }
                    OutlinedButton(onClick = {
                        try { ProcessBuilder("explorer", serversDirPath).start() } catch (_: Exception) {}
                    }) { Text(Strings.tr("settings.open")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            checking = true
                            val j = JavaManager.ensureJava(17) { msg -> javaInfo = msg }
                            javaInfo = if (j != null) "${j.path} (v${j.version})" else Strings.tr("settings.javaDownloadFailed")
                            checking = false
                        }
                    }, enabled = !checking) {
                        if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(Strings.tr("settings.checkJava"))
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.tr("settings.logsFirewall"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("${Strings.tr("settings.appLog")} ${com.pockethost.desktop.util.AppLogger.getLogFile().absolutePath}", style = MaterialTheme.typography.bodySmall)
                Text("${Strings.tr("settings.serverLogs")} ${AppPaths.logsDir.absolutePath}", style = MaterialTheme.typography.bodySmall)
                var fwCheck by remember { mutableStateOf<String>(Strings.tr("settings.checkFw")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        try {
                            val f = com.pockethost.desktop.util.AppLogger.getLogFile()
                            if (f.exists()) ProcessBuilder("explorer", "/select,", f.absolutePath).start()
                            else ProcessBuilder("explorer", AppPaths.logsDir.absolutePath).start()
                        } catch (_: Exception) {}
                    }) { Text(Strings.tr("settings.openLogs")) }
                    OutlinedButton(onClick = {
                        val port = 25565
                        val ok = com.pockethost.desktop.network.FirewallManager.checkFirewallRuleExists(port)
                        fwCheck = if (ok) Strings.tr("settings.fwOk").replace("{port}", port.toString()) else Strings.tr("settings.fwNeedAdmin").replace("{port}", port.toString())
                    }) { Text(fwCheck) }
                }
                Text(Strings.tr("settings.fwPlatforms"), style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.tr("settings.mvpTitle"), style = MaterialTheme.typography.titleMedium)
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
                Text(Strings.tr("settings.aboutTitle"), style = MaterialTheme.typography.titleMedium)
                Text("PocketHost Desktop v1.0.0 MVP")
                Text("Kotlin Multiplatform + Compose Desktop + SQLDelight")
                Text("Ported from Android ANServer")
            }
        }
    }
}
