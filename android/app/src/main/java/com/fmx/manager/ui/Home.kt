package com.fmx.manager.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fmx.manager.Prefs
import com.fmx.manager.humanSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Cat(
    val label: String,
    val icon: ImageVector,
    val dir: File?,
    val apps: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: Prefs,
    onOpenDrawer: () -> Unit,
    onBrowse: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenApps: () -> Unit,
) {
    @Suppress("unused")
    prefs
    val ext = Environment.getExternalStorageDirectory()
    val cats = listOf(
        Cat("Images", Icons.Filled.Image, firstExisting(ext, "DCIM", "Pictures")),
        Cat("Audio", Icons.Filled.Audiotrack, firstExisting(ext, "Music", "Audio")),
        Cat("Video", Icons.Filled.Movie, firstExisting(ext, "Movies", "Video")),
        Cat("Downloads", Icons.Filled.Download, firstExisting(ext, "Download", "Downloads")),
        Cat("Documents", Icons.Filled.Description, firstExisting(ext, "Documents", "Docs")),
        Cat("APK & backup", Icons.Filled.Archive, firstExisting(ext, "FMX", "Download")),
        Cat("Apps", Icons.Filled.Apps, null, apps = true),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                },
                title = { Text("Home") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {
            StorageCard(ext)
            Spacer(Modifier.height(12.dp))
            Text("Categories", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cats) { c ->
                    val stats by produceState<Pair<Int, Long>?>(null, c.label) {
                        value = if (c.dir != null) catStats(c.dir) else null
                    }
                    Card(onClick = {
                        if (c.apps) onOpenApps()
                        else c.dir?.let(onBrowse)
                    }) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.padding(4.dp))
                            Column {
                                Text(c.label, style = MaterialTheme.typography.titleSmall)
                                val s = stats
                                Text(
                                    if (c.apps) "Installed apps"
                                    else if (s == null) "…"
                                    else "${s.first} files · ${humanSize(s.second)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCard(ext: File) {
    val info = rememberStorage(ext)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Internal storage", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${humanSize(info.used)} of ${humanSize(info.total)} used",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (info.used.toFloat() / info.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${humanSize(info.free)} free",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private data class StorageInfo(val total: Long, val free: Long, val used: Long)

@Composable
private fun rememberStorage(dir: File): StorageInfo {
    return try {
        val st = StatFs(dir.absolutePath)
        val total = st.totalBytes
        val free = st.availableBytes
        StorageInfo(total, free, (total - free).coerceAtLeast(0))
    } catch (_: Exception) {
        StorageInfo(1, 1, 0)
    }
}

private suspend fun catStats(dir: File): Pair<Int, Long> = withContext(Dispatchers.IO) {
    var n = 0
    var bytes = 0L
    var visited = 0
    try {
        dir.walkTopDown().onFail { _, _ -> }.forEach {
            if (++visited > 8000) return@withContext n to bytes
            if (it.isFile) {
                n++
                bytes += it.length()
            }
        }
    } catch (_: Exception) {
    }
    n to bytes
}

private fun firstExisting(root: File, vararg names: String): File? {
    names.forEach {
        val f = File(root, it)
        if (f.isDirectory) return f
    }
    return null
}
