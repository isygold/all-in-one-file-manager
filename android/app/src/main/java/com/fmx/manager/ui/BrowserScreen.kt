package com.fmx.manager.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private fun tintFor(item: FileItem, palette: FmxPalette): Color {
    if (item.isDir) return palette.folder
    return when {
        item.ext in IMAGE_EXTS -> palette.image
        item.ext in VIDEO_EXTS -> palette.video
        item.ext in AUDIO_EXTS -> palette.audio
        item.ext in setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar") ->
            palette.archive
        item.ext == "apk" -> palette.apk
        item.ext == "pdf" -> palette.pdf
        item.ext in TEXT_EXTS -> palette.document
        else -> palette.document
    }
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
    var selMore by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<File?>(null) }
    var chmodFor by remember { mutableStateOf<List<File>>(emptyList()) }
    var compressAsk by remember { mutableStateOf(false) }
    var batchAsk by remember { mutableStateOf(false) }
    var compareAsk by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var permDelete by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var pwTarget by remember { mutableStateOf<File?>(null) }
    var pwText by remember { mutableStateOf("") }
    var splitTarget by remember { mutableStateOf<File?>(null) }
    var splitMb by remember { mutableStateOf("100") }
    val selecting = ui.selection.isNotEmpty()
    val dirCount = ui.entries.count { it.isDir }
    val fileCount = ui.entries.size - dirCount

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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snacks) },
        bottomBar = {
            if (selecting) {
                SelectionBar(
                    clipAvailable = ui.clipboard != null,
                    onCopy = { vm.copySel() },
                    onCut = { vm.cutSel() },
                    onPaste = { vm.paste() },
                    onDelete = { confirmDelete = true },
                    onMore = { selMore = true },
                )
            }
        },
        topBar = {
            if (selecting) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    navigationIcon = {
                        IconButton({ vm.clearSelection() }) {
                            Icon(Icons.Filled.Close, "Clear selection")
                        }
                    },
                    title = { Text("${ui.selection.size} selected") },
                    actions = {
                        IconButton({ vm.selectedFiles().let(ctx::shareFiles) }) {
                            Icon(Icons.Filled.Share, "Share")
                        }
                        IconButton({ vm.selectAll() }) {
                            Icon(Icons.Filled.Checklist, "Select all")
                        }
                        IconButton({ confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "Delete")
                        }
                    },
                )
            } else if (searchOpen) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
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
                            placeholder = { Text("Search in this folder") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    navigationIcon = {
                        IconButton(onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                    },
                    title = {
                        Column {
                            Text(
                                when {
                                    ui.searching != null -> "Search"
                                    ui.path.name.isNotBlank() -> ui.path.name
                                    else -> "Internal storage"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    ui.searching != null ->
                                        "${ui.entries.size} results for \"${ui.searching}\""
                                    ui.loading -> "Loading…"
                                    else -> "$dirCount folders, $fileCount files"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
                    actions = {
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
                                    text = { Text(if (ui.grid) "List view" else "Grid view") },
                                    leadingIcon = {
                                        Icon(
                                            if (ui.grid) Icons.Filled.ViewList
                                            else Icons.Filled.GridView,
                                            null,
                                        )
                                    },
                                    onClick = { overflow = false; vm.toggleGrid() },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (ui.showHidden) "Hide hidden" else "Show hidden") },
                                    onClick = { overflow = false; vm.toggleHidden() },
                                )
                                DropdownMenuItem(
                                    text = { Text("New tab") },
                                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                                    onClick = { overflow = false; vm.newTab() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                    onClick = { overflow = false; vm.refresh() },
                                )
                                if (onToggleDual != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (dual) "Single pane" else "Dual pane") },
                                        onClick = { overflow = false; onToggleDual() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    leadingIcon = { Icon(Icons.Filled.Star, null) },
                                    onClick = { overflow = false; showBookmarks = true },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(
                    onClick = { showNew = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.Add, "New")
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PathBar(path = ui.path, onUp = { vm.goUp() }, onOpen = vm::openDir)

            if (ui.tabs.size > 1) {
                TabStrip(
                    tabs = ui.tabs,
                    active = ui.activeTab,
                    onSelect = vm::switchTab,
                    onClose = vm::closeTab,
                    onNew = vm::newTab,
                )
            }

            if (searchOpen) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = inContent,
                        onClick = {
                            inContent = !inContent
                            if (searchText.isNotBlank()) vm.search(searchText, inContent)
                        },
                        label = { Text("Search inside files") },
                    )
                }
            }

            ui.clipboard?.let { clip ->
                val n = when (clip) {
                    is ClipOp.Copy -> clip.paths.size
                    is ClipOp.Cut -> clip.paths.size
                }
                val kind = if (clip is ClipOp.Copy) "Copied" else "Cut"
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$kind $n item(s) · paste into this folder",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton({ vm.paste() }) { Text("Paste") }
                    }
                }
            }

            if (ui.searching != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${ui.entries.size} result(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({ vm.clearSearch(); searchText = "" }) { Text("Clear") }
                }
            }

            when {
                ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.error != null && ui.entries.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error!!, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        TextButton({ vm.refresh() }) { Text("Retry") }
                    }
                }
                ui.entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (ui.searching != null) "No matches" else "This folder is empty",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (ui.searching != null) "Try a different search"
                            else "Tap + to create a folder or file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                !ui.grid -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.entries, key = { it.file.absolutePath }) { item ->
                        val sel = ui.selection.contains(item.file.absolutePath)
                        FileRow(
                            item = item,
                            selected = sel,
                            selecting = selecting,
                            onClick = {
                                if (selecting) vm.toggleSelect(item.file.absolutePath)
                                else if (item.isDir) vm.openDir(item.file)
                                else onOpenFile(item.file)
                            },
                            onLongClick = { vm.toggleSelect(item.file.absolutePath) },
                            onMore = { sheetFor = item },
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.entries, key = { it.file.absolutePath }) { item ->
                        val sel = ui.selection.contains(item.file.absolutePath)
                        GridTile(
                            item = item,
                            selected = sel,
                            selecting = selecting,
                            onClick = {
                                if (selecting) vm.toggleSelect(item.file.absolutePath)
                                else if (item.isDir) vm.openDir(item.file)
                                else onOpenFile(item.file)
                            },
                            onLongClick = { vm.toggleSelect(item.file.absolutePath) },
                        )
                    }
                }
            }
        }
    }

    // ---- per-item action sheet
    sheetFor?.let { item ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            Column(Modifier.padding(bottom = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        iconFor(item),
                        null,
                        tint = tintFor(item, LocalFmxPalette.current),
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (item.isDir) "Folder" else humanSize(item.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (!item.isDir) {
                    SheetRow("Open with…", Icons.Filled.FolderOpen) {
                        sheetFor = null
                        try {
                            ctx.openWith(item.file)
                        } catch (_: Exception) {
                        }
                    }
                }
                SheetRow("Rename", Icons.Filled.Edit) {
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
                    SheetRow("Compress", Icons.Filled.Archive) {
                        vm.clearSelection()
                        vm.toggleSelect(item.file.absolutePath)
                        sheetFor = null
                        compressAsk = true
                    }
                    SheetRow("Permissions", Icons.Filled.Lock) {
                        sheetFor = null
                        chmodFor = listOf(item.file)
                    }
                }
                if (!item.isDir && !item.file.name.endsWith(".aes")) {
                    SheetRow("Encrypt (AES)…", Icons.Filled.Lock) {
                        pwText = ""
                        pwTarget = item.file
                        sheetFor = null
                    }
                }
                if (item.file.name.endsWith(".aes")) {
                    SheetRow("Decrypt (AES)…", Icons.Filled.LockOpen) {
                        pwText = ""
                        pwTarget = item.file
                        sheetFor = null
                    }
                }
                if (!item.isDir && !item.file.name.endsWith(".aes")) {
                    SheetRow("Split file…", Icons.Filled.CallSplit) {
                        splitMb = "100"
                        splitTarget = item.file
                        sheetFor = null
                    }
                }
                if (Regex(""".*\.\d{3}$""").matches(item.file.name)) {
                    SheetRow("Merge parts", Icons.Filled.CallMerge) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                SheetRow("Delete", Icons.Filled.Delete) {
                    sheetFor = null
                    vm.clearSelection()
                    vm.toggleSelect(item.file.absolutePath)
                    confirmDelete = true
                }
            }
        }
    }

    // ---- multi-select action sheet
    if (selMore) {
        val selected = vm.selectedFiles()
        ModalBottomSheet(onDismissRequest = { selMore = false }) {
            Column(Modifier.padding(bottom = 20.dp)) {
                Text(
                    "${selected.size} item(s)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
                SheetRow("Rename", Icons.Filled.Edit) {
                    selMore = false
                    if (selected.size == 1) renameFor = selected[0] else batchAsk = true
                }
                SheetRow("Compress", Icons.Filled.Archive) {
                    selMore = false
                    compressAsk = true
                }
                SheetRow("Permissions", Icons.Filled.Lock) {
                    selMore = false
                    chmodFor = selected
                }
                if (selected.size == 2) {
                    SheetRow("Compare checksums", Icons.Filled.CompareArrows) {
                        selMore = false
                        compareAsk = true
                    }
                }
                SheetRow("Share", Icons.Filled.Share) {
                    selMore = false
                    ctx.shareFiles(selected)
                }
                if (peerPath != null) {
                    SheetRow("Copy to other pane", Icons.Filled.ContentCopy) {
                        selMore = false
                        vm.copySelectedTo(peerPath)
                    }
                    SheetRow("Move to other pane", Icons.Filled.ContentCut) {
                        selMore = false
                        vm.moveSelectedTo(peerPath)
                    }
                }
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
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({
                            if (name.isNotBlank()) {
                                vm.mkdir(name.trim())
                                showNew = false
                            }
                        }) { Text("New folder") }
                        TextButton({
                            if (name.isNotBlank()) {
                                vm.mkfile(name.trim())
                                showNew = false
                            }
                        }) { Text("New file") }
                    }
                }
            },
            confirmButton = { TextButton({ showNew = false }) { Text("Cancel") } },
        )
    }
    renameFor?.let { f ->
        var name by remember(f.absolutePath) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
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
                    OutlinedTextField(
                        mode, { mode = it.filter(Char::isDigit) },
                        label = { Text("Octal (e.g. 755)") },
                        singleLine = true,
                    )
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
                        OutlinedTextField(
                            pw, { pw = it },
                            label = { Text("Password (optional, AES)") },
                            singleLine = true,
                        )
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
                OutlinedTextField(
                    pwText, { pwText = it },
                    label = { Text("Password (min 4 chars)") },
                    singleLine = true,
                )
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
                OutlinedTextField(
                    splitMb, { splitMb = it.filter(Char::isDigit) },
                    label = { Text("Part size (MB)") },
                    singleLine = true,
                )
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
                    OutlinedTextField(
                        pattern, { pattern = it },
                        label = { Text("Pattern: {n} number, {name}, {ext}") },
                        singleLine = true,
                    )
                    OutlinedTextField(find, { find = it }, label = { Text("Find (optional)") }, singleLine = true)
                    OutlinedTextField(replace, { replace = it }, label = { Text("Replace with") }, singleLine = true)
                    OutlinedTextField(
                        start, { start = it.filter(Char::isDigit) },
                        label = { Text("Counter start") },
                        singleLine = true,
                    )
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
        var result by remember { mutableStateOf("computing…") }
        LaunchedEffect(pair) {
            result = try {
                val h = pair.map {
                    it.name to com.fmx.manager.FileRepo.checksum(it, "SHA-256")
                }
                if (h[0].second == h[1].second) "✓ Files are IDENTICAL (sha256 match)"
                else "✗ Files DIFFER:\n${h[0].first}: ${h[0].second.take(16)}…\n${h[1].first}: ${h[1].second.take(16)}…"
            } catch (e: Exception) {
                "Compare failed: ${e.message}"
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
        var refreshKey by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("Bookmarks") },
            text = {
                Column {
                    val list = remember(refreshKey) { prefs.bookmarks() }
                    if (list.isEmpty()) Text("No bookmarks yet.")
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
}

// ---------------------------------------------------------------- pieces

@Composable
private fun PathBar(path: File, onUp: () -> Unit, onOpen: (File) -> Unit) {
    val segments = remember(path.absolutePath) {
        path.absolutePath.split("/").filter { it.isNotEmpty() }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUp, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.ArrowUpward,
                "Up one level",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PathCrumb("/") { onOpen(File("/")) }
        segments.forEachIndexed { i, s ->
            Text(
                "›",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            val target = remember(i, path.absolutePath) {
                File("/" + segments.take(i + 1).joinToString("/"))
            }
            PathCrumb(s, bold = i == segments.lastIndex) { onOpen(target) }
        }
    }
}

@Composable
private fun PathCrumb(text: String, bold: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (bold) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

@Composable
private fun TabStrip(
    tabs: List<String>,
    active: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNew: () -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, t ->
            val on = i == active
            Surface(
                color = if (on) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Row(
                    Modifier.clickable { onSelect(i) }
                        .padding(start = 12.dp, end = if (tabs.size > 1) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        File(t).name.ifBlank { "/" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                    if (tabs.size > 1) {
                        IconButton(onClick = { onClose(i) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, "Close tab", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        TextButton(onClick = onNew) { Text("+") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val palette = LocalFmxPalette.current
    val childCount = if (item.isDir) rememberChildCount(item.file) else null
    Column {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                )
                .padding(start = 14.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                iconFor(item),
                null,
                tint = tintFor(item, palette),
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (item.isDir) {
                        when (childCount) {
                            null -> "Folder"
                            0 -> "Empty"
                            1 -> "1 item"
                            else -> "$childCount items"
                        }
                    } else {
                        humanSize(item.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                fmtDate(item.lastModified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                IconButton(onClick = onMore, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "Actions",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 58.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(
    item: FileItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalFmxPalette.current
    val childCount = if (item.isDir) rememberChildCount(item.file) else null
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                iconFor(item),
                null,
                tint = tintFor(item, palette),
                modifier = Modifier.size(40.dp),
            )
            if (selecting && selected) {
                Icon(
                    Icons.Filled.Check,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).align(Alignment.TopEnd),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (item.isDir) {
                when (childCount) {
                    null -> "Folder"
                    0 -> "Empty"
                    1 -> "1 item"
                    else -> "$childCount items"
                }
            } else {
                humanSize(item.size)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun rememberChildCount(file: File): Int? {
    val count by produceState<Int?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            try {
                file.list()?.size
            } catch (_: Exception) {
                null
            }
        }
    }
    return count
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    clipAvailable: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SelectionAction(Icons.Filled.ContentCopy, "Copy", onCopy)
            SelectionAction(Icons.Filled.ContentCut, "Cut", onCut)
            SelectionAction(Icons.Filled.ContentPaste, "Paste", onPaste, enabled = clipAvailable)
            SelectionAction(Icons.Filled.Delete, "Delete", onDelete)
            SelectionAction(Icons.Filled.MoreVert, "More", onMore)
        }
    }
}

@Composable
private fun RowScope.SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Column(
        Modifier.weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun SheetRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(
        onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
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
