package com.fmx.manager.ui

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.fmx.manager.ArchiveEntryInfo
import com.fmx.manager.FileRepo
import com.fmx.manager.PowerTools
import com.fmx.manager.humanSize
import com.fmx.manager.isArchive
import com.fmx.manager.isZipArchive
import com.fmx.manager.tarKind
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(file: File, onBack: () -> Unit, onOpenExtracted: (File) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf<String?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var askDest by remember { mutableStateOf(false) }
    var destName by remember { mutableStateOf(file.nameWithoutExtension) }

    val data by produceState<ArchData>(ArchData.Loading, file.absolutePath) {
        value = try {
            when {
                PowerTools.isSevenZ(file) ->
                    ArchData.Ok(PowerTools.sevenZipEntries(file), "7z")
                isZipArchive(file) -> {
                    val enc = PowerTools.isZipEncrypted(file)
                    ArchData.Ok(FileRepo.zipEntries(file), "zip", enc)
                }
                tarKind(file) != null ->
                    ArchData.Ok(FileRepo.tarEntries(file), "tar")
                else -> ArchData.Unsupported
            }
        } catch (e: Exception) {
            ArchData.Error(e.message ?: "Cannot read archive")
        }
    }

    fun needPassword(): Boolean = (data as? ArchData.Ok)?.encrypted == true && password.isEmpty()

    fun doExtractAll(dst: File) {
        if (needPassword()) {
            askPassword = true
            return
        }
        busy = "Extracting…"
        scope.launch {
            try {
                val n = when {
                    PowerTools.isSevenZ(file) -> PowerTools.extract7z(file, dst)
                    isZipArchive(file) ->
                        if (password.isNotEmpty()) PowerTools.extractZipEncrypted(file, dst, password)
                        else FileRepo.extractZip(file, dst)
                    else -> FileRepo.extractTar(file, dst)
                }
                snacks.showSnackbar("Extracted $n entries to ${dst.name}")
            } catch (e: Exception) {
                snacks.showSnackbar("Extract failed: ${e.message}")
            }
            busy = null
        }
    }

    fun previewEntry(e: ArchiveEntryInfo) {
        if (e.isDir) return
        if (needPassword()) {
            askPassword = true
            return
        }
        busy = "Opening ${File(e.name).name}…"
        scope.launch {
            try {
                val tmp = File(ctx.cacheDir, "preview").apply { mkdirs() }
                val out = when {
                    PowerTools.isSevenZ(file) -> PowerTools.extractOne7z(file, e.name, tmp)
                    isZipArchive(file) ->
                        if (password.isNotEmpty()) PowerTools.extractOneZipEncrypted(file, e.name, tmp, password)
                        else FileRepo.extractOneZip(file, e.name, tmp)
                    else -> throw IllegalStateException("Preview is for zip/7z entries")
                }
                busy = null
                if (isArchive(out)) {
                    snacks.showSnackbar("Nested archive extracted to cache")
                } else {
                    onOpenExtracted(out)
                }
            } catch (ex: Exception) {
                busy = null
                snacks.showSnackbar("Open failed: ${ex.message}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (data is ArchData.Ok) {
                        TextButton({
                            doExtractAll(File(file.parentFile, file.nameWithoutExtension))
                        }) { Text("Extract") }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            busy?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            when (val d = data) {
                ArchData.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                ArchData.Unsupported -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("This format is view-only unsupported yet (RAR/others on roadmap).")
                }
                is ArchData.Error -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(d.msg)
                }
                is ArchData.Ok -> {
                    if (d.encrypted) {
                        Text(
                            if (password.isEmpty()) "🔒 Password-protected — tap Extract to unlock"
                            else "🔓 Unlocked for this session",
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "${d.entries.size} entries · ${d.kind}",
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(d.entries, key = { it.name }) { e ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        File(e.name).name.ifBlank { e.name },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (e.isDir) e.name else "${humanSize(e.size)} · ${e.name}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        if (e.isDir) Icons.Filled.Folder else Icons.Filled.Description,
                                        null,
                                        modifier = Modifier.size(26.dp),
                                    )
                                },
                                modifier = Modifier.combinedClickable(
                                    onClick = { previewEntry(e) },
                                    onLongClick = {
                                        if (!e.isDir && !isZipArchive(file) && !PowerTools.isSevenZ(file)) return@combinedClickable
                                        if (needPassword()) {
                                            askPassword = true
                                            return@combinedClickable
                                        }
                                        busy = "Extracting ${File(e.name).name}…"
                                        scope.launch {
                                            try {
                                                if (PowerTools.isSevenZ(file)) {
                                                    PowerTools.extractOne7z(file, e.name, file.parentFile!!)
                                                } else if (password.isNotEmpty()) {
                                                    PowerTools.extractOneZipEncrypted(
                                                        file, e.name, file.parentFile!!, password,
                                                    )
                                                } else {
                                                    FileRepo.extractOneZip(file, e.name, file.parentFile!!)
                                                }
                                                snacks.showSnackbar("Extracted beside archive")
                                            } catch (ex: Exception) {
                                                snacks.showSnackbar("Failed: ${ex.message}")
                                            }
                                            busy = null
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (askPassword) {
        AlertDialog(
            onDismissRequest = { askPassword = false },
            title = { Text("Archive password") },
            text = {
                OutlinedTextField(
                    password, { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                )
            },
            confirmButton = {
                TextButton({
                    askPassword = false
                    if (password.isNotEmpty()) {
                        doExtractAll(File(file.parentFile, destName.ifBlank { file.nameWithoutExtension }))
                    }
                }) { Text("Unlock & extract") }
            },
            dismissButton = { TextButton({ askPassword = false }) { Text("Cancel") } },
        )
    }
    if (askDest) {
        AlertDialog(
            onDismissRequest = { askDest = false },
            title = { Text("Extract to…") },
            text = {
                OutlinedTextField(destName, { destName = it }, singleLine = true)
            },
            confirmButton = {
                TextButton({
                    askDest = false
                    doExtractAll(File(file.parentFile, destName.ifBlank { file.nameWithoutExtension }))
                }) { Text("Extract") }
            },
            dismissButton = { TextButton({ askDest = false }) { Text("Cancel") } },
        )
    }
}

private sealed interface ArchData {
    data object Loading : ArchData
    data object Unsupported : ArchData
    data class Error(val msg: String) : ArchData
    data class Ok(
        val entries: List<ArchiveEntryInfo>,
        val kind: String,
        val encrypted: Boolean = false,
    ) : ArchData
}
