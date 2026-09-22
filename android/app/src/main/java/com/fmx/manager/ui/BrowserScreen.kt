package com.fmx.manager.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmx.manager.AUDIO_EXTS
import com.fmx.manager.BrowserViewModel
import com.fmx.manager.ClipOp
import com.fmx.manager.FileItem
import com.fmx.manager.IMAGE_EXTS
import com.fmx.manager.Prefs
import com.fmx.manager.PowerTools
import com.fmx.manager.SortMode
import com.fmx.manager.TEXT_EXTS
import com.fmx.manager.VIDEO_EXTS
import com.fmx.manager.fmtDate
import com.fmx.manager.humanSize
import com.fmx.manager.openWith
import com.fmx.manager.shareFiles
import java.io.File
import kotlinx.coroutines.launch

fun iconFor(item: FileItem): ImageVector = when {
    item.isDir -> Icons.Filled.Folder
    item.ext in IMAGE_EXTS -> Icons.Filled.Image
    item.ext in VIDEO_EXTS -> Icons.Filled.Movie
    item.ext in AUDIO_EXTS -> Icons.Filled.Audiotrack
    item.ext in setOf("zip", "apk", "jar", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar") ->
        Icons.Filled.Archive
    item.ext == "pdf" -> Icons.Filled.PictureAsPdf
    item.ext in TEXT_EXTS -> Icons.Filled.TextSnippet
    else -> Icons.Filled.Description
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    prefs: Prefs,
    onOpenDrawer: () -> Unit,
    onOpenFile: (File) -> Unit,
    onToggleDual: (() -> Unit)? = null,
    dual: Boolean = false,
    peerPath: File? = null,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var inContent by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<FileItem?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<File?>(null) }
    var chmodFor by remember { mutableStateOf<List<File>>(emptyList()) }
    var compressAsk by remember { mutableStateOf(false) }
    var batchAsk by remember { mutableStateOf(false) }
    var compareAsk by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var permDelete by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var pwTarget by remember { mutableStateOf<File?>(null) } // AES encrypt/decrypt
    var pwText by remember { mutableStateOf("") }
    var splitTarget by remember { mutableStateOf<File?>(null) }
    var splitMb by remember { mutableStateOf("100") }
    val selecting = ui.selection.isNotEmpty()

    BackHandler {
        when {
            selecting -> vm.clearSelection()
            searchOpen -> {
                searchOpen = false
                searchText = ""
                vm.clearSearch()
            }
            else -> if (!vm.goUp()) (ctx as? Activity)?.finish()
        }
    }

    LaunchedEffect(ui.notice, ui.error) {
        (ui.notice ?: ui.error)?.let { snacks.showSnackbar(it) }
        vm.consumeNotice()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        bottomBar = {
            if (selecting) {
                BottomAppBar {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton({ chmodFor = vm.selectedFiles() }) { Text("Permissions") }
                        TextButton({ compressAsk = true }) { Text("Compress") }
                        TextButton({
                            val sel = vm.selectedFiles()
                            if (sel.size == 1) renameFor = sel[0] else batchAsk = true
                        }) { Text("Rename") }
                        if (vm.selectedFiles().size == 2) {
                            TextButton({ compareAsk = true }) { Text("Compare") }
                        }
                        if (peerPath != null) {
                            TextButton({ vm.copySelectedTo(peerPath) }) { Text("Copy →") }
                            TextButton({ vm.moveSelectedTo(peerPath) }) { Text("Move →") }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton({ vm.paste() }) { Text("Paste here") }
                    }
                }
            }
        },
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton({ vm.clearSelection() }) {
                            Icon(Icons.Filled.Close, "Clear selection")
                        }
                    },
                    title = { Text("${ui.selection.size} selected") },
                    actions = {
                        IconButton({ vm.copySel() }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                        IconButton({ vm.cutSel(); }) { Icon(Icons.Filled.ContentCut, "Cut") }
                        IconButton({ vm.selectedFiles().let(ctx::shareFiles) }) {
                            Icon(Icons.Filled.Share, "Share")
                        }
                        IconButton({ confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "Delete")
                        }
                        IconButton({ vm.selectAll() }) { Icon(Icons.Filled.Check, "Select all") }
                    },
                )
            } else if (searchOpen) {
                TopAppBar(
                    navigationIcon = {
                        IconButton({
                            searchOpen = false
                            searchText = ""
                            vm.clearSearch()
                        }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    },
                    title = {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("Search here…") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { vm.search(searchText, inContent) },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = {
                        IconButton({ vm.search(searchText, inContent) }) {
                            Icon(Icons.Filled.Search, "Search")
                        }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                    },
                    title = {
                        Text(
                            ui.path.name.ifBlank { ui.path.path },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton({ vm.goUp() }) {
                            Icon(Icons.Filled.ArrowBack, "Up")
                        }
                        IconButton({ searchOpen = true }) {
                            Icon(Icons.Filled.Search, "Search")
                        }
                        Box {
                            IconButton({ sortMenu = true }) {
                                Icon(Icons.Filled.Sort, "Sort")
                            }
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                SortMode.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(sortLabel(m, ui.sort == m, ui.asc)) },
                                        onClick = {
                                            if (ui.sort == m) vm.setSort(m, !ui.asc)
                                            else vm.setSort(m, true)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton({ overflow = true }) {
                                Icon(Icons.Filled.MoreVert, "More")
                            }
                            DropdownMenu(overflow, { overflow = false }) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = { overflow = false; vm.refresh() },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Refresh, null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (ui.grid) "List view" else "Grid view") },
                                    onClick = { overflow = false; vm.toggleGrid() },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (ui.showHidden) "Hide hidden" else "Show hidden") },
                                    onClick = { overflow = false; vm.toggleHidden() },
                                )
                                if (onToggleDual != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (dual) "Single pane" else "Dual pane") },
                                        onClick = { overflow = false; onToggleDual() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    onClick = { overflow = false; showBookmarks = true },
                                    leadingIcon = { Icon(Icons.Filled.Star, null) },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton({ showNew = true }) {
                    Icon(Icons.Filled.Add, "New")
                }
            } else {
                ExtendedFloatingActionButton(
                    text = { Text("Paste here") },
                    icon = { Icon(Icons.Filled.ContentPaste, null) },
                    onClick = { vm.paste() },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Tabs (MiX-style unlimited tabs).
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ui.tabs.forEachIndexed { i, t ->
                    val active = i == ui.activeTab
                    Surface(
                        tonalElevation = if (active) 4.dp else 0.dp,
                        shape = MaterialTheme.shapes.small,
                        color = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Row(
                            Modifier.combinedClickable({ vm.switchTab(i) })
                                .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                File(t).name.ifBlank { "/" },
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (ui.tabs.size > 1) {
                                Text(
                                    "  ✕ ",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.combinedClickable({ vm.closeTab(i) })
                                        .padding(2.dp),
                                )
                            } else {
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                }
                TextButton({ vm.newTab() }) { Text("+") }
            }
            // Storage roots — always one tap away.
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.roots.forEach { r ->
                    AssistChip(
                        onClick = { vm.openDir(r.file) },
                        label = { Text(r.label) },
                    )
                }
            }
            // Breadcrumb path.
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val segs = ui.path.absolutePath.split("/").filter { it.isNotEmpty() }
                TextButton({ vm.openDir(File("/")) }) { Text("/") }
                segs.forEachIndexed { i, s ->
                    Text("›", color = MaterialTheme.colorScheme.outline)
                    TextButton({
                        var p = ""
                        repeat(i + 1) { k -> p += "/" + segs[k] }
                        vm.openDir(File(p))
                    }) {
                        Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // Clipboard banner.
            ui.clipboard?.let { clip ->
                val n = when (clip) {
                    is ClipOp.Copy -> clip.paths.size
                    is ClipOp.Cut -> clip.paths.size
                }
                val kind = if (clip is ClipOp.Copy) "Copying" else "Moving"
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$kind $n item(s) → ${ui.path.name.ifBlank { "/" }}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton({ vm.paste() }) { Text("Paste") }
                    }
                }
            }
            if (searchOpen) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = inContent,
                        onClick = {
                            inContent = !inContent
                            if (searchText.isNotBlank()) vm.search(searchText, inContent)
                        },
                        label = { Text("Inside files") },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Names" + if (inContent) " + contents" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (ui.searching != null) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Results for \"${ui.searching}\"",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({ vm.clearSearch() }) { Text("Clear") }
                }
            }
            // List.
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.error != null && ui.entries.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error!!)
                        Spacer(Modifier.height(8.dp))
                        TextButton({ vm.refresh() }) { Text("Retry") }
                    }
                }
                ui.entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Empty folder", color = MaterialTheme.colorScheme.outline)
                }
                !ui.grid -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.entries, key = { it.file.absolutePath }) { item ->
                        val sel = ui.selection.contains(item.file.absolutePath)
                        ListItem(
                            headlineContent = {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    if (item.isDir) "Folder · ${fmtDate(item.lastModified)}"
                                    else "${humanSize(item.size)} · ${fmtDate(item.lastModified)}",
                                    maxLines = 1,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    iconFor(item),
                                    null,
                                    tint = if (item.isDir) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                            },
                            trailingContent = {
                                if (selecting) {
                                    Checkbox(sel, { vm.toggleSelect(item.file.absolutePath) })
                                } else {
                                    IconButton({ sheetFor = item }) {
                                        Icon(Icons.Filled.MoreVert, "Actions")
                                    }
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (selecting) vm.toggleSelect(item.file.absolutePath)
                                    else if (item.isDir) vm.openDir(item.file)
                                    else onOpenFile(item.file)
                                },
                                onLongClick = { vm.toggleSelect(item.file.absolutePath) },
                            ),
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.entries, key = { it.file.absolutePath }) { item ->
                        val sel = ui.selection.contains(item.file.absolutePath)
                        Column(
                            Modifier.combinedClickable(
                                onClick = {
                                    if (selecting) vm.toggleSelect(item.file.absolutePath)
                                    else if (item.isDir) vm.openDir(item.file)
                                    else onOpenFile(item.file)
                                },
                                onLongClick = { vm.toggleSelect(item.file.absolutePath) },
                            )
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                )
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                Icon(
                                    iconFor(item),
                                    null,
                                    tint = if (item.isDir) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp),
                                )
                                if (sel) {
                                    Icon(
                                        Icons.Filled.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp).align(Alignment.TopEnd),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                if (item.isDir) "Folder" else humanSize(item.size),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- bottom sheet: details + actions for one file
    sheetFor?.let { item ->
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet({ sheetFor = null }) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFor(item), null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (item.isDir) "Folder" else humanSize(item.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!item.isDir) {
                    SheetRow("Open with…", Icons.Filled.Info) {
                        sheetFor = null
                        try {
                            ctx.openWith(item.file)
                        } catch (_: Exception) {
                        }
                    }
                }
                SheetRow("Rename", Icons.Filled.Info) {
                    sheetFor = null
                    renameFor = item.file
                }
                SheetRow("Copy", Icons.Filled.ContentCopy) {
                    vm.clearSelection()
                    vm.toggleSelect(item.file.absolutePath)
                    vm.copySel()
                    vm.clearSelection()
                    sheetFor = null
                }
                SheetRow("Cut", Icons.Filled.ContentCut) {
                    vm.clearSelection()
                    vm.toggleSelect(item.file.absolutePath)
                    vm.cutSel()
                    vm.clearSelection()
                    sheetFor = null
                }
                SheetRow("Share", Icons.Filled.Share) {
                    sheetFor = null
                    ctx.shareFiles(listOf(item.file))
                }
                SheetRow("Copy path", Icons.Filled.ContentCopy) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("path", item.file.absolutePath))
                    sheetFor = null
                    vm.postNotice("Path copied")
                }
                if (!item.isDir) {
                    SheetRow("Encrypt (AES)…", Icons.Filled.Info) {
                        pwText = ""
                        pwTarget = item.file
                        sheetFor = null
                    }
                }
                if (item.file.name.endsWith(".aes")) {
                    SheetRow("Decrypt (AES)…", Icons.Filled.Info) {
                        pwText = ""
                        pwTarget = item.file
                        sheetFor = null
                    }
                }
                if (!item.isDir && !item.file.name.endsWith(".aes")) {
                    SheetRow("Split file…", Icons.Filled.Info) {
                        splitMb = "100"
                        splitTarget = item.file
                        sheetFor = null
                    }
                }
                if (Regex(""".*\.\d{3}$""").matches(item.file.name)) {
                    SheetRow("Merge parts", Icons.Filled.Info) {
                        sheetFor = null
                        val target = item.file
                        scope.launch {
                            try {
                                val out = PowerTools.mergeParts(target, target.parentFile!!)
                                vm.postNotice("Merged → ${out.name}")
                            } catch (e: Exception) {
                                vm.postNotice("Merge failed: ${e.message}")
                            }
                            vm.refresh()
                        }
                    }
                }
                val linkTarget = remember(item.file.absolutePath) {
                    try {
                        val c = item.file.canonicalPath
                        if (c != item.file.absolutePath) c else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (linkTarget != null) {
                    Text(
                        "Link → $linkTarget",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                SheetRow("Delete", Icons.Filled.Delete) {
                    sheetFor = null
                    vm.clearSelection()
                    vm.toggleSelect(item.file.absolutePath)
                    confirmDelete = true
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ---- dialogs
    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New") },
            text = {
                Column {
                    var name by remember { mutableStateOf("") }
                    OutlinedTextField(
                        name, { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({
                            if (name.isNotBlank()) {
                                vm.mkdir(name.trim())
                                showNew = false
                            }
                        }) { Text("Folder") }
                        TextButton({
                            if (name.isNotBlank()) {
                                vm.mkfile(name.trim())
                                showNew = false
                            }
                        }) { Text("File") }
                    }
                }
            },
            confirmButton = { TextButton({ showNew = false }) { Text("Close") } },
        )
    }
    renameFor?.let { f ->
        var name by remember(f.absolutePath) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton({
                    if (name.isNotBlank()) vm.rename(f, name.trim())
                    renameFor = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ renameFor = null }) { Text("Cancel") } },
        )
    }
    if (chmodFor.isNotEmpty()) {
        var mode by remember { mutableStateOf("755") }
        AlertDialog(
            onDismissRequest = { chmodFor = emptyList() },
            title = { Text("Permissions") },
            text = {
                Column {
                    OutlinedTextField(mode, { mode = it.filter(Char::isDigit) }, label = {
                        Text("Octal (e.g. 755)")
                    }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("644", "755", "777").forEach { p ->
                            TextButton({ mode = p }) { Text(p) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    vm.chmod(chmodFor, mode.ifBlank { "644" })
                    chmodFor = emptyList()
                }) { Text("Apply") }
            },
            dismissButton = { TextButton({ chmodFor = emptyList() }) { Text("Cancel") } },
        )
    }
    if (compressAsk) {
        var name by remember { mutableStateOf(ui.path.name.ifBlank { "archive" } + ".zip") }
        var tar by remember { mutableStateOf(false) }
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { compressAsk = false },
            title = { Text("Compress ${ui.selection.size} item(s)") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(tar, { tar = it })
                        Text("TAR format")
                    }
                    if (!tar) {
                        OutlinedTextField(pw, { pw = it }, label = {
                            Text("Password (optional, AES)")
                        }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                TextButton({
                    if (name.isNotBlank()) vm.compressSel(name.trim(), tar, pw)
                    compressAsk = false
                }) { Text("Compress") }
            },
            dismissButton = { TextButton({ compressAsk = false }) { Text("Cancel") } },
        )
    }
    pwTarget?.let { target ->
        val encrypt = !target.name.endsWith(".aes")
        AlertDialog(
            onDismissRequest = { pwTarget = null; pwText = "" },
            title = { Text(if (encrypt) "Encrypt ${target.name}" else "Decrypt ${target.name}") },
            text = {
                OutlinedTextField(pwText, { pwText = it }, label = {
                    Text("Password (min 4 chars)")
                }, singleLine = true)
            },
            confirmButton = {
                TextButton({
                    val pw = pwText
                    pwTarget = null
                    pwText = ""
                    if (pw.length < 4) {
                        vm.postNotice("Password too short")
                        return@TextButton
                    }
                    scope.launch {
                        try {
                            val out = if (encrypt) PowerTools.aesEncrypt(target, pw)
                            else PowerTools.aesDecrypt(target, pw)
                            vm.postNotice("Done → ${out.name}")
                        } catch (e: Exception) {
                            vm.postNotice("Failed (wrong password?): ${e.message}")
                        }
                        vm.refresh()
                    }
                }) { Text(if (encrypt) "Encrypt" else "Decrypt") }
            },
            dismissButton = { TextButton({ pwTarget = null; pwText = "" }) { Text("Cancel") } },
        )
    }
    splitTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { splitTarget = null },
            title = { Text("Split ${target.name}") },
            text = {
                OutlinedTextField(splitMb, { splitMb = it.filter(Char::isDigit) }, label = {
                    Text("Part size (MB)")
                }, singleLine = true)
            },
            confirmButton = {
                TextButton({
                    val mb = splitMb.toLongOrNull() ?: 100
                    splitTarget = null
                    scope.launch {
                        try {
                            val parts = PowerTools.splitFile(
                                target, mb * 1024 * 1024, target.parentFile!!,
                            )
                            vm.postNotice("Split into ${parts.size} parts")
                        } catch (e: Exception) {
                            vm.postNotice("Split failed: ${e.message}")
                        }
                        vm.refresh()
                    }
                }) { Text("Split") }
            },
            dismissButton = { TextButton({ splitTarget = null }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false; permDelete = false; vm.clearSelection() },
            title = { Text(if (permDelete) "Delete forever?" else "Move to Trash?") },
            text = {
                Column {
                    Text("${vm.selectedFiles().size} item(s). Trash keeps restorable copies for 30 days.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(permDelete, { permDelete = it })
                        Text("Delete permanently")
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val perm = permDelete
                    confirmDelete = false
                    permDelete = false
                    vm.deleteSel(perm)
                }) { Text(if (permDelete) "Delete" else "Move") }
            },
            dismissButton = {
                TextButton({ confirmDelete = false; permDelete = false; vm.clearSelection() }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (batchAsk) {
        var pattern by remember { mutableStateOf("file_{n}") }
        var find by remember { mutableStateOf("") }
        var replace by remember { mutableStateOf("") }
        var start by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { batchAsk = false },
            title = { Text("Batch rename (${vm.selectedFiles().size})") },
            text = {
                Column {
                    OutlinedTextField(pattern, { pattern = it }, label = {
                        Text("Pattern: {n} number, {name}, {ext}")
                    }, singleLine = true)
                    OutlinedTextField(find, { find = it }, label = {
                        Text("Find (optional)")
                    }, singleLine = true)
                    OutlinedTextField(replace, { replace = it }, label = {
                        Text("Replace with")
                    }, singleLine = true)
                    OutlinedTextField(start, { start = it.filter(Char::isDigit) }, label = {
                        Text("Counter start")
                    }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    batchAsk = false
                    vm.batchRenameSel(
                        pattern.ifBlank { "{name}" }, find, replace,
                        start.toIntOrNull() ?: 1,
                    )
                }) { Text("Rename") }
            },
            dismissButton = { TextButton({ batchAsk = false }) { Text("Cancel") } },
        )
    }
    if (compareAsk) {
        val pair = remember { vm.selectedFiles().take(2) }
        val hashes by remember {
            mutableStateOf<Map<String, String>>(emptyMap())
        }
        var loaded by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf("computing…") }
        LaunchedEffect(pair) {
            if (!loaded) {
                loaded = true
                result = try {
                    val h = pair.map {
                        it.name to
                            com.fmx.manager.FileRepo.checksum(it, "SHA-256")
                    }
                    if (h[0].second == h[1].second) "✓ Files are IDENTICAL (sha256 match)"
                    else "✗ Files DIFFER:\n${h[0].first}: ${h[0].second.take(16)}…\n${h[1].first}: ${h[1].second.take(16)}…"
                } catch (e: Exception) {
                    "Compare failed: ${e.message}"
                }
            }
        }
        AlertDialog(
            onDismissRequest = { compareAsk = false },
            title = { Text("Compare checksums") },
            text = { Text(result) },
            confirmButton = {
                TextButton({
                    compareAsk = false
                    vm.clearSelection()
                }) { Text("Done") }
            },
        )
    }
    if (showBookmarks) {
        val bms = remember { prefs.bookmarks().toMutableList() }
        var refreshKey by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("Bookmarks") },
            text = {
                Column {
                    val list = remember(refreshKey) { prefs.bookmarks() }
                    if (list.isEmpty()) Text("No bookmarks yet. Use ★ below.")
                    list.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                { showBookmarks = false; vm.openDir(File(b)) },
                                modifier = Modifier.weight(1f),
                            ) { Text(b, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            IconButton({
                                prefs.removeBookmark(b)
                                refreshKey++
                            }) { Icon(Icons.Filled.Delete, "Remove") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton({
                        prefs.addBookmark(ui.path.absolutePath)
                        refreshKey++
                    }) { Text("★ Bookmark current folder") }
                }
            },
            confirmButton = { TextButton({ showBookmarks = false }) { Text("Close") } },
        )
    }

    // chmod/compress entry points for selection mode live in the bottom bar.
}

@Composable
private fun SheetRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}

private fun sortLabel(m: SortMode, active: Boolean, asc: Boolean): String {
    val arrow = if (active) (if (asc) " ↑" else " ↓") else ""
    return when (m) {
        SortMode.NAME -> "Name$arrow"
        SortMode.SIZE -> "Size$arrow"
        SortMode.DATE -> "Date$arrow"
        SortMode.TYPE -> "Type$arrow"
    }
}
