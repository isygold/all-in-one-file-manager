package com.fmx.manager

import android.system.Os
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

/** All filesystem work. Every function is IO-bound; call from Dispatchers.IO. */
object FileRepo {

    suspend fun listDir(
        dir: File,
        sort: SortMode,
        asc: Boolean,
        showHidden: Boolean,
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val kids = dir.listFiles()?.toList() ?: throw SecurityException("Cannot list ${dir.path}")
        val items = kids
            .filter { showHidden || !it.name.startsWith(".") }
            .mapNotNull { f ->
                try {
                    FileItem(f)
                } catch (_: Exception) {
                    null
                }
            }
        val base: Comparator<FileItem> = when (sort) {
            SortMode.NAME -> compareBy { it.name.lowercase() }
            SortMode.SIZE -> compareBy { it.size }
            SortMode.DATE -> compareBy { it.lastModified }
            SortMode.TYPE -> compareBy<FileItem> { it.ext }.thenBy { it.name.lowercase() }
        }
        // Folders first, like MT Manager / ZArchiver.
        val cmp = compareBy<FileItem> { !it.isDir }.then(base)
        val sorted = items.sortedWith(cmp)
        if (asc) sorted else sorted.reversed()
    }

    suspend fun search(base: File, query: String, max: Int = 300): List<FileItem> =
        withContext(Dispatchers.IO) {
            val q = query.lowercase()
            val out = ArrayList<FileItem>()
            var visited = 0
            try {
                base.walkTopDown()
                    .onFail { _, _ -> }
                    .forEach { f ->
                        if (++visited > 40000) return@forEach
                        if (f != base && f.name.lowercase().contains(q)) {
                            try {
                                out += FileItem(f)
                            } catch (_: Exception) {
                            }
                            if (out.size >= max) return@withContext out
                        }
                    }
            } catch (_: Exception) {
            }
            out
        }

    /** MiX-style content search: text files containing the query. */
    suspend fun searchContent(base: File, query: String, maxFiles: Int = 100): List<FileItem> =
        withContext(Dispatchers.IO) {
            val q = query.lowercase()
            val out = ArrayList<FileItem>()
            var visited = 0
            val deadline = System.currentTimeMillis() + 8000
            try {
                base.walkTopDown()
                    .onFail { _, _ -> }
                    .forEach { f ->
                        if (++visited > 15000 || System.currentTimeMillis() > deadline) return@withContext out
                        if (!f.isFile || f.length() > 1024 * 1024) return@forEach
                        val ext = f.extension.lowercase()
                        if (ext !in TEXT_HINT && ext !in setOf(
                                "java", "kt", "c", "h", "cpp", "hpp", "cs", "go", "rs",
                                "php", "rb", "swift", "ini", "cfg", "conf", "text",
                            )
                        ) return@forEach
                        try {
                            f.bufferedReader(Charsets.UTF_8).useLines { lines ->
                                if (lines.any { it.lowercase().contains(q) }) {
                                    out += FileItem(f)
                                }
                            }
                        } catch (_: Exception) {
                        }
                        if (out.size >= maxFiles) return@withContext out
                    }
            } catch (_: Exception) {
            }
            out
        }

    private val TEXT_HINT = setOf(
        "txt", "md", "py", "js", "ts", "html", "css", "json", "kt",
        "sh", "xml", "yml", "yaml", "toml", "gradle",
        "properties", "log", "csv", "sql",
    )

    suspend fun mkdir(parent: File, name: String): File = withContext(Dispatchers.IO) {
        val d = File(parent, name)
        if (!d.mkdir()) throw IllegalStateException("Could not create $name")
        d
    }

    suspend fun mkfile(parent: File, name: String): File = withContext(Dispatchers.IO) {
        val f = File(parent, name)
        if (!f.createNewFile()) throw IllegalStateException("$name already exists")
        f
    }

    suspend fun rename(file: File, newName: String): File = withContext(Dispatchers.IO) {
        val t = File(file.parentFile, newName)
        if (!file.renameTo(t)) throw IllegalStateException("Rename failed")
        t
    }

    suspend fun delete(files: List<File>) = withContext(Dispatchers.IO) {
        files.forEach { f ->
            if (f.isDirectory) {
                f.walkBottomUp().forEach { if (!it.delete()) throw IllegalStateException("Delete failed: ${it.name}") }
            } else if (!f.delete()) {
                throw IllegalStateException("Delete failed: ${f.name}")
            }
        }
    }

    suspend fun copyTo(srcs: List<File>, dstDir: File) = withContext(Dispatchers.IO) {
        srcs.forEach { s -> copyRec(s, File(dstDir, s.name)) }
    }

    suspend fun moveTo(srcs: List<File>, dstDir: File) = withContext(Dispatchers.IO) {
        srcs.forEach { s ->
            val t = File(dstDir, s.name)
            if (s.canonicalPath == t.canonicalPath) return@forEach
            if (!s.renameTo(t)) {
                copyRec(s, t)
                delete(listOf(s))
            }
        }
    }

    private fun copyRec(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyRec(it, File(dst, it.name)) }
        } else {
            dst.parentFile?.mkdirs()
            FileInputStream(src).use { ins ->
                FileOutputStream(dst).use { out -> ins.copyTo(out) }
            }
        }
    }

    suspend fun chmod(files: List<File>, octal: String) = withContext(Dispatchers.IO) {
        val mode = octal.toInt(8)
        files.forEach { Os.chmod(it.absolutePath, mode) }
    }

    suspend fun modeOf(file: File): String = withContext(Dispatchers.IO) {
        try {
            String.format("%o", Os.stat(file.absolutePath).st_mode and 0xFFF)
        } catch (_: Exception) {
            "---"
        }
    }

    suspend fun checksum(file: File, algo: String): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance(algo)
        BufferedInputStream(FileInputStream(file)).use { ins ->
            val buf = ByteArray(1024 * 256)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    data class TextLoad(val text: String, val truncated: Boolean)

    suspend fun readText(file: File, maxChars: Int = 200_000): TextLoad =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            var truncated = false
            file.bufferedReader(Charsets.UTF_8).use { r ->
                val buf = CharArray(8192)
                while (sb.length < maxChars) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (sb.length >= maxChars) {
                        truncated = true
                        // drain check: is there more?
                        if (r.read() >= 0) truncated = true
                        break
                    }
                }
            }
            TextLoad(if (sb.length > maxChars) sb.substring(0, maxChars) else sb.toString(), truncated)
        }

    suspend fun saveText(file: File, text: String) = withContext(Dispatchers.IO) {
        file.writeText(text, Charsets.UTF_8)
    }

    suspend fun hexDump(file: File, offset: Long, len: Int): List<String> =
        withContext(Dispatchers.IO) {
            val lines = ArrayList<String>()
            FileInputStream(file).use { ins ->
                var skipped = 0L
                while (skipped < offset) {
                    val s = ins.skip(offset - skipped)
                    if (s <= 0) break
                    skipped += s
                }
                val buf = ByteArray(16)
                var addr = offset
                var remaining = len
                while (remaining > 0) {
                    val n = ins.read(buf, 0, minOf(16, remaining))
                    if (n <= 0) break
                    val hex = (0 until n).joinToString(" ") { "%02x".format(buf[it]) }
                    val asc = (0 until n).joinToString("") {
                        val c = buf[it].toInt() and 0xFF
                        if (c in 32..126) c.toChar().toString() else "."
                    }
                    lines += "%08x  %-48s |%s|".format(addr, hex, asc)
                    addr += n
                    remaining -= n
                }
            }
            lines
        }

    // ---------------------------------------------------------- archives

    suspend fun zipEntries(file: File, max: Int = 3000): List<ArchiveEntryInfo> =
        withContext(Dispatchers.IO) {
            ZipFile(file).use { z ->
                z.entries().asSequence().take(max).map {
                    ArchiveEntryInfo(it.name, it.size.coerceAtLeast(0), it.isDirectory)
                }.toList()
            }
        }

    suspend fun tarEntries(file: File, max: Int = 3000): List<ArchiveEntryInfo> =
        withContext(Dispatchers.IO) {
            openTarIn(file).use { tin ->
                val out = ArrayList<ArchiveEntryInfo>()
                while (out.size < max) {
                    val e = tin.nextTarEntry ?: break
                    out += ArchiveEntryInfo(e.name, e.size.coerceAtLeast(0), e.isDirectory)
                }
                out
            }
        }

    private fun openTarIn(file: File): TarArchiveInputStream {
        val raw = BufferedInputStream(FileInputStream(file))
        val decomp: java.io.InputStream = when (tarKind(file)) {
            "gz" -> GzipCompressorInputStream(raw)
            "bz2" -> BZip2CompressorInputStream(raw)
            "xz" -> XZCompressorInputStream(raw)
            else -> raw
        }
        return TarArchiveInputStream(decomp)
    }

    suspend fun createZip(srcs: List<File>, out: File) = withContext(Dispatchers.IO) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            srcs.forEach { addToZip(z, it, it.name) }
        }
    }

    private fun addToZip(z: ZipOutputStream, f: File, entryName: String) {
        if (f.isDirectory) {
            f.listFiles()?.forEach { addToZip(z, it, "$entryName/${it.name}") }
        } else {
            z.putNextEntry(ZipEntry(entryName))
            FileInputStream(f).use { it.copyTo(z) }
            z.closeEntry()
        }
    }

    suspend fun createTar(srcs: List<File>, out: File, kind: String) =
        withContext(Dispatchers.IO) {
            val raw = BufferedOutputStream(FileOutputStream(out))
            val comp: java.io.OutputStream = when (kind) {
                "gz" -> GzipCompressorOutputStream(raw)
                "bz2" -> BZip2CompressorOutputStream(raw)
                "xz" -> XZCompressorOutputStream(raw)
                else -> raw
            }
            TarArchiveOutputStream(comp).use { t ->
                t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                srcs.forEach { addToTar(t, it, it.name) }
                t.finish()
            }
        }

    private fun addToTar(t: TarArchiveOutputStream, f: File, entryName: String) {
        val e = TarArchiveEntry(f, entryName)
        t.putArchiveEntry(e)
        if (f.isFile) FileInputStream(f).use { it.copyTo(t) }
        t.closeArchiveEntry()
        if (f.isDirectory) f.listFiles()?.forEach { addToTar(t, it, "$entryName/${it.name}") }
    }

    suspend fun extractZip(archive: File, dst: File): Int = withContext(Dispatchers.IO) {
        var n = 0
        ZipFile(archive).use { z ->
            val base = dst.canonicalPath
            z.entries().asSequence().forEach { e ->
                val target = File(dst, e.name)
                if (!target.canonicalPath.startsWith(base)) return@forEach // zip-slip guard
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    z.getInputStream(e).use { ins ->
                        FileOutputStream(target).use { out -> ins.copyTo(out) }
                    }
                }
                n++
            }
        }
        n
    }

    suspend fun extractTar(archive: File, dst: File): Int = withContext(Dispatchers.IO) {
        var n = 0
        openTarIn(archive).use { tin ->
            val base = dst.canonicalPath
            while (true) {
                val e = tin.nextTarEntry ?: break
                val target = File(dst, e.name)
                if (!target.canonicalPath.startsWith(base)) continue // slip guard
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out -> tin.copyTo(out) }
                }
                n++
            }
        }
        n
    }

    suspend fun extractOneZip(archive: File, inner: String, dst: File): File =
        withContext(Dispatchers.IO) {
            ZipFile(archive).use { z ->
                val e = z.getEntry(inner) ?: throw IllegalStateException("Entry not found")
                val target = File(dst, File(inner).name)
                target.parentFile?.mkdirs()
                z.getInputStream(e).use { ins ->
                    FileOutputStream(target).use { out -> ins.copyTo(out) }
                }
                target
            }
        }
}
