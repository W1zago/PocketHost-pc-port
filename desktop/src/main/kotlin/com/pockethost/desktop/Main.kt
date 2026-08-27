package com.pockethost.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.pockethost.desktop.ui.App
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "PocketHost",
        state = windowState
    ) {
        window.minimumSize = Dimension(1024, 600)
        App(onExit = ::exitApplication)
    }
}
