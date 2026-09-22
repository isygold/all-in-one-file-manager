package com.fmx.manager.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fmx.manager.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: Prefs,
    onOpenDrawer: () -> Unit,
    onThemeChange: (ThemeMode, Boolean) -> Unit,
) {
    var current by remember { mutableStateOf(prefs.theme) }
    var dynamic by remember { mutableStateOf(prefs.dynamicColor) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                },
                title = { Text("Settings") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "system" to "System default",
                "dark" to "Dark",
                "black" to "True black (Material-style)",
                "light" to "Light",
            ).forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = current == key,
                        onClick = {
                            current = key
                            onThemeChange(
                                runCatching { ThemeMode.valueOf(key.uppercase()) }
                                    .getOrDefault(ThemeMode.SYSTEM),
                                dynamic,
                            )
                        },
                    )
                    Text(label)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = dynamic,
                    enabled = Build.VERSION.SDK_INT >= 31,
                    onCheckedChange = {
                        dynamic = it
                        onThemeChange(
                            runCatching { ThemeMode.valueOf(current.uppercase()) }
                                .getOrDefault(ThemeMode.SYSTEM),
                            it,
                        )
                    },
                )
                Text(
                    "Dynamic (Material You) colors" +
                        if (Build.VERSION.SDK_INT < 31) " — needs Android 12+" else "",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "FMX Manager 1.2 — all-in-one file manager.\n" +
                    "File workflows inspired by ZArchiver (archives), " +
                    "MT Manager (APK tools, dual pane), MiXplorer " +
                    "(tabs, trash, servers, tools) and Material Files " +
                    "(Material Design, Linux-aware details).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
