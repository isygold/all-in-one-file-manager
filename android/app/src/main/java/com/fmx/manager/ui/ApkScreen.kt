package com.fmx.manager.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.fmx.manager.ApkDetails
import com.fmx.manager.FileRepo
import com.fmx.manager.humanSize
import com.fmx.manager.installApk
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.launch

private data class ApkFull(
    val details: ApkDetails,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
    val permissions: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkScreen(file: File, onBack: () -> Unit, onBrowseZip: (File) -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    var showPerms by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val info by produceState<ApkFull?>(null, file.absolutePath) {
        value = try {
            @Suppress("DEPRECATION")
            val pi = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_PERMISSIONS.toLong(),
                    ),
                )
            } else {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS)
            } ?: throw IllegalStateException("Not a valid APK")
            val ai = pi.applicationInfo ?: throw IllegalStateException("No application info")
            ai.sourceDir = file.absolutePath
            ai.publicSourceDir = file.absolutePath
            val label = pm.getApplicationLabel(ai)?.toString() ?: file.name
            val iconBmp = try {
                drawableToBitmap(pm.getApplicationIcon(ai))?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
            val dex = ArrayList<String>()
            var so = 0
            var count = 0
            try {
                ZipFile(file).use { z ->
                    val en = z.entries()
                    while (en.hasMoreElements()) {
                        val e = en.nextElement()
                        count++
                        val n = e.name
                        if (n.endsWith(".dex")) dex += n
                        if (n.endsWith(".so")) so++
                    }
                }
            } catch (_: Exception) {
            }
            ApkFull(
                ApkDetails(
                    packageName = pi.packageName ?: "?",
                    versionName = pi.versionName ?: "?",
                    versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toString()
                    else @Suppress("DEPRECATION") pi.versionCode.toString(),
                    label = label,
                    dexFiles = dex.sorted(),
                    soCount = so,
                    entryCount = count,
                    md5 = FileRepo.checksum(file, "MD5"),
                ),
                iconBmp,
                pi.requestedPermissions?.toList().orEmpty(),
            )
        } catch (e: Exception) {
            null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = { Text(file.name, maxLines = 1) },
            )
        },
    ) { pad ->
        val a = info
        if (a == null) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                a.icon?.let {
                    Image(it, null, Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(a.details.label, style = MaterialTheme.typography.headlineSmall)
                    Text(a.details.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "v${a.details.versionName} (${a.details.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Button({
                    try {
                        ctx.installApk(file)
                    } catch (e: Exception) {
                        scope.launch { snacks.showSnackbar("Install failed: ${e.message}") }
                    }
                }) { Text("Install") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton({
                    busy = true
                    scope.launch {
                        try {
                            val dst = File(
                                Environment.getExternalStorageDirectory(),
                                "FMX/Backups",
                            ).apply { mkdirs() }
                            val target = File(
                                dst,
                                "${a.details.packageName}_${a.details.versionName}.apk",
                            )
                            FileRepo.copyTo(listOf(file), dst)
                            // rename to versioned name
                            File(dst, file.name).renameTo(target)
                            snacks.showSnackbar("Backed up to ${target.absolutePath}")
                        } catch (e: Exception) {
                            snacks.showSnackbar("Backup failed: ${e.message}")
                        }
                        busy = false
                    }
                }) { Text(if (busy) "…" else "Backup") }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton({ onBrowseZip(file) }) { Text("Browse as ZIP") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton({
                    busy = true
                    scope.launch {
                        try {
                            val dst = File(file.parentFile, file.nameWithoutExtension)
                            val n = FileRepo.extractZip(file, dst)
                            snacks.showSnackbar("Extracted $n entries")
                        } catch (e: Exception) {
                            snacks.showSnackbar("Extract failed: ${e.message}")
                        }
                        busy = false
                    }
                }) { Text("Extract") }
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    InfoLine("Size", humanSize(file.length()))
                    InfoLine("MD5", a.details.md5)
                    InfoLine("Entries", a.details.entryCount.toString())
                    InfoLine("DEX", a.details.dexFiles.joinToString(", ").ifBlank { "-" })
                    InfoLine("Native libs (.so)", a.details.soCount.toString())
                    InfoLine(
                        "Permissions",
                        if (a.permissions.isEmpty()) "none"
                        else "${a.permissions.size} requested",
                    )
                }
            }
            if (a.permissions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TextButton({ showPerms = !showPerms }) {
                    Text(if (showPerms) "Hide permissions" else "Show permissions")
                }
                if (showPerms) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            a.permissions.forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun drawableToBitmap(d: Drawable): Bitmap? {
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
