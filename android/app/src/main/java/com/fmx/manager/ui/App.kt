package com.fmx.manager.ui

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmx.manager.BrowserViewModel
import com.fmx.manager.Prefs
import com.fmx.manager.RootEntry
import com.fmx.manager.humanSize
import java.io.File
import kotlinx.coroutines.launch

sealed interface Route {
    data object Browser : Route
    data class Viewer(val path: String) : Route
    data class Editor(val path: String) : Route
    data class Hex(val path: String) : Route
    data class Archive(val path: String) : Route
    data class Apk(val path: String) : Route
}

enum class Dest(val title: String, val icon: ImageVector) {
    FILES("Files", Icons.Filled.Folder),
    HOME("Home", Icons.Filled.Home),
    APPS("Apps", Icons.Filled.Apps),
    TRASH("Trash", Icons.Filled.Delete),
    TOOLS("Tools", Icons.Filled.Build),
    SETTINGS("Settings", Icons.Filled.Settings),
}

private data class Shortcut(val label: String, val dir: File, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FmxApp(
    vm: BrowserViewModel,
    prefs: Prefs,
    onThemeChange: (ThemeMode, Boolean) -> Unit,
) {
    var dest by remember { mutableStateOf(Dest.FILES) }
    var route by remember { mutableStateOf<Route>(Route.Browser) }
    var dual by remember { mutableStateOf(false) }
    var toolsStart by remember { mutableStateOf<String?>(null) }
    var toolsNonce by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val vmB = remember { BrowserViewModel(ctx.applicationContext) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ui by vm.ui.collectAsStateWithLifecycle()

    fun openDest(d: Dest) {
        dest = d
        scope.launch { drawer.close() }
    }
    fun openPath(f: File) {
        dest = Dest.FILES
        route = Route.Browser
        vm.openDir(f)
        scope.launch { drawer.close() }
    }
    fun openTool(tool: String?) {
        toolsStart = tool
        toolsNonce++
        dest = Dest.TOOLS
        scope.launch { drawer.close() }
    }
    fun openFile(f: File) {
        route = when {
            f.name.lowercase().endsWith(".apk") -> Route.Apk(f.absolutePath)
            com.fmx.manager.isArchive(f) || com.fmx.manager.PowerTools.isSevenZ(f) ->
                Route.Archive(f.absolutePath)
            f.name.lowercase().endsWith(".ttf") ||
                f.name.lowercase().endsWith(".otf") -> Route.Viewer(f.absolutePath)
            else -> Route.Viewer(f.absolutePath)
        }
    }
    fun backToBrowser() {
        route = Route.Browser
        vm.refresh()
    }
    BackHandler(enabled = route != Route.Browser) { backToBrowser() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerContent(
                path = ui.path,
                roots = vm.roots,
                bookmarks = prefs.bookmarks(),
                currentDest = if (route == Route.Browser) dest else null,
                onOpenPath = ::openPath,
                onDest = { openDest(it) },
                onTool = { openTool(it) },
            )
        },
    ) {
        when (val r = route) {
            Route.Browser -> when (dest) {
                Dest.FILES -> FilesDual(
                    vmA = vm,
                    vmB = vmB,
                    dual = dual,
                    onToggleDual = { dual = !dual },
                    prefs = prefs,
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onOpenFile = ::openFile,
                )
                Dest.HOME -> HomeScreen(
                    prefs = prefs,
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onBrowse = { openDest(Dest.FILES); vm.openDir(it) },
                    onOpenFile = ::openFile,
                    onOpenApps = { openDest(Dest.APPS) },
                )
                Dest.APPS -> AppsScreen(
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onApkFile = { route = Route.Apk(it.absolutePath) },
                )
                Dest.TRASH -> TrashScreen(
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onOpenFile = ::openFile,
                )
                Dest.TOOLS -> ToolsScreen(
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onBrowse = { openDest(Dest.FILES); vm.openDir(it) },
                    initialTool = toolsStart,
                    initialToolNonce = toolsNonce,
                )
                Dest.SETTINGS -> SettingsScreen(
                    prefs = prefs,
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onThemeChange = onThemeChange,
                )
            }
            is Route.Viewer -> ViewerScreen(
                file = File(r.path),
                onBack = ::backToBrowser,
                onEdit = { route = Route.Editor(r.path) },
                onHex = { route = Route.Hex(r.path) },
            )
            is Route.Editor -> EditorScreen(file = File(r.path), onBack = ::backToBrowser)
            is Route.Hex -> HexScreen(file = File(r.path), onBack = ::backToBrowser)
            is Route.Archive -> ArchiveScreen(
                file = File(r.path),
                onBack = ::backToBrowser,
                onOpenExtracted = { route = Route.Viewer(it.absolutePath) },
            )
            is Route.Apk -> ApkScreen(
                file = File(r.path),
                onBack = ::backToBrowser,
                onBrowseZip = { route = Route.Archive(it.absolutePath) },
            )
        }
    }
}

@Composable
private fun DrawerContent(
    path: File,
    roots: List<RootEntry>,
    bookmarks: List<String>,
    currentDest: Dest?,
    onOpenPath: (File) -> Unit,
    onDest: (Dest) -> Unit,
    onTool: (String?) -> Unit,
) {
    val ext = Environment.getExternalStorageDirectory()
    val shortcuts = remember(ext) {
        listOf(
            Shortcut("Pictures", File(ext, "Pictures"), Icons.Filled.Image),
            Shortcut("DCIM", File(ext, "DCIM"), Icons.Filled.PhotoCamera),
            Shortcut("Downloads", File(ext, "Download"), Icons.Filled.Download),
            Shortcut("Documents", File(ext, "Documents"), Icons.Filled.Description),
            Shortcut("Music", File(ext, "Music"), Icons.Filled.MusicNote),
            Shortcut("Movies", File(ext, "Movies"), Icons.Filled.Movie),
        ).filter { it.dir.isDirectory }
    }
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("FMX", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "All-in-one file manager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
            ) {
                SectionLabel("Storage")
                roots.forEach { root ->
                    DrawerRow(
                        icon = rootIcon(root),
                        label = root.label,
                        caption = spaceCaption(root.file),
                        selected = isUnder(path, root.file),
                        onClick = { onOpenPath(root.file) },
                    )
                }

                if (shortcuts.isNotEmpty()) {
                    SectionLabel("Locations")
                    shortcuts.forEach { s ->
                        DrawerRow(
                            icon = s.icon,
                            label = s.label,
                            selected = path.absolutePath == s.dir.absolutePath,
                            onClick = { onOpenPath(s.dir) },
                        )
                    }
                }

                SectionLabel("Browse")
                DrawerRow(
                    Icons.Filled.Home, "Home", "Storage & categories",
                    selected = currentDest == Dest.HOME,
                ) { onDest(Dest.HOME) }
                DrawerRow(
                    Icons.Filled.Apps, "Apps", "Backup & uninstall",
                    selected = currentDest == Dest.APPS,
                ) { onDest(Dest.APPS) }
                DrawerRow(
                    Icons.Filled.Delete, "Trash", "30-day recycle bin",
                    selected = currentDest == Dest.TRASH,
                ) { onDest(Dest.TRASH) }

                SectionLabel("Tools")
                DrawerRow(
                    Icons.Filled.Wifi, "Wi-Fi file share", "Browse from another device",
                    selected = currentDest == Dest.TOOLS,
                ) { onTool("share") }
                DrawerRow(
                    Icons.Filled.Storage, "Storage analyzer", "Largest folders & files",
                    selected = false,
                ) { onTool("analyze") }
                DrawerRow(
                    Icons.Filled.Settings, "Settings", "Theme & preferences",
                    selected = currentDest == Dest.SETTINGS,
                ) { onDest(Dest.SETTINGS) }

                if (bookmarks.isNotEmpty()) {
                    SectionLabel("Bookmarks")
                    bookmarks.forEach { bm ->
                        DrawerRow(
                            Icons.Filled.Bookmark, File(bm).name.ifBlank { bm }, bm,
                            selected = path.absolutePath == bm,
                        ) { onOpenPath(File(bm)) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "FMX Manager",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "v1.3",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    caption: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun rootIcon(root: RootEntry): ImageVector = when {
    root.label.contains("SD", true) -> Icons.Filled.SdCard
    root.label.contains("Internal", true) -> Icons.Filled.Smartphone
    root.label.contains("App", true) -> Icons.Filled.Folder
    else -> Icons.Filled.Storage
}

private fun spaceCaption(file: File): String? = try {
    val total = file.totalSpace
    val free = file.usableSpace
    if (total > 0) "${humanSize(free)} free of ${humanSize(total)}" else null
} catch (_: Exception) {
    null
}

private fun isUnder(path: File, root: File): Boolean {
    if (path.absolutePath == root.absolutePath) return true
    if (root.absolutePath == "/") return false
    val prefix = root.absolutePath.trimEnd('/') + "/"
    return path.absolutePath.startsWith(prefix)
}

/** Single- or dual-pane (MT-style) file browsing. */
@Composable
private fun FilesDual(
    vmA: BrowserViewModel,
    vmB: BrowserViewModel,
    dual: Boolean,
    onToggleDual: () -> Unit,
    prefs: Prefs,
    onOpenDrawer: () -> Unit,
    onOpenFile: (File) -> Unit,
) {
    if (!dual) {
        BrowserScreen(
            vm = vmA,
            prefs = prefs,
            onOpenDrawer = onOpenDrawer,
            onOpenFile = onOpenFile,
            onToggleDual = onToggleDual,
            dual = false,
        )
        return
    }
    val a by vmA.ui.collectAsStateWithLifecycle()
    val b by vmB.ui.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            BrowserScreen(
                vm = vmA,
                prefs = prefs,
                onOpenDrawer = onOpenDrawer,
                onOpenFile = onOpenFile,
                onToggleDual = onToggleDual,
                dual = true,
                peerPath = b.path,
            )
        }
        Box(
            Modifier.width(1.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline),
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            BrowserScreen(
                vm = vmB,
                prefs = prefs,
                onOpenDrawer = onOpenDrawer,
                onOpenFile = onOpenFile,
                onToggleDual = onToggleDual,
                dual = true,
                peerPath = a.path,
            )
        }
    }
}
