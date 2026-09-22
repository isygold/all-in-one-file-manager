package com.fmx.manager.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fmx.manager.FileRepo
import com.fmx.manager.humanSize
import java.io.File
import kotlinx.coroutines.launch

private data class AppEntry(
    val label: String,
    val pkg: String,
    val version: String,
    val icon: ImageBitmap?,
    val sourceDir: String,
    val isSystem: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onOpenDrawer: () -> Unit, onApkFile: (File) -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var backingUp by remember { mutableStateOf<String?>(null) }

    val apps by produceState<List<AppEntry>?>(null) {
        value = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA).map { ai ->
                val icon = try {
                    appDrawableToBitmap(pm.getApplicationIcon(ai))?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
                val pi = try {
                    pm.getPackageInfo(ai.packageName, 0)
                } catch (_: Exception) {
                    null
                }
                AppEntry(
                    label = pm.getApplicationLabel(ai)?.toString() ?: ai.packageName,
                    pkg = ai.packageName,
                    version = pi?.versionName ?: "?",
                    icon = icon,
                    sourceDir = ai.sourceDir ?: "",
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }.sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                },
                title = { Text("Apps") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                query, { query = it },
                label = { Text("Filter apps") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("User") })
                Tab(tab == 1, { tab = 1 }, text = { Text("System") })
            }
            val list = apps
            if (list == null) {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.weight(1f))
                return@Column
            }
            val shown = list.filter {
                (if (tab == 0) !it.isSystem else it.isSystem) &&
                    (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.pkg }) { a ->
                    Card(
                        onClick = {
                            if (a.sourceDir.isNotEmpty()) onApkFile(File(a.sourceDir))
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            a.icon?.let { Image(it, null, Modifier.size(44.dp)) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${a.pkg} · v${a.version}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val sizeText = try {
                                    val sz = File(a.sourceDir).length()
                                    if (sz > 0) humanSize(sz) else null
                                } catch (_: Exception) {
                                    null
                                }
                                sizeText?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Row(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                            TextButton({
                                try {
                                    ctx.startActivity(
                                        pm.getLaunchIntentForPackage(a.pkg)?.addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK,
                                        ),
                                    )
                                } catch (e: Exception) {
                                    scope.launch { snacks.showSnackbar("Cannot launch: ${e.message}") }
                                }
                            }) { Text("Open") }
                            TextButton({
                                backingUp = a.pkg
                                scope.launch {
                                    try {
                                        val dst = File(
                                            Environment.getExternalStorageDirectory(),
                                            "FMX/Backups",
                                        ).apply { mkdirs() }
                                        FileRepo.copyTo(
                                            listOf(File(a.sourceDir)),
                                            dst,
                                        )
                                        File(dst, File(a.sourceDir).name).renameTo(
                                            File(dst, "${a.pkg}_${a.version}.apk"),
                                        )
                                        snacks.showSnackbar("Backed up ${a.label}")
                                    } catch (e: Exception) {
                                        snacks.showSnackbar("Backup failed: ${e.message}")
                                    }
                                    backingUp = null
                                }
                            }) { Text(if (backingUp == a.pkg) "…" else "Backup") }
                            TextButton({
                                try {
                                    ctx.startActivity(
                                        Intent(
                                            Intent.ACTION_DELETE,
                                            Uri.parse("package:${a.pkg}"),
                                        ),
                                    )
                                } catch (e: Exception) {
                                    scope.launch { snacks.showSnackbar("Cannot uninstall: ${e.message}") }
                                }
                            }) { Text("Uninstall") }
                        }
                    }
                }
            }
        }
    }
}

private fun appDrawableToBitmap(d: Drawable): Bitmap? {
    if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
    val w = d.intrinsicWidth.coerceAtLeast(1).coerceAtMost(512)
    val h = d.intrinsicHeight.coerceAtLeast(1).coerceAtMost(512)
    return try {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        b
    } catch (_: Exception) {
        null
    }
}
