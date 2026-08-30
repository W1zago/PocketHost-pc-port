package com.pockethost.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.pockethost.desktop.ui.components.Sidebar
import com.pockethost.desktop.ui.screens.CreateServerScreen
import com.pockethost.desktop.ui.screens.ServerDetailScreen
import com.pockethost.desktop.ui.screens.ServerListScreen
import com.pockethost.desktop.ui.screens.SettingsScreen

sealed class Screen {
    object ServerList : Screen()
    data class ServerDetail(val serverId: String) : Screen()
    object CreateServer : Screen()
    object Settings : Screen()
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.ServerList) }
    var selectedServerId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showError(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    MaterialTheme(
        colorScheme = darkColorScheme()
        // Use dark by default for MVP, light toggle Phase 2
    ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
            Surface(modifier = Modifier.fillMaxSize().padding(paddingValues), color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxSize()) {
            Sidebar(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    currentScreen = screen
                    if (screen is Screen.ServerList) {
                        // keep selection
                    }
                }
            )
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        when (val screen = currentScreen) {
                            is Screen.ServerList -> ServerListScreen(
                                onServerSelected = { server ->
                                    selectedServerId = server.id
                                    currentScreen = Screen.ServerDetail(server.id)
                                },
                                onCreateServer = { currentScreen = Screen.CreateServer },
                                selectedServerId = selectedServerId,
                                onError = ::showError
                            )
                            is Screen.ServerDetail -> ServerDetailScreen(
                                serverId = screen.serverId,
                                onBack = { currentScreen = Screen.ServerList },
                                onServerDeleted = { currentScreen = Screen.ServerList },
                                onError = ::showError
                            )
                            is Screen.CreateServer -> CreateServerScreen(
                                onServerCreated = { server ->
                                    selectedServerId = server.id
                                    currentScreen = Screen.ServerDetail(server.id)
                                },
                                onCancel = { currentScreen = Screen.ServerList },
                                onError = ::showError
                            )
                            is Screen.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
