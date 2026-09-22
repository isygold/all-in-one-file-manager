package com.fmx.manager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmx.manager.PowerTools
import com.fmx.manager.ShareServer
import com.fmx.manager.humanSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onOpenDrawer: () -> Unit, onBrowse: (File) -> Unit) {
    var tool by remember { mutableStateOf<String?>(null) }
    when (tool) {
        "share" -> ShareTool(onBack = { tool = null })
        "analyze" -> AnalyzeTool(onBack = { tool = null }, onBrowse = onBrowse)
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                    },
                    title = { Text("Tools") },
                )
            },
        ) { pad ->
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {
                item {
                    ToolCard(
                        "Wi-Fi file share",
                        "Serve files to other devices over HTTP",
                        Icons.Filled.Share,
                    ) { tool = "share" }
                    Spacer(Modifier.height(8.dp))
                    ToolCard(
                        "Storage analyzer",
                        "Biggest folders & largest files",
                        Icons.Filled.Storage,
                    ) { tool = "analyze" }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(sub) },
            leadingContent = {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareTool(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val running by ShareServer.running.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf<String?>(ShareServer.url) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = { Text("Wi-Fi share") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Text(
                "Shares Internal storage for browsing + download on your Wi-Fi network.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            if (!running) {
                Button({
                    try {
                        url = ShareServer.start(Environment.getExternalStorageDirectory())
                    } catch (e: Exception) {
                        url = "Failed: ${e.message}"
                    }
                }) { Text("Start server") }
            } else {
                Button({ ShareServer.stop() }) { Text("Stop server") }
            }
            Spacer(Modifier.height(16.dp))
            url?.let {
                Text("Open on the other device:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton({
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("url", it))
                }) { Text("Copy URL") }
            }
        }
    }
}

private data class Analyzed(val folders: List<Pair<File, Long>>, val biggest: List<File>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyzeTool(onBack: () -> Unit, onBrowse: (File) -> Unit) {
    val root = Environment.getExternalStorageDirectory()
    val data by produceState<Analyzed?>(null) {
        value = withContext(Dispatchers.IO) {
            val folders = ArrayList<Pair<File, Long>>()
            val biggest = ArrayList<File>()
            try {
                root.listFiles()?.forEach { f ->
                    val sz = if (f.isDirectory) PowerTools.dirSize(f, 8000) else f.length()
                    if (f.isDirectory) folders += f to sz
                    else biggest += f
                }
                // sample largest files across top-level dirs (capped walk)
                try {
                    var visited = 0
                    root.walkTopDown().onFail { _, _ -> }.forEach {
                        if (++visited > 15000) return@forEach
                        if (it.isFile) biggest += it
                    }
                } catch (_: Exception) {
                }
            } catch (_: Exception) {
            }
            Analyzed(
                folders.sortedByDescending { it.second }.take(20),
                biggest.sortedByDescending { it.length() }.take(15),
            )
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = { Text("Storage analyzer") },
            )
        },
    ) { pad ->
        val d = data
        if (d == null) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Scanning…")
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Text(
                    "Biggest folders",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
            }
            val max = d.folders.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
            items(d.folders) { (f, sz) ->
                ListItem(
                    headlineContent = {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Column {
                            LinearProgressIndicator(
                                progress = { (sz.toFloat() / max).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(humanSize(sz))
                        }
                    },
                    leadingContent = { Icon(Icons.Filled.Folder, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Largest files",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
            }
            items(d.biggest) { f ->
                ListItem(
                    headlineContent = {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("${humanSize(f.length())} · ${f.parent}") },
                    leadingContent = { Icon(Icons.Filled.Description, null) },
                )
            }
        }
    }
}
