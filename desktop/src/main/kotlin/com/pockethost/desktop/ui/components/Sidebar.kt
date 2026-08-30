package com.pockethost.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pockethost.common.i18n.Language
import com.pockethost.common.i18n.Strings
import com.pockethost.desktop.ui.Screen

@Composable
fun Sidebar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    Surface(
        modifier = Modifier.width(220.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "PocketHost",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                text = Strings.tr("app.subtitle"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            SidebarItem(
                icon = Icons.Default.Storage,
                label = Strings.tr("nav.servers"),
                selected = currentScreen is Screen.ServerList || currentScreen is Screen.ServerDetail,
                onClick = { onScreenSelected(Screen.ServerList) }
            )
            SidebarItem(
                icon = Icons.Default.Add,
                label = Strings.tr("nav.newServer"),
                selected = currentScreen is Screen.CreateServer,
                onClick = { onScreenSelected(Screen.CreateServer) }
            )

            Spacer(Modifier.weight(1f))

            SidebarItem(
                icon = Icons.Default.Settings,
                label = Strings.tr("nav.settings"),
                selected = currentScreen is Screen.Settings,
                onClick = { onScreenSelected(Screen.Settings) }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "MVP • Windows • v1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
