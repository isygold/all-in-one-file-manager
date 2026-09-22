package com.fmx.manager

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile

/**
 * Power tools merged from ZArchiver / MT Manager / MiXplorer:
 * 7z read+extract, password ZIP (AES), AES file vault, split/merge,
 * batch rename, hex edit, folder sizing.
 */
object PowerTools {

    fun isSevenZ(f: File) = f.name.lowercase().endsWith(".7z")

    suspend fun sevenZipEntries(file: File, max: Int = 3000): List<ArchiveEntryInfo> =
        withContext(Dispatchers.IO) {
            SevenZFile(file).use { z ->
                z.entries.take(max).map {
                    ArchiveEntryInfo(it.name, it.size.coerceAtLeast(0), it.isDirectory)
                }
            }
        }

    suspend fun extract7z(archive: File, dst: File): Int = withContext(Dispatchers.IO) {
        var n = 0
        SevenZFile(archive).use { z ->
            val base = dst.canonicalPath
            for (e in z.entries) {
                val target = File(dst, e.name)
                if (!target.canonicalPath.startsWith(base)) continue
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

    suspend fun extractOne7z(archive: File, inner: String, dst: File): File =
        withContext(Dispatchers.IO) {
            SevenZFile(archive).use { z ->
                val e = z.entries.firstOrNull { it.name == inner }
                    ?: throw IllegalStateException("Entry not found")
                val target = File(dst, File(inner).name)
                target.parentFile?.mkdirs()
                z.getInputStream(e).use { ins ->
                    FileOutputStream(target).use { out -> ins.copyTo(out) }
                }
                target
            }
        }

    // ------------------------------------------------ password ZIP (AES via zip4j)

    suspend fun isZipEncrypted(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipFile(file.absolutePath).isEncrypted
        } catch (_: Exception) {
            false
        }
    }

    suspend fun createZipEncrypted(srcs: List<File>, out: File, password: String) =
        withContext(Dispatchers.IO) {
            val zf = ZipFile(out.absolutePath, password.toCharArray())
            val p = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
            }
            srcs.forEach { if (it.isDirectory) zf.addFolder(it, p) else zf.addFile(it, p) }
        }

    suspend fun extractZipEncrypted(archive: File, dst: File, password: String): Int =
        withContext(Dispatchers.IO) {
            val zf = ZipFile(archive.absolutePath, password.toCharArray())
            zf.extractAll(dst.absolutePath)
            zf.fileHeaders.size
        }

    suspend fun extractOneZipEncrypted(archive: File, inner: String, dst: File, password: String): File =
        withContext(Dispatchers.IO) {
            val zf = ZipFile(archive.absolutePath, password.toCharArray())
            val h = zf.getFileHeader(inner) ?: throw IllegalStateException("Entry not found")
            val target = File(dst, File(inner).name)
            target.parentFile?.mkdirs()
            zf.extractFile(h, dst.absolutePath, File(inner).name)
            target
        }

    // ------------------------------------------------ AES file vault (MiX-style encrypt)

    private fun keyFrom(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 20_000, 256)
        return SecretKeySpec(f.generateSecret(spec).encoded, "AES")
    }

    /** Encrypts to <name>.aes (salt+iv prepended). */
    suspend fun aesEncrypt(src: File, password: String): File = withContext(Dispatchers.IO) {
        val rnd = java.security.SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyFrom(password, salt), GCMParameterSpec(128, iv))
        val out = File(src.parentFile, src.name + ".aes")
        FileOutputStream(out).use { fo ->
            fo.write(salt)
            fo.write(iv)
            FileInputStream(src).use { fi ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = fi.read(buf)
                    if (n < 0) break
                    val enc = cipher.update(buf, 0, n)
                    if (enc != null) fo.write(enc)
                }
            }
            fo.write(cipher.doFinal())
        }
        out
    }

    /** Decrypts a .aes file made by [aesEncrypt]. */
    suspend fun aesDecrypt(src: File, password: String): File = withContext(Dispatchers.IO) {
        FileInputStream(src).use { fi ->
            val salt = ByteArray(16).also { fi.read(it) }
            val iv = ByteArray(12).also { fi.read(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyFrom(password, salt), GCMParameterSpec(128, iv))
            val base = src.name.removeSuffix(".aes")
            val out = File(src.parentFile, base.ifBlank { "decrypted.bin" })
            FileOutputStream(out).use { fo ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = fi.read(buf)
                    if (n < 0) break
                    val dec = cipher.update(buf, 0, n)
                    if (dec != null) fo.write(dec)
                }
                fo.write(cipher.doFinal())
            }
            out
        }
    }

    // ------------------------------------------------ split & merge

    suspend fun splitFile(src: File, partBytes: Long, outDir: File): List<File> =
        withContext(Dispatchers.IO) {
            outDir.mkdirs()
            val parts = ArrayList<File>()
            FileInputStream(src).use { fi ->
                var idx = 1
                val buf = ByteArray(1024 * 256)
                var current: FileOutputStream? = null
                var written = 0L
                while (true) {
                    val n = fi.read(buf)
                    if (n < 0) break
                    var off = 0
                    while (off < n) {
                        if (current == null) {
                            val p = File(outDir, "%s.%03d".format(src.name, idx++))
                            current = FileOutputStream(p)
                            parts += p
                            written = 0
                        }
                        val room = (partBytes - written).coerceAtLeast(1)
                        val take = minOf(room, (n - off).toLong()).toInt()
                        current!!.write(buf, off, take)
                        off += take
                        written += take
                        if (written >= partBytes) {
                            current!!.close()
                            current = null
                        }
                    }
                }
                current?.close()
            }
            parts
        }

    suspend fun mergeParts(firstPart: File, outDir: File): File = withContext(Dispatchers.IO) {
        val m = Regex("""^(.*)\.(\d{3})$""").matchEntire(firstPart.name)
            ?: throw IllegalStateException("Not a .NNN part")
        val base = m.groupValues[1]
        outDir.mkdirs()
        val out = File(outDir, base)
        FileOutputStream(out).use { fo ->
            var idx = 1
            while (true) {
                val p = File(firstPart.parentFile, "%s.%03d".format(base, idx++))
                if (!p.exists()) break
                FileInputStream(p).use { it.copyTo(fo) }
            }
        }
        out
    }

    // ------------------------------------------------ hex edit (MT-style)

    suspend fun writeByte(file: File, offset: Long, value: Int) =
        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "rw").use {
                it.seek(offset)
                it.write(value and 0xFF)
            }
        }

    // ------------------------------------------------ batch rename

    /**
     * Pattern tokens: {n} counter (starting at [start]), {name} old name w/o ext,
     * {ext} extension. If pattern has no {n}/{name}, it is used as prefix + old name.
     */
    suspend fun batchRename(
        files: List<File>,
        pattern: String,
        find: String = "",
        replace: String = "",
        start: Int = 1,
    ): Int = withContext(Dispatchers.IO) {
        var i = start
        var done = 0
        files.sortedBy { it.name }.forEach { f ->
            var base = f.nameWithoutExtension
            if (find.isNotEmpty()) base = base.replace(find, replace)
            var nb = pattern
                .replace("{n}", (i++).toString())
                .replace("{name}", base)
                .replace("{ext}", f.extension)
            if (!pattern.contains("{n}") && !pattern.contains("{name}")) nb = pattern + base
            val nn = if (f.extension.isNotEmpty() && !nb.endsWith(".${f.extension}")) {
                "$nb.${f.extension}"
            } else nb
            if (nn != f.name) {
                val t = File(f.parentFile, nn)
                if (!t.exists() && f.renameTo(t)) done++
            }
        }
        done
    }

    // ------------------------------------------------ sizing (analyzer)

    suspend fun dirSize(dir: File, maxVisit: Int = 20000): Long =
        withContext(Dispatchers.IO) {
            var total = 0L
            var visited = 0
            try {
                dir.walkTopDown().onFail { _, _ -> }.forEach {
                    if (++visited > maxVisit) return@withContext total
                    if (it.isFile) total += it.length()
                }
            } catch (_: Exception) {
            }
            total
        }
}
