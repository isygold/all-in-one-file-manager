package com.fmx.manager

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TrashEntry(
    val id: String,
    val name: String,
    val origPath: String,
    val time: Long,
    val isDir: Boolean,
)

/** Recycle bin (MiX-style): deletes park here first, restore or wipe later. */
object TrashRepo {
    private fun dir(ctx: Context) = File(ctx.filesDir, "trash").apply { mkdirs() }
    private fun meta(ctx: Context) = File(dir(ctx), ".meta.json")

    suspend fun list(ctx: Context): List<TrashEntry> = withContext(Dispatchers.IO) {
        readMeta(ctx).sortedByDescending { it.time }
    }

    suspend fun moveToTrash(ctx: Context, files: List<File>): Int =
        withContext(Dispatchers.IO) {
            val d = dir(ctx)
            val meta = readMeta(ctx).toMutableList()
            var n = 0
            files.forEach { f ->
                if (!f.exists()) return@forEach
                val id = "${System.currentTimeMillis()}_${f.name}"
                val target = File(d, id)
                if (f.renameTo(target)) {
                    meta += TrashEntry(id, f.name, f.absolutePath, System.currentTimeMillis(), f.isDirectory)
                    n++
                } else {
                    // cross-volume fallback: copy + delete
                    try {
                        FileRepo.copyTo(listOf(f), d)
                        File(d, f.name).renameTo(target)
                        FileRepo.delete(listOf(f))
                        meta += TrashEntry(id, f.name, f.absolutePath, System.currentTimeMillis(), f.isDirectory)
                        n++
                    } catch (_: Exception) {
                    }
                }
            }
            writeMeta(ctx, meta)
            prune(ctx, meta)
            n
        }

    suspend fun restore(ctx: Context, e: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        val src = File(dir(ctx), e.id)
        if (!src.exists()) return@withContext false
        var dest = File(e.origPath)
        if (dest.exists()) {
            dest = File(dest.parentFile, e.name + "_restored")
        }
        dest.parentFile?.mkdirs()
        val ok = src.renameTo(dest)
        if (ok) writeMeta(ctx, readMeta(ctx).filter { it.id != e.id })
        ok
    }

    suspend fun deleteForever(ctx: Context, entries: List<TrashEntry>) =
        withContext(Dispatchers.IO) {
            entries.forEach { File(dir(ctx), it.id).deleteRecursively() }
            val ids = entries.map { it.id }.toSet()
            writeMeta(ctx, readMeta(ctx).filter { it.id !in ids })
        }

    suspend fun emptyAll(ctx: Context) = withContext(Dispatchers.IO) {
        dir(ctx).listFiles()?.forEach { if (it.name != ".meta.json") it.deleteRecursively() }
        writeMeta(ctx, emptyList())
    }

    private fun readMeta(ctx: Context): List<TrashEntry> {
        val f = meta(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TrashEntry(
                    o.getString("id"), o.getString("name"), o.getString("orig"),
                    o.getLong("time"), o.optBoolean("dir", false),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeMeta(ctx: Context, list: List<TrashEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("orig", it.origPath).put("time", it.time).put("dir", it.isDir),
            )
        }
        meta(ctx).writeText(arr.toString())
    }

    /** Auto-empty items older than 30 days. */
    private fun prune(ctx: Context, meta: List<TrashEntry>) {
        val cut = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        val old = meta.filter { it.time < cut }
        if (old.isEmpty()) return
        old.forEach { File(dir(ctx), it.id).deleteRecursively() }
        writeMeta(ctx, meta.filter { it.time >= cut })
    }
}
