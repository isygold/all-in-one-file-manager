package com.fmx.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmx.manager.BrowserViewModel
import com.fmx.manager.Prefs
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

@Composable
fun FmxApp(
    vm: BrowserViewModel,
    prefs: Prefs,
    onThemeChange: (ThemeMode, Boolean) -> Unit,
) {
    var dest by remember { mutableStateOf(Dest.FILES) }
    var route by remember { mutableStateOf<Route>(Route.Browser) }
    var dual by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val vmB = remember { BrowserViewModel(ctx.applicationContext) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun openDest(d: Dest) {
        dest = d
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
            ModalDrawerSheet {
                Text(
                    "FMX Manager",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp),
                )
                Dest.entries.forEach { d ->
                    NavigationDrawerItem(
                        selected = dest == d && route == Route.Browser,
                        onClick = { openDest(d) },
                        label = { Text(d.title) },
                        icon = { Icon(d.icon, null) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "All-in-one: files · archives · apps",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
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
