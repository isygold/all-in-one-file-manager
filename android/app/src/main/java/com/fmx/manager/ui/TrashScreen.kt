package com.fmx.manager.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.fmx.manager.TrashEntry
import com.fmx.manager.TrashRepo
import com.fmx.manager.fmtDate
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onOpenDrawer: () -> Unit, onOpenFile: (File) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    var tick by remember { mutableStateOf(0) }
    var askEmpty by remember { mutableStateOf(false) }

    val entries by produceState<List<TrashEntry>?>(null, tick) {
        value = try {
            TrashRepo.list(ctx)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun refresh() {
        tick++
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                },
                title = { Text("Trash") },
                actions = {
                    IconButton({ askEmpty = true }) {
                        Icon(Icons.Filled.Delete, "Empty trash")
                    }
                },
            )
        },
    ) { pad ->
        val list = entries
        when {
            list == null -> Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                Text("Trash is empty", color = MaterialTheme.colorScheme.outline)
            }
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                item {
                    Text(
                        "Auto-deletes items older than 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp, 8.dp),
                    )
                }
                items(list, key = { it.id }) { e ->
                    ListItem(
                        headlineContent = {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text("${e.origPath}\n${fmtDate(e.time)}", maxLines = 2)
                        },
                        leadingContent = {
                            Icon(
                                if (e.isDir) Icons.Filled.Folder else Icons.Filled.Description,
                                null,
                            )
                        },
                        trailingContent = {
                            Row {
                                TextButton({
                                    scope.launch {
                                        val ok = TrashRepo.restore(ctx, e)
                                        snacks.showSnackbar(
                                            if (ok) "Restored ${e.name}" else "Restore failed",
                                        )
                                        refresh()
                                    }
                                }) { Text("Restore") }
                                IconButton({
                                    scope.launch {
                                        TrashRepo.deleteForever(ctx, listOf(e))
                                        snacks.showSnackbar("Deleted forever")
                                        refresh()
                                    }
                                }) { Icon(Icons.Filled.Delete, "Delete forever") }
                            }
                        },
                    )
                }
            }
        }
    }
    if (askEmpty) {
        AlertDialog(
            onDismissRequest = { askEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("All items will be permanently deleted.") },
            confirmButton = {
                TextButton({
                    askEmpty = false
                    scope.launch {
                        TrashRepo.emptyAll(ctx)
                        snacks.showSnackbar("Trash emptied")
                        refresh()
                    }
                }) { Text("Empty") }
            },
            dismissButton = { TextButton({ askEmpty = false }) { Text("Cancel") } },
        )
    }
}
