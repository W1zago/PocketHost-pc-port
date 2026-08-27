package com.pockethost.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("General", style = MaterialTheme.typography.titleMedium)
                Text("App Directory: $appDirInfo", style = MaterialTheme.typography.bodySmall)
                Text("Servers: ${AppPaths.serversDir.absolutePath}", style = MaterialTheme.typography.bodySmall)
                Text("Database: ${AppPaths.databaseFile().absolutePath}", style = MaterialTheme.typography.bodySmall)
                Text("Java: $javaInfo", style = MaterialTheme.typography.bodySmall)
                var checking by remember { mutableStateOf(false) }
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
