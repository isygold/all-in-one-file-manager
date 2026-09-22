package com.fmx.manager

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RootEntry(val label: String, val file: File)

data class BrowserUiState(
    val path: File = Environment.getExternalStorageDirectory(),
    val entries: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val sort: SortMode = SortMode.NAME,
    val asc: Boolean = true,
    val showHidden: Boolean = false,
    val selection: Set<String> = emptySet(), // absolute paths
    val clipboard: ClipOp? = null,
    val searching: String? = null, // active query, null = browsing
    val searchContent: Boolean = false,
    val tabs: List<String> = emptyList(), // tab paths
    val activeTab: Int = 0,
    val grid: Boolean = false,
    val notice: String? = null, // one-shot snackbar message
)

class BrowserViewModel(appCtx: Context) : ViewModel() {
    private val ctx = appCtx.applicationContext
    private val _ui = MutableStateFlow(BrowserUiState())
    val ui: StateFlow<BrowserUiState> = _ui
    private var job: Job? = null

    val roots: List<RootEntry> = buildList {
        try {
            add(RootEntry("Internal", Environment.getExternalStorageDirectory()))
        } catch (_: Exception) {
        }
        try {
            val sm = ctx.getSystemService(StorageManager::class.java)
            sm?.storageVolumes?.forEach { v ->
                try {
                    val dir = v.directory
                    if (v.isRemovable && dir != null && dir.isDirectory) {
                        add(RootEntry("SD card", dir))
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        add(RootEntry("App files", ctx.filesDir))
        add(RootEntry("Root", File("/")))
    }

    init {
        val start = roots.firstOrNull()?.file ?: File("/")
        _ui.value = _ui.value.copy(path = start, tabs = listOf(start.absolutePath))
        refresh()
    }

    private fun set(s: BrowserUiState) {
        _ui.value = s
    }

    fun refresh() {
        val st = _ui.value
        job?.cancel()
        job = viewModelScope.launch {
            set(st.copy(loading = true, error = null))
            try {
                val entries = if (st.searching != null) {
                    if (st.searchContent) FileRepo.searchContent(st.path, st.searching)
                    else FileRepo.search(st.path, st.searching)
                } else {
                    FileRepo.listDir(st.path, st.sort, st.asc, st.showHidden)
                }
                set(_ui.value.copy(entries = entries, loading = false))
            } catch (e: Exception) {
                set(_ui.value.copy(loading = false, error = e.message ?: "Failed to list folder"))
            }
        }
    }

    fun openDir(dir: File) {
        val st = _ui.value
        val tabs = st.tabs.toMutableList()
        if (tabs.isEmpty()) tabs += dir.absolutePath
        else tabs[st.activeTab.coerceIn(tabs.indices)] = dir.absolutePath
        set(st.copy(path = dir, tabs = tabs, selection = emptySet(), searching = null))
        refresh()
    }

    // ---- tabs (MiX-style unlimited tabs)
    fun newTab() {
        val st = _ui.value
        val tabs = st.tabs + st.path.absolutePath
        set(st.copy(tabs = tabs, activeTab = tabs.lastIndex))
        refresh()
    }

    fun switchTab(i: Int) {
        val st = _ui.value
        if (i !in st.tabs.indices || i == st.activeTab) return
        set(
            st.copy(
                activeTab = i, path = File(st.tabs[i]),
                selection = emptySet(), searching = null,
            ),
        )
        refresh()
    }

    fun closeTab(i: Int) {
        val st = _ui.value
        if (st.tabs.size <= 1) return
        val tabs = st.tabs.toMutableList().also { it.removeAt(i) }
        var active = st.activeTab
        if (active >= tabs.size) active = tabs.lastIndex
        if (i < st.activeTab) active = (st.activeTab - 1).coerceAtLeast(0)
        set(
            st.copy(
                tabs = tabs, activeTab = active, path = File(tabs[active]),
                selection = emptySet(), searching = null,
            ),
        )
        refresh()
    }

    fun toggleGrid() {
        set(_ui.value.copy(grid = !_ui.value.grid))
    }

    fun goUp(): Boolean {
        val p = _ui.value.path.parentFile ?: return false
        if (p.absolutePath == _ui.value.path.absolutePath) return false
        openDir(p)
        return true
    }

    fun postNotice(msg: String) = set(_ui.value.copy(notice = msg))

    fun setSort(sort: SortMode, asc: Boolean) {
        set(_ui.value.copy(sort = sort, asc = asc))
        refresh()
    }

    fun toggleHidden() {
        set(_ui.value.copy(showHidden = !_ui.value.showHidden))
        refresh()
    }

    fun search(q: String, inContent: Boolean = _ui.value.searchContent) {
        set(
            _ui.value.copy(
                searching = q.ifBlank { null }, searchContent = inContent,
                selection = emptySet(),
            ),
        )
        refresh()
    }

    fun clearSearch() {
        set(_ui.value.copy(searching = null))
        refresh()
    }

    fun toggleSelect(path: String) {
        val sel = _ui.value.selection.toMutableSet()
        if (!sel.add(path)) sel.remove(path)
        set(_ui.value.copy(selection = sel))
    }

    fun selectAll() {
        set(_ui.value.copy(selection = _ui.value.entries.map { it.file.absolutePath }.toSet()))
    }

    fun clearSelection() = set(_ui.value.copy(selection = emptySet()))

    fun selectedFiles(): List<File> =
        _ui.value.selection.map { File(it) }.filter { it.exists() }

    fun copySel() {
        set(_ui.value.copy(clipboard = ClipOp.Copy(selectedFiles().map { it.absolutePath }),
            notice = "Copied ${selectedFiles().size}"))
    }

    fun cutSel() {
        set(_ui.value.copy(clipboard = ClipOp.Cut(selectedFiles().map { it.absolutePath }),
            notice = "Cut ${selectedFiles().size}"))
    }

    fun paste() {
        val clip = _ui.value.clipboard ?: return
        val dst = _ui.value.path
        viewModelScope.launch {
            try {
                when (clip) {
                    is ClipOp.Copy -> FileRepo.copyTo(clip.paths.map { File(it) }, dst)
                    is ClipOp.Cut -> {
                        FileRepo.moveTo(clip.paths.map { File(it) }, dst)
                        set(_ui.value.copy(clipboard = null))
                    }
                }
                set(_ui.value.copy(selection = emptySet(), notice = "Pasted"))
            } catch (e: Exception) {
                set(_ui.value.copy(notice = "Paste failed: ${e.message}"))
            }
            refresh()
        }
    }

    /** Trash by default (MiX-style recycle bin); permanent wipes immediately. */
    fun deleteSel(permanent: Boolean = false, onDone: () -> Unit = {}) {
        val files = selectedFiles()
        viewModelScope.launch {
            try {
                if (permanent) FileRepo.delete(files)
                else TrashRepo.moveToTrash(ctx, files)
                set(
                    _ui.value.copy(
                        selection = emptySet(),
                        notice = if (permanent) "Deleted ${files.size}"
                        else "Moved ${files.size} to Trash",
                    ),
                )
            } catch (e: Exception) {
                set(_ui.value.copy(notice = "Delete failed: ${e.message}"))
            }
            refresh()
            onDone()
        }
    }

    fun copySelectedTo(dst: File) = runOp("Copied to ${dst.name}") {
        FileRepo.copyTo(selectedFiles(), dst)
        set(_ui.value.copy(selection = emptySet()))
    }

    fun moveSelectedTo(dst: File) = runOp("Moved to ${dst.name}") {
        FileRepo.moveTo(selectedFiles(), dst)
        set(_ui.value.copy(selection = emptySet(), clipboard = null))
    }

    fun batchRenameSel(pattern: String, find: String, replace: String, start: Int) {
        viewModelScope.launch {
            try {
                val n = PowerTools.batchRename(selectedFiles(), pattern, find, replace, start)
                set(_ui.value.copy(selection = emptySet(), notice = "Renamed $n"))
            } catch (e: Exception) {
                set(_ui.value.copy(notice = "Rename failed: ${e.message}"))
            }
            refresh()
        }
    }

    fun mkdir(name: String) = runOp("Folder created") { FileRepo.mkdir(_ui.value.path, name) }
    fun mkfile(name: String) = runOp("File created") { FileRepo.mkfile(_ui.value.path, name) }

    fun rename(file: File, newName: String) = runOp("Renamed") {
        FileRepo.rename(file, newName)
        set(_ui.value.copy(selection = emptySet()))
    }

    fun chmod(files: List<File>, octal: String) = runOp("Permissions updated") {
        FileRepo.chmod(files, octal)
    }

    fun compressSel(outName: String, tar: Boolean, password: String = "") {
        val srcs = selectedFiles()
        val dst = _ui.value.path
        viewModelScope.launch {
            try {
                if (tar) {
                    val kind = if (outName.endsWith(".gz") || outName.endsWith(".tgz")) "gz" else "tar"
                    FileRepo.createTar(srcs, File(dst, outName), kind)
                } else if (password.isNotEmpty()) {
                    val name = if (outName.endsWith(".zip")) outName else "$outName.zip"
                    PowerTools.createZipEncrypted(srcs, File(dst, name), password)
                } else {
                    val name = if (outName.endsWith(".zip")) outName else "$outName.zip"
                    FileRepo.createZip(srcs, File(dst, name))
                }
                set(_ui.value.copy(selection = emptySet(), notice = "Archive created"))
            } catch (e: Exception) {
                set(_ui.value.copy(notice = "Compress failed: ${e.message}"))
            }
            refresh()
        }
    }

    private fun runOp(msg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                set(_ui.value.copy(notice = msg))
            } catch (e: Exception) {
                set(_ui.value.copy(notice = e.message ?: "Operation failed"))
            }
            refresh()
        }
    }

    fun consumeNotice() = set(_ui.value.copy(notice = null, error = null))
}
