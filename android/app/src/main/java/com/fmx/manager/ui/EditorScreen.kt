package com.fmx.manager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fmx.manager.FileRepo
import com.fmx.manager.humanSize
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(file: File, onBack: () -> Unit) {
    val snacks = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val load by produceState<FileRepo.TextLoad?>(null, file.absolutePath) {
        value = try {
            if (file.length() > 500 * 1024) null else FileRepo.readText(file, 500_000)
        } catch (_: Exception) {
            null
        }
    }
    var text by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replText by remember { mutableStateOf("") }

    fun goBack() {
        if (dirty) askDiscard = true else onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(::goBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = { Text(file.name, maxLines = 1) },
                actions = {
                    IconButton({ findOpen = !findOpen }) {
                        Icon(Icons.Filled.Search, "Find")
                    }
                    IconButton({
                        val t = text ?: return@IconButton
                        saving = true
                        scope.launch {
                            try {
                                FileRepo.saveText(file, t)
                                dirty = false
                                snacks.showSnackbar("Saved")
                            } catch (e: Exception) {
                                snacks.showSnackbar("Save failed: ${e.message}")
                            }
                            saving = false
                        }
                    }) { Icon(Icons.Filled.Save, "Save") }
                },
            )
        },
    ) { pad ->
        when (val l = load) {
            null -> Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                if (file.length() > 500 * 1024) Text("File too large to edit (>500KB)")
                else CircularProgressIndicator()
            }
            else -> {
                if (text == null) {
                    text = l.text
                    dirty = false
                }
                Column(Modifier.padding(pad).fillMaxSize()) {
                    if (findOpen) {
                        val hits = if (findText.isEmpty()) 0
                        else (text ?: "").split(findText, ignoreCase = false).size - 1
                        OutlinedTextField(
                            findText, { findText = it },
                            label = { Text("Find ($hits)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            replText, { replText = it },
                            label = { Text("Replace with") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton({
                            val t = text ?: return@TextButton
                            if (findText.isNotEmpty()) {
                                text = t.replace(findText, replText)
                                dirty = true
                            }
                        }) { Text("Replace all") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        TextField(
                            value = text ?: "",
                            onValueChange = { text = it; dirty = true },
                            modifier = Modifier.fillMaxSize(),
                            singleLine = false,
                            maxLines = Int.MAX_VALUE,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                        if (saving) Box(
                            Modifier.fillMaxSize(),
                            Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved edits.") },
            confirmButton = { TextButton(onBack) { Text("Discard") } },
            dismissButton = { TextButton({ askDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexScreen(file: File, onBack: () -> Unit) {
    var offset by remember { mutableStateOf(0L) }
    var ver by remember { mutableStateOf(0) }
    var editOpen by remember { mutableStateOf(false) }
    var editOff by remember { mutableStateOf("") }
    var editVal by remember { mutableStateOf("") }
    var editErr by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    val lines by produceState<List<String>?>(null, file.absolutePath, offset, ver) {
        value = try {
            FileRepo.hexDump(file, offset, 4096)
        } catch (_: Exception) {
            emptyList()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = { Text("Hex · ${file.name}", maxLines = 1) },
                actions = {
                    TextButton({ editOpen = true }) { Text("Edit byte") }
                },
            )
        },
    ) { pad ->
        androidx.compose.foundation.layout.Column(
            Modifier.padding(pad).fillMaxSize().padding(8.dp),
        ) {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.OutlinedButton(
                    { if (offset >= 4096) offset -= 4096 },
                    modifier = Modifier.weight(1f),
                ) { Text("‹ Prev 4K") }
                androidx.compose.foundation.layout.Spacer(
                    Modifier.width(8.dp),
                )
                androidx.compose.material3.OutlinedButton(
                    { offset += 4096 },
                    modifier = Modifier.weight(1f),
                ) { Text("Next 4K ›") }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            val l = lines
            if (l == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                androidx.compose.foundation.text.selection.SelectionContainer(
                    Modifier.weight(1f)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    Text(
                        if (l.isEmpty()) "(no data)" else l.joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "offset 0x${offset.toString(16)} · ${humanSize(file.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    if (editOpen) {
        AlertDialog(
            onDismissRequest = { editOpen = false; editErr = null },
            title = { Text("Edit byte (MT-style)") },
            text = {
                Column {
                    OutlinedTextField(editOff, { editOff = it }, label = {
                        Text("Offset hex (e.g. 1A3F)")
                    }, singleLine = true)
                    OutlinedTextField(editVal, { editVal = it }, label = {
                        Text("New value hex (00–FF)")
                    }, singleLine = true)
                    editErr?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton({
                    try {
                        val off = editOff.trim().toLong(16)
                        val v = editVal.trim().toInt(16)
                        require(v in 0..255)
                        scope.launch {
                            try {
                                com.fmx.manager.PowerTools.writeByte(file, off, v)
                                ver++
                                editOpen = false
                                editErr = null
                                snacks.showSnackbar("Wrote 0x${v.toString(16)} at 0x${off.toString(16)}")
                            } catch (e: Exception) {
                                editErr = "Write failed: ${e.message}"
                            }
                        }
                    } catch (_: Exception) {
                        editErr = "Enter valid hex numbers"
                    }
                }) { Text("Write") }
            },
            dismissButton = { TextButton({ editOpen = false; editErr = null }) { Text("Cancel") } },
        )
    }
}
