package com.pockethost.desktop

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pockethost.desktop.ui.App
import com.pockethost.desktop.util.TrayManager
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    var showCloseDialog by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = { showCloseDialog = true },
        title = "PocketHost",
        state = windowState
    ) {
        window.minimumSize = Dimension(1024, 600)
        try {
            TrayManager.getWindowIcon()?.let { window.iconImage = it }
        } catch (_: Exception) {}

        // Tray setup effect
        LaunchedEffect(Unit) {
            // Preload tray icon support check
        }

        App()

        if (showCloseDialog) {
            AlertDialog(
                onDismissRequest = { showCloseDialog = false },
                title = { Text("Закрити PocketHost?") },
                text = { Text("Оберіть дію: згорнути в системний трей або повністю закрити програму.") },
                confirmButton = {
                    Button(onClick = {
                        showCloseDialog = false
                        val ok = TrayManager.install(
                            onOpen = {
                                window.isVisible = true
                                window.toFront()
                                window.requestFocus()
                            },
                            onStartServers = {
                                window.isVisible = true
                                window.toFront()
                            },
                            onSettings = {
                                window.isVisible = true
                                window.toFront()
                            },
                            onExit = { exitApplication() }
                        )
                        if (ok) {
                            window.isVisible = false
                        } else {
                            window.isVisible = false
                        }
                    }) { Text("Згорнути в трей") }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showCloseDialog = false
                        TrayManager.uninstall()
                        exitApplication()
                    }) { Text("Закрити повністю") }
                }
            )
        }

        DisposableEffect(Unit) {
            onDispose { TrayManager.uninstall() }
        }
    }
}
