package com.fmx.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLConnection

fun mimeOf(file: File): String {
    val ext = file.extension.lowercase()
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    URLConnection.guessContentTypeFromName(file.name)?.let { return it }
    return "application/octet-stream"
}

fun Context.contentUri(file: File): Uri =
    FileProvider.getUriForFile(this, "com.fmx.manager.provider", file)

fun Context.openWith(file: File) {
    val uri = contentUri(file)
    val i = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(i, "Open with"))
}

fun Context.shareFiles(files: List<File>) {
    if (files.isEmpty()) return
    val uris = files.map { contentUri(it) }
    val i = Intent().apply {
        action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        if (uris.size == 1) {
            putExtra(Intent.EXTRA_STREAM, uris[0])
            type = mimeOf(files[0])
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            type = "*/*"
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(i, "Share"))
}

fun Context.installApk(file: File) {
    val uri = contentUri(file)
    val i = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(i)
}
