package com.fmx.manager

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One row in the browser list. */
data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDir: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val ext: String = if (file.isDirectory) "" else file.extension.lowercase(),
)

enum class SortMode { NAME, SIZE, DATE, TYPE }

data class ArchiveEntryInfo(
    val name: String,
    val size: Long,
    val isDir: Boolean,
)

data class ApkDetails(
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val label: String,
    val dexFiles: List<String>,
    val soCount: Int,
    val entryCount: Int,
    val md5: String,
)

sealed interface ClipOp {
    data class Copy(val paths: List<String>) : ClipOp
    data class Cut(val paths: List<String>) : ClipOp
}

fun humanSize(n: Long): String {
    if (n < 1024) return "${n}B"
    var v = n / 1024.0
    for (u in arrayOf("KB", "MB", "GB", "TB")) {
        if (v < 1024) return String.format(Locale.US, "%.1f%s", v, u)
        v /= 1024.0
    }
    return String.format(Locale.US, "%.1fPB", v)
}

private val dateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
fun fmtDate(ms: Long): String = try {
    dateFmt.format(Date(ms))
} catch (_: Exception) {
    "-"
}

val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")
val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "3gp", "avi")
val AUDIO_EXTS = setOf("mp3", "ogg", "m4a", "flac", "wav", "opus")
val TEXT_EXTS = setOf(
    "txt", "md", "py", "js", "ts", "html", "css", "json", "java", "kt",
    "c", "cpp", "h", "sh", "xml", "yml", "yaml", "toml", "gradle",
    "properties", "log", "csv", "sql",
)

fun isZipArchive(f: File): Boolean {
    val n = f.name.lowercase()
    return n.endsWith(".zip") || n.endsWith(".apk") || n.endsWith(".jar")
}

fun tarKind(f: File): String? {
    val n = f.name.lowercase()
    return when {
        n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "gz"
        n.endsWith(".tar.bz2") || n.endsWith(".tbz2") -> "bz2"
        n.endsWith(".tar.xz") || n.endsWith(".txz") -> "xz"
        n.endsWith(".tar") -> "tar"
        else -> null
    }
}

fun isArchive(f: File): Boolean = isZipArchive(f) || tarKind(f) != null
