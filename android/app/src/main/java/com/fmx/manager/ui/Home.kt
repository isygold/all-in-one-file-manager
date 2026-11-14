package com.fmx.manager.ui

import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmx.manager.Prefs
import com.fmx.manager.humanSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Category(
    val label: String,
    val icon: ImageVector,
    val dir: File?,
    val tint: Color,
    val apps: Boolean = false,
)

private data class Volume(
    val label: String,
    val dir: File,
    val total: Long,
    val free: Long,
    val removable: Boolean,
)

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: Prefs,
    onOpenDrawer: () -> Unit,
    onBrowse: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenApps: () -> Unit,
) {
    val ext = Environment.getExternalStorageDirectory()
    val palette = LocalFmxPalette.current
    val volumes = rememberVolumes()
    val categories = remember(palette) {
        buildList {
            firstExisting(ext, "DCIM", "Pictures")?.let {
                add(Category("Images", Icons.Filled.Image, it, palette.image))
            }
            firstExisting(ext, "Movies", "Video")?.let {
                add(Category("Video", Icons.Filled.Movie, it, palette.video))
            }
            firstExisting(ext, "Music", "Audio")?.let {
                add(Category("Audio", Icons.Filled.MusicNote, it, palette.audio))
            }
            firstExisting(ext, "Documents", "Docs")?.let {
                add(Category("Documents", Icons.Filled.Description, it, palette.document))
            }
            firstExisting(ext, "Download", "Downloads")?.let {
                add(Category("Downloads", Icons.Filled.Download, it, palette.archive))
            }
            val backups = firstExisting(ext, "FMX") ?: firstExisting(ext, "Download", "Downloads")
            add(Category("APK & backups", Icons.Filled.Archive, backups, palette.apk))
            add(Category("Apps", Icons.Filled.Apps, null, palette.document, apps = true))
        }
    }
    val shortcuts = remember(ext) {
        listOf(
            "Downloads" to File(ext, "Download"),
            "DCIM" to File(ext, "DCIM"),
            "Pictures" to File(ext, "Pictures"),
            "Music" to File(ext, "Music"),
            "Movies" to File(ext, "Movies"),
        ).filter { it.second.isDirectory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                },
                title = {
                    Column {
                        Text("Home", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Storage & categories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            item { SectionLabel("Storage") }
            items(volumes) { volume ->
                VolumeCard(volume, onClick = { onBrowse(volume.dir) })
                Spacer(Modifier.height(8.dp))
            }

            item { SectionLabel("Categories") }
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { cat ->
                                CategoryTile(
                                    cat = cat,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (cat.apps) onOpenApps()
                                        else cat.dir?.let(onBrowse)
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (shortcuts.isNotEmpty()) {
                item { SectionLabel("Shortcuts") }
                items(shortcuts) { (label, dir) ->
                    ShortcutRow(label, dir, onClick = { onBrowse(dir) })
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun VolumeCard(volume: Volume, onClick: () -> Unit) {
    val used = (volume.total - volume.free).coerceAtLeast(0)
    val fraction = (used.toFloat() / volume.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (volume.removable) Icons.Filled.SdCard else Icons.Filled.Smartphone,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    volume.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${humanSize(used)} used of ${humanSize(volume.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${humanSize(volume.free)} free",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(cat: Category, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cat.tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(cat.icon, null, tint = cat.tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cat.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val stats by produceState<Pair<Int, Long>?>(null, cat.label) {
                    value = cat.dir?.let { catStats(it) }
                }
                Text(
                    when {
                        cat.apps -> "Installed apps"
                        stats == null -> "Scanning…"
                        else -> "${stats!!.first} files · ${humanSize(stats!!.second)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(label: String, dir: File, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Folder,
            null,
            tint = LocalFmxPalette.current.folder,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                dir.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Filled.ArrowForward,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun rememberVolumes(): List<Volume> {
    val ctx = LocalContext.current
    return remember {
        val out = ArrayList<Volume>()
        fun add(label: String, dir: File, removable: Boolean) {
            try {
                if (!dir.isDirectory) return
                if (out.any { it.dir.absolutePath == dir.absolutePath }) return
                val total = dir.totalSpace
                val free = dir.usableSpace
                if (total <= 0) return
                out += Volume(label, dir, total, free, removable)
            } catch (_: Exception) {
            }
        }
        add("Internal shared storage", Environment.getExternalStorageDirectory(), false)
        try {
            val sm = ctx.getSystemService(StorageManager::class.java)
            sm?.storageVolumes?.forEach { v ->
                val dir = v.directory ?: return@forEach
                if (v.isRemovable) add(v.getDescription(ctx), dir, true)
            }
        } catch (_: Exception) {
        }
        out
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
