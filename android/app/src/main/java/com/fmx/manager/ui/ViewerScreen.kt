package com.fmx.manager.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.fmx.manager.AUDIO_EXTS
import com.fmx.manager.FileRepo
import com.fmx.manager.IMAGE_EXTS
import com.fmx.manager.TEXT_EXTS
import com.fmx.manager.VIDEO_EXTS
import com.fmx.manager.fmtDate
import com.fmx.manager.humanSize
import com.fmx.manager.mimeOf
import com.fmx.manager.openWith
import com.fmx.manager.shareFiles
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(file: File, onBack: () -> Unit, onEdit: () -> Unit, onHex: () -> Unit) {
    val ctx = LocalContext.current
    val snacks = remember { SnackbarHostState() }
    val ext = file.extension.lowercase()
    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = { Text(file.name, maxLines = 1) },
                actions = {
                    if (ext in TEXT_EXTS) {
                        IconButton(onEdit) { Icon(Icons.Filled.Edit, "Edit") }
                    }
                    IconButton({ ctx.shareFiles(listOf(file)) }) {
                        Icon(Icons.Filled.Share, "Share")
                    }
                    IconButton({
                        try {
                            ctx.openWith(file)
                        } catch (_: Exception) {
                        }
                    }) { Icon(Icons.Filled.Info, "Open with") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                ext in IMAGE_EXTS -> ZoomableImage(file)
                ext in VIDEO_EXTS -> VideoPlayer(file)
                ext in AUDIO_EXTS -> AudioPlayer(file)
                ext == "pdf" -> PdfPages(file)
                ext in setOf("ttf", "otf", "ttc") -> FontPreview(file)
                else -> SmartTextOrInfo(file = file, onEdit = onEdit, onHex = onHex)
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = file,
        contentDescription = file.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            .transformable(state),
    )
}

@Composable
private fun VideoPlayer(file: File) {
    AndroidView(
        factory = { c ->
            VideoView(c).apply {
                setVideoPath(file.absolutePath)
                val mc = MediaController(c)
                mc.setAnchorView(this)
                setMediaController(mc)
                setOnPreparedListener { start() }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun AudioPlayer(file: File) {
    val player = remember(file.absolutePath) {
        MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(0) }
    val dur = remember(player) { player.duration.coerceAtLeast(1) }
    LaunchedEffect(playing) {
        while (isActive && playing) {
            try {
                pos = player.currentPosition
            } catch (_: Exception) {
            }
            delay(500)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(file.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${humanSize(file.length())} · ${mimeOf(file)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
        IconButton({
            if (playing) player.pause() else player.start()
            playing = !playing
        }) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "Pause" else "Play",
                modifier = Modifier.fillMaxSize(0.25f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Slider(
            value = pos.toFloat(),
            onValueChange = {
                pos = it.toInt()
                try {
                    player.seekTo(pos)
                } catch (_: Exception) {
                }
            },
            valueRange = 0f..dur.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${pos / 1000}s / ${dur / 1000}s", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PdfPages(file: File) {
    val pages by produceState<List<Bitmap>?>(null, file.absolutePath) {
        value = try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                try {
                    val out = ArrayList<Bitmap>()
                    val n = minOf(renderer.pageCount, 30)
                    for (i in 0 until n) {
                        val page = renderer.openPage(i)
                        try {
                            val w = (page.width * 2).coerceAtMost(2200)
                            val h = (w.toFloat() / page.width * page.height).toInt()
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            out += bmp
                        } finally {
                            page.close()
                        }
                    }
                    out
                } finally {
                    renderer.close()
                }
            } finally {
                pfd.close()
            }
        } catch (_: Exception) {
            null
        }
    }
    when (val p = pages) {
        null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            p.forEach { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
            if (p.isEmpty()) Text("Could not render PDF", Modifier.padding(16.dp))
        }
    }
}

/** MiX-style font viewer: renders samples in the actual typeface. */
@Composable
private fun FontPreview(file: File) {
    val family = try {
        androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.createFromFile(file))
    } catch (_: Exception) {
        null
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(file.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (family == null) {
            Text("Could not load font.")
            return@Column
        }
        val sample = "AaBbCcDdEeFfGg 0123456789"
        listOf(32, 24, 18, 14).forEach { sp ->
            Text(
                sample,
                fontFamily = family,
                fontSize = sp.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The quick brown fox jumps over the lazy dog.\nАаБбВв 123 !?#",
            fontFamily = family,
        )
    }
}

/** Shows text when the file looks like text, otherwise a file-info card. */
@Composable
private fun SmartTextOrInfo(file: File, onEdit: () -> Unit, onHex: () -> Unit) {
    val ext = file.extension.lowercase()
    val info by produceState<Result>(Result.Loading, file.absolutePath) {
        value = try {
            if (ext in TEXT_EXTS) {
                val t = FileRepo.readText(file)
                Result.Text(t.text, t.truncated)
            } else if (file.length() <= 1024 * 1024) {
                val head = ByteArray(4096)
                val n = file.inputStream().use { it.read(head) }
                val looksText = (0 until n).none { head[it] == 0.toByte() }
                if (looksText) {
                    val t = FileRepo.readText(file)
                    Result.Text(t.text, t.truncated)
                } else {
                    Result.Binary
                }
            } else {
                Result.Binary
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Cannot open")
        }
    }
    when (val r = info) {
        Result.Loading, Result.Binary, is Result.Error -> FileInfoCard(
            file = file,
            extra = (r as? Result.Error)?.msg,
            onEdit = null,
            onHex = onHex,
        )
        is Result.Text -> Column(Modifier.fillMaxSize()) {
            if (r.truncated) {
                Text(
                    "Preview limited to 200k chars — Edit shows full file",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
            SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    r.text.ifBlank { "(empty file)" },
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Row(Modifier.padding(12.dp)) {
                Button(onEdit) { Text("Open in editor") }
            }
        }
    }
}

private sealed interface Result {
    data object Loading : Result
    data object Binary : Result
    data class Error(val msg: String) : Result
    data class Text(val text: String, val truncated: Boolean) : Result
}

@Composable
fun FileInfoCard(file: File, extra: String?, onEdit: (() -> Unit)?, onHex: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val sums by produceState(mapOf<String, String>(), file.absolutePath) {
        value = try {
            mapOf(
                "MD5" to FileRepo.checksum(file, "MD5"),
                "SHA-256" to FileRepo.checksum(file, "SHA-256"),
            )
        } catch (_: Exception) {
            emptyMap()
        }
    }
    val mode by produceState("…", file.absolutePath) {
        value = FileRepo.modeOf(file)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(file.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(mimeOf(file), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                InfoLine("Size", humanSize(file.length()))
                InfoLine("Modified", fmtDate(file.lastModified))
                InfoLine("Permissions", mode)
                InfoLine("Path", file.absolutePath)
                extra?.let { InfoLine("Note", it) }
                sums.forEach { (k, v) -> InfoLine(k, v) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            OutlinedButton({ ctx.shareFiles(listOf(file)) }) { Text("Share") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton({
                try {
                    ctx.openWith(file)
                } catch (_: Exception) {
                }
            }) { Text("Open with…") }
            if (onEdit != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onEdit) { Text("Edit") }
            }
            if (onHex != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onHex) { Text("Hex") }
            }
        }
    }
}

@Composable
fun InfoLine(k: String, v: String) {
    SelectionContainer {
        Text(
            "$k: $v",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}
